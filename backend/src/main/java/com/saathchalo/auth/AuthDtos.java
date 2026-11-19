package com.saathchalo.auth;

import com.saathchalo.user.UserDtos.MeResponse;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class AuthDtos {

	private AuthDtos() {
	}

	public record OtpRequest(@NotBlank(message = "Please enter your mobile number") String phone) {
	}

	/** {@code devCode} is only filled in local development (app.otp.expose-code=true). */
	public record OtpRequestResponse(String phone, long expiresInSeconds, long resendInSeconds, String devCode) {
	}

	public record OtpVerifyRequest(@NotBlank(message = "Please enter your mobile number") String phone,
			@NotBlank(message = "Please enter the code") String code) {
	}

	/** {@code staff} is null for members who are not on the staff. */
	public record AuthResponse(String accessToken, Instant expiresAt, MeResponse user, StaffInfo staff) {
	}

	public record StaffInfo(String role, String roleName, boolean totpEnabled) {
	}
}
