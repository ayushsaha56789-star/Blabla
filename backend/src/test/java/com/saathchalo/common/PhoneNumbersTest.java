package com.saathchalo.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

	@ParameterizedTest
	@ValueSource(strings = { "9876543210", "98765 43210", "+91 98765 43210", "+91-98765-43210", "919876543210",
			"09876543210", "(98765) 43210" })
	void acceptsCommonWaysOfTypingAnIndianMobile(String input) {
		assertThat(PhoneNumbers.normalizeIndianMobile(input)).isEqualTo("+919876543210");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "12345", "5876543210", "98765432101", "+1 415 555 0100", "abcdefghij" })
	void rejectsAnythingElse(String input) {
		assertThatThrownBy(() -> PhoneNumbers.normalizeIndianMobile(input)).isInstanceOf(ApiException.class)
			.hasMessageContaining("10-digit");
	}
}
