package com.saathchalo.auth;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/** Checks the {@code X-Confirm-Code} header for sensitive staff actions; usable from code as well as by annotation. */
@Service
public class StaffConfirmations {

	public static final String HEADER = "X-Confirm-Code";

	private final StaffTwoFactorService twoFactor;

	public StaffConfirmations(StaffTwoFactorService twoFactor) {
		this.twoFactor = twoFactor;
	}

	public void check(HttpServletRequest request) {
		String code = request.getHeader(HEADER);
		if (code == null || code.isBlank()) {
			throw ApiException.forbidden("confirmation_required",
					"Please confirm with the 6-digit code from your authenticator app.");
		}
		twoFactor.confirm(CurrentUser.require(), code.strip());
	}
}
