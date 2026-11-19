package com.saathchalo.auth;

import com.saathchalo.auth.AuthDtos.OtpRequestResponse;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.Crypto;
import com.saathchalo.common.PhoneNumbers;
import com.saathchalo.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.SecretKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {

	private final OtpChallengeRepository challenges;

	private final SmsSender sms;

	private final AppProperties.Otp config;

	private final Clock clock;

	private final byte[] hashKey;

	public OtpService(OtpChallengeRepository challenges, SmsSender sms, AppProperties props, Clock clock,
			SecretKey jwtSigningKey) {
		this.challenges = challenges;
		this.sms = sms;
		this.config = props.otp();
		this.clock = clock;
		// A key derived from the app secret, so a leaked database alone cannot reveal codes.
		this.hashKey = Crypto.hmacSha256Hex(jwtSigningKey.getEncoded(), "otp-hash-key").getBytes(StandardCharsets.UTF_8);
	}

	@Transactional
	public OtpRequestResponse requestCode(String rawPhone) {
		String phone = PhoneNumbers.normalizeIndianMobile(rawPhone);
		Instant now = clock.instant();

		challenges.findFirstByPhoneOrderByCreatedAtDesc(phone).ifPresent(last -> {
			Duration wait = Duration.between(now, last.getCreatedAt().plus(config.resendCooldown()));
			if (!wait.isNegative() && !wait.isZero()) {
				throw ApiException.tooManyRequests("otp_cooldown",
						"Please wait " + Math.max(1, wait.toSeconds()) + " seconds before asking for a new code.");
			}
		});
		if (challenges.countByPhoneAndCreatedAtAfter(phone, now.minus(Duration.ofHours(1))) >= config.maxPerHour()) {
			throw ApiException.tooManyRequests("otp_hourly_limit",
					"Too many codes requested for this number. Please try again in an hour.");
		}

		String code = Crypto.randomDigits(6);
		challenges.save(new OtpChallenge(phone, hash(phone, code), now, now.plus(config.ttl())));
		sms.send(phone, code + " is your SaathChalo login code. It expires in " + config.ttl().toMinutes()
				+ " minutes. Never share it with anyone.");

		return new OtpRequestResponse(phone, config.ttl().toSeconds(), config.resendCooldown().toSeconds(),
				config.exposeCode() ? code : null);
	}

	/**
	 * Checks a code against the latest challenge for the number.
	 * Wrong guesses are counted even though an exception is thrown, hence {@code noRollbackFor}.
	 * @return the normalised phone number, now proven to belong to the caller
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public String verifyCode(String rawPhone, String code) {
		String phone = PhoneNumbers.normalizeIndianMobile(rawPhone);
		String cleanCode = code == null ? "" : code.strip();
		if (!cleanCode.matches("\\d{6}")) {
			throw ApiException.badRequest("invalid_code", "Please enter the 6-digit code.");
		}
		Instant now = clock.instant();
		OtpChallenge challenge = challenges.findLatestForUpdate(phone)
			.orElseThrow(() -> ApiException.badRequest("otp_not_requested", "Please request a code first."));
		if (!challenge.isUsable(now)) {
			throw ApiException.badRequest("otp_expired", "This code has expired. Please request a new one.");
		}
		if (challenge.getAttempts() >= config.maxAttempts()) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "otp_locked",
					"Too many wrong attempts. Please request a new code.");
		}
		challenge.recordAttempt();
		if (!Crypto.constantTimeEquals(hash(phone, cleanCode), challenge.getCodeHash())) {
			int left = config.maxAttempts() - challenge.getAttempts();
			throw ApiException.badRequest("invalid_code", left > 0
					? "That code isn't right. " + left + (left == 1 ? " try" : " tries") + " left."
					: "That code isn't right. Please request a new code.");
		}
		challenge.consume(now);
		return phone;
	}

	private String hash(String phone, String code) {
		return Crypto.hmacSha256Hex(hashKey, phone + ":" + code);
	}
}
