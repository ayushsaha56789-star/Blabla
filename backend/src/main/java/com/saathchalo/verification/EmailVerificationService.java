package com.saathchalo.verification;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.Crypto;
import com.saathchalo.common.Masking;
import com.saathchalo.common.RateLimiter;
import com.saathchalo.config.AppProperties;
import com.saathchalo.user.UserService;
import com.saathchalo.verification.VerificationDtos.EmailCodeSent;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Confirms a member's email address with a 6-digit code, stored only as a keyed hash (like login codes). */
@Service
public class EmailVerificationService {

	static final Duration CODE_TTL = Duration.ofMinutes(15);

	static final Duration RESEND_AFTER = Duration.ofSeconds(60);

	static final int MAX_ATTEMPTS = 5;

	static final int MAX_PER_DAY = 10;

	private final UserService users;

	private final JdbcTemplate jdbc;

	private final EmailSender email;

	private final RateLimiter limiter;

	private final Clock clock;

	private final byte[] hashKey;

	private final boolean exposeCode;

	public EmailVerificationService(UserService users, JdbcTemplate jdbc, EmailSender email, RateLimiter limiter,
			AppProperties props, Clock clock) {
		this.users = users;
		this.jdbc = jdbc;
		this.email = email;
		this.limiter = limiter;
		this.clock = clock;
		this.hashKey = Crypto.hmacSha256Hex(Base64.getDecoder().decode(props.security().dataKey()), "email-code-key")
			.getBytes(StandardCharsets.UTF_8);
		this.exposeCode = props.otp().exposeCode();
	}

	@Transactional
	public EmailCodeSent sendCode(UUID me) {
		String address = users.email(me)
			.orElseThrow(() -> ApiException.badRequest("no_email", "Add your email address to your profile first."));
		if (users.me(me).emailVerified()) {
			throw ApiException.conflict("already_verified", "Your email address is already verified.");
		}
		Instant now = clock.instant();
		List<Timestamp> last = jdbc.queryForList("SELECT sent_at FROM email_codes WHERE user_id = ?", Timestamp.class, me);
		if (!last.isEmpty()) {
			Duration wait = Duration.between(now, last.getFirst().toInstant().plus(RESEND_AFTER));
			if (!wait.isNegative() && !wait.isZero()) {
				throw ApiException.tooManyRequests("code_cooldown", "Please wait a moment before asking for another code.")
					.with("retryAfterSeconds", wait.toSeconds() + 1);
			}
		}
		if (!limiter.tryAcquire("email-code:" + me, MAX_PER_DAY, Duration.ofDays(1))) {
			throw ApiException.tooManyRequests("slow_down", "You've asked for several codes today. Please try again tomorrow.");
		}
		String code = Crypto.randomDigits(6);
		jdbc.update("""
				INSERT INTO email_codes (user_id, email, code_hash, attempts, sent_at, expires_at) VALUES (?, ?, ?, 0, ?, ?)
				ON CONFLICT (user_id) DO UPDATE SET email = EXCLUDED.email, code_hash = EXCLUDED.code_hash, attempts = 0,
				    sent_at = EXCLUDED.sent_at, expires_at = EXCLUDED.expires_at""",
				me, address, hash(me, address, code), Timestamp.from(now), Timestamp.from(now.plus(CODE_TTL)));
		email.send(address, "Your SaathChalo code: " + code, """
				Hi!

				Use this code to confirm your email address on SaathChalo: %s

				It expires in 15 minutes. If you didn't ask for it, you can ignore this email.

				— SaathChalo""".formatted(code));
		return new EmailCodeSent(Masking.email(address), CODE_TTL.toSeconds(), RESEND_AFTER.toSeconds(), exposeCode ? code : null);
	}

	/** Wrong guesses are counted even though the request fails, hence no rollback for {@link ApiException}. */
	@Transactional(noRollbackFor = ApiException.class)
	public void confirm(UUID me, String code) {
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT email, code_hash, attempts, expires_at FROM email_codes WHERE user_id = ? FOR UPDATE", me);
		if (rows.isEmpty() || ((Timestamp) rows.getFirst().get("expires_at")).toInstant().isBefore(clock.instant())) {
			throw ApiException.badRequest("code_expired", "That code has expired. Ask for a new one.");
		}
		Map<String, Object> row = rows.getFirst();
		int attempts = ((Number) row.get("attempts")).intValue();
		if (attempts >= MAX_ATTEMPTS) {
			throw ApiException.tooManyRequests("too_many_attempts", "Too many wrong codes. Ask for a new one.");
		}
		String address = (String) row.get("email");
		if (!Crypto.constantTimeEquals(hash(me, address, code), (String) row.get("code_hash"))) {
			jdbc.update("UPDATE email_codes SET attempts = attempts + 1 WHERE user_id = ?", me);
			throw ApiException.badRequest("wrong_code", "That code isn't right. Check the email and try again.")
				.with("attemptsLeft", MAX_ATTEMPTS - attempts - 1);
		}
		jdbc.update("DELETE FROM email_codes WHERE user_id = ?", me);
		if (!users.markEmailVerified(me, address)) {
			throw ApiException.conflict("email_changed", "Your email address changed after we sent the code. Ask for a new one.");
		}
	}

	private String hash(UUID me, String address, String code) {
		return Crypto.hmacSha256Hex(hashKey, me + ":" + address.toLowerCase(java.util.Locale.ROOT) + ":" + code);
	}
}
