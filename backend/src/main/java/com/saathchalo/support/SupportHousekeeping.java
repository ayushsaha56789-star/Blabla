package com.saathchalo.support;

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

/** Closes answered help requests the member hasn't come back to for a week. Writing again reopens them. */
@Component
public class SupportHousekeeping {

	private static final Logger log = LoggerFactory.getLogger(SupportHousekeeping.class);

	static final Duration CLOSE_ANSWERED_AFTER = Duration.ofDays(7);

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public SupportHousekeeping(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 3_600_000, initialDelay = 300_000)
	@Transactional
	public void closeQuietRequests() {
		Instant now = clock.instant();
		int closed = jdbc.update("UPDATE support_tickets SET status = 'CLOSED', closed_at = ? WHERE status = 'ANSWERED' AND updated_at < ?",
				Timestamp.from(now), Timestamp.from(now.minus(CLOSE_ANSWERED_AFTER)));
		if (closed > 0) {
			log.info("Closed {} answered help requests with no reply for a week", closed);
		}
	}
}
