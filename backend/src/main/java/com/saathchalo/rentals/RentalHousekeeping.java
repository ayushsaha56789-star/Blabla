package com.saathchalo.rentals;

import com.saathchalo.files.DocumentVault;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every five minutes: requests the owner didn't answer lapse, accepted online bookings that weren't paid in time lapse,
 * and returns the renter didn't respond to within 48 hours are completed (the money is then settled by payments).
 * Every few hours: pickup and return photos are deleted 90 days after the rental ended. The readings (odometer, fuel)
 * stay, so the history still makes sense.
 */
@Component
public class RentalHousekeeping {

	private static final Logger log = LoggerFactory.getLogger(RentalHousekeeping.class);

	static final Duration KEEP_PHOTOS_FOR = Duration.ofDays(90);

	private final JdbcTemplate jdbc;

	private final DocumentVault vault;

	private final Clock clock;

	public RentalHousekeeping(JdbcTemplate jdbc, DocumentVault vault, Clock clock) {
		this.jdbc = jdbc;
		this.vault = vault;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 21_600_000, initialDelay = 600_000)
	@Transactional
	public void forgetOldPhotos() {
		Timestamp before = Timestamp.from(clock.instant().minus(KEEP_PHOTOS_FOR));
		var old = jdbc.queryForList("""
				SELECT c.id, c.photos FROM rental_checks c JOIN rental_bookings b ON b.id = c.booking_id
				WHERE c.photos <> '' AND b.status IN ('COMPLETED', 'CANCELLED') AND COALESCE(b.completed_at, b.cancelled_at) < ?""",
				before);
		for (var row : old) {
			jdbc.update("UPDATE rental_checks SET photos = '' WHERE id = ?", row.get("id"));
			Arrays.stream(((String) row.get("photos")).split(",")).filter(f -> !f.isBlank()).forEach(vault::deleteQuietly);
		}
		if (!old.isEmpty()) {
			log.info("Deleted the photos of {} pickup/return records older than 90 days", old.size());
		}
	}

	@Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
	@Transactional
	public void run() {
		Instant now = clock.instant();
		Timestamp at = Timestamp.from(now);
		int unanswered = jdbc.update("""
				UPDATE rental_bookings SET status = 'EXPIRED', cancelled_at = ?, cancelled_by = 'SYSTEM'
				WHERE status = 'REQUESTED' AND (created_at < ? OR starts_at <= ?)""",
				at, Timestamp.from(now.minus(RentalRules.ANSWER_WITHIN)), at);
		int unpaid = jdbc.update("""
				UPDATE rental_bookings SET status = 'EXPIRED', cancelled_at = ?, cancelled_by = 'SYSTEM'
				WHERE status = 'ACCEPTED' AND pay_online AND pay_by <= ?""", at, at);
		int completed = jdbc.update("""
				UPDATE rental_bookings SET status = 'COMPLETED', completed_at = ?
				WHERE status = 'RETURNED' AND returned_at < ?""", at, Timestamp.from(now.minus(RentalRules.RESPOND_WITHIN)));
		if (unanswered + unpaid + completed > 0) {
			log.info("Rentals: {} requests and {} unpaid bookings lapsed, {} returns completed", unanswered, unpaid, completed);
		}
	}
}
