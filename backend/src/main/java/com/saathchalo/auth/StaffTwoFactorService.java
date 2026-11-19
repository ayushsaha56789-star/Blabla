package com.saathchalo.auth;

import com.saathchalo.access.AccessService;
import com.saathchalo.access.StaffMembership;
import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.auth.StaffSessionService.AdminSession;
import com.saathchalo.auth.staff.QrCodes;
import com.saathchalo.auth.staff.StaffRecoveryCode;
import com.saathchalo.auth.staff.StaffRecoveryCodeRepository;
import com.saathchalo.auth.staff.StaffTotp;
import com.saathchalo.auth.staff.StaffTotpRepository;
import com.saathchalo.auth.staff.Totp;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.Crypto;
import com.saathchalo.common.FieldEncryptor;
import com.saathchalo.common.RateLimiter;
import com.saathchalo.config.AccountStatusPort;
import com.saathchalo.config.AppProperties;
import com.saathchalo.user.UserService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-step verification for staff with an authenticator app. Wrong codes are counted per staff member; after five
 * within 15 minutes, codes are refused for 15 minutes. Every code is single-use.
 */
@Service
public class StaffTwoFactorService {

	public record StaffStatus(boolean staff, String role, String roleName, boolean totpEnabled, int recoveryCodesLeft) {
	}

	public record TotpSetup(String secret, String otpauthUri, String qrCode) {
	}

	public record SetupComplete(List<String> recoveryCodes, AdminSession session) {
	}

	static final int MAX_FAILURES = 5;

	static final Duration LOCK_WINDOW = Duration.ofMinutes(15);

	private static final int RECOVERY_CODES = 10;

	private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private static final SecureRandom RANDOM = new SecureRandom();

	private final AccessService access;

	private final UserService users;

	private final StaffTotpRepository totps;

	private final StaffRecoveryCodeRepository recoveryCodes;

	private final StaffSessionService sessions;

	private final AccountStatusPort accounts;

	private final FieldEncryptor encryptor;

	private final RateLimiter limiter;

	private final AuditService audit;

	private final Clock clock;

	private final String issuer;

	private final byte[] recoveryKey;

	public StaffTwoFactorService(AccessService access, UserService users, StaffTotpRepository totps,
			StaffRecoveryCodeRepository recoveryCodes, StaffSessionService sessions, AccountStatusPort accounts,
			FieldEncryptor encryptor, RateLimiter limiter, AuditService audit, Clock clock, AppProperties props) {
		this.access = access;
		this.users = users;
		this.totps = totps;
		this.recoveryCodes = recoveryCodes;
		this.sessions = sessions;
		this.accounts = accounts;
		this.encryptor = encryptor;
		this.limiter = limiter;
		this.audit = audit;
		this.clock = clock;
		this.issuer = props.staff().totpIssuer();
		byte[] dataKey = Base64.getDecoder().decode(props.security().dataKey());
		this.recoveryKey = Crypto.hmacSha256Hex(dataKey, "staff-recovery-codes").getBytes(StandardCharsets.UTF_8);
	}

	@Transactional(readOnly = true)
	public StaffStatus status(UUID userId) {
		return access.membership(userId)
			.map(m -> new StaffStatus(true, m.roleCode(), m.roleName(), isEnabled(userId),
					recoveryCodes.findByUserIdAndUsedAtIsNull(userId).size()))
			.orElse(new StaffStatus(false, null, null, false, 0));
	}

	@Transactional
	public TotpSetup setup(UUID userId) {
		requireUsableStaff(userId);
		StaffTotp existing = totps.findById(userId).orElse(null);
		if (existing != null && existing.isEnabled()) {
			throw ApiException.conflict("totp_already_enabled", "Two-step verification is already set up.");
		}
		String secret = Totp.newSecret();
		if (existing == null) {
			totps.save(new StaffTotp(userId, encryptor.encrypt(secret), clock.instant()));
		}
		else {
			existing.replaceSecret(encryptor.encrypt(secret), clock.instant());
		}
		String label = URLEncoder.encode(issuer + ":" + users.displayName(userId), StandardCharsets.UTF_8).replace("+", "%20");
		String uri = "otpauth://totp/" + label + "?secret=" + secret + "&issuer="
				+ URLEncoder.encode(issuer, StandardCharsets.UTF_8) + "&algorithm=SHA1&digits=6&period=30";
		return new TotpSetup(group(secret), uri, QrCodes.pngDataUrl(uri));
	}

