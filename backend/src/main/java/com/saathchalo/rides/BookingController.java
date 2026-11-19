package com.saathchalo.rides;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.rides.RideDtos.BookingView;
import com.saathchalo.rides.RideDtos.Trips;
import com.saathchalo.rides.RideDtos.TripsSummary;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answering and cancelling bookings, and "My rides". These need only the main carpooling switch: members must always be
 * able to manage trips they already have, even if booking or offering is paused.
 */
@RestController
@RequestMapping("/api")
@RequiresFeature("rides")
public class BookingController {

	private final BookingService bookings;

	private final TripService trips;

	public BookingController(BookingService bookings, TripService trips) {
		this.bookings = bookings;
		this.trips = trips;
	}

	@PostMapping("/bookings/{id}/accept")
	public BookingView accept(@PathVariable UUID id) {
		return bookings.accept(id, CurrentUser.require());
	}

	@PostMapping("/bookings/{id}/decline")
	public BookingView decline(@PathVariable UUID id) {
		return bookings.decline(id, CurrentUser.require());
	}

	@PostMapping("/bookings/{id}/cancel")
	public BookingView cancel(@PathVariable UUID id) {
		return bookings.cancel(id, CurrentUser.require());
	}

	/** {@code when}: "upcoming" (default) or "past". */
	@GetMapping("/trips")
	public Trips trips(@RequestParam(defaultValue = "upcoming") String when) {
		return trips.trips(CurrentUser.require(), "past".equalsIgnoreCase(when));
	}

	@GetMapping("/trips/summary")
	public TripsSummary summary() {
		return trips.summary(CurrentUser.require());
	}
}
