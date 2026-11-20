package com.saathchalo.auth;

import com.saathchalo.access.AccessService;
import com.saathchalo.auth.AuthDtos.AuthResponse;
import com.saathchalo.auth.AuthDtos.StaffInfo;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.Crypto;
import com.saathchalo.config.AccountStatus;
import com.saathchalo.config.AccountStatusPort;
import com.saathchalo.config.AppProperties;
import com.saathchalo.features.FeatureService;
import com.saathchalo.user.UserDtos.MeResponse;
import com.saathchalo.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	/** What the controller needs: the JSON body plus the raw refresh token for the cookie. */
	public record Session(AuthResponse body, String refreshToken, Duration refreshTtl) {
	}

	private final UserService users;

	private final AccessService access;

	private final FeatureService features;

	private final AccountStatusPort accounts;

	private final StaffTwoFactorService twoFactor;

	private final StaffSessionService staffSessions;

	private final RefreshTokenRepository refreshTokens;

	private final OtpChallengeRepository otpChallenges;

	private final TokenService tokenService;

	private final AppProperties.Jwt config;

	private final Clock clock;

	public AuthService(UserService users, AccessService access, FeatureService features, AccountStatusPort accounts,
			StaffTwoFactorService twoFactor, StaffSessionService staffSessions, RefreshTokenRepository refreshTokens,
			OtpChallengeRepository otpChallenges, TokenService tokenService, AppProperties props, Clock clock) {
		this.users = users;
		this.access = access;
		this.features = features;
		this.accounts = accounts;
		this.twoFactor = twoFactor;
		this.staffSessions = staffSessions;
		this.refreshTokens = refreshTokens;
		this.otpChallenges = otpChallenges;
		this.tokenService = tokenService;
		this.config = props.jwt();
		this.clock = clock;
	}

	/** Called before sending a login code, so no SMS goes to numbers that can't log in anyway. */
	@Transactional(readOnly = true)
	public void ensureCanLogIn(String phone) {
		Optional<UUID> existing = users.idByPhone(phone);
		if (existing.isEmpty() && !features.signupsOpen()) {
			throw signupsPaused();
		}
		existing.ifPresent(this::ensureNotSuspended);
	}

	/** Signs in (or signs up) the owner of a phone number that has just passed OTP verification. */
	@Transactional
	public Session loginWithVerifiedPhone(String phone) {
		ensureCanLogIn(phone);
		UserService.LoginResult login = users.loginByPhone(phone);
		access.bootstrapSuperAdmin(login.me().id(), phone, login.me().displayName());
		return startSession(login.me(), login.restored());
	}

	/** Exchanges a refresh token for a new access token, rotating the refresh token. */
	@Transactional(noRollbackFor = ApiException.class)
	public Session refresh(String rawRefreshToken) {
		Instant now = clock.instant();
		RefreshToken token = refreshTokens.findByTokenHash(Crypto.sha256Hex(rawRefreshToken))
			.filter(t -> t.isActive(now))
			.orElseThrow(() -> ApiException.unauthorized("session_expired", "Your session has ended. Please log in again."));
		token.revoke(now);
		ensureNotSuspended(token.getUserId());
		return startSession(users.me(token.getUserId()), false);
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		Instant now = clock.instant();
		refreshTokens.findByTokenHash(Crypto.sha256Hex(rawRefreshToken)).ifPresent(t -> {
			t.revoke(now);
			staffSessions.revokeAll(t.getUserId());
		});
	}

	private Session startSession(MeResponse me, boolean restored) {
		Instant now = clock.instant();
		String raw = Crypto.randomUrlSafeToken(32);
		refreshTokens.save(new RefreshToken(me.id(), Crypto.sha256Hex(raw), now, now.plus(config.refreshTokenTtl())));
		TokenService.AccessToken accessToken = tokenService.createAccessToken(me.id());
		StaffInfo staff = access.membership(me.id())
			.map(m -> new StaffInfo(m.roleCode(), m.roleName(), twoFactor.isEnabled(me.id())))
			.orElse(null);
		AuthResponse body = new AuthResponse(accessToken.value(), accessToken.expiresAt(), me, staff, restored);
		return new Session(body, raw, config.refreshTokenTtl());
	}

	private void ensureNotSuspended(UUID userId) {
		AccountStatus status = accounts.statusOf(userId);
		if (status.suspended()) {
			throw ApiException.forbidden("account_suspended", "Your account is suspended.")
				.with("reason", status.reason())
				.with("until", status.until() == null ? null : status.until().toString());
		}
	}

	private static ApiException signupsPaused() {
		return ApiException.forbidden("signups_paused",
				"New sign-ups are paused right now. Please try again a little later.");
	}

	/** Nightly housekeeping: old OTP challenges, dead sessions. */
	@Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
	@Transactional
	public void cleanUp() {
		Instant now = clock.instant();
		int otps = otpChallenges.deleteCreatedBefore(now.minus(Duration.ofDays(1)));
		int tokens = refreshTokens.deleteExpired(now, now.minus(Duration.ofDays(7)));
		int consoles = staffSessions.deleteOld();
		log.info("Cleanup removed {} OTP challenges, {} refresh tokens and {} console sessions", otps, tokens, consoles);
	}
}
