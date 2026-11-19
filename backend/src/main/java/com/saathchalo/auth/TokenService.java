package com.saathchalo.auth;

import com.saathchalo.config.AppProperties;
import com.saathchalo.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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

	public AccessToken createAccessToken(User user) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(config.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(config.issuer())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.subject(user.getId().toString())
			.claim("roles", List.of(user.getRole().name()))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(value, expiresAt);
	}
}
