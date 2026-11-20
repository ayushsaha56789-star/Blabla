package com.saathchalo.drivers;

import com.saathchalo.user.MemberProfiles.MemberCard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Driver partners and the trips members hire them for. Money in rupees. */
public final class DriverDtos {

	private DriverDtos() {
	}

	/** LMV: private cars. TRANSPORT: commercial vehicles too. */
	public enum LicenceClass {
		LMV, TRANSPORT
	}

	/** LOCAL: by the hour, in and around the town. OUTSTATION: by the day, to another town and back. */
	public enum TripKind {
		LOCAL, OUTSTATION
	}

	public enum Sort {
		BEST, PRICE, NEAREST
	}

	// ----- Drivers -----

	/** {@code licenceNumber} may be left empty when editing, to keep the one already saved. */
	public record DriverRequest(
			@Size(max = 24, message = "That licence number is too long") String licenceNumber,
			@NotNull(message = "Choose your licence type") LicenceClass licenceClass,
			@NotNull(message = "Until when is your licence valid?") LocalDate licenceValidUntil,
			@NotNull(message = "How many years have you been driving?")
			@Min(value = 0, message = "Can't be negative") @Max(value = 60, message = "At most 60 years") Integer experienceYears,
			@Size(max = 6, message = "Up to 6 languages") List<String> languages,
			Boolean manual,
			Boolean automatic,
			Boolean outstation,
			@NotNull(message = "Set your price per hour")
			@Min(value = 50, message = "At least ₹50 an hour") @Max(value = 2000, message = "At most ₹2,000 an hour") Integer hourPrice,
			@NotNull(message = "Set your price for a full day")
			@Min(value = 300, message = "At least ₹300 a day") @Max(value = 10000, message = "At most ₹10,000 a day") Integer dayPrice,
			@NotNull(message = "Set your price per day for outstation trips")
			@Min(value = 300, message = "At least ₹300 a day") @Max(value = 15000, message = "At most ₹15,000 a day") Integer outstationPrice,
			@Min(value = 0, message = "Can't be negative") @Max(value = 3000, message = "At most ₹3,000 a day") Integer allowance,
			@Min(value = 0, message = "Can't be negative") @Max(value = 2000, message = "At most ₹2,000") Integer nightCharge,
			@Size(max = 500, message = "Keep it under 500 characters") String about) {
	}

	public record AreaRequest(
			@NotNull(message = "Choose a town") Long placeId,
			@NotNull(message = "How far around it?")
			@Min(value = 2, message = "At least 2 km") @Max(value = 60, message = "At most 60 km") Integer radiusKm) {
	}

	public record TakingRequest(@NotNull(message = "On or off?") Boolean on) {
	}

	public record BlockRequest(@NotNull(message = "From when?") Instant from, @NotNull(message = "Until when?") Instant to,
			@Size(max = 100, message = "Keep the note under 100 characters") String note) {
	}

	public record AreaView(UUID id, long placeId, String placeName, String state, int radiusKm) {
	}

	public record BlockView(UUID id, Instant from, Instant to, String note) {
	}

	public record Rates(int hourPrice, int dayPrice, int outstationPrice, int allowance, int nightCharge) {
	}

	/**
	 * A driver as members see them. {@code rating}: from members they drove for (null until reviewed).
	 * {@code distanceKm} and {@code fare}: for the place and trip searched for (null otherwise).
	 */
	public record DriverCard(UUID id, MemberCard member, Double rating, int reviewCount, int tripsDone, int experienceYears,
			List<String> languages, boolean manual, boolean automatic, boolean outstation, boolean policeChecked,
			List<String> areas, Rates rates, Double distanceKm, Integer fare) {
	}

	public record ReviewView(String authorName, int stars, String comment, Instant at) {
	}

	public record DriverDetail(DriverCard driver, String about, LicenceClass licenceClass, List<AreaView> areas,
			List<ReviewView> reviews, boolean mine) {
	}

	/**
	 * The price of one trip. LOCAL: {@code units} hours at the hourly price, never more than the day price, plus the
	 * night charge when it runs between 10 pm and 6 am. OUTSTATION: {@code units} days at the outstation price plus
	 * the daily allowance. {@code extraPrice}: per extra hour (local) or day (outstation), paid to the driver directly.
	 */
	public record Quote(boolean available, String problem, TripKind kind, Instant from, Instant to, int units, int base,
			boolean dayPriceApplied, int night, int allowance, int fare, int extraPrice) {
	}

