package com.saathchalo.safety;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Location data is kept only as long as it helps: a trip link's last location is wiped when the link expires (24
 * hours after the trip) and old links are removed after a week; SOS locations are wiped 90 days after the alert is
 * resolved (the alert itself stays as a safety record).
 */
@Component
public class SafetyHousekeeping {

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public SafetyHousekeeping(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 3_600_000, initialDelay = 600_000)
	@Transactional
	public void run() {
		Instant now = clock.instant();
		jdbc.update("""
				UPDATE trip_shares SET live_lat = NULL, live_lng = NULL, live_accuracy = NULL, live_at = NULL
				WHERE live_at IS NOT NULL AND (expires_at < ? OR revoked_at IS NOT NULL)
				""", Timestamp.from(now));
		jdbc.update("DELETE FROM trip_shares WHERE expires_at < ?", Timestamp.from(now.minus(Duration.ofDays(7))));
		jdbc.update("""
				UPDATE sos_alerts SET lat = NULL, lng = NULL, accuracy = NULL
				WHERE status = 'RESOLVED' AND lat IS NOT NULL AND resolved_at < ?
				""", Timestamp.from(now.minus(Duration.ofDays(90))));
	}
}
