package com.saathchalo.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts individual sensitive values (2FA secrets now; licence and RC numbers later) with AES-256-GCM,
 * so a copy of the database alone does not reveal them. Stored form: {@code v1:<base64(iv + ciphertext)>}.
 */
public final class FieldEncryptor {

	private static final String PREFIX = "v1:";

	private static final int IV_BYTES = 12;

	private static final int TAG_BITS = 128;

	private final SecretKey key;

	private final SecureRandom random = new SecureRandom();

	public FieldEncryptor(byte[] keyBytes) {
		if (keyBytes == null || keyBytes.length != 32) {
			throw new IllegalArgumentException("The data encryption key must be exactly 32 bytes (base64 of 32 random bytes)");
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	public String encrypt(String plain) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] out = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
			return PREFIX + Base64.getEncoder().encodeToString(out);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Encryption failed", e);
		}
	}

	public String decrypt(String stored) {
		if (stored == null || !stored.startsWith(PREFIX)) {
			throw new IllegalArgumentException("Unknown encrypted value format");
		}
		try {
			byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
			byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
			return new String(plain, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Decryption failed (wrong key or tampered value)", e);
		}
	}
}
