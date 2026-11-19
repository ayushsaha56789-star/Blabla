package com.saathchalo.rides;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.PageResult;
import com.saathchalo.config.AppConfig;
import com.saathchalo.rides.RideDtos.AdminRideRow;
import com.saathchalo.rides.RideDtos.PassengerView;
import com.saathchalo.rides.RideDtos.RideDetails;
import com.saathchalo.rides.RideDtos.RideStats;
import com.saathchalo.rides.RideDtos.Viewer;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rides as staff see them in the console: a searchable list, details, numbers, and cancelling a ride. */
@Service
public class RideAdmin {

	/** List filters: upcoming (default), past, cancelled, all. */
	public enum Show {
		UPCOMING, PAST, CANCELLED, ALL
	}

	/** The ride page, plus every booking (any status) with the passenger's message. */
	public record AdminRideDetails(RideDetails ride, List<PassengerView> bookings) {
	}

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

	private final RideRepository rides;

	private final BookingRepository bookings;

	private final RideService rideService;

	private final MemberProfiles members;

	private final AuditService audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public RideAdmin(RideRepository rides, BookingRepository bookings, RideService rideService, MemberProfiles members,
			AuditService audit, JdbcTemplate jdbc, Clock clock) {
		this.rides = rides;
		this.bookings = bookings;
		this.rideService = rideService;
		this.members = members;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** {@code q}: part of any stop's name, e.g. "jaipur". */
	@Transactional(readOnly = true)
	public PageResult<AdminRideRow> list(Show show, String q, int page, int size) {
		int pageSize = PageResult.clampSize(size);
		int pageNo = Math.max(0, page);
		Timestamp now = Timestamp.from(clock.instant());
		StringBuilder where = new StringBuilder(" WHERE 1=1");
		List<Object> args = new ArrayList<>();
		switch (show == null ? Show.UPCOMING : show) {
			case UPCOMING -> {
				where.append(" AND r.status = 'SCHEDULED' AND r.departure_at + make_interval(mins => r.duration_min) > ?");
				args.add(now);
			}
			case PAST -> {
				where.append(" AND (r.status = 'COMPLETED' OR (r.status = 'SCHEDULED'"
						+ " AND r.departure_at + make_interval(mins => r.duration_min) <= ?))");
				args.add(now);
			}
			case CANCELLED -> where.append(" AND r.status = 'CANCELLED'");
			case ALL -> {
			}
		}
		if (q != null && !q.isBlank()) {
			where.append(" AND EXISTS (SELECT 1 FROM ride_stops s WHERE s.ride_id = r.id AND s.name ILIKE ? ESCAPE '\\')");
			args.add("%" + q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
		}
		Long total = jdbc.queryForObject("SELECT count(*) FROM rides r" + where, Long.class, args.toArray());
		String order = show == Show.UPCOMING ? " ORDER BY r.departure_at ASC" : " ORDER BY r.departure_at DESC";
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(pageSize);
		pageArgs.add((long) pageNo * pageSize);
		List<UUID> ids = jdbc.queryForList("SELECT r.id FROM rides r" + where + order + " LIMIT ? OFFSET ?", UUID.class,
				pageArgs.toArray());
		if (ids.isEmpty()) {
			return new PageResult<>(List.of(), total == null ? 0 : total, pageNo, pageSize);
		}
		Map<UUID, Ride> byId = rides.findWithStops(ids).stream().collect(Collectors.toMap(Ride::getId, Function.identity()));
		Map<UUID, List<Booking>> live = bookings.findByRideIdInAndStatusIn(ids, BookingStatus.LIVE)
			.stream()
			.collect(Collectors.groupingBy(Booking::getRideId));
		Map<UUID, MemberCard> drivers = members.cards(byId.values().stream().map(Ride::getDriverId).toList());
		List<AdminRideRow> rows = ids.stream().map(byId::get).map(r -> {
			List<Booking> bs = live.getOrDefault(r.getId(), List.of());
			int booked = bs.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).mapToInt(Booking::getSeats).sum();
			int pending = (int) bs.stream().filter(b -> b.getStatus() == BookingStatus.REQUESTED).count();
			return new AdminRideRow(r.getId(), r.getStatus(), r.getDepartureAt(), r.getStops().getFirst().getName(),
					r.getStops().getLast().getName(), r.getStops().size(),
					drivers.getOrDefault(r.getDriverId(), MemberProfiles.deleted()), r.getSeatsTotal(), booked, pending,
					r.getPricePerSeat(), r.isLadiesOnly(), r.getCreatedAt());
		}).toList();
		return new PageResult<>(rows, total == null ? 0 : total, pageNo, pageSize);
	}

	@Transactional(readOnly = true)
	public AdminRideDetails details(UUID rideId) {
		Ride ride = rideService.load(rideId);
		return staffView(ride, bookings.findByRideIdOrderByCreatedAtAsc(rideId));
	}

	/** Staff cancel a ride (for example after a safety report). Passengers' bookings are cancelled with it. */
	@Transactional
	public AdminRideDetails cancel(UUID rideId, String reason, Actor actor) {
		Ride ride = rides.lockById(rideId).orElseThrow(RideService::notFound);
		if (ride.getStatus() != RideStatus.SCHEDULED) {
			throw ApiException.conflict("ride_not_scheduled", "This ride was already cancelled or completed.");
		}
		int cancelledBookings = rideService.cancel(ride, "STAFF", reason);
		String when = DAY.format(ride.getDepartureAt().atZone(AppConfig.ZONE));
		audit.record(actor, "ride.cancelled", "ride", rideId.toString(),
				"Cancelled the ride " + RideViews.route(ride) + " on " + when,
				Map.of("reason", reason, "bookingsCancelled", cancelledBookings, "driverId", ride.getDriverId().toString()));
		return staffView(ride, bookings.findByRideIdOrderByCreatedAtAsc(rideId));
	}

	@Transactional(readOnly = true)
	public RideStats stats() {
		Instant now = clock.instant();
		Timestamp at = Timestamp.from(now);
		Timestamp weekAgo = Timestamp.from(now.minus(Duration.ofDays(7)));
		return jdbc.queryForObject("""
				SELECT
				  (SELECT count(*) FROM rides WHERE status = 'SCHEDULED' AND departure_at > ?) AS upcoming,
				  (SELECT count(*) FROM rides WHERE created_at > ?) AS published,
				  (SELECT count(*) FROM bookings WHERE created_at > ?) AS booked,
				  (SELECT coalesce(sum(b.seats), 0) FROM bookings b JOIN rides r ON r.id = b.ride_id
				     WHERE b.status = 'CONFIRMED' AND r.status = 'SCHEDULED' AND r.departure_at > ?) AS seats,
				  (SELECT count(*) FROM rides WHERE status = 'CANCELLED' AND cancelled_at > ?) AS cancelled
				""", (rs, n) -> new RideStats(rs.getLong("upcoming"), rs.getLong("published"), rs.getLong("booked"),
				rs.getLong("seats"), rs.getLong("cancelled")), at, weekAgo, weekAgo, at, weekAgo);
	}

	private AdminRideDetails staffView(Ride ride, List<Booking> all) {
		RideDetails base = rideService.details(ride, all, null, Viewer.STAFF);
		Map<UUID, MemberCard> cards = members.cards(all.stream().map(Booking::getPassengerId).toList());
		Map<UUID, PassengerView> views = new LinkedHashMap<>();
		for (Booking b : all) {
			views.put(b.getId(), new PassengerView(b.getId(), cards.getOrDefault(b.getPassengerId(), MemberProfiles.deleted()),
					b.getStatus(), b.getFromPosition(), b.getToPosition(), b.getSeats(), b.getPricePerSeat(), b.getMessage(),
					null, b.getCreatedAt(), b.getCancelledBy()));
		}
		return new AdminRideDetails(base, List.copyOf(views.values()));
	}
}
