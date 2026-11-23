package com.saathchalo.notifications;

/**
 * A short notice for a member's inbox: a title such as "Ravi accepted your request", an optional line of detail and a
 * path inside the app where the member can act. Long texts are shortened; links must stay inside the app.
 */
public record Notice(Topic topic, String icon, String title, String body, String link) {

	public Notice {
		if (topic == null || icon == null || icon.isBlank() || title == null || title.isBlank()) {
			throw new IllegalArgumentException("A notice needs a topic, an icon and a title");
		}
		if (link != null && (!link.startsWith("/") || link.startsWith("//") || link.contains("\\") || link.length() > 200)) {
			throw new IllegalArgumentException("Notice links must be paths inside the app");
		}
		title = shorten(title.strip(), 120);
		body = body == null || body.isBlank() ? null : shorten(body.strip(), 300);
	}

	public static Notice trips(String icon, String title, String body, String link) {
		return new Notice(Topic.TRIPS, icon, title, body, link);
	}

	public static Notice reminder(String icon, String title, String body, String link) {
		return new Notice(Topic.REMINDERS, icon, title, body, link);
	}

	public static Notice account(String icon, String title, String body, String link) {
		return new Notice(Topic.ACCOUNT, icon, title, body, link);
	}

	public static Notice reward(String icon, String title, String body, String link) {
		return new Notice(Topic.REWARDS, icon, title, body, link);
	}

	private static String shorten(String text, int max) {
		return text.length() <= max ? text : text.substring(0, max - 1).stripTrailing() + "…";
	}
}
