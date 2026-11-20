package com.saathchalo.reviews;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.PageResult;
import com.saathchalo.reviews.ReviewDtos.PendingReview;
import com.saathchalo.reviews.ReviewDtos.ReviewPage;
import com.saathchalo.reviews.ReviewDtos.ReviewView;
import com.saathchalo.reviews.ReviewDtos.WriteRequest;
import com.saathchalo.reviews.internal.Review;
import com.saathchalo.reviews.internal.ReviewRepository;
import com.saathchalo.rides.BookingStatus;
import com.saathchalo.rides.RideLookup;
import com.saathchalo.rides.RideLookup.FinishedTrip;
import com.saathchalo.rides.RideLookup.RideBrief;
import com.saathchalo.rides.RideLookup.RideParticipants;
import com.saathchalo.rides.RideLookup.Seat;
import com.saathchalo.rides.RideStatus;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import com.saathchalo.user.RatingProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reviews after a completed trip: the driver reviews each passenger, and each passenger reviews the driver, once,
 * within 14 days. Only real trips can be reviewed, so ratings can't be faked. Ratings show on profiles and rides.
 */
@Service
public class ReviewService implements RatingProvider {

	static final Duration WINDOW = Duration.ofDays(14);

	private final ReviewRepository reviews;

	private final RideLookup rides;

	private final MemberProfiles members;

	private final NamedParameterJdbcTemplate jdbc;

	private final Clock clock;

	public ReviewService(ReviewRepository reviews, RideLookup rides, MemberProfiles members,
			NamedParameterJdbcTemplate jdbc, Clock clock) {
		this.reviews = reviews;
		this.rides = rides;
		this.members = members;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional
	public ReviewView write(UUID me, WriteRequest req) {
		if (me.equals(req.subjectId())) {
			throw ApiException.badRequest("self_review", "You can't review yourself.");
		}
		RideParticipants trip = rides.participants(req.rideId())
			.orElseThrow(() -> ApiException.notFound("ride_not_found", "We couldn't find that trip."));
		RideBrief ride = trip.ride();
		if (ride.status() != RideStatus.COMPLETED) {
			throw ApiException.conflict("trip_not_finished", "You can review your co-travellers once the trip is over.");
		}
		if (clock.instant().isAfter(ride.arrivalAt().plus(WINDOW))) {
			throw ApiException.conflict("review_window_closed", "Reviews can be written up to 14 days after a trip.");
		}
		String subjectRole;
		if (trip.isDriver(me) && travelled(trip.passengers().get(req.subjectId()))) {
			subjectRole = "PASSENGER";
		}
		else if (travelled(trip.passengers().get(me)) && trip.isDriver(req.subjectId())) {
			subjectRole = "DRIVER";
		}
		else {
			throw ApiException.forbidden("not_your_trip", "You can only review people you travelled with.");
		}
		if (reviews.existsByRideIdAndAuthorIdAndSubjectId(ride.id(), me, req.subjectId())) {
			throw ApiException.conflict("already_reviewed", "You've already reviewed this person for this trip.");
		}
		String comment = req.comment() == null || req.comment().isBlank() ? null : req.comment().strip();
		Review saved = reviews.save(new Review(ride.id(), me, req.subjectId(), subjectRole, req.rating(), comment, clock.instant()));
		return view(saved, members.card(me), ride);
	}

	/** People this member travelled with in the last 14 days and hasn't reviewed yet. */
	@Transactional(readOnly = true)
	public List<PendingReview> pending(UUID me) {
		List<FinishedTrip> trips = rides.finishedTrips(me, clock.instant().minus(WINDOW));
		if (trips.isEmpty()) {
			return List.of();
		}
		Set<String> done = new HashSet<>();
		reviews.findByAuthorIdAndRideIdIn(me, trips.stream().map(t -> t.ride().id()).toList())
			.forEach(r -> done.add(r.getRideId() + "/" + r.getSubjectId()));
		List<UUID> mates = trips.stream().flatMap(t -> t.mates().stream()).toList();
		Map<UUID, MemberCard> cards = members.cards(mates);
		List<PendingReview> pending = new ArrayList<>();
		for (FinishedTrip t : trips) {
			for (UUID mate : t.mates()) {
				if (!mate.equals(me) && !done.contains(t.ride().id() + "/" + mate)) {
					pending.add(new PendingReview(t.ride(), cards.getOrDefault(mate, MemberProfiles.deleted()),
							t.asDriver() ? "PASSENGER" : "DRIVER"));
				}
			}
		}
		return pending;
	}

	/** Public reviews about a member, newest first, with their average rating. */
	@Transactional(readOnly = true)
	public ReviewPage about(UUID memberId, int page, int size) {
		int pageSize = Math.min(PageResult.clampSize(size), 50);
		Page<Review> found = reviews.findBySubjectIdAndHiddenAtIsNullOrderByCreatedAtDesc(memberId,
				PageRequest.of(Math.max(0, page), pageSize));
		Map<UUID, MemberCard> authors = members.cards(found.stream().map(Review::getAuthorId).toList());
		Map<UUID, RideBrief> briefs = rides.briefs(found.stream().map(Review::getRideId).toList());
		List<ReviewView> items = found.stream()
			.map(r -> view(r, authors.getOrDefault(r.getAuthorId(), MemberProfiles.deleted()), briefs.get(r.getRideId())))
			.toList();
		Rating rating = ratings(List.of(memberId)).get(memberId);
		return new ReviewPage(rating == null ? null : rating.average(), rating == null ? 0 : rating.count(), items,
				found.getTotalElements(), found.getNumber(), pageSize);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<UUID, Rating> ratings(Collection<UUID> memberIds) {
		Map<UUID, Rating> out = new HashMap<>();
		if (memberIds.isEmpty()) {
			return out;
		}
		jdbc.query("""
				SELECT subject_id, avg(rating) AS average, count(*) AS n FROM reviews
				WHERE hidden_at IS NULL AND subject_id IN (:ids) GROUP BY subject_id
				""", new MapSqlParameterSource("ids", memberIds.stream().distinct().toList()),
				(rs, n) -> out.put(rs.getObject("subject_id", UUID.class),
						new Rating(Math.round(rs.getDouble("average") * 10) / 10.0, rs.getInt("n"))));
		return out;
	}

	/** A public review someone reports. */
	public record ReportedReview(UUID reviewId, UUID authorId, UUID subjectId, int rating, String comment) {
	}

	@Transactional(readOnly = true)
	public java.util.Optional<ReportedReview> reviewForReport(UUID reviewId) {
		return reviews.findById(reviewId).filter(r -> r.getHiddenAt() == null)
			.map(r -> new ReportedReview(r.getId(), r.getAuthorId(), r.getSubjectId(), r.getRating(), r.getComment()));
	}

	/** Staff remove a review that breaks the rules; it no longer shows or counts towards the rating. */
	@Transactional
	public boolean hideReview(UUID reviewId) {
		return reviews.findById(reviewId).map(r -> {
			r.hide(clock.instant());
			return true;
		}).orElse(false);
	}

	private static boolean travelled(Seat seat) {
		return seat != null && seat.status() == BookingStatus.COMPLETED;
	}

	private static ReviewView view(Review r, MemberCard author, RideBrief ride) {
		return new ReviewView(r.getId(), author, r.getSubjectRole(), r.getRating(), r.getComment(),
				ride == null ? null : ride.fromName(), ride == null ? null : ride.toName(), r.getCreatedAt());
	}
}
