package com.saathchalo.rides;

import com.saathchalo.rides.internal.Booking;
import com.saathchalo.rides.internal.BookingRepository;
import com.saathchalo.rides.internal.Ride;
import com.saathchalo.rides.internal.RideRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What happens to a member's rides when they delete their account. */
@Service
public class LeavingMembers {

	/** Trips other people are counting on: rides with confirmed passengers, and seats the member has confirmed. */
	public record Commitments(int ridesWithPassengers, int confirmedSeats) {

		public boolean any() {
			return ridesWithPassengers > 0 || confirmedSeats > 0;
		}
	}

	private final RideRepository rides;

	private final BookingRepository bookings;

	private final RideService rideService;

	private final Clock clock;

	public LeavingMembers(RideRepository rides, BookingRepository bookings, RideService rideService, Clock clock) {
		this.rides = rides;
		this.bookings = bookings;
		this.rideService = rideService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Commitments commitments(UUID memberId) {
		Instant now = clock.instant();
		List<UUID> driving = upcomingRides(memberId, now).stream().map(Ride::getId).toList();
		int ridesWithPassengers = driving.isEmpty() ? 0
				: (int) bookings.findByRideIdInAndStatusIn(driving, List.of(BookingStatus.CONFIRMED)).stream()
					.map(Booking::getRideId).distinct().count();
		List<Booking> confirmed = bookings.findByPassengerIdOrderByCreatedAtDesc(memberId).stream()
			.filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
			.toList();
		Set<UUID> rideIds = confirmed.stream().map(Booking::getRideId).collect(Collectors.toSet());
		Set<UUID> upcoming = rideIds.isEmpty() ? Set.of()
				: rides.findWithStops(rideIds).stream()
					.filter(r -> r.getStatus() == RideStatus.SCHEDULED && r.arrivalAt().isAfter(now))
					.map(Ride::getId)
					.collect(Collectors.toSet());
		int confirmedSeats = (int) confirmed.stream().filter(b -> upcoming.contains(b.getRideId())).count();
		return new Commitments(ridesWithPassengers, confirmedSeats);
	}

	/**
	 * Cancels the member's upcoming rides that nobody is confirmed on (people waiting for an answer are told the ride
	 * is cancelled), and withdraws their own unanswered seat requests.
	 */
	@Transactional
	public void release(UUID memberId) {
		Instant now = clock.instant();
		for (Ride ride : upcomingRides(memberId, now)) {
			Ride locked = rides.lockById(ride.getId()).orElseThrow();
			boolean anyoneConfirmed = !bookings.findByRideIdInAndStatusIn(List.of(locked.getId()), List.of(BookingStatus.CONFIRMED))
				.isEmpty();
			if (locked.getStatus() == RideStatus.SCHEDULED && !anyoneConfirmed) {
				rideService.cancel(locked, "DRIVER", "The driver has left SaathChalo.");
			}
		}
		bookings.findByPassengerIdOrderByCreatedAtDesc(memberId).stream()
			.filter(b -> b.getStatus() == BookingStatus.REQUESTED)
			.forEach(b -> bookings.lockById(b.getId())
				.filter(fresh -> fresh.getStatus() == BookingStatus.REQUESTED)
				.ifPresent(fresh -> fresh.cancel("PASSENGER", now)));
	}

	/** Rides the member drives that haven't finished yet. */
	private List<Ride> upcomingRides(UUID memberId, Instant now) {
		return rides.findByDriverIdAndStatusAndDepartureAtAfter(memberId, RideStatus.SCHEDULED, now.minus(Duration.ofDays(2)))
			.stream()
			.filter(r -> r.arrivalAt().isAfter(now))
			.toList();
	}
}
