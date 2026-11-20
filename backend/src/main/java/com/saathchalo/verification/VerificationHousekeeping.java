package com.saathchalo.verification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes ID photos 30 days after staff decided, and email codes nobody used. */
@Component
public class VerificationHousekeeping {

	private static final Logger log = LoggerFactory.getLogger(VerificationHousekeeping.class);

	private final IdCheckService ids;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public VerificationHousekeeping(IdCheckService ids, JdbcTemplate jdbc, Clock clock) {
		this.ids = ids;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 3_600_000, initialDelay = 420_000)
	public void run() {
		int photos = ids.deleteOldPhotos();
		int codes = jdbc.update("DELETE FROM email_codes WHERE expires_at < ?", Timestamp.from(clock.instant().minus(Duration.ofDays(1))));
		if (photos + codes > 0) {
			log.info("Deleted {} ID photos past their 30 days and {} unused email codes", photos, codes);
		}
	}
}
