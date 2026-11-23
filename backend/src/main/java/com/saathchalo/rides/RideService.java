package com.saathchalo.rides;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.RateLimiter;
import com.saathchalo.config.AppConfig;
import com.saathchalo.config.AppProperties;
import com.saathchalo.features.FeatureService;
import com.saathchalo.geo.GeoMath;
import com.saathchalo.geo.GeoMath.RouteEstimate;
import com.saathchalo.geo.Place;
import com.saathchalo.geo.PlaceDirectory;
import com.saathchalo.rides.RideDtos.CarView;
import com.saathchalo.rides.RideDtos.DriverView;
import com.saathchalo.rides.RideDtos.PassengerView;
import com.saathchalo.rides.RideDtos.PublishRequest;
import com.saathchalo.rides.RideDtos.Quote;
import com.saathchalo.rides.RideDtos.RideDetails;
import com.saathchalo.rides.RideDtos.StopRequest;
import com.saathchalo.rides.RideDtos.Viewer;
import com.saathchalo.rides.internal.Booking;
import com.saathchalo.rides.internal.BookingRepository;
import com.saathchalo.rides.internal.Ride;
import com.saathchalo.rides.internal.RideRepository;
import com.saathchalo.rides.internal.RideStop;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import com.saathchalo.user.MemberProfiles.Traveller;
import com.saathchalo.user.UserService;
import com.saathchalo.vehicles.VehicleInfo;
import com.saathchalo.vehicles.VehicleService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Offering rides, the ride page, and cancelling by the driver. */
@Service
public class RideService {

	static final int MAX_UPCOMING_RIDES = 30;

	static final int MIN_DISTANCE_KM = 10;

	private static final Duration MIN_LEAD_TIME = Duration.ofMinutes(15);

	private final RideRepository rides;

	private final BookingRepository bookings;

	private final PlaceDirectory places;

	private final VehicleService vehicles;

	private final MemberProfiles members;

	private final UserService users;

	private final FeatureService features;

	private final RateLimiter limiter;

	private final RideViews views;

	private final AppProperties.Rides config;

	private final Clock clock;

	private final RideNotices notices;

	public RideService(RideRepository rides, BookingRepository bookings, PlaceDirectory places, VehicleService vehicles,
			MemberProfiles members, UserService users, FeatureService features, RateLimiter limiter, RideViews views,
			AppProperties props, Clock clock, RideNotices notices) {
		this.notices = notices;
		this.rides = rides;
		this.bookings = bookings;
		this.places = places;
		this.vehicles = vehicles;
		this.members = members;
		this.users = users;
		this.features = features;
		this.limiter = limiter;
		this.views = views;
		this.config = props.rides();
		this.clock = clock;
	}

	/** Distance, time and price guidance for a route (start, stops, end). */
	public Quote quote(List<Long> placeIds) {
		return RideRules.quote(GeoMath.estimate(resolve(placeIds)), config);
	}

