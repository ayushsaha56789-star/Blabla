package com.saathchalo.auth;

import com.saathchalo.access.StaffAccessRevoked;
import com.saathchalo.user.MemberSuspended;
import java.time.Clock;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Ends sessions when other modules report that someone lost access. Runs inside the publisher's transaction. */
@Component
class AuthEventListeners {

	private final RefreshTokenRepository refreshTokens;

	private final StaffSessionService staffSessions;

	private final StaffTwoFactorService twoFactor;

	private final Clock clock;

	AuthEventListeners(RefreshTokenRepository refreshTokens, StaffSessionService staffSessions,
			StaffTwoFactorService twoFactor, Clock clock) {
		this.refreshTokens = refreshTokens;
		this.staffSessions = staffSessions;
		this.twoFactor = twoFactor;
		this.clock = clock;
	}

	@EventListener
	void memberSuspended(MemberSuspended event) {
		refreshTokens.revokeAllForUser(event.userId(), clock.instant());
		staffSessions.revokeAll(event.userId());
	}

	@EventListener
	void staffAccessRevoked(StaffAccessRevoked event) {
		twoFactor.removeFor(event.userId());
	}
}
