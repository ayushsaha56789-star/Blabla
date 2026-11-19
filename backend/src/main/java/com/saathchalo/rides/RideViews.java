package com.saathchalo.rides;

import com.saathchalo.config.AppProperties;
import com.saathchalo.rides.RideDtos.BookingView;
import com.saathchalo.rides.RideDtos.RideSummary;
import com.saathchalo.rides.RideDtos.StopView;
import com.saathchalo.rides.internal.Booking;
import com.saathchalo.rides.internal.Ride;
import com.saathchalo.rides.internal.RideStop;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/** Turns rides and bookings into the shapes the API returns. */
@Component
class RideViews {

	private final AppProperties.Rides config;

	RideViews(AppProperties props) {
		this.config = props.rides();
	}

	StopView stop(Ride ride, int position) {
		RideStop s = ride.getStops().get(position);
		return new StopView(position, s.getPlaceId(), s.getName(), s.getState(), s.getMeetingPoint(), s.getLat(),
				s.getLng(), s.getKmFromStart(), ride.timeAt(position));
	}

	List<StopView> stops(Ride ride) {
		return IntStream.rangeClosed(0, ride.lastPosition()).mapToObj(i -> stop(ride, i)).toList();
	}

	List<Integer> kmFromStart(Ride ride) {
		return ride.getStops().stream().map(RideStop::getKmFromStart).toList();
	}

	RideSummary summary(Ride ride) {
		List<RideStop> s = ride.getStops();
		List<String> via = s.subList(1, s.size() - 1).stream().map(RideStop::getName).toList();
		return new RideSummary(ride.getId(), ride.getStatus(), ride.getDepartureAt(), ride.arrivalAt(), s.getFirst().getName(),
				s.getLast().getName(), via, ride.getPricePerSeat(), ride.getSeatsTotal(), ride.isLadiesOnly(),
				ride.isInstantBooking());
	}

	BookingView booking(Booking b, Ride ride) {
		Instant pickupAt = ride.timeAt(b.getFromPosition());
		Instant expiresAt = null;
		if (b.getStatus() == BookingStatus.REQUESTED) {
			Instant lapse = b.getCreatedAt().plus(config.requestTtl());
			expiresAt = lapse.isBefore(pickupAt) ? lapse : pickupAt;
		}
		return new BookingView(b.getId(), ride.getId(), b.getStatus(), b.getFromPosition(), b.getToPosition(),
				ride.getStops().get(b.getFromPosition()).getName(), ride.getStops().get(b.getToPosition()).getName(),
				pickupAt, ride.timeAt(b.getToPosition()), b.getSeats(), b.getPricePerSeat(), b.getSeats() * b.getPricePerSeat(),
				b.getMessage(), b.getCreatedAt(), b.getDecidedAt(), expiresAt, b.getCancelledBy());
	}

	/** "Delhi → Jaipur". */
	static String route(Ride ride) {
		return ride.getStops().getFirst().getName() + " → " + ride.getStops().getLast().getName();
	}
}
