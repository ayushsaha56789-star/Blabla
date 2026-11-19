package com.saathchalo.vehicles;

import com.saathchalo.common.ApiException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Indian registration numbers: "MH 12 AB 1234", "DL 3C AB 1234", and Bharat series "22 BH 1234 AB". */
public final class Plates {

	private static final Pattern STANDARD = Pattern.compile("^([A-Z]{2})(\\d{1,2})([A-Z]{0,3})(\\d{1,4})$");

	private static final Pattern BHARAT = Pattern.compile("^(\\d{2})(BH)(\\d{4})([A-Z]{1,2})$");

	private Plates() {
	}

	/** Upper case without spaces, dots or dashes, e.g. "MH12AB1234". Throws a friendly error if it isn't a plate. */
	public static String normalize(String raw) {
		String plate = raw == null ? "" : raw.toUpperCase(Locale.ROOT).replaceAll("[\\s.\\-]", "");
		if (!STANDARD.matcher(plate).matches() && !BHARAT.matcher(plate).matches()) {
			throw ApiException.badRequest("bad_plate",
					"That doesn't look like an Indian registration number. Example: MH 12 AB 1234.");
		}
		return plate;
	}

	/** "MH12AB1234" → "MH 12 AB 1234". */
	public static String format(String normalized) {
		Matcher m = STANDARD.matcher(normalized);
		if (!m.matches()) {
			m = BHARAT.matcher(normalized);
			if (!m.matches()) {
				return normalized;
			}
		}
		StringBuilder b = new StringBuilder();
		for (int g = 1; g <= m.groupCount(); g++) {
			if (!m.group(g).isEmpty()) {
				b.append(b.isEmpty() ? "" : " ").append(m.group(g));
			}
		}
		return b.toString();
	}

	/** The last four characters, shown where the full number isn't needed. */
	public static String hint(String normalized) {
		return normalized.substring(Math.max(0, normalized.length() - 4));
	}
}
