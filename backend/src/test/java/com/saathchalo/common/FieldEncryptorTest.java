package com.saathchalo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class FieldEncryptorTest {

	private final FieldEncryptor encryptor = new FieldEncryptor(new byte[32]);

	@Test
	void roundTripsAndNeverRepeatsCiphertext() {
		String a = encryptor.encrypt("JBSWY3DPEHPK3PXP");
		String b = encryptor.encrypt("JBSWY3DPEHPK3PXP");
		assertThat(a).startsWith("v1:").isNotEqualTo(b).doesNotContain("JBSWY3DPEHPK3PXP");
		assertThat(encryptor.decrypt(a)).isEqualTo("JBSWY3DPEHPK3PXP");
	}

	@Test
	void detectsTamperingAndWrongKeys() {
		String stored = encryptor.encrypt("secret");
		byte[] raw = Base64.getDecoder().decode(stored.substring(3));
		raw[raw.length - 1] ^= 1;
		String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);
		assertThatThrownBy(() -> encryptor.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
		byte[] otherKey = new byte[32];
		otherKey[0] = 1;
		assertThatThrownBy(() -> new FieldEncryptor(otherKey).decrypt(stored)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void masksPhoneNumbers() {
		assertThat(Masking.phone("+919876543210")).isEqualTo("+91 98••• ••210");
	}
}
