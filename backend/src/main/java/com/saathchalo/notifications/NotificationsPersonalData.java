package com.saathchalo.notifications;

import com.saathchalo.common.PersonalData;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The member's notices and muted topics. On erasure both are deleted. */
@Component
class NotificationsPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	private final NotificationService notifications;

	NotificationsPersonalData(JdbcTemplate jdbc, NotificationService notifications) {
		this.jdbc = jdbc;
		this.notifications = notifications;
	}

	@Override
	public String section() {
		return "notifications";
	}

	@Override
	public Object export(UUID memberId) {
		return Map.of("notices", PersonalData.rows(jdbc.queryForList("""
				SELECT topic, title, body, link, created_at AS created, read_at AS "readAt"
				FROM notifications WHERE user_id = ? ORDER BY id""", memberId)),
				"mutedTopics", jdbc.queryForList("SELECT muted FROM notification_settings WHERE user_id = ?", String.class, memberId));
	}

	@Override
	public void erase(UUID memberId) {
		jdbc.update("DELETE FROM notifications WHERE user_id = ?", memberId);
		jdbc.update("DELETE FROM notification_settings WHERE user_id = ?", memberId);
	}

	/** Once a day: notices older than 90 days go. */
	@Scheduled(fixedDelay = 86_400_000, initialDelay = 1_500_000)
	void tidy() {
		notifications.tidy();
	}
}
