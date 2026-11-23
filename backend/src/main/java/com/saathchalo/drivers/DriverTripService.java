package com.saathchalo.drivers;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.drivers.DriverDtos.MyTrips;
import com.saathchalo.drivers.DriverDtos.Quote;
import com.saathchalo.drivers.DriverDtos.ReviewRequest;
import com.saathchalo.drivers.DriverDtos.TripKind;
import com.saathchalo.drivers.DriverDtos.TripRequest;
import com.saathchalo.drivers.DriverDtos.TripView;
import com.saathchalo.drivers.DriverProfileService.Stats;
import com.saathchalo.drivers.internal.DriverProfile;
import com.saathchalo.drivers.internal.DriverProfileRepository;
import com.saathchalo.drivers.internal.DriverTrip;
import com.saathchalo.drivers.internal.DriverTripRepository;
import com.saathchalo.geo.Place;
import com.saathchalo.geo.PlaceDirectory;
import com.saathchalo.payments.PaymentDtos.CheckoutView;
import com.saathchalo.payments.PaymentService;
import com.saathchalo.payments.PaymentService.StartPayment;
import com.saathchalo.user.BlockService;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hiring a driver: the request, the driver's answer, paying, "on my way", the start (the member's 4-digit code proves
 * the right driver came), the end, and reviews. The money is handled by payments ({@link DriverPayments} says who gets
 * what).
 */
@Service
public class DriverTripService {

	private static final Set<String> NOT_STARTED = Set.of(DriverTrip.REQUESTED, DriverTrip.ACCEPTED, DriverTrip.PAID,
			DriverTrip.ON_THE_WAY);

	private final DriverTripRepository trips;

	private final DriverProfileRepository profiles;

	private final DriverProfileService drivers;

	private final MemberProfiles members;

	private final BlockService blocks;

	private final PlaceDirectory places;

	private final PaymentService payments;

	private final AuditService audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	private final DriverNotices notices;

