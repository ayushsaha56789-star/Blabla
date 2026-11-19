package com.saathchalo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** All custom settings under the {@code app.*} prefix. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
		@Valid @NotNull Jwt jwt,
		@Valid @NotNull Auth auth,
		@Valid @NotNull Otp otp,
		@Valid @NotNull Sms sms,
		@Valid @NotNull Storage storage,
		Cors cors) {

	public record Jwt(@NotBlank String secret, @NotBlank String issuer,
			@NotNull Duration accessTokenTtl, @NotNull Duration refreshTokenTtl) {
	}

	public record Auth(boolean refreshCookieSecure) {
	}

	public record Otp(@NotNull Duration ttl, @Min(1) int maxAttempts, @NotNull Duration resendCooldown,
			@Min(1) int maxPerHour, boolean exposeCode) {
	}

	public record Sms(@NotBlank String provider) {
	}

	public record Storage(@NotBlank String dir) {
	}

	public record Cors(List<String> allowedOrigins) {

		public List<String> origins() {
			return allowedOrigins == null ? List.of()
					: allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
		}
	}
}