	@Transactional(noRollbackFor = ApiException.class)
	public SetupComplete confirmSetup(UUID userId, String code, String ip) {
		requireUsableStaff(userId);
		StaffTotp totp = totps.findById(userId)
			.orElseThrow(() -> ApiException.badRequest("totp_setup_missing", "Please start the setup again."));
		if (totp.isEnabled()) {
			throw ApiException.conflict("totp_already_enabled", "Two-step verification is already set up.");
		}
		checkNotLocked(userId);
		OptionalLong step = Totp.matchingStep(secret(totp), clean(code), clock.instant());
		if (step.isEmpty()) {
			throw failed(userId, "setup");
		}
		totp.enable(clock.instant(), step.getAsLong());
		limiter.reset(failKey(userId));
		List<String> codes = newRecoveryCodes(userId);
		audit.record(actor(userId), "staff.2fa_enabled", "member", userId.toString(),
				users.displayName(userId) + " set up two-step verification", null);
		return new SetupComplete(codes, startSession(userId, ip));
	}

	/** Opens a console session with a code from the authenticator app, or with an unused recovery code. */
	@Transactional(noRollbackFor = ApiException.class)
	public AdminSession signIn(UUID userId, String code, String recoveryCode, String ip) {
		requireUsableStaff(userId);
		StaffTotp totp = totps.findById(userId).filter(StaffTotp::isEnabled)
			.orElseThrow(() -> ApiException.conflict("totp_not_set_up", "Please set up two-step verification first."));
		checkNotLocked(userId);
		if (recoveryCode != null && !recoveryCode.isBlank()) {
			String hash = recoveryHash(recoveryCode);
			StaffRecoveryCode match = recoveryCodes.findByUserIdAndUsedAtIsNull(userId).stream()
				.filter(c -> Crypto.constantTimeEquals(c.getCodeHash(), hash))
				.findFirst()
				.orElse(null);
			if (match == null) {
				throw failed(userId, "recovery code");
			}
			match.use(clock.instant());
			audit.record(actor(userId), "staff.recovery_code_used", "member", userId.toString(),
					users.displayName(userId) + " used a recovery code to open the console", null);
		}
		else {
			OptionalLong step = Totp.matchingStep(secret(totp), clean(code), clock.instant());
			if (step.isEmpty() || step.getAsLong() <= totp.getLastLoginStep()) {
				throw failed(userId, "sign-in");
			}
			totp.setLastLoginStep(step.getAsLong());
		}
		limiter.reset(failKey(userId));
		return startSession(userId, ip);
	}

	/** Checks a fresh code before a sensitive action (staff changes, critical switches, new recovery codes). */
	@Transactional(noRollbackFor = ApiException.class)
	public void confirm(UUID userId, String code) {
		StaffTotp totp = totps.findById(userId).filter(StaffTotp::isEnabled)
			.orElseThrow(() -> ApiException.forbidden("totp_not_set_up", "Please set up two-step verification first."));
		checkNotLocked(userId);
		OptionalLong step = Totp.matchingStep(secret(totp), clean(code), clock.instant());
		if (step.isEmpty() || step.getAsLong() <= totp.getLastConfirmStep()) {
			limiter.record(failKey(userId));
			audit.recordSeparately(actor(userId), "staff.2fa_failed", "member", userId.toString(),
					users.displayName(userId) + " entered a wrong code to confirm an action", null);
			throw new ApiException(HttpStatus.FORBIDDEN, "confirmation_invalid",
					"That code isn't right, or it was already used. Wait for the next code and try again.");
		}
		totp.setLastConfirmStep(step.getAsLong());
		limiter.reset(failKey(userId));
	}

