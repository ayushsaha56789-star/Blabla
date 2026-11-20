package com.saathchalo.rentals;

import com.saathchalo.user.MemberProfiles.MemberCard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Car rentals. Money in rupees. */
public final class RentalDtos {

	private RentalDtos() {
	}

	public enum Mode {
		SELF_DRIVE, WITH_DRIVER
	}

	public enum FuelPolicy {
		/** Return it with as much fuel as you got it. */
		SAME_LEVEL,
		/** Fuel is included in the price. */
		INCLUDED
	}

	public enum Fuel {
		EMPTY, QUARTER, HALF, THREE_QUARTERS, FULL
	}

	public enum DocumentKind {
		RC, INSURANCE, PERMIT
	}

	public record ListingRequest(
			@NotNull(message = "Choose the car") UUID vehicleId,
			Boolean selfDrive,
			Boolean withDriver,
			@NotNull(message = "Set a price per day")
			@Min(value = 100, message = "At least ₹100 a day") @Max(value = 50000, message = "At most ₹50,000 a day") Integer dayPrice,
			@Min(value = 50, message = "At least ₹50 an hour") @Max(value = 5000, message = "At most ₹5,000 an hour") Integer hourPrice,
			@Min(value = 0, message = "The deposit can't be negative") @Max(value = 100000, message = "At most ₹1,00,000") Integer deposit,
			@NotNull(message = "How many km a day are included?")
			@Min(value = 0, message = "Can't be negative") @Max(value = 2000, message = "At most 2,000 km a day") Integer kmPerDay,
			@NotNull(message = "Set the price for extra km")
			@Min(value = 0, message = "Can't be negative") @Max(value = 100, message = "At most ₹100 a km") Integer extraKmPrice,
			@NotNull(message = "Choose a fuel rule") FuelPolicy fuelPolicy,
			@Min(value = 18, message = "At least 18") @Max(value = 30, message = "At most 30") Integer minAge,
			@NotNull(message = "Choose the town for pickup") Long placeId,
			@NotBlank(message = "Say roughly where (e.g. Near MG Road metro)")
			@Size(max = 120, message = "Keep it under 120 characters") String area,
			@Size(max = 300, message = "Keep it under 300 characters") String pickupAddress,
			@Size(max = 1000, message = "Keep it under 1,000 characters") String description,
			@Size(max = 500, message = "Keep it under 500 characters") String rules,
			Boolean instant) {
	}

	public record CarSummary(UUID vehicleId, String name, String color, int seats) {
	}

	/** One search result; {@code priceForPeriod} when dates were given. */
	public record ListingCard(UUID id, CarSummary car, String photoUrl, String placeName, String area, Double distanceKm,
			boolean selfDrive, boolean withDriver, int dayPrice, Integer hourPrice, int deposit, int kmPerDay, boolean verified,
			boolean instant, MemberCard owner, Integer priceForPeriod) {
	}

	public record Period(Instant from, Instant to) {
	}

	public record ListingDetail(UUID id, CarSummary car, List<String> photoUrls, long placeId, String placeName, String state,
			String area, double lat, double lng, boolean selfDrive, boolean withDriver, int dayPrice, Integer hourPrice, int deposit,
			int kmPerDay, int extraKmPrice, FuelPolicy fuelPolicy, int minAge, String description, String rules, boolean instant,
			boolean verified, String status, MemberCard owner, List<Period> unavailable, boolean mine) {
	}

	public record DocumentView(UUID id, DocumentKind kind, Instant uploadedAt) {
	}

	public record PhotoView(UUID id, String url) {
	}

	public record BlockView(UUID id, Instant from, Instant to, String note) {
	}

	/** The owner's own view: everything, including what only they see. */
	public record MyListing(ListingDetail listing, String pickupAddress, String reviewNote, List<PhotoView> photos,
			List<DocumentView> documents, List<BlockView> blocks, long openRequests) {
	}

	public record BlockRequest(@NotNull(message = "From when?") Instant from, @NotNull(message = "Until when?") Instant to,
			@Size(max = 100, message = "Keep the note under 100 characters") String note) {
	}

	/** {@code hours}: the length charged; {@code hourly}: priced by the hour. */
	public record Quote(boolean available, String reason, int rent, int deposit, int kmIncluded, int extraKmPrice, long hours,
			boolean hourly) {
	}

	public record RentRequest(@NotNull(message = "Choose a car") UUID listingId, @NotNull(message = "Self-drive or with driver?") Mode mode,
			@NotNull(message = "From when?") Instant from, @NotNull(message = "Until when?") Instant to,
			@Size(max = 300, message = "Keep your message under 300 characters") String message) {
	}

	public record DisputeRequest(@NotBlank(message = "Tell us what's wrong")
			@Size(max = 500, message = "Keep it under 500 characters") String note) {
	}

	public record PayRequest(Boolean useCredits) {
	}

	public record CheckView(String stage, String byRole, int odometer, Fuel fuel, String notes, List<String> photoUrls,
			Instant at) {
	}

	/** {@code viewer}: RENTER, OWNER or STAFF. Contact details only once the owner has accepted. */
	public record RentalView(UUID id, String viewer, ListingCard listing, Mode mode, Instant from, Instant to, int rent,
			int deposit, int kmIncluded, int extraKmPrice, boolean payOnline, Instant payBy, String status, MemberCard renter,
			MemberCard owner, String otherPhone, String pickupAddress, String message, List<CheckView> checks, Integer kmDriven,
			int extraKmCharge, int extraCharges, String extraNote, String disputeNote, String resolution, Instant createdAt,
			Instant decidedAt, Instant pickedUpAt, Instant returnedAt, Instant respondBy, Instant completedAt,
			Instant cancelledAt, String cancelledBy) {
	}

	public record MyRentals(List<RentalView> renting, List<RentalView> lending) {
	}

	// ----- Staff -----

	public record AdminListingRow(ListingDetail listing, String pickupAddress, String reviewNote, List<DocumentView> documents,
			Instant updatedAt) {
	}

	public record ReviewRequest(@Size(max = 300, message = "Keep the note under 300 characters") String note) {
	}

	public record ResolveRequest(
			@NotNull(message = "How much does the renter pay from the deposit?")
			@Min(value = 0, message = "Can't be negative") Integer extraCharges,
			@NotBlank(message = "Explain the decision") @Size(max = 300, message = "Keep it under 300 characters") String resolution) {
	}
}
