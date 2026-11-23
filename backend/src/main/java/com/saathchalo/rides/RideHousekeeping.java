package com.saathchalo.rides;

import com.saathchalo.config.AppProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps rides tidy every few minutes: requests the driver didn't answer in time (or whose pickup time has passed)
 * expire and free their seats, and rides are marked completed a few hours after their planned arrival.
 */
@Component
public class RideHousekeeping {

	private static final Logger log = LoggerFactory.getLogger(RideHousekeeping.class);

	private record Trip(UUID rideId, UUID driverId, UUID passengerId) {
	}

	/** How long after the planned arrival a ride counts as done. */
	static final Duration COMPLETE_AFTER = Duration.ofHours(3);

	private final JdbcTemplate jdbc;

	private final AppProperties.Rides config;

	private final Clock clock;

	private final RideNotices notices;

	public RideHousekeeping(JdbcTemplate jdbc, AppProperties props, Clock clock, RideNotices notices) {
		this.jdbc = jdbc;
		this.config = props.rides();
		this.clock = clock;
		this.notices = notices;
	}

	@Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
	@Transactional
	public void run() {
		Instant now = clock.instant();
		Timestamp at = Timestamp.from(now);
		List<UUID> unanswered = jdbc.queryForList("""
				UPDATE bookings b SET status = 'EXPIRED', decided_at = ?, cancelled_by = 'SYSTEM'
				FROM rides r, ride_stops s
				WHERE b.status = 'REQUESTED' AND r.id = b.ride_id AND s.ride_id = b.ride_id AND s.position = b.from_position
				  AND (b.created_at < ? OR r.status <> 'SCHEDULED'
				       OR r.departure_at + make_interval(mins => s.minutes_from_start) <= ?)
				RETURNING b.passenger_id
				""", UUID.class, at, Timestamp.from(now.minus(config.requestTtl())), at);
		unanswered.forEach(notices::requestExpired);
		int expired = unanswered.size();
		// Online payments that didn't come through in time give their seats back.
		expired += jdbc.update("""
				UPDATE bookings b SET status = 'EXPIRED', decided_at = ?, cancelled_by = 'SYSTEM'
				FROM rides r, ride_stops s
				WHERE b.status = 'AWAITING_PAYMENT' AND r.id = b.ride_id AND s.ride_id = b.ride_id AND s.position = b.from_position
				  AND (b.created_at < ? OR r.status <> 'SCHEDULED'
				       OR r.departure_at + make_interval(mins => s.minutes_from_start) <= ?)
				""", at, Timestamp.from(now.minus(config.paymentHold())), at);
		Timestamp doneBefore = Timestamp.from(now.minus(COMPLETE_AFTER));
		List<Trip> trips = jdbc.query("""
				UPDATE bookings b SET status = 'COMPLETED'
				FROM rides r
				WHERE b.ride_id = r.id AND b.status = 'CONFIRMED' AND r.status = 'SCHEDULED'
				  AND r.departure_at + make_interval(mins => r.duration_min) < ?
				RETURNING b.ride_id, r.driver_id, b.passenger_id
				""", (rs, n) -> new Trip(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)),
				doneBefore);
		// Everyone who travelled is asked for a rating: each passenger once, each driver once per ride.
		trips.forEach(t -> notices.rateDriver(t.passengerId(), t.driverId(), t.rideId()));
		trips.stream()
			.collect(Collectors.groupingBy(Trip::rideId, LinkedHashMap::new, Collectors.toList()))
			.forEach((rideId, list) -> notices.ratePassengers(list.getFirst().driverId(),
					list.stream().map(Trip::passengerId).toList(), rideId));
		int completed = jdbc.update("""
				UPDATE rides SET status = 'COMPLETED', completed_at = ?
				WHERE status = 'SCHEDULED' AND departure_at + make_interval(mins => duration_min) < ?
				""", at, doneBefore);
		if (expired > 0 || completed > 0) {
			log.info("Ride housekeeping: {} requests expired, {} rides completed", expired, completed);
		}
	}
}