	public DriverTripService(DriverTripRepository trips, DriverProfileRepository profiles, DriverProfileService drivers,
			MemberProfiles members, BlockService blocks, PlaceDirectory places, PaymentService payments, AuditService audit,
			JdbcTemplate jdbc, Clock clock, DriverNotices notices) {
		this.notices = notices;
		this.trips = trips;
		this.profiles = profiles;
		this.drivers = drivers;
		this.members = members;
		this.blocks = blocks;
		this.places = places;
		this.payments = payments;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// ----- The member who hires -----

	@Transactional
	public TripView request(UUID me, TripRequest req) {
		if (!members.traveller(me).profileComplete()) {
			throw ApiException.forbidden("profile_incomplete", "Add your first name and gender to your profile before hiring a driver.");
		}
		DriverProfile d = profiles.findById(req.driverId()).orElseThrow(DriverProfileService::notFound);
		if (blocks.blockedEitherWay(me, d.getUserId())) {
			throw ApiException.forbidden("blocked", "You can't hire this driver.");
		}
		Instant now = clock.instant();
		String problem = DriverRules.periodProblem(req.kind(), req.from(), req.hours(), req.days(), now);
		if (problem != null) {
			throw ApiException.badRequest("bad_period", problem);
		}
		Place place = places.find(req.placeId())
			.orElseThrow(() -> ApiException.badRequest("place_not_found", "Choose the town from the list."));
		Place destination = null;
		if (req.kind() == TripKind.OUTSTATION) {
			if (req.destinationId() == null) {
				throw ApiException.badRequest("destination_missing", "Where are you going? Choose the town.");
			}
			destination = places.find(req.destinationId())
				.orElseThrow(() -> ApiException.badRequest("place_not_found", "Choose the destination from the list."));
			if (destination.id() == place.id()) {
				throw ApiException.badRequest("same_town", "For trips around one town, choose Local.");
			}
		}
		Quote q = DriverRules.quote(d, req.kind(), req.from(), req.hours(), req.days());
		String unavailable = drivers.availabilityProblem(d, place.id(), req.kind(), req.from(), q.to(), req.automatic(), me);
		if (unavailable != null) {
			throw ApiException.conflict("driver_unavailable", unavailable);
		}
		if (trips.countByCustomerIdAndStatusIn(me, Set.of(DriverTrip.REQUESTED)) >= DriverRules.MAX_OPEN_REQUESTS) {
			throw ApiException.tooManyRequests("too_many_requests", "You have several requests waiting. Wait for an answer first.");
		}
		Instant answerBy = min(now.plus(DriverRules.ANSWER_WITHIN), req.from().minus(Duration.ofMinutes(30)));
		DriverTrip t = new DriverTrip(d.getUserId(), me, req.kind().name(), req.from(), q.to(), place.id(), place.name(), place.lat(),
				place.lng(), req.pickupAddress().strip(), destination == null ? null : destination.id(),
				destination == null ? null : destination.name(), req.car().strip(), req.automatic(), q.fare(), q.extraPrice(),
				payments.availableFor(me), startCode(), blankToNull(req.message()), now, answerBy);
		trips.save(t);
		notices.requested(t);
		return view(t, me);
	}

	/** Starts (or resumes) paying online for an accepted trip. */
	public CheckoutView pay(UUID me, UUID id, boolean useCredits) {
		DriverTrip t = trips.findById(id).filter(x -> x.getCustomerId().equals(me)).orElseThrow(DriverTripService::notFound);
		if (!DriverTrip.ACCEPTED.equals(t.getStatus()) || !t.isPayOnline()) {
			throw ApiException.conflict("nothing_to_pay", "There's nothing to pay online for this trip.");
		}
		if (t.getPayBy() == null || !t.getPayBy().isAfter(clock.instant())) {
			throw ApiException.conflict("too_late_to_pay", "The time to pay has passed. Please book again.");
		}
		return payments.startPayment(new StartPayment(DriverPayments.KIND, t.getId(), me, t.getDriverId(), t.getFare() * 100L, 0,
				t.getPayBy(), useCredits));
	}

	// ----- The driver -----

	@Transactional
	public TripView accept(UUID me, UUID id) {
		DriverTrip t = lock(id);
		if (!t.getDriverId().equals(me)) {
			throw notFound();
		}
		if (!DriverTrip.REQUESTED.equals(t.getStatus())) {
			throw ApiException.conflict("not_requested", "This request was already answered or cancelled.");
		}
		Instant now = clock.instant();
		if (!t.getAnswerBy().isAfter(now)) {
			throw ApiException.conflict("too_late", "The time to answer this request has passed.");
		}
		if (!drivers.isFree(me, t.getStartsAt(), t.getEndsAt())) {
			throw ApiException.conflict("busy", "You already have a trip or time off around then.");
		}
		t.accept(now, payBy(t, now));
		try {
			trips.saveAndFlush(t);
		}
		catch (DataIntegrityViolationException e) {
			throw ApiException.conflict("busy", "You already have a trip at that time.");
		}
		notices.answered(t, true);
		return view(t, me);
	}

	@Transactional
	public TripView decline(UUID me, UUID id) {
		DriverTrip t = lock(id);
		if (!t.getDriverId().equals(me)) {
			throw notFound();
		}
		if (!DriverTrip.REQUESTED.equals(t.getStatus())) {
			throw ApiException.conflict("not_requested", "This request was already answered or cancelled.");
		}
		t.decline(clock.instant());
		notices.answered(t, false);
		return view(t, me);
	}

	@Transactional
	public TripView onTheWay(UUID me, UUID id) {
		DriverTrip t = lock(id);
		if (!t.getDriverId().equals(me)) {
			throw notFound();
		}
		if (!(t.isPayOnline() ? DriverTrip.PAID : DriverTrip.ACCEPTED).equals(t.getStatus())) {
			throw ApiException.conflict("not_ready", t.isPayOnline() && DriverTrip.ACCEPTED.equals(t.getStatus())
					? "The member hasn't paid yet." : "This trip isn't waiting for you to set off.");
		}
		if (clock.instant().isBefore(t.getStartsAt().minus(DriverRules.ON_THE_WAY_FROM))) {
			throw ApiException.conflict("too_early", "You can set off from 3 hours before the trip.");
		}
		t.onTheWay(clock.instant());
		return view(t, me);
	}

	// ----- Both sides -----

	/**
	 * Starts the trip: the driver with the 4-digit code the member tells them in person, or the member themselves. Wrong
	 * codes are counted (and kept, hence no rollback); after five the member has to start it.
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public TripView start(UUID me, UUID id, String code) {
		DriverTrip t = lock(id);
		String role = roleOf(t, me);
		boolean ready = t.isPayOnline() ? Set.of(DriverTrip.PAID, DriverTrip.ON_THE_WAY).contains(t.getStatus())
				: Set.of(DriverTrip.ACCEPTED, DriverTrip.ON_THE_WAY).contains(t.getStatus());
		if (!ready) {
			throw ApiException.conflict("not_ready", t.isPayOnline() && DriverTrip.ACCEPTED.equals(t.getStatus())
					? "The trip isn't paid yet." : "This trip can't start now.");
		}
		if (clock.instant().isBefore(t.getStartsAt().minus(DriverRules.START_FROM))) {
			throw ApiException.conflict("too_early", "The trip can start from an hour before the booked time.");
		}
		if ("DRIVER".equals(role)) {
			if (t.getWrongCodes() >= DriverRules.MAX_WRONG_CODES) {
				throw ApiException.conflict("code_locked", "Too many wrong codes. Ask the member to start the trip on their phone.");
			}
			String typed = code == null ? "" : code.strip();
			if (!MessageDigest.isEqual(typed.getBytes(StandardCharsets.UTF_8), t.getStartCode().getBytes(StandardCharsets.UTF_8))) {
				t.wrongCode();
				throw ApiException.badRequest("wrong_code", "That's not the code. Ask the member for the 4-digit code on their trip page.");
			}
		}
		t.start(clock.instant());
		return view(t, me);
	}

	/** Either side ends a started trip. Time over the booking is worked out and paid to the driver directly. */
	@Transactional
	public TripView end(UUID me, UUID id) {
		DriverTrip t = lock(id);
		roleOf(t, me);
		if (!DriverTrip.STARTED.equals(t.getStatus())) {
			throw ApiException.conflict("not_started", "The trip hasn't started.");
		}
		Instant now = clock.instant();
		t.end(now, DriverRules.extraUnits(TripKind.valueOf(t.getKind()), t.getEndsAt(), now));
		return view(t, me);
	}

	/** Before the start. The money follows the rules in {@link DriverPayments}. A driver must say why. */
	@Transactional
	public TripView cancel(UUID me, UUID id, String reason) {
		DriverTrip t = lock(id);
		String role = roleOf(t, me);
		if (!NOT_STARTED.contains(t.getStatus())) {
			throw ApiException.conflict("cant_cancel", "This trip can't be cancelled now.");
		}
		if ("DRIVER".equals(role)) {
			if (DriverTrip.REQUESTED.equals(t.getStatus())) {
				throw ApiException.conflict("decline_instead", "Decline the request instead.");
			}
			if (reason == null || reason.isBlank()) {
				throw ApiException.badRequest("reason_missing", "Tell the member why you can't come.");
			}
		}
		t.cancel(role, blankToNull(reason), clock.instant());
		notices.cancelled(t, role, reason);
		return view(t, me);
	}

	@Transactional
	public TripView review(UUID me, UUID id, ReviewRequest req) {
		DriverTrip t = trips.findById(id).orElseThrow(DriverTripService::notFound);
		String role = roleOf(t, me);
		if (!DriverTrip.COMPLETED.equals(t.getStatus())) {
			throw ApiException.conflict("not_completed", "You can rate the trip once it's over.");
		}
		if (t.getEndedAt().plus(DriverRules.REVIEW_WITHIN).isBefore(clock.instant())) {
			throw ApiException.conflict("too_late", "Ratings close two weeks after the trip.");
		}
		UUID subject = "CUSTOMER".equals(role) ? t.getDriverId() : t.getCustomerId();
		int added = jdbc.update("""
				INSERT INTO driver_reviews (id, trip_id, author_id, subject_id, by_role, stars, comment, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (trip_id, by_role) DO NOTHING""",
				UUID.randomUUID(), t.getId(), me, subject, role, req.stars(), blankToNull(req.comment()), Timestamp.from(clock.instant()));
		if (added == 0) {
			throw ApiException.conflict("already_rated", "You've already rated this trip.");
		}
		return view(t, me);
	}

	@Transactional(readOnly = true)
	public MyTrips mine(UUID me) {
		return new MyTrips(views(trips.findByCustomerIdOrderByStartsAtDesc(me, PageRequest.of(0, 100)), me),
				views(trips.findByDriverIdOrderByStartsAtDesc(me, PageRequest.of(0, 200)), me));
	}

	@Transactional(readOnly = true)
	public TripView get(UUID me, UUID id) {
		DriverTrip t = trips.findById(id).orElseThrow(DriverTripService::notFound);
		roleOf(t, me);
		return view(t, me);
	}

	// ----- Staff -----

	@Transactional(readOnly = true)
	public List<TripView> forStaff() {
		return views(trips.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 150)), null);
	}

	/** Staff cancel a trip before it starts (say, after a safety report). Everything is refunded. */
	@Transactional
	public TripView staffCancel(UUID id, String reason, Actor actor) {
		DriverTrip t = lock(id);
		if (!NOT_STARTED.contains(t.getStatus())) {
			throw ApiException.conflict("cant_cancel", "Only trips that haven't started can be cancelled.");
		}
		t.cancel("STAFF", reason == null || reason.isBlank() ? "Cancelled by SaathChalo" : reason.strip(), clock.instant());
		audit.record(actor, "driver_trip.cancelled", "driver_trip", id.toString(), "Cancelled a hired-driver trip",
				reason == null ? null : Map.of("reason", reason.strip()));
		notices.cancelled(t, "STAFF", reason);
		return view(t, null);
	}

	// ----- Inside -----

	/** Paid within two hours of being accepted, and a quarter of an hour before the start. */
	private static Instant payBy(DriverTrip t, Instant now) {
		Instant by = min(now.plus(DriverRules.PAY_WITHIN), t.getStartsAt().minus(Duration.ofMinutes(15)));
		return by.isAfter(now) ? by : t.getStartsAt();
	}

	private String startCode() {
		return String.format("%04d", random.nextInt(10_000));
	}

	private DriverTrip lock(UUID id) {
		return trips.lockById(id).orElseThrow(DriverTripService::notFound);
	}

	private static String roleOf(DriverTrip t, UUID me) {
		if (t.getCustomerId().equals(me)) {
			return "CUSTOMER";
		}
		if (t.getDriverId().equals(me)) {
			return "DRIVER";
		}
		throw notFound();
	}

	TripView view(DriverTrip t, UUID viewer) {
		return views(List.of(t), viewer).getFirst();
	}

	private List<TripView> views(List<DriverTrip> list, UUID viewer) {
		if (list.isEmpty()) {
			return List.of();
		}
		Set<UUID> driverIds = new HashSet<>();
		Set<UUID> customerIds = new HashSet<>();
		list.forEach(t -> {
			driverIds.add(t.getDriverId());
			customerIds.add(t.getCustomerId());
		});
		Set<UUID> people = new HashSet<>(driverIds);
		people.addAll(customerIds);
		Map<UUID, MemberCard> cards = members.cards(people);
		Map<UUID, Stats> driverStats = drivers.stats(driverIds);
		Map<UUID, double[]> customerRatings = drivers.ratings(customerIds, "DRIVER");
		Map<UUID, Integer> myStars = new HashMap<>();
		if (viewer != null) {
			jdbc.query("SELECT trip_id, stars FROM driver_reviews WHERE author_id = ? AND trip_id = ANY (?)", rs -> {
				myStars.put(rs.getObject("trip_id", UUID.class), rs.getInt("stars"));
			}, viewer, list.stream().map(DriverTrip::getId).toArray(UUID[]::new));
		}
		Instant now = clock.instant();
		return list.stream().map(t -> {
			String role = viewer == null ? "STAFF" : t.getCustomerId().equals(viewer) ? "CUSTOMER" : "DRIVER";
			boolean agreed = DriverTrip.HOLDING.contains(t.getStatus());
			boolean justDone = DriverTrip.COMPLETED.equals(t.getStatus()) && t.getEndedAt() != null
					&& t.getEndedAt().plus(Duration.ofDays(1)).isAfter(now);
			String otherPhone = viewer != null && (agreed || justDone)
					? members.phone("CUSTOMER".equals(role) ? t.getDriverId() : t.getCustomerId()).orElse(null) : null;
			boolean showAddress = !"DRIVER".equals(role) || agreed || DriverTrip.COMPLETED.equals(t.getStatus());
			boolean showCode = "CUSTOMER".equals(role) && Set.of(DriverTrip.ACCEPTED, DriverTrip.PAID, DriverTrip.ON_THE_WAY)
				.contains(t.getStatus());
			Stats ds = driverStats.getOrDefault(t.getDriverId(), Stats.NONE);
			double[] cr = customerRatings.get(t.getCustomerId());
			Integer stars = myStars.get(t.getId());
			boolean canReview = viewer != null && stars == null && DriverTrip.COMPLETED.equals(t.getStatus()) && t.getEndedAt() != null
					&& t.getEndedAt().plus(DriverRules.REVIEW_WITHIN).isAfter(now);
			TripKind kind = TripKind.valueOf(t.getKind());
			Duration length = Duration.between(t.getStartsAt(), t.getEndsAt());
			int units = (int) (kind == TripKind.LOCAL ? length.toHours() : length.toDays());
			return new TripView(t.getId(), role, cards.getOrDefault(t.getDriverId(), MemberProfiles.deleted()), ds.rating(), ds.reviews(),
					cards.getOrDefault(t.getCustomerId(), MemberProfiles.deleted()), cr == null ? null : cr[0], kind, t.getStartsAt(),
					t.getEndsAt(), units, t.getPlaceName(), showAddress ? t.getPickupAddress() : null, t.getDestinationName(), t.getCar(),
					t.isAutomatic(), t.getFare(), t.getExtraPrice(), t.getExtraUnits(), t.isPayOnline(), t.getPayBy(), t.getStatus(),
					showCode ? t.getStartCode() : null, t.getWrongCodes(), otherPhone, t.getMessage(), t.getAnswerBy(), t.getCreatedAt(),
					t.getDecidedAt(), t.getOnTheWayAt(), t.getStartedAt(), t.getEndedAt(), t.getCancelledAt(), t.getCancelledBy(),
					t.getCancelReason(), stars, canReview);
		}).toList();
	}

	private static Instant min(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}

	static ApiException notFound() {
		return ApiException.notFound("trip_not_found", "We couldn't find that trip.");
	}
}
