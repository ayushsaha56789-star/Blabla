package com.saathchalo.notifications;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Requests and responses of the notification inbox. */
public final class NotificationDtos {

	private NotificationDtos() {
	}

	public record NoticeView(long id, Topic topic, String icon, String title, String body, String link, Instant createdAt,
			boolean read) {
	}

	/** One page of the inbox, newest first; {@code more} says whether older notices exist. */
	public record Inbox(List<NoticeView> items, boolean more, int unread) {
	}

	public record Unread(int count) {
	}

	/** Marks the given notices read, or every notice when {@code all} is true. */
	public record MarkRead(@Size(max = 100) List<Long> ids, Boolean all) {
	}

	public record TopicChoice(Topic topic, String label, boolean canMute, boolean muted) {
	}

	public record Settings(List<TopicChoice> topics) {
	}

	public record SettingsRequest(@NotNull Set<Topic> muted) {
	}
}
