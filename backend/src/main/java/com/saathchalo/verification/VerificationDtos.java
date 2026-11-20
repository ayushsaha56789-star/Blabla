package com.saathchalo.verification;

import com.saathchalo.user.Gender;
import com.saathchalo.user.MemberProfiles.MemberCard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class VerificationDtos {

	private VerificationDtos() {
	}

	/** IDs we accept. Aadhaar only with the first 8 digits covered ("masked Aadhaar"). */
	public enum DocumentType {
		DRIVING_LICENCE, AADHAAR, PAN, PASSPORT, VOTER_ID
	}

	public enum RejectReason {
		UNREADABLE, NAME_MISMATCH, NOT_ACCEPTED, EXPIRED, AADHAAR_NOT_MASKED, OTHER
	}

	/** {@code status}: PENDING, APPROVED or REJECTED. */
	public record IdCheckView(UUID id, DocumentType documentType, String status, Instant submittedAt, Instant decidedAt,
			RejectReason rejectReason, String rejectNote) {
	}

	/** {@code email} is masked; {@code latestIdCheck} is null until the member has sent one. */
	public record VerificationStatus(boolean phoneVerified, String email, boolean emailVerified, boolean idVerified,
			IdCheckView latestIdCheck) {
	}

	/** {@code devCode} only in development and tests, like login codes. */
	public record EmailCodeSent(String sentTo, long expiresInSeconds, long resendInSeconds, String devCode) {
	}

	public record ConfirmEmailRequest(
			@NotBlank(message = "Enter the 6-digit code") @Pattern(regexp = "\\d{6}", message = "Enter the 6-digit code") String code) {
	}

	/** What staff need to compare with the photo: the profile's full name, gender and date of birth. */
	public record StaffIdCheck(UUID id, MemberCard member, String fullName, Gender gender, LocalDate dateOfBirth,
			Integer age, DocumentType documentType, String status, Instant submittedAt, Instant decidedAt, String decidedBy,
			RejectReason rejectReason, String rejectNote, boolean photoAvailable) {
	}

	public record RejectRequest(@NotNull(message = "Choose a reason") RejectReason reason,
			@Size(max = 300, message = "Keep the note under 300 characters") String note) {
	}
}
