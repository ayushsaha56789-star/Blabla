package com.saathchalo.auth;

import com.saathchalo.access.AccessService;
import com.saathchalo.auth.staff.StaffSession;
import com.saathchalo.auth.staff.StaffSessionRepository;
import com.saathchalo.config.AppProperties;
import com.saathchalo.config.StaffSessionPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff console sessions: created after two-step verification and checked on every console request. */
@Service
public class StaffSessionService implements StaffSessionPort {

	/** A staff session that has been explicitly started. The token goes in the Authorization header for /api/admin. */
	public record AdminSession(String token, Instant expiresAt, long idleTimeoutSeconds) {
	}

	private static final Duration TOUCH_EVERY = Duration.ofSeconds(30);

	private final StaffSessionRepository sessions;

	private final AccessService access;

	private final TokenService tokens;

	private final AppProperties.Staff config;

	private final Clock clock;

	public StaffSessionService(StaffSessionRepository sessions, AccessService access, TokenService tokens,
			AppProperties props, Clock clock) {
		this.sessions = sessions;
		this.access = access;
		this.tokens = tokens;
		this.config = props.staff();
		this.clock = clock;
	}

	@Transactional
	public AdminSession start(UUID userId, String ip) {
		Instant now = clock.instant();
		StaffSession session = sessions.save(new StaffSession(userId, now, now.plus(config.sessionMaxAge()), ip));
		TokenService.AccessToken token = tokens.createStaffToken(userId, session.getId(), session.getExpiresAt());
		return new AdminSession(token.value(), session.getExpiresAt(), config.sessionIdleTimeout().toSeconds());
	}

	@Override
	@Transactional
	public Set<String> validate(UUID sessionId, UUID userId) {
		Instant now = clock.instant();
		StaffSession session = sessions.findById(sessionId)
			.filter(s -> s.getUserId().equals(userId))
			.orElseThrow(() -> new InvalidBearerTokenException("Console session not found"));
		if (session.getRevokedAt() != null || !now.isBefore(session.getExpiresAt())) {
			throw new InvalidBearerTokenException("Console session has ended");
		}
		if (Duration.between(session.getLastSeenAt(), now).compareTo(config.sessionIdleTimeout()) > 0) {
			session.revoke(now);
			throw new InvalidBearerTokenException("Console session timed out");
		}
		Set<String> permissions = access.permissionsOf(userId);
		if (permissions.isEmpty()) {
			session.revoke(now);
			throw new InvalidBearerTokenException("No longer on the staff");
		}
		if (Duration.between(session.getLastSeenAt(), now).compareTo(TOUCH_EVERY) > 0) {
			session.touch(now);
		}
		return permissions;
	}

	@Transactional
	public void revoke(UUID sessionId) {
		sessions.findById(sessionId).ifPresent(s -> s.revoke(clock.instant()));
	}

	@Transactional
	public void revokeAll(UUID userId) {
		sessions.revokeAllForUser(userId, clock.instant());
	}

	@Transactional(readOnly = true)
	public Map<UUID, Instant> lastActive(Collection<UUID> userIds) {
		Map<UUID, Instant> out = new HashMap<>();
		if (!userIds.isEmpty()) {
			for (Object[] row : sessions.lastSeen(userIds)) {
				out.put((UUID) row[0], (Instant) row[1]);
			}
		}
		return out;
	}

	@Transactional
	public int deleteOld() {
		return sessions.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(30)));
	}
}
