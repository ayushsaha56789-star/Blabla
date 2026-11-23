package com.saathchalo.notifications;

import com.saathchalo.features.FeatureService;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * How other modules tell a member something happened. The notice is stored in the caller's transaction, so it appears
 * only if the change itself is saved. Nothing is stored while notifications are switched off for the member, or for a
 * topic the member muted.
 */
@Service
public class Notifier {

	private final JdbcTemplate jdbc;

	private final FeatureService features;

	private final NotificationSettings settings;

	private final Clock clock;

	Notifier(JdbcTemplate jdbc, FeatureService features, NotificationSettings settings, Clock clock) {
		this.jdbc = jdbc;
		this.features = features;
		this.settings = settings;
		this.clock = clock;
	}

	public void send(UUID memberId, Notice notice) {
		if (memberId == null || !features.isOnFor("notifications", memberId)) {
			return;
		}
		if (notice.topic().mutable() && settings.muted(memberId).contains(notice.topic())) {
			return;
		}
		jdbc.update("""
				INSERT INTO notifications (user_id, topic, icon, title, body, link, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)""", memberId, notice.topic().name(), notice.icon(), notice.title(), notice.body(),
				notice.link(), Timestamp.from(clock.instant()));
	}
}
