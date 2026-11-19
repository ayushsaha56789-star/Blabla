package com.saathchalo.rides;

import com.saathchalo.config.AppProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

	/** How long after the planned arrival a ride counts as done. */
	static final Duration COMPLETE_AFTER = Duration.ofHours(3);

	private final JdbcTemplate jdbc;

	private final AppProperties.Rides config;

	private final Clock clock;

	public RideHousekeeping(JdbcTemplate jdbc, AppProperties props, Clock clock) {
		this.jdbc = jdbc;
		this.config = props.rides();
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
	@Transactional
	public void run() {
		Instant now = clock.instant();
		Timestamp at = Timestamp.from(now);
		int expired = jdbc.update("""
				UPDATE bookings b SET status = 'EXPIRED', decided_at = ?, cancelled_by = 'SYSTEM'
				FROM rides r, ride_stops s
				WHERE b.status = 'REQUESTED' AND r.id = b.ride_id AND s.ride_id = b.ride_id AND s.position = b.from_position
				  AND (b.created_at < ? OR r.status <> 'SCHEDULED'
				       OR r.departure_at + make_interval(mins => s.minutes_from_start) <= ?)
				""", at, Timestamp.from(now.minus(config.requestTtl())), at);
		Timestamp doneBefore = Timestamp.from(now.minus(COMPLETE_AFTER));
		jdbc.update("""
				UPDATE bookings b SET status = 'COMPLETED'
				FROM rides r
				WHERE b.ride_id = r.id AND b.status = 'CONFIRMED' AND r.status = 'SCHEDULED'
				  AND r.departure_at + make_interval(mins => r.duration_min) < ?
				""", doneBefore);
		int completed = jdbc.update("""
				UPDATE rides SET status = 'COMPLETED', completed_at = ?
				WHERE status = 'SCHEDULED' AND departure_at + make_interval(mins => duration_min) < ?
				""", at, doneBefore);
		if (expired > 0 || completed > 0) {
			log.info("Ride housekeeping: {} requests expired, {} rides completed", expired, completed);
		}
	}
}
