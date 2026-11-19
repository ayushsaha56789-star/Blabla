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
		Cors cors,
		@Valid @NotNull Security security,
		Bootstrap bootstrap,
		@Valid @NotNull Staff staff) {

	public record Jwt(@NotBlank String secret, @NotBlank String issuer,
			@NotNull Duration accessTokenTtl, @NotNull Duration refreshTokenTtl) {
	}

	public record Auth(boolean refreshCookieSecure) {
	}

	public record Otp(@NotNull Duration ttl, @Min(1) int maxAttempts, @NotNull Duration resendCooldown,
			@Min(1) int maxPerHour, @Min(1) int maxPerIpPerHour, boolean exposeCode) {
	}

	public record Sms(@NotBlank String provider) {
	}

	public record Storage(@NotBlank String dir) {
	}

	/** {@code dataKey}: base64 of 32 random bytes, used to encrypt sensitive fields such as 2FA secrets. */
	public record Security(@NotBlank String dataKey) {
	}

	/** The owner's phone number. When it logs in and no super admin exists yet, it becomes super admin. */
	public record Bootstrap(String superAdminPhone) {
	}

	/** Staff console sessions end after {@code sessionIdleTimeout} without activity, and always after {@code sessionMaxAge}. */
	public record Staff(@NotNull Duration sessionIdleTimeout, @NotNull Duration sessionMaxAge, @NotBlank String totpIssuer) {
	}

	public record Cors(List<String> allowedOrigins) {

		public List<String> origins() {
			return allowedOrigins == null ? List.of()
					: allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
		}
	}
}
