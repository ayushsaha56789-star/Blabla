package com.saathchalo.drivers;

import com.saathchalo.user.MemberLeaving;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every five minutes: requests the driver didn't answer in time lapse, accepted trips that weren't paid in time lapse,
 * trips that never started are cancelled once their time is over (everything back), and trips nobody ended finish
 * by themselves. Once a day drivers whose licence has run out are taken off until they update it.
 */
@Component
public class DriverHousekeeping {

	private static final Logger log = LoggerFactory.getLogger(DriverHousekeeping.class);

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public DriverHousekeeping(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 300_000, initialDelay = 150_000)
	@Transactional
	public void run() {
		Instant now = clock.instant();
		Timestamp at = Timestamp.from(now);
		int unanswered = jdbc.update("""
				UPDATE driver_trips SET status = 'EXPIRED', cancelled_at = ?, cancelled_by = 'SYSTEM'
				WHERE status = 'REQUESTED' AND answer_by <= ?""", at, at);
		int unpaid = jdbc.update("""
				UPDATE driver_trips SET status = 'EXPIRED', cancelled_at = ?, cancelled_by = 'SYSTEM'
				WHERE status = 'ACCEPTED' AND pay_online AND pay_by <= ?""", at, at);
		int neverStarted = jdbc.update("""
				UPDATE driver_trips SET status = 'CANCELLED', cancelled_at = ?, cancelled_by = 'SYSTEM',
				    cancel_reason = 'The trip never started'
				WHERE status IN ('ACCEPTED', 'PAID', 'ON_THE_WAY') AND ends_at <= ?""", at, at);
		int finished = jdbc.update("""
				UPDATE driver_trips SET status = 'COMPLETED', ended_at = ends_at
				WHERE status = 'STARTED' AND ends_at < ?""", Timestamp.from(now.minus(DriverRules.FINISH_BY)));
		if (unanswered + unpaid + neverStarted + finished > 0) {
			log.info("Driver trips: {} requests and {} unpaid trips lapsed, {} never started, {} finished by themselves",
					unanswered, unpaid, neverStarted, finished);
		}
	}

	@Scheduled(fixedDelay = 21_600_000, initialDelay = 600_000)
	@Transactional
	public void licencesRunOut() {
		int off = jdbc.update("""
				UPDATE driver_profiles SET status = 'REJECTED', updated_at = ?,
				    review_note = 'Your driving licence has run out. Add the new expiry date and send your details again.'
				WHERE status IN ('LIVE', 'IN_REVIEW') AND licence_valid_until < ?""",
				Timestamp.from(clock.instant()), Date.valueOf(LocalDate.now(clock.withZone(DriverRules.INDIA))));
		if (off > 0) {
			log.info("{} driver partners taken off: their licence ran out", off);
		}
	}

	/** A member who asks to delete their account stops taking (and making) new requests at once. */
	@EventListener
	@Transactional
	public void memberLeaving(MemberLeaving event) {
		Timestamp at = Timestamp.from(clock.instant());
		jdbc.update("UPDATE driver_profiles SET taking_bookings = FALSE, updated_at = ? WHERE user_id = ?", at, event.userId());
		jdbc.update("""
				UPDATE driver_trips SET status = 'CANCELLED', cancelled_at = ?, cancelled_by = 'SYSTEM',
				    cancel_reason = 'The member left SaathChalo'
				WHERE status = 'REQUESTED' AND (driver_id = ? OR customer_id = ?)""", at, event.userId(), event.userId());
	}
}
