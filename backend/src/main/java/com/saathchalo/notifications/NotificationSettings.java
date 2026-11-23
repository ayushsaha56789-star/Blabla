package com.saathchalo.notifications;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The topics each member muted. Only topics that can be muted are ever stored. */
@Component
class NotificationSettings {

	private final JdbcTemplate jdbc;

	private final Clock clock;

	NotificationSettings(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	Set<Topic> muted(UUID memberId) {
		String stored = jdbc.query("SELECT muted FROM notification_settings WHERE user_id = ?",
				rs -> rs.next() ? rs.getString(1) : "", memberId);
		Set<Topic> topics = EnumSet.noneOf(Topic.class);
		Arrays.stream(stored == null ? new String[0] : stored.split(","))
			.map(String::strip)
			.filter(s -> !s.isEmpty())
			.forEach(s -> {
				try {
					topics.add(Topic.valueOf(s));
				}
				catch (IllegalArgumentException ignored) {
					// A topic that no longer exists.
				}
			});
		return topics;
	}

	void save(UUID memberId, Set<Topic> muted) {
		String value = muted.stream().filter(Topic::mutable).map(Topic::name).sorted().collect(Collectors.joining(","));
		jdbc.update("""
				INSERT INTO notification_settings (user_id, muted, updated_at) VALUES (?, ?, ?)
				ON CONFLICT (user_id) DO UPDATE SET muted = EXCLUDED.muted, updated_at = EXCLUDED.updated_at""",
				memberId, value, Timestamp.from(clock.instant()));
	}
}