	@Transactional
	public RideDetails publish(UUID driverId, PublishRequest req) {
		Traveller me = members.traveller(driverId);
		if (!me.profileComplete()) {
			throw ApiException.forbidden("profile_incomplete",
					"Add your first name and gender to your profile before offering a ride.");
		}
		VehicleInfo car = vehicles.activeOf(driverId, req.vehicleId())
			.orElseThrow(() -> ApiException.badRequest("vehicle_not_found", "Please choose one of your cars."));
		List<Place> route = resolve(req.stops().stream().map(StopRequest::placeId).toList());
		RouteEstimate estimate = GeoMath.estimate(route);
		if (estimate.distanceKm() < MIN_DISTANCE_KM) {
			throw ApiException.badRequest("route_too_short", "Rides must be at least " + MIN_DISTANCE_KM + " km long.");
		}
		Instant now = clock.instant();
		Instant departure = req.date().atTime(req.time()).atZone(AppConfig.ZONE).toInstant();
		if (departure.isBefore(now.plus(MIN_LEAD_TIME))) {
			throw ApiException.badRequest("departure_too_soon", "Please choose a time at least 15 minutes from now.");
		}
		if (departure.isAfter(now.plus(Duration.ofDays(config.maxDaysAhead())))) {
			throw ApiException.badRequest("departure_too_far",
					"You can offer rides up to " + config.maxDaysAhead() + " days ahead.");
		}
		if (req.seats() > car.seats()) {
			throw ApiException.badRequest("too_many_seats",
					"Your " + car.name() + " has " + car.seats() + (car.seats() == 1 ? " passenger seat." : " passenger seats."))
				.with("maxSeats", car.seats());
		}
		Quote quote = RideRules.quote(estimate, config);
		if (req.pricePerSeat() < quote.minPrice()) {
			throw ApiException.badRequest("price_too_low", "The lowest price per seat is ₹" + quote.minPrice() + ".")
				.with("minPrice", quote.minPrice());
		}
		if (req.pricePerSeat() > quote.maxPrice()) {
			throw ApiException.badRequest("price_too_high", "SaathChalo is for sharing costs, so the most you can ask for "
					+ "this route is ₹" + quote.maxPrice() + " per seat.")
				.with("maxPrice", quote.maxPrice());
		}
		boolean ladiesOnly = Boolean.TRUE.equals(req.ladiesOnly());
		if (ladiesOnly && (!me.isWoman() || !features.isOnFor("rides.ladies_only", driverId))) {
			throw ApiException.forbidden("ladies_only_unavailable", "Ladies-only rides can be offered by women members.");
		}
		checkNoOverlap(driverId, departure, estimate.durationMin());
		if (rides.countByDriverIdAndStatusAndDepartureAtAfter(driverId, RideStatus.SCHEDULED, now) >= MAX_UPCOMING_RIDES) {
			throw ApiException.conflict("too_many_rides", "You already have " + MAX_UPCOMING_RIDES
					+ " upcoming rides. Offer more once some of them are done.");
		}
		if (!limiter.tryAcquire("ride-publish:" + driverId, 10, Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("slow_down", "You've offered many rides in the last hour. Please try again later.");
		}
		List<RideStop> stops = new ArrayList<>();
		for (int i = 0; i < route.size(); i++) {
			Place p = route.get(i);
			stops.add(new RideStop(p.id(), p.name(), p.state(), clean(req.stops().get(i).meetingPoint()), p.lat(), p.lng(),
					estimate.kmFromStart().get(i), estimate.minutesFromStart().get(i)));
		}
		Ride ride = rides.save(new Ride(driverId, car.id(), departure, req.seats(), req.pricePerSeat(), estimate.distanceKm(),
				estimate.durationMin(), ladiesOnly, Boolean.TRUE.equals(req.instantBooking()), clean(req.notes()), stops, now));
		return details(ride, List.of(), driverId);
	}

	/** The ride page. {@code viewerId} is null for visitors who aren't logged in. */
	@Transactional(readOnly = true)
	public RideDetails details(UUID rideId, UUID viewerId) {
		Ride ride = load(rideId);
		return details(ride, bookings.findByRideIdOrderByCreatedAtAsc(rideId), viewerId);
	}

	@Transactional
	public RideDetails cancelByDriver(UUID rideId, UUID driverId, String reason) {
		Ride ride = rides.lockById(rideId)
			.filter(r -> r.getDriverId().equals(driverId))
			.orElseThrow(RideService::notFound);
		if (ride.getStatus() != RideStatus.SCHEDULED) {
			throw ApiException.conflict("ride_not_scheduled", "This ride was already cancelled or completed.");
		}
		if (!ride.arrivalAt().isAfter(clock.instant())) {
			throw ApiException.conflict("ride_finished", "This ride has already finished.");
		}
		cancel(ride, "DRIVER", reason);
		return details(ride, bookings.findByRideIdOrderByCreatedAtAsc(rideId), driverId);
	}

	/** Cancels a ride and every booking that still holds a seat on it. Returns how many bookings were cancelled. */
	int cancel(Ride ride, String by, String reason) {
		Instant now = clock.instant();
		ride.cancel(by, clean(reason), now);
		List<Booking> live = bookings.findByRideIdInAndStatusIn(List.of(ride.getId()), BookingStatus.LIVE);
		live.forEach(b -> b.cancel(by, now));
		notices.rideCancelled(ride, live, clean(reason), "STAFF".equals(by));
		return live.size();
	}

	RideDetails details(Ride ride, List<Booking> all, UUID viewerId) {
		return details(ride, all, viewerId, null);
	}

	/** {@code staff}: Viewer.STAFF for the console (sees the ride like a visitor, but ladies-only rides too). */
	RideDetails details(Ride ride, List<Booking> all, UUID viewerId, Viewer staff) {
		Instant now = clock.instant();
		boolean isDriver = staff == null && ride.getDriverId().equals(viewerId);
		Booking mine = staff != null || viewerId == null || isDriver ? null
				: all.stream()
					.filter(b -> b.getPassengerId().equals(viewerId))
					.max(Comparator.comparing(Booking::getCreatedAt))
					.orElse(null);
		Viewer viewer = staff != null ? staff : isDriver ? Viewer.DRIVER : mine != null ? Viewer.PASSENGER : Viewer.VISITOR;
		if (ride.isLadiesOnly() && viewer == Viewer.VISITOR && !isWoman(viewerId)) {
			throw ApiException.forbidden("ladies_only", "This ride is for women only.");
		}
		boolean upcoming = ride.getStatus() == RideStatus.SCHEDULED;
		boolean confirmed = mine != null && mine.getStatus() == BookingStatus.CONFIRMED && upcoming;

		List<Booking> live = all.stream().filter(b -> b.getStatus().isLive()).toList();
		int[] left = RideRules.seatsLeftByLeg(ride.getSeatsTotal(), ride.lastPosition(), live);
		List<Booking> shown = all.stream()
			.filter(b -> isDriver ? b.getStatus().isLive() || b.getStatus() == BookingStatus.COMPLETED
					: b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED)
			.toList();
		Map<UUID, MemberCard> cards = members.cards(shown.stream().map(Booking::getPassengerId).toList());
		List<PassengerView> passengers = shown.stream()
			.map(b -> new PassengerView(b.getId(), cards.getOrDefault(b.getPassengerId(), MemberProfiles.deleted()),
					b.getStatus(), b.getFromPosition(), b.getToPosition(), b.getSeats(), b.getPricePerSeat(),
					isDriver ? b.getMessage() : null,
					isDriver && upcoming && b.getStatus() == BookingStatus.CONFIRMED
							? members.phone(b.getPassengerId()).orElse(null) : null,
					b.getCreatedAt(), b.getCancelledBy()))
			.toList();

		VehicleInfo car = vehicles.infos(List.of(ride.getVehicleId())).get(ride.getVehicleId());
		String plate = (isDriver || confirmed) ? vehicles.plate(ride.getVehicleId()).orElse(null) : null;
		CarView carView = car == null ? null : new CarView(car.make(), car.model(), car.color(), plate);
		DriverView driver = new DriverView(users.publicProfile(ride.getDriverId()),
				confirmed ? members.phone(ride.getDriverId()).orElse(null) : null,
				rides.countByDriverIdAndStatus(ride.getDriverId(), RideStatus.COMPLETED));

		boolean canBookAgain = mine == null || mine.getStatus() == BookingStatus.CANCELLED || mine.getStatus() == BookingStatus.EXPIRED;
		boolean bookable = upcoming && staff == null && !isDriver && canBookAgain && ride.timeAt(ride.lastPosition() - 1).isAfter(now)
				&& IntStream.of(left).anyMatch(n -> n > 0);
		return new RideDetails(ride.getId(), ride.getStatus(), ride.getDepartureAt(), ride.arrivalAt(), ride.getDistanceKm(),
				ride.getDurationMin(), ride.getPricePerSeat(), ride.getSeatsTotal(), IntStream.of(left).boxed().toList(),
				RideRules.priceMatrix(ride.getPricePerSeat(), views.kmFromStart(ride)), ride.isLadiesOnly(),
				ride.isInstantBooking(), ride.getNotes(), ride.getCancelledAt(), ride.getCancelledBy(), ride.getCancelReason(),
				views.stops(ride), driver, carView, passengers, viewer, mine == null ? null : views.booking(mine, ride),
				bookable);
	}

	Ride load(UUID rideId) {
		return rides.findWithStops(List.of(rideId)).stream().findFirst().orElseThrow(RideService::notFound);
	}

	static ApiException notFound() {
		return ApiException.notFound("ride_not_found", "We couldn't find that ride. It may have been removed.");
	}

	private boolean isWoman(UUID viewerId) {
		return viewerId != null && members.findTraveller(viewerId).map(Traveller::isWoman).orElse(false);
	}

	private void checkNoOverlap(UUID driverId, Instant departure, int durationMin) {
		Instant end = departure.plus(Duration.ofMinutes(durationMin));
		for (Ride r : rides.findByDriverBetween(driverId, departure.minus(Duration.ofDays(3)), end)) {
			if (r.getStatus() == RideStatus.SCHEDULED && r.getDepartureAt().isBefore(end) && r.arrivalAt().isAfter(departure)) {
				throw ApiException.conflict("ride_overlap",
						"You already have a ride at that time (" + RideViews.route(r) + "). Pick another time.");
			}
		}
	}

	private List<Place> resolve(List<Long> placeIds) {
		Set<Long> seen = new HashSet<>();
		List<Place> out = new ArrayList<>();
		for (Long id : placeIds) {
			if (id == null || !seen.add(id)) {
				throw ApiException.badRequest("duplicate_stop", "Each place can be on a ride only once.");
			}
			out.add(places.find(id)
				.orElseThrow(() -> ApiException.badRequest("place_not_found", "We couldn't find one of the places. Please choose it again.")));
		}
		return out;
	}

	private static String clean(String s) {
		return s == null || s.isBlank() ? null : s.strip().replaceAll("[\\t ]+", " ");
	}
}
