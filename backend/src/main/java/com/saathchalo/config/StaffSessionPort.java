package com.saathchalo.config;

import java.util.Set;
import java.util.UUID;

/** Implemented by the auth module; checks a staff console session on every console request. */
public interface StaffSessionPort {

	/**
	 * @return the staff member's permissions, e.g. {@code users.view}
	 * @throws org.springframework.security.oauth2.server.resource.InvalidBearerTokenException if the session has
	 * ended (signed out, idle too long, too old, or the person is no longer staff)
	 */
	Set<String> validate(UUID sessionId, UUID userId);
}
