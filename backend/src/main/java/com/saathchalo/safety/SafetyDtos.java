package com.saathchalo.safety;

import com.saathchalo.rides.RideLookup.RideBrief;
import com.saathchalo.rides.RideLookup.StopTime;
import com.saathchalo.user.MemberProfiles.MemberCard;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SafetyDtos {

	private SafetyDtos() {
	}

	public record ContactRequest(
			@NotBlank(message = "Please enter their name")
			@Size(max = 60, message = "Use up to 60 characters")
			@Pattern(regexp = "^[\\p{L}\\p{M} .'-]+$", message = "Please use letters only") String name,
			@NotBlank(message = "Please enter their mobile number") String phone) {
	}

	/** An emergency contact as its owner sees it. */
	public record ContactView(UUID id, String name, String phone, Instant createdAt) {
	}

	public record LocationRequest(
			@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
			@NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
			@Min(0) @Max(100_000) Integer accuracy) {
	}

	/** A trip link. {@code path} is "/trip/…"; the website adds its own address. */
	public record ShareView(String path, Instant expiresAt, Instant liveAt) {
	}

	public record CarInfo(String make, String model, String color, String plate) {
	}

	/** The latest location the member shared, and the nearest known town. */
	public record LiveLocation(double lat, double lng, Integer accuracy, Instant at, String near) {
	}

	/** What someone opening a trip link sees. No phone numbers. */
	public record SharedTrip(String sharerName, boolean sharerIsDriver, RideBrief ride, List<StopTime> stops,
			MemberCard driver, CarInfo car, LiveLocation live, Instant sosAt, Instant expiresAt) {
	}

	public record SosRequest(UUID rideId,
			@DecimalMin("-90") @DecimalMax("90") Double lat,
			@DecimalMin("-180") @DecimalMax("180") Double lng,
			@Min(0) @Max(100_000) Integer accuracy) {
	}

	/** {@code sharePath}: the trip link sent to emergency contacts, when the alert is about a ride. */
	public record SosView(UUID id, int contactsNotified, String sharePath, Instant createdAt) {
	}

	/** An SOS alert as staff see it. The member's number is masked; staff reveal it through the member page. */
	public record SosRow(UUID id, String status, MemberCard member, String maskedPhone, RideBrief ride, Double lat,
			Double lng, Integer accuracy, String near, int contactsNotified, Instant createdAt, Instant acknowledgedAt,
			String acknowledgedBy, Instant resolvedAt, String resolvedBy, String notes) {
	}

	public record ResolveRequest(@Size(max = 1000, message = "Keep notes under 1,000 characters") String notes) {
	}
}
