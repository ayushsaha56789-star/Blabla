package com.saathchalo.common;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** The member (and, for console requests, the staff session) behind the current request. */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static Optional<UUID> id() {
		return token().map(t -> UUID.fromString(t.getToken().getSubject()));
	}

	public static UUID require() {
		return id().orElseThrow(() -> ApiException.unauthorized("login_required", "Please log in."));
	}

	/** Set only on tokens issued after staff two-step verification. */
	public static Optional<UUID> staffSessionId() {
		return token().map(t -> t.getToken().getClaimAsString("sid")).map(UUID::fromString);
	}

	private static Optional<JwtAuthenticationToken> token() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		return auth instanceof JwtAuthenticationToken jwt ? Optional.of(jwt) : Optional.empty();
	}
}
