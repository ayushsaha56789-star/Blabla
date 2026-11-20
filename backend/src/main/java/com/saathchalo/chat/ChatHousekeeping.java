package com.saathchalo.chat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chats are kept for 6 months after the trip, then removed (privacy: keep personal data only as long as needed).
 * Reported messages are copied into the report, so moderation doesn't depend on the chat still existing.
 */
@Component
public class ChatHousekeeping {

	private static final Logger log = LoggerFactory.getLogger(ChatHousekeeping.class);

	static final Duration KEEP_FOR = Duration.ofDays(180);

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public ChatHousekeeping(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 21_600_000, initialDelay = 300_000)
	@Transactional
	public void removeOldChats() {
		int removed = jdbc.update("DELETE FROM conversations c USING rides r WHERE c.ride_id = r.id AND r.departure_at < ?",
				Timestamp.from(clock.instant().minus(KEEP_FOR)));
		if (removed > 0) {
			log.info("Removed {} chats older than 6 months", removed);
		}
	}
}
