package com.saathchalo.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.saathchalo.auth.staff.Totp;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTest {

	/** RFC 6238 appendix B test vectors (SHA-1), last 6 digits. Secret is ASCII "12345678901234567890". */
	@ParameterizedTest
	@CsvSource({ "59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924",
			"2000000000, 279037", "20000000000, 353130" })
	void matchesTheRfcTestVectors(long epochSeconds, String expected) {
		String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
		assertThat(Totp.codeAt(secret, Totp.stepAt(Instant.ofEpochSecond(epochSeconds)))).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({ "-30, true", "0, true", "30, true", "60, false", "-60, false" })
	void acceptsOneStepOfClockDrift(long offsetSeconds, boolean accepted) {
		String secret = Totp.newSecret();
		Instant now = Instant.ofEpochSecond(1_800_000_015L);
		String code = Totp.codeAt(secret, Totp.stepAt(now.plusSeconds(offsetSeconds)));
		assertThat(Totp.matchingStep(secret, code, now).isPresent()).isEqualTo(accepted);
	}
}
