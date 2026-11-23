package com.saathchalo.promotions;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The automatic checks every promotion passes before a moderator sees it. Hard rules refuse it at once (money games,
 * alcohol, tobacco, adult content, weapons, promises of guaranteed returns or cures, unsafe links); soft signs are
 * flagged for the moderator.
 */
final class PromotionRules {

	static final int MAX_DAYS = 30;

	static final int MAX_DAYS_AHEAD = 60;

	static final int MAX_CITIES = 10;

	static final int MAX_ACTIVE = 5;

	/** Shown at most this many times a day to the same member. */
	static final int DAILY_CAP = 3;

	private static final Pattern BANNED = Pattern.compile("\\b(bet|bets|betting|casino|satta|matka|gambl\\w*|jackpot|lottery|rummy|poker|"
			+ "teen ?patti|fantasy (?:cricket|league)|whisk(?:e)?y|vodka|beer|wine|rum|liquor|alcohol\\w*|daaru|daru|cigarettes?|"
			+ "tobacco|gutkha?|vap(?:e|es|ing)|hookah|porn\\w*|xxx|escorts?|sexy?|adult|guns?|pistols?|rifles?|weapons?|ammo|"
			+ "guaranteed (?:returns?|profits?|income)|double your money|get rich|crypto signals?|forex signals?|miracle cure|100% cure)\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Set<String> SHORTENERS = Set.of("bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "cutt.ly",
			"rebrand.ly", "shorturl.at", "tiny.cc", "rb.gy", "t.ly");

	private static final Pattern PHONE = Pattern.compile("(?:\\+?91[\\s-]?)?[6-9]\\d{4}[\\s-]?\\d{5}");

	private PromotionRules() {
	}

	/** Why the text can't be promoted, or null. */
	static String bannedContent(String... texts) {
		for (String t : texts) {
			if (t != null && BANNED.matcher(t).find()) {
				return "Promotions can't be about betting or money games, alcohol, tobacco, adult content or weapons, "
						+ "or promise guaranteed returns or cures.";
			}
		}
		return null;
	}

	/** Why the link is unsafe, or null. Only plain https links to a named website. */
	static String linkProblem(String link) {
		if (link == null || link.isBlank()) {
			return null;
		}
		URI uri;
		try {
			uri = new URI(link.strip());
		}
		catch (Exception e) {
			return "That link doesn't look right.";
		}
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		if (!"https".equalsIgnoreCase(uri.getScheme()) || host.isEmpty() || uri.getUserInfo() != null) {
			return "Use a full https:// link to your website or page.";
		}
		if (host.matches("[0-9.]+") || host.contains(":") || !host.contains(".")) {
			return "Link to a website name, not a number address.";
		}
		if (SHORTENERS.contains(host) || SHORTENERS.stream().anyMatch(s -> host.endsWith("." + s))) {
			return "Short links hide where they go. Use the full address.";
		}
		return null;
	}

	/** Soft signs for the moderator: shouting, phone numbers in the text, lots of exclamation marks. */
	static List<String> flags(String title, String body) {
		List<String> flags = new ArrayList<>();
		String letters = title.replaceAll("[^A-Za-z]", "");
		if (letters.length() >= 8 && letters.equals(letters.toUpperCase(Locale.ROOT))) {
			flags.add("Headline in capitals");
		}
		if (PHONE.matcher(title + " " + body).find()) {
			flags.add("Phone number in the text");
		}
		if ((title + body).chars().filter(c -> c == '!').count() >= 4) {
			flags.add("Many exclamation marks");
		}
		return flags;
	}

	/** Why these dates don't work, or null. */
	static String datesProblem(LocalDate startsOn, LocalDate endsOn, LocalDate today) {
		if (startsOn.isBefore(today)) {
			return "Start today or later.";
		}
		if (startsOn.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
			return "Start within the next " + MAX_DAYS_AHEAD + " days.";
		}
		if (endsOn.isBefore(startsOn)) {
			return "The end can't be before the start.";
		}
		if (endsOn.isAfter(startsOn.plusDays(MAX_DAYS - 1))) {
			return "A promotion runs for up to " + MAX_DAYS + " days.";
		}
		return null;
	}
}
