package com.saathchalo.reviews;

import com.saathchalo.rides.RideLookup.RideBrief;
import com.saathchalo.user.MemberProfiles.MemberCard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReviewDtos {

	private ReviewDtos() {
	}

	public record WriteRequest(
			@NotNull(message = "Which trip?") UUID rideId,
			@NotNull(message = "Who are you reviewing?") UUID subjectId,
			@NotNull(message = "Please choose 1 to 5 stars")
			@Min(value = 1, message = "Please choose 1 to 5 stars") @Max(value = 5, message = "Please choose 1 to 5 stars") Integer rating,
			@Size(max = 500, message = "Keep your review under 500 characters") String comment) {
	}

	/** {@code subjectRole}: DRIVER or PASSENGER — what the reviewed member was on that trip. */
	public record ReviewView(UUID id, MemberCard author, String subjectRole, int rating, String comment,
			String fromName, String toName, Instant createdAt) {
	}

	/** Someone this member travelled with and can still review. */
	public record PendingReview(RideBrief ride, MemberCard subject, String subjectRole) {
	}

	public record ReviewPage(Double average, int count, List<ReviewView> items, long total, int page, int size) {
	}
}
