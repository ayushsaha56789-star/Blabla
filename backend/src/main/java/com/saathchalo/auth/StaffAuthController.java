package com.saathchalo.auth;

import com.saathchalo.auth.StaffSessionService.AdminSession;
import com.saathchalo.auth.StaffTwoFactorService.SetupComplete;
import com.saathchalo.auth.StaffTwoFactorService.StaffStatus;
import com.saathchalo.auth.StaffTwoFactorService.TotpSetup;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.CurrentUser;
import com.saathchalo.common.RequestInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff two-step verification and console sign-in. Uses the normal member token, except where noted. */
@RestController
@RequestMapping("/api/auth/staff")
public class StaffAuthController {

	public record CodeRequest(@NotBlank(message = "Please enter the 6-digit code") String code) {
	}

	public record SignInRequest(String code, String recoveryCode) {
	}

	public record RecoveryCodes(List<String> recoveryCodes) {
	}

	private final StaffTwoFactorService twoFactor;

	private final StaffSessionService sessions;

	private final StaffConfirmations confirmations;

	public StaffAuthController(StaffTwoFactorService twoFactor, StaffSessionService sessions,
			StaffConfirmations confirmations) {
		this.twoFactor = twoFactor;
		this.sessions = sessions;
		this.confirmations = confirmations;
	}

	@GetMapping("/status")
	public StaffStatus status() {
		return twoFactor.status(CurrentUser.require());
	}

	@PostMapping("/totp/setup")
	public TotpSetup setup() {
		return twoFactor.setup(CurrentUser.require());
	}

	@PostMapping("/totp/confirm")
	public SetupComplete confirmSetup(@Valid @RequestBody CodeRequest request) {
		return twoFactor.confirmSetup(CurrentUser.require(), request.code(), RequestInfo.clientIp());
	}

	@PostMapping("/session")
	public AdminSession signIn(@RequestBody SignInRequest request) {
		if ((request.code() == null || request.code().isBlank())
				&& (request.recoveryCode() == null || request.recoveryCode().isBlank())) {
			throw ApiException.badRequest("invalid_code", "Please enter the 6-digit code from your authenticator app.");
		}
		return twoFactor.signIn(CurrentUser.require(), request.code(), request.recoveryCode(), RequestInfo.clientIp());
	}

	/** Ends the console session of the token used for this call (send the console token). */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout() {
		CurrentUser.staffSessionId().ifPresent(sessions::revoke);
		return ResponseEntity.noContent().build();
	}

	/** Replaces all recovery codes. Needs the console token and a fresh code in {@code X-Confirm-Code}. */
	@PostMapping("/recovery-codes")
	public RecoveryCodes regenerateRecoveryCodes(jakarta.servlet.http.HttpServletRequest request) {
		if (CurrentUser.staffSessionId().isEmpty()) {
			throw ApiException.forbidden("console_session_required", "Please open the staff console first.");
		}
		confirmations.check(request);
		return new RecoveryCodes(twoFactor.regenerateRecoveryCodes(CurrentUser.require()));
	}
}