	@Transactional
	public List<String> regenerateRecoveryCodes(UUID userId) {
		requireUsableStaff(userId);
		List<String> codes = newRecoveryCodes(userId);
		audit.record(actor(userId), "staff.recovery_codes_renewed", "member", userId.toString(),
				users.displayName(userId) + " created new recovery codes", null);
		return codes;
	}

	/** Removes someone's 2FA so they must set it up again (e.g. a lost phone). Done by a super admin. */
	@Transactional
	public void reset(UUID userId, Actor actor, String memberLabel) {
		removeFor(userId);
		audit.record(actor, "staff.2fa_reset", "member", userId.toString(),
				"Reset two-step verification for " + memberLabel, null);
	}

	@Transactional
	public void removeFor(UUID userId) {
		totps.findById(userId).ifPresent(totps::delete);
		recoveryCodes.deleteForUser(userId);
		sessions.revokeAll(userId);
	}

	public boolean isEnabled(UUID userId) {
		return totps.findById(userId).map(StaffTotp::isEnabled).orElse(false);
	}

	public Set<UUID> enabledAmong(Collection<UUID> userIds) {
		return totps.findAllById(userIds).stream().filter(StaffTotp::isEnabled).map(StaffTotp::getUserId)
			.collect(Collectors.toSet());
	}

	private AdminSession startSession(UUID userId, String ip) {
		AdminSession session = sessions.start(userId, ip);
		audit.record(actor(userId), "staff.console_opened", "member", userId.toString(),
				users.displayName(userId) + " opened the staff console", null);
		return session;
	}

	private StaffMembership requireUsableStaff(UUID userId) {
		StaffMembership m = access.membership(userId)
			.orElseThrow(() -> ApiException.forbidden("not_staff", "This account is not on the SaathChalo staff."));
		if (accounts.statusOf(userId).suspended()) {
			throw ApiException.forbidden("account_suspended", "Your account is suspended.");
		}
		return m;
	}

	private void checkNotLocked(UUID userId) {
		if (limiter.count(failKey(userId), LOCK_WINDOW) >= MAX_FAILURES) {
			throw ApiException.tooManyRequests("2fa_locked", "Too many wrong codes. Please wait 15 minutes and try again.");
		}
	}

	/** Counts and records a wrong code; returns the error for the caller to throw. */
	private ApiException failed(UUID userId, String where) {
		limiter.record(failKey(userId));
		int left = MAX_FAILURES - limiter.count(failKey(userId), LOCK_WINDOW);
		audit.recordSeparately(actor(userId), "staff.2fa_failed", "member", userId.toString(),
				users.displayName(userId) + " entered a wrong code (" + where + ")", null);
		return ApiException.badRequest("invalid_code", left > 0
				? "That code isn't right. Check that your phone's clock is correct and try again."
				: "Too many wrong codes. Please wait 15 minutes and try again.").with("attemptsLeft", Math.max(0, left));
	}

	private List<String> newRecoveryCodes(UUID userId) {
		recoveryCodes.deleteForUser(userId);
		List<String> codes = new ArrayList<>();
		for (int i = 0; i < RECOVERY_CODES; i++) {
			StringBuilder sb = new StringBuilder();
			for (int c = 0; c < 10; c++) {
				sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
			}
			String code = sb.substring(0, 5) + "-" + sb.substring(5);
			codes.add(code);
			recoveryCodes.save(new StaffRecoveryCode(userId, recoveryHash(code), clock.instant()));
		}
		return codes;
	}

	private String recoveryHash(String code) {
		String normal = code.toUpperCase().replaceAll("[^A-Z0-9]", "");
		return Crypto.hmacSha256Hex(recoveryKey, normal);
	}

	private String secret(StaffTotp totp) {
		return encryptor.decrypt(totp.getSecretEnc());
	}

	private Actor actor(UUID userId) {
		return new Actor(userId, users.displayName(userId));
	}

	private static String failKey(UUID userId) {
		return "2fa-fail:" + userId;
	}

	private static String clean(String code) {
		return code == null ? "" : code.replaceAll("\\s", "");
	}

	private static String group(String secret) {
		return secret.replaceAll("(.{4})(?!$)", "$1 ");
	}
}
