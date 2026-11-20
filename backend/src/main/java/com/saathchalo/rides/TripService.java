package com.saathchalo.rides;

import com.saathchalo.config.AppProperties;
import com.saathchalo.rides.RideDtos.DrivingTrip;
import com.saathchalo.rides.RideDtos.RidingTrip;
import com.saathchalo.rides.RideDtos.Trips;
import com.saathchalo.rides.RideDtos.TripsSummary;
import com.saathchalo.rides.internal.Booking;
import com.saathchalo.rides.internal.BookingRepository;
import com.saathchalo.rides.internal.Ride;
import com.saathchalo.rides.internal.RideRepository;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** "My rides": the rides a member drives and the seats they booked, upcoming or past. */
@Service
public class TripService {

	private static final Duration HISTORY = Duration.ofDays(365);

	private final RideRepository rides;

	private final BookingRepository bookings;

	private final MemberProfiles members;

	private final RideViews views;

	private final JdbcTemplate jdbc;

	private final AppProperties.Rides config;

	private final Clock clock;

	public TripService(RideRepository rides, BookingRepository bookings, MemberProfiles members, RideViews views,
			JdbcTemplate jdbc, AppProperties props, Clock clock) {
		this.rides = rides;
		this.bookings = bookings;
		this.members = members;
		this.views = views;
		this.jdbc = jdbc;
		this.config = props.rides();
		this.clock = clock;
	}

	/** Upcoming: rides that haven't arrived yet (cancelled ones too, so members notice). Past: everything else. */
	@Transactional(readOnly = true)
	public Trips trips(UUID memberId, boolean past) {
		Instant now = clock.instant();
		Comparator<Ride> order = past ? Comparator.comparing(Ride::getDepartureAt).reversed()
				: Comparator.comparing(Ride::getDepartureAt);

		List<Ride> driven = rides.findByDriverBetween(memberId, now.minus(HISTORY),
					now.plus(Duration.ofDays(config.maxDaysAhead() + 1)))
			.stream()
			.filter(r -> isPast(r, now) == past)
			.sorted(order)
			.toList();
		Map<UUID, List<Booking>> liveByRide = driven.isEmpty() ? Map.of()
				: bookings.findByRideIdInAndStatusIn(driven.stream().map(Ride::getId).toList(), BookingStatus.LIVE)
					.stream()
					.collect(Collectors.groupingBy(Booking::getRideId));
		List<DrivingTrip> driving = driven.stream().map(r -> {
			List<Booking> live = liveByRide.getOrDefault(r.getId(), List.of());
			int booked = live.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).mapToInt(Booking::getSeats).sum();
			int pending = (int) live.stream().filter(b -> b.getStatus() == BookingStatus.REQUESTED).count();
			return new DrivingTrip(views.summary(r), booked, pending);
		}).toList();

		// The latest booking per ride (a member may cancel and book the same ride again).
		Map<UUID, Booking> latest = new LinkedHashMap<>();
		for (Booking b : bookings.findByPassengerIdOrderByCreatedAtDesc(memberId)) {
			latest.putIfAbsent(b.getRideId(), b);
			if (latest.size() >= 300) {
				break;
			}
		}
		Map<UUID, Ride> rideById = latest.isEmpty() ? Map.of()
				: rides.findWithStops(latest.keySet()).stream().collect(Collectors.toMap(Ride::getId, Function.identity()));
		Map<UUID, MemberCard> drivers = members.cards(rideById.values().stream().map(Ride::getDriverId).toList());
		List<RidingTrip> riding = new ArrayList<>();
		latest.values()
			.stream()
			.filter(b -> rideById.containsKey(b.getRideId()))
			.filter(b -> isPast(rideById.get(b.getRideId()), now) == past)
			.sorted(Comparator.comparing((Booking b) -> rideById.get(b.getRideId()), order))
			.forEach(b -> {
				Ride r = rideById.get(b.getRideId());
				riding.add(new RidingTrip(views.booking(b, r), views.summary(r),
						drivers.getOrDefault(r.getDriverId(), MemberProfiles.deleted())));
			});
		return new Trips(driving, riding);
	}

	@Transactional(readOnly = true)
	public TripsSummary summary(UUID memberId) {
		Timestamp now = Timestamp.from(clock.instant());
		Long pending = jdbc.queryForObject("""
				SELECT count(*) FROM bookings b JOIN rides r ON r.id = b.ride_id
				WHERE r.driver_id = ? AND r.status = 'SCHEDULED' AND b.status = 'REQUESTED'
				""", Long.class, memberId);
		Long upcoming = jdbc.queryForObject("""
				SELECT (SELECT count(*) FROM rides WHERE driver_id = ? AND status = 'SCHEDULED' AND departure_at > ?)
				     + (SELECT count(*) FROM bookings b JOIN rides r ON r.id = b.ride_id
				        WHERE b.passenger_id = ? AND b.status IN ('AWAITING_PAYMENT', 'REQUESTED', 'CONFIRMED')
				          AND r.status = 'SCHEDULED' AND r.departure_at > ?)
				""", Long.class, memberId, now, memberId, now);
		return new TripsSummary(pending == null ? 0 : pending, upcoming == null ? 0 : upcoming);
	}

	private static boolean isPast(Ride r, Instant now) {
		return r.getStatus() == RideStatus.COMPLETED || !r.arrivalAt().isAfter(now);
	}
}
