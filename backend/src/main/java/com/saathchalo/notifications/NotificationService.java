package com.saathchalo.notifications;

import com.saathchalo.notifications.NotificationDtos.Inbox;
import com.saathchalo.notifications.NotificationDtos.NoticeView;
import com.saathchalo.notifications.NotificationDtos.Settings;
import com.saathchalo.notifications.NotificationDtos.TopicChoice;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A member's inbox: reading it, marking notices read, and choosing which topics to mute. */
@Service
class NotificationService {

	static final int PAGE = 30;

	static final Duration KEEP = Duration.ofDays(90);

	private static final RowMapper<NoticeView> VIEW = (rs, n) -> new NoticeView(rs.getLong("id"),
			Topic.valueOf(rs.getString("topic")), rs.getString("icon"), rs.getString("title"), rs.getString("body"),
			rs.getString("link"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("read_at") != null);

	private final JdbcTemplate jdbc;

	private final NotificationSettings settings;

	private final Clock clock;

	NotificationService(JdbcTemplate jdbc, NotificationSettings settings, Clock clock) {
		this.jdbc = jdbc;
		this.settings = settings;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	Inbox inbox(UUID me, Long beforeId) {
		List<NoticeView> rows = jdbc.query("""
				SELECT id, topic, icon, title, body, link, created_at, read_at FROM notifications
				WHERE user_id = ? AND id < ? ORDER BY id DESC LIMIT ?""", VIEW, me, beforeId == null ? Long.MAX_VALUE : beforeId,
				PAGE + 1);
		boolean more = rows.size() > PAGE;
		return new Inbox(more ? rows.subList(0, PAGE) : rows, more, unread(me));
	}

	@Transactional(readOnly = true)
	int unread(UUID me) {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
				Integer.class, me);
		return count == null ? 0 : count;
	}

	@Transactional
	void markRead(UUID me, List<Long> ids, boolean all) {
		Timestamp now = Timestamp.from(clock.instant());
		if (all) {
			jdbc.update("UPDATE notifications SET read_at = ? WHERE user_id = ? AND read_at IS NULL", now, me);
		}
		else if (ids != null && !ids.isEmpty()) {
			jdbc.update("UPDATE notifications SET read_at = ? WHERE user_id = ? AND read_at IS NULL AND id = ANY (?)", now, me,
					ids.toArray(new Long[0]));
		}
	}

	@Transactional(readOnly = true)
	Settings settings(UUID me) {
		Set<Topic> muted = settings.muted(me);
		List<TopicChoice> choices = new ArrayList<>();
		Arrays.stream(Topic.values())
			.forEach(t -> choices.add(new TopicChoice(t, t.label(), t.mutable(), t.mutable() && muted.contains(t))));
		return new Settings(choices);
	}

	@Transactional
	Settings saveSettings(UUID me, Set<Topic> muted) {
		settings.save(me, muted);
		return settings(me);
	}

	/** Notices older than 90 days are deleted. */
	@Transactional
	int tidy() {
		return jdbc.update("DELETE FROM notifications WHERE created_at < ?", Timestamp.from(clock.instant().minus(KEEP)));
	}
}
