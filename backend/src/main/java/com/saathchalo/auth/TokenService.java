package com.saathchalo.auth;

import com.saathchalo.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived access tokens (JWT, HS256). */
@Service
public class TokenService {

	public record AccessToken(String value, Instant expiresAt) {
	}

	private final JwtEncoder encoder;

	private final AppProperties.Jwt config;

	private final Clock clock;

	public TokenService(JwtEncoder encoder, AppProperties props, Clock clock) {
		this.encoder = encoder;
		this.config = props.jwt();
		this.clock = clock;
	}

	public AccessToken createAccessToken(UUID userId) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(config.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(config.issuer())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.subject(userId.toString())
			.claim("roles", List.of("USER"))
			.build();
		return new AccessToken(encode(claims), expiresAt);
	}

	/**
	 * A console token, issued only after two-step verification. {@code sid} points at a server-side session that is
	 * checked on every request, so it can be ended at any time (sign-out, idle timeout, removal from the staff).
	 */
	public AccessToken createStaffToken(UUID userId, UUID sessionId, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(config.issuer())
			.issuedAt(clock.instant())
			.expiresAt(expiresAt)
			.subject(userId.toString())
			.claim("sid", sessionId.toString())
			.claim("roles", List.of("USER", "STAFF"))
			.claim("amr", List.of("otp", "totp"))
			.build();
		return new AccessToken(encode(claims), expiresAt);
	}

	private String encode(JwtClaimsSet claims) {
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
