package com.saathchalo.rides;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.RateLimiter;
import com.saathchalo.rides.RideDtos.BookRequest;
import com.saathchalo.rides.RideDtos.BookingView;
import com.saathchalo.rides.internal.Booking;
import com.saathchalo.rides.internal.BookingRepository;
import com.saathchalo.rides.internal.Ride;
import com.saathchalo.rides.internal.RideRepository;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.Traveller;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seats on rides. Every change locks the ride row first, so two passengers can never get the last seat, and a driver's
 * answer can't cross with a passenger's cancellation.
 */
@Service
public class BookingService {

	private final RideRepository rides;

	private final BookingRepository bookings;

	private final MemberProfiles members;

	private final RateLimiter limiter;

	private final RideViews views;

	private final Clock clock;

	public BookingService(RideRepository rides, BookingRepository bookings, MemberProfiles members, RateLimiter limiter,
			RideViews views, Clock clock) {
		this.rides = rides;
		this.bookings = bookings;
		this.members = members;
		this.limiter = limiter;
		this.views = views;
		this.clock = clock;
	}

	@Transactional
	public BookingView book(UUID rideId, UUID passengerId, BookRequest req) {
		Traveller me = members.traveller(passengerId);
		if (!me.profileComplete()) {
			throw ApiException.forbidden("profile_incomplete", "Add your first name and gender to your profile before booking.");
		}
		Ride ride = rides.lockById(rideId).orElseThrow(RideService::notFound);
		if (ride.getDriverId().equals(passengerId)) {
			throw ApiException.badRequest("own_ride", "This is your own ride.");
		}
		if (ride.isLadiesOnly() && !me.isWoman()) {
			throw ApiException.forbidden("ladies_only", "This ride is for women only.");
		}
		if (ride.getStatus() != RideStatus.SCHEDULED) {
			throw ApiException.conflict("ride_not_available", "This ride is no longer available.");
		}
		int from = req.fromPosition();
		int to = req.toPosition();
		if (from < 0 || to > ride.lastPosition() || from >= to) {
			throw ApiException.badRequest("bad_stretch", "Please choose where you get in and where you get out.");
		}
		Instant now = clock.instant();
		if (!ride.timeAt(from).isAfter(now)) {
			throw ApiException.conflict("ride_departed", "This ride has already left " + ride.getStops().get(from).getName() + ".");
		}
		List<Booking> earlier = bookings.findByRideIdOrderByCreatedAtAsc(rideId);
		for (Booking b : earlier) {
			if (!b.getPassengerId().equals(passengerId)) {
				continue;
			}
			if (b.getStatus().isLive()) {
				throw ApiException.conflict("already_booked", "You already have a booking on this ride.");
			}
			if (b.getStatus() == BookingStatus.DECLINED) {
				throw ApiException.conflict("request_declined", "The driver declined your request for this ride.");
			}
		}
		List<Booking> live = earlier.stream().filter(b -> b.getStatus().isLive()).toList();
		int available = RideRules.seatsLeft(RideRules.seatsLeftByLeg(ride.getSeatsTotal(), ride.lastPosition(), live), from, to);
		if (req.seats() > available) {
			String message = available == 0 ? "This ride is full for your stretch."
					: "Only " + available + (available == 1 ? " seat is" : " seats are") + " left for your stretch.";
			throw ApiException.conflict("not_enough_seats", message).with("seatsLeft", available);
		}
		if (!limiter.tryAcquire("booking:" + passengerId, 15, Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("slow_down", "You've made many bookings in the last hour. Please try again later.");
		}
		int price = RideRules.segmentPrice(ride.getPricePerSeat(), views.kmFromStart(ride), from, to);
		String message = req.message() == null || req.message().isBlank() ? null : req.message().strip();
		Booking booking = bookings.save(new Booking(rideId, passengerId, from, to, req.seats(), price,
				ride.isInstantBooking(), message, now));
		return views.booking(booking, ride);
	}

	@Transactional
	public BookingView accept(UUID bookingId, UUID driverId) {
		return decide(bookingId, driverId, true);
	}

	@Transactional
	public BookingView decline(UUID bookingId, UUID driverId) {
		return decide(bookingId, driverId, false);
	}

	@Transactional
	public BookingView cancel(UUID bookingId, UUID passengerId) {
		Ride ride = lockRideOf(bookingId);
		Booking b = bookings.lockById(bookingId)
			.filter(x -> x.getPassengerId().equals(passengerId))
			.orElseThrow(BookingService::notFound);
		if (!b.getStatus().isLive()) {
			throw ApiException.conflict("booking_not_active", "This booking is no longer active.");
		}
		if (!ride.timeAt(b.getFromPosition()).isAfter(clock.instant())) {
			throw ApiException.conflict("trip_started", "Your pickup time has passed, so this booking can't be cancelled now.");
		}
		b.cancel("PASSENGER", clock.instant());
		return views.booking(b, ride);
	}

	private BookingView decide(UUID bookingId, UUID driverId, boolean accept) {
		Ride ride = lockRideOf(bookingId);
		if (!ride.getDriverId().equals(driverId)) {
			throw notFound();
		}
		Booking b = bookings.lockById(bookingId).orElseThrow(BookingService::notFound);
		if (b.getStatus() != BookingStatus.REQUESTED) {
			throw ApiException.conflict("booking_not_pending", "This request was already answered or cancelled.");
		}
		if (accept && !ride.timeAt(b.getFromPosition()).isAfter(clock.instant())) {
			throw ApiException.conflict("request_too_late", "The pickup time for this request has passed.");
		}
		b.decide(accept, clock.instant());
		return views.booking(b, ride);
	}

	private Ride lockRideOf(UUID bookingId) {
		UUID rideId = bookings.findRideIdOf(bookingId).orElseThrow(BookingService::notFound);
		return rides.lockById(rideId).orElseThrow(BookingService::notFound);
	}

	private static ApiException notFound() {
		return ApiException.notFound("booking_not_found", "We couldn't find that booking.");
	}
}
