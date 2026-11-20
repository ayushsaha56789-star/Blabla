package com.saathchalo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
		@Valid @NotNull Staff staff,
		@Valid @NotNull Rides rides,
		@NotBlank String publicUrl,
		Legal legal) {

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

	/**
	 * Carpooling rules. Prices are rupees per seat for a whole route; {@code maxPricePerKm} keeps rides cost-sharing
	 * rather than commercial. Searches match stops within {@code searchRadiusKm} of the places searched for. Booking
	 * requests the driver hasn't answered expire after {@code requestTtl}.
	 */
	public record Rides(@Positive double suggestedPricePerKm, @Positive double maxPricePerKm, @Min(1) int minPrice,
			@Min(1) int searchRadiusKm, @Min(1) int maxDaysAhead, @NotNull Duration requestTtl) {
	}

	/** Who members can complain to, as the law asks us to publish. Blank values mean "not appointed yet". */
	public record Legal(String company, String grievanceOfficer, String grievanceEmail, String address) {
	}

	public record Cors(List<String> allowedOrigins) {

		public List<String> origins() {
			return allowedOrigins == null ? List.of()
					: allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
		}
	}
}
