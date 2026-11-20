package com.saathchalo.support;

import com.saathchalo.user.MemberProfiles.MemberCard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SupportDtos {

	private SupportDtos() {
	}

	public enum Category {
		ACCOUNT, BOOKING, OFFERING, PAYMENT, SAFETY, PRIVACY, BUG, GRIEVANCE, OTHER
	}

	public record NewTicket(
			@NotNull(message = "Choose what it's about") Category category,
			@NotBlank(message = "Add a short subject")
			@Size(max = 120, message = "Keep the subject under 120 characters") String subject,
			@NotBlank(message = "Tell us what happened")
			@Size(max = 4000, message = "Keep it under 4,000 characters") String body,
			UUID rideId) {
	}

	public record Reply(@NotBlank(message = "Write a reply first")
			@Size(max = 4000, message = "Keep it under 4,000 characters") String body) {
	}

	/** {@code author}: who wrote it; for staff replies only the first name and "SaathChalo support" are shown. */
	public record MessageView(UUID id, boolean fromStaff, String author, String body, Instant createdAt) {
	}

	/** {@code newReply}: support answered and the member hasn't opened the request since. */
	public record TicketSummary(UUID id, Category category, String subject, String status, boolean newReply,
			Instant createdAt, Instant updatedAt) {
	}

	public record TicketView(UUID id, Category category, String subject, UUID rideId, String status, Instant createdAt,
			Instant updatedAt, Instant firstResponseAt, List<MessageView> messages) {
	}

	/** {@code respondBy}: 24 hours after the request for the first reply. */
	public record StaffTicketRow(UUID id, MemberCard member, Category category, String subject, String status,
			Instant createdAt, Instant updatedAt, Instant firstResponseAt, Instant respondBy, boolean overdue) {
	}

	public record StaffTicketView(StaffTicketRow ticket, String maskedPhone, UUID rideId, List<MessageView> messages) {
	}
}
