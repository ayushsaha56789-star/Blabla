package com.saathchalo.common;

/** Hides most of a value so staff and logs see only what they need. */
public final class Masking {

	private Masking() {
	}

	/** {@code +919876543210} → {@code +91 98••• ••210}. */
	/** "pr•••@gmail.com": enough to recognise, not enough to copy. */
	public static String email(String email) {
		if (email == null) {
			return null;
		}
		int at = email.indexOf('@');
		if (at < 1) {
			return "•••";
		}
		return email.substring(0, Math.min(2, at)) + "•••" + email.substring(at);
	}

	public static String phone(String e164) {
		if (e164 == null || !e164.matches("\\+91\\d{10}")) {
			return e164 == null ? null : "•••";
		}
		String d = e164.substring(3);
		return "+91 " + d.substring(0, 2) + "••• ••" + d.substring(7);
	}
}
