package com.saathchalo.auth;

import com.saathchalo.auth.AuthDtos.AuthResponse;
import com.saathchalo.auth.AuthDtos.OtpRequest;
import com.saathchalo.auth.AuthDtos.OtpRequestResponse;
import com.saathchalo.auth.AuthDtos.OtpVerifyRequest;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.PhoneNumbers;
import com.saathchalo.common.RateLimiter;
import com.saathchalo.common.RequestInfo;
import com.saathchalo.config.AppProperties;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	static final String REFRESH_COOKIE = "sc_refresh";

	private final OtpService otpService;

	private final AuthService authService;

	private final RateLimiter rateLimiter;

	private final boolean secureCookie;

	private final int maxCodesPerIp;

	public AuthController(OtpService otpService, AuthService authService, RateLimiter rateLimiter, AppProperties props) {
		this.otpService = otpService;
		this.authService = authService;
		this.rateLimiter = rateLimiter;
		this.secureCookie = props.auth().refreshCookieSecure();
		this.maxCodesPerIp = props.otp().maxPerIpPerHour();
	}

	@PostMapping("/otp/request")
	public OtpRequestResponse requestOtp(@Valid @RequestBody OtpRequest request) {
		String phone = PhoneNumbers.normalizeIndianMobile(request.phone());
		String ip = RequestInfo.clientIp();
		// Per-number limits live in OtpService; this one stops a single client from texting many numbers.
		if (ip != null && !rateLimiter.tryAcquire("otp-ip:" + ip, maxCodesPerIp, Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("otp_ip_limit", "Too many login codes requested. Please try again later.");
		}
		authService.ensureCanLogIn(phone);
		return otpService.requestCode(phone);
	}

	@PostMapping("/otp/verify")
	public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
		String phone = otpService.verifyCode(request.phone(), request.code());
		return withSession(authService.loginWithVerifiedPhone(phone));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
		if (token == null || token.isBlank()) {
			throw ApiException.unauthorized("no_session", "Please log in.");
		}
		return withSession(authService.refresh(token));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
		if (token != null && !token.isBlank()) {
			authService.logout(token);
		}
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO)).build();
	}

	private ResponseEntity<AuthResponse> withSession(AuthService.Session session) {
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), session.refreshTtl()))
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(session.body());
	}

	/** HttpOnly so page scripts can never read it; scoped to the auth endpoints only. */
	private String cookie(String value, Duration maxAge) {
		return ResponseCookie.from(REFRESH_COOKIE, value)
			.httpOnly(true)
			.secure(secureCookie)
			.sameSite("Strict")
			.path("/api/auth")
			.maxAge(maxAge)
			.build()
			.toString();
	}
}