	/** The driver's own view of their profile. {@code licenceStatus}: APPROVED, PENDING, REJECTED or NONE. */
	public record MyDriverProfile(String status, String reviewNote, String licenceStatus, String licenceNumberMasked,
			LicenceClass licenceClass, LocalDate licenceValidUntil, int experienceYears, List<String> languages, boolean manual,
			boolean automatic, boolean outstation, Rates rates, String about, boolean policeCertificate, boolean policeChecked,
			boolean takingBookings, List<AreaView> areas, List<BlockView> blocks, Double rating, int reviewCount, int tripsDone,
			long requestsWaiting, boolean hasPhoto) {
	}

	// ----- Trips -----

	public record TripRequest(
			@NotNull(message = "Choose a driver") UUID driverId,
			@NotNull(message = "Local or outstation?") TripKind kind,
			@NotNull(message = "When should the driver come?") Instant from,
			@Min(value = 2, message = "At least 2 hours") @Max(value = 12, message = "At most 12 hours") Integer hours,
			@Min(value = 1, message = "At least 1 day") @Max(value = 15, message = "At most 15 days") Integer days,
			@NotNull(message = "Choose the town for pickup") Long placeId,
			@NotBlank(message = "Where should the driver come? Add the address")
			@Size(max = 300, message = "Keep the address under 300 characters") String pickupAddress,
			Long destinationId,
			@NotBlank(message = "Which car? (e.g. White Maruti Swift)")
			@Size(max = 100, message = "Keep it under 100 characters") String car,
			@NotNull(message = "Is the car automatic?") Boolean automatic,
			@Size(max = 300, message = "Keep the message under 300 characters") String message) {
	}

	/**
	 * A trip, from the side of {@code viewer} (CUSTOMER, DRIVER or STAFF). {@code startCode} only for the member who
	 * hired, until the trip starts; {@code pickupAddress} for the driver once they accept; {@code otherPhone} once
	 * accepted, until a day after the trip.
	 */
	public record TripView(UUID id, String viewer, MemberCard driver, Double driverRating, int driverReviews, MemberCard customer,
			Double customerRating, TripKind kind, Instant from, Instant to, int units, String placeName, String pickupAddress,
			String destinationName, String car, boolean automatic, int fare, int extraPrice, int extraUnits, boolean payOnline,
			Instant payBy, String status, String startCode, int wrongCodes, String otherPhone, String message, Instant answerBy,
			Instant createdAt, Instant decidedAt, Instant onTheWayAt, Instant startedAt, Instant endedAt, Instant cancelledAt,
			String cancelledBy, String cancelReason, Integer myStars, boolean canReview) {
	}

	public record MyTrips(List<TripView> hired, List<TripView> driving) {
	}

	/** The driver types the code the member tells them; the member themselves start without one. */
	public record StartRequest(@Size(max = 8, message = "The code has 4 digits") String code) {
	}

	public record CancelRequest(@Size(max = 300, message = "Keep the reason under 300 characters") String reason) {
	}

	public record PayRequest(Boolean useCredits) {
	}

	public record ReviewRequest(
			@NotNull(message = "Choose 1 to 5 stars") @Min(value = 1, message = "At least 1 star") @Max(value = 5, message = "At most 5 stars") Integer stars,
			@Size(max = 500, message = "Keep it under 500 characters") String comment) {
	}

	// ----- Staff -----

	public record AdminDriverRow(UUID id, MemberCard member, String status, String licenceStatus, String licenceNumberMasked,
			LicenceClass licenceClass, LocalDate licenceValidUntil, int experienceYears, List<String> languages, boolean manual,
			boolean automatic, boolean outstation, Rates rates, String about, List<AreaView> areas, boolean policeCertificate,
			boolean policeChecked, boolean takingBookings, String reviewNote, Instant updatedAt, int tripsDone, Double rating) {
	}

	public record ApproveRequest(Boolean policeChecked) {
	}

	public record StaffNote(@NotBlank(message = "Say what the driver should fix")
			@Size(min = 3, max = 300, message = "Between 3 and 300 characters") String note) {
	}

	public record LicenceNumber(String number) {
	}
}
