package com.saathchalo.moderation;

import com.saathchalo.user.MemberProfiles.MemberCard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ModerationDtos {

	private ModerationDtos() {
	}

	public enum TargetType {
		MEMBER, RIDE, MESSAGE, REVIEW
	}

	public enum Reason {
		SAFETY, HARASSMENT, FAKE_PROFILE, NO_SHOW, SPAM, INAPPROPRIATE, OTHER
	}

	/** What staff did about a report. */
	public enum Action {
		/** Nothing breaks the rules. */
		DISMISS,
		/** Hide the reported message or review. */
		REMOVE_CONTENT,
		/** Action taken elsewhere, e.g. the member restricted or the ride cancelled. */
		ACTION_TAKEN
	}

	public record ReportRequest(
			@NotNull(message = "What are you reporting?") TargetType targetType,
			@NotNull(message = "What are you reporting?") UUID targetId,
			@NotNull(message = "Please choose a reason") Reason reason,
			@Size(max = 1000, message = "Keep it under 1,000 characters") String details) {
	}

	public record ReportReceipt(UUID id, Instant createdAt) {
	}

	public record ResolveRequest(
			@NotNull(message = "Choose what you did") Action action,
			@NotBlank(message = "Write a short note for the record")
			@Size(min = 3, max = 500, message = "Use 3 to 500 characters") String resolution) {
	}

	/**
	 * A report as staff see it. {@code acknowledgeBy}/{@code resolveBy}: the IT Rules deadlines (24 hours to
	 * acknowledge, 15 days to resolve). {@code reportsAboutMember}: all reports ever made about the reported member.
	 */
	public record ReportRow(UUID id, String status, TargetType targetType, UUID targetId, UUID rideId, Reason reason,
			String details, String snapshot, MemberCard reporter, MemberCard reported, long reportsAboutMember,
			Instant createdAt, Instant acknowledgedAt, Instant acknowledgeBy, Instant resolveBy, boolean overdue,
			Instant resolvedAt, String handledBy, String resolution) {
	}
}
