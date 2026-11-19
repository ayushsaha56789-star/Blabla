package com.saathchalo.common;

import java.util.regex.Pattern;

/** Indian mobile numbers, stored in E.164 form: {@code +91XXXXXXXXXX}. */
public final class PhoneNumbers {

	private static final Pattern INDIAN_MOBILE = Pattern.compile("[6-9]\\d{9}");

	private PhoneNumbers() {
	}

	/**
	 * Accepts what people actually type ("98765 43210", "+91-98765-43210", "098765 43210")
	 * and returns {@code +919876543210}.
	 */
	public static String normalizeIndianMobile(String raw) {
		String digits = raw == null ? "" : raw.replaceAll("[\\s\\-().]", "");
		if (digits.startsWith("+91")) {
			digits = digits.substring(3);
		}
		else if (digits.length() == 12 && digits.startsWith("91")) {
			digits = digits.substring(2);
		}
		else if (digits.length() == 11 && digits.startsWith("0")) {
			digits = digits.substring(1);
		}
		if (!INDIAN_MOBILE.matcher(digits).matches()) {
			throw ApiException.badRequest("invalid_phone", "Please enter a valid 10-digit Indian mobile number.");
		}
		return "+91" + digits;
	}
}
