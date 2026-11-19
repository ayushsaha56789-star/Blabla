package com.saathchalo.auth.staff;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time codes (RFC 6238), as used by Google Authenticator, Microsoft Authenticator and others:
 * HMAC-SHA1, 6 digits, a new code every 30 seconds. Codes from one step before or after are accepted to allow for
 * small clock differences.
 */
public final class Totp {

	static final int DIGITS = 6;

	static final long STEP_SECONDS = 30;

	private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	private static final SecureRandom RANDOM = new SecureRandom();

	private Totp() {
	}

	/** A new random secret (160 bits) in base32, the form authenticator apps expect. */
	public static String newSecret() {
		byte[] bytes = new byte[20];
		RANDOM.nextBytes(bytes);
		return base32Encode(bytes);
	}

	public static long stepAt(Instant time) {
		return Math.floorDiv(time.getEpochSecond(), STEP_SECONDS);
	}

	public static String codeAt(String base32Secret, long step) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(base32Decode(base32Secret), "HmacSHA1"));
			byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
			int offset = hash[hash.length - 1] & 0x0f;
			int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
					| ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
			return String.format("%06d", binary % 1_000_000);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/** @return the time step the code belongs to, if it is valid now (±1 step) */
	public static OptionalLong matchingStep(String base32Secret, String code, Instant now) {
		if (code == null || !code.matches("\\d{6}")) {
			return OptionalLong.empty();
		}
		long current = stepAt(now);
		long found = -1;
		for (long step = current - 1; step <= current + 1; step++) {
			// Check every candidate (no early exit) so timing does not reveal which one matched.
			boolean equal = MessageDigest.isEqual(codeAt(base32Secret, step).getBytes(StandardCharsets.US_ASCII),
					code.getBytes(StandardCharsets.US_ASCII));
			if (equal && found < 0) {
				found = step;
			}
		}
		return found < 0 ? OptionalLong.empty() : OptionalLong.of(found);
	}

	static String base32Encode(byte[] data) {
		StringBuilder out = new StringBuilder();
		int buffer = 0;
		int bits = 0;
		for (byte b : data) {
			buffer = (buffer << 8) | (b & 0xff);
			bits += 8;
			while (bits >= 5) {
				out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
				bits -= 5;
			}
		}
		if (bits > 0) {
			out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
		}
		return out.toString();
	}

	static byte[] base32Decode(String text) {
		String clean = text.replace(" ", "").replace("=", "").toUpperCase();
		ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
		int buffer = 0;
		int bits = 0;
		for (char c : clean.toCharArray()) {
			int value = BASE32.indexOf(c);
			if (value < 0) {
				throw new IllegalArgumentException("Not base32");
			}
			buffer = (buffer << 5) | value;
			bits += 5;
			if (bits >= 8) {
				out.put((byte) ((buffer >> (bits - 8)) & 0xff));
				bits -= 8;
			}
		}
		return out.array();
	}
}
