package com.saathchalo.auth;

import com.saathchalo.auth.AuthDtos.AuthResponse;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.Crypto;
import com.saathchalo.config.AppProperties;
import com.saathchalo.user.User;
import com.saathchalo.user.UserRepository;
import com.saathchalo.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

	private final UserRepository users;

	private final RefreshTokenRepository refreshTokens;

	private final OtpChallengeRepository otpChallenges;

	private final TokenService tokenService;

	private final UserService userService;

	private final AppProperties.Jwt config;

	private final Clock clock;

	public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
			OtpChallengeRepository otpChallenges, TokenService tokenService, UserService userService,
			AppProperties props, Clock clock) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.otpChallenges = otpChallenges;
		this.tokenService = tokenService;
		this.userService = userService;
		this.config = props.jwt();
		this.clock = clock;
	}

	/** Signs in (or signs up) the owner of a phone number that has just passed OTP verification. */
	@Transactional
	public Session loginWithVerifiedPhone(String phone) {
		User user = users.findByPhone(phone).orElseGet(() -> users.save(new User(phone)));
		user.setPhoneVerified(true);
		return startSession(user);
	}

	/** Exchanges a refresh token for a new access token, rotating the refresh token. */
	@Transactional
	public Session refresh(String rawRefreshToken) {
		Instant now = clock.instant();
		RefreshToken token = refreshTokens.findByTokenHash(Crypto.sha256Hex(rawRefreshToken))
			.filter(t -> t.isActive(now))
			.orElseThrow(() -> ApiException.unauthorized("session_expired", "Your session has ended. Please log in again."));
		token.revoke(now);
		return startSession(token.getUser());
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		Instant now = clock.instant();
		refreshTokens.findByTokenHash(Crypto.sha256Hex(rawRefreshToken)).ifPresent(t -> t.revoke(now));
	}

	private Session startSession(User user) {
		Instant now = clock.instant();
		String raw = Crypto.randomUrlSafeToken(32);
		refreshTokens.save(new RefreshToken(user, Crypto.sha256Hex(raw), now, now.plus(config.refreshTokenTtl())));
		TokenService.AccessToken access = tokenService.createAccessToken(user);
		AuthResponse body = new AuthResponse(access.value(), access.expiresAt(), userService.toMe(user));
		return new Session(body, raw, config.refreshTokenTtl());
	}

	/** Nightly housekeeping: old OTP challenges and dead sessions. */
	@Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
	@Transactional
	public void cleanUp() {
		Instant now = clock.instant();
		int otps = otpChallenges.deleteCreatedBefore(now.minus(Duration.ofDays(1)));
		int tokens = refreshTokens.deleteExpired(now, now.minus(Duration.ofDays(7)));
		log.info("Cleanup removed {} OTP challenges and {} refresh tokens", otps, tokens);
	}
}
