package com.saathchalo.rides;

import com.saathchalo.geo.Place;
import com.saathchalo.user.MemberProfiles.MemberCard;
import com.saathchalo.user.UserDtos.PublicProfileResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class RideDtos {

	private RideDtos() {
	}

	private static final String STOPS_MESSAGE = "A ride has a start, up to 6 stops on the way, and an end";

	// ----- Requests -------------------------------------------------------------------------------------------------

	public record StopRequest(@NotNull(message = "Please choose a place") Long placeId,
			@Size(max = 160, message = "Keep the meeting point under 160 characters") String meetingPoint) {
	}

	public record QuoteRequest(@NotNull @Size(min = 2, max = 8, message = STOPS_MESSAGE) List<@NotNull Long> placeIds) {
	}

	public record PublishRequest(
			@NotNull(message = "Please choose your car") UUID vehicleId,
			@NotNull @Size(min = 2, max = 8, message = STOPS_MESSAGE) List<@Valid @NotNull StopRequest> stops,
			@NotNull(message = "Please choose a date") LocalDate date,
			@NotNull(message = "Please choose a time") LocalTime time,
			@NotNull(message = "How many seats are you offering?")
			@Min(value = 1, message = "Offer at least 1 seat") @Max(value = 8, message = "At most 8 seats") Integer seats,
			@NotNull(message = "Please set a price")
			@Min(value = 1, message = "Please set a price") @Max(value = 100_000, message = "That price is too high") Integer pricePerSeat,
			Boolean ladiesOnly,
			Boolean instantBooking,
			@Size(max = 500, message = "Keep your note under 500 characters") String notes) {
	}

	public record CancelRideRequest(@Size(max = 300, message = "Keep it under 300 characters") String reason) {
	}

	public record BookRequest(
			@NotNull(message = "Please choose where you get in") @Min(0) Integer fromPosition,
			@NotNull(message = "Please choose where you get out") @Min(1) Integer toPosition,
			@NotNull(message = "How many seats?")
			@Min(value = 1, message = "Book at least 1 seat") @Max(value = 8, message = "At most 8 seats") Integer seats,
			@Size(max = 300, message = "Keep your message under 300 characters") String message) {
	}

	// ----- Responses ------------------------------------------------------------------------------------------------

	/** Route estimate and price guidance while offering a ride. Prices are rupees per seat for the whole route. */
	public record Quote(int distanceKm, int durationMin, int suggestedPrice, int minPrice, int maxPrice,
			List<Integer> kmFromStart, List<Integer> minutesFromStart) {
	}

	/** {@code at}: when the car is expected there. */
	public record StopView(int position, long placeId, String name, String state, String meetingPoint, double lat,
			double lng, int kmFromStart, Instant at) {
	}

	/** {@code plate} is only filled in for the driver and passengers with a confirmed seat. */
	public record CarView(String make, String model, String color, String plate) {
	}

	/** {@code phone} is only filled in for passengers with a confirmed seat. */
	public record DriverView(PublicProfileResponse profile, String phone, long ridesCompleted) {
	}

	/** The driver sees requests, messages and confirmed passengers' phone numbers; others see confirmed passengers only. */
	public record PassengerView(UUID bookingId, MemberCard member, BookingStatus status, int fromPosition,
			int toPosition, int seats, int pricePerSeat, String message, String phone, Instant requestedAt,
			String cancelledBy) {
	}

	/** {@code expiresAt}: when an unanswered request lapses. */
	public record BookingView(UUID id, UUID rideId, BookingStatus status, int fromPosition, int toPosition,
			String fromName, String toName, Instant pickupAt, Instant dropAt, int seats, int pricePerSeat, int total,
			String message, Instant createdAt, Instant decidedAt, Instant expiresAt, String cancelledBy) {
	}

	public enum Viewer {
		DRIVER, PASSENGER, VISITOR, STAFF
	}

	/**
	 * A ride page. {@code seatsLeftByLeg[i]}: free seats between stop i and stop i+1. {@code prices[i][j]}: price per
	 * seat from stop i to stop j (j > i). {@code bookable}: the viewer can book a seat now.
	 */
	public record RideDetails(UUID id, RideStatus status, Instant departureAt, Instant arrivalAt, int distanceKm,
			int durationMin, int pricePerSeat, int seatsTotal, List<Integer> seatsLeftByLeg, List<List<Integer>> prices,
			boolean ladiesOnly, boolean instantBooking, String notes, Instant cancelledAt, String cancelledBy,
			String cancelReason, List<StopView> stops, DriverView driver, CarView car, List<PassengerView> passengers,
			Viewer viewer, BookingView myBooking, boolean bookable) {
	}

	/** One ride in search results, for the stretch the passenger asked for. Distances: from the searched places. */
	public record SearchResult(UUID rideId, int fromPosition, int toPosition, StopView pickup, StopView drop,
			int pickupDistanceKm, int dropDistanceKm, int price, int seatsLeft, boolean instantBooking,
			boolean ladiesOnly, MemberCard driver, String car, String rideFrom, String rideTo, int stopCount,
			boolean yours, BookingStatus yourBooking) {
	}

	public record DateCount(LocalDate date, int rides) {
	}

	/** {@code otherDates}: nearby days that have rides, for when the chosen day has few or none. */
	public record SearchResponse(Place from, Place to, LocalDate date, int seats, List<SearchResult> rides,
			List<DateCount> otherDates) {
	}

	public record RideSummary(UUID id, RideStatus status, Instant departureAt, Instant arrivalAt, String fromName,
			String toName, List<String> via, int pricePerSeat, int seatsTotal, boolean ladiesOnly,
			boolean instantBooking) {
	}

	public record DrivingTrip(RideSummary ride, int seatsBooked, int pendingRequests) {
	}

	public record RidingTrip(BookingView booking, RideSummary ride, MemberCard driver) {
	}

	public record Trips(List<DrivingTrip> driving, List<RidingTrip> riding) {
	}

	/** For the "My rides" badge: requests waiting for this member's answer, and trips coming up. */
	public record TripsSummary(long pendingRequests, long upcomingTrips) {
	}

	// ----- Staff console --------------------------------------------------------------------------------------------

	public record AdminRideRow(UUID id, RideStatus status, Instant departureAt, String fromName, String toName,
			int stopCount, MemberCard driver, int seatsTotal, int seatsBooked, int pendingRequests, int pricePerSeat,
			boolean ladiesOnly, Instant createdAt) {
	}

	public record RideStats(long upcoming, long publishedLast7Days, long bookingsLast7Days, long seatsBookedUpcoming,
			long cancelledLast7Days) {
	}
}
