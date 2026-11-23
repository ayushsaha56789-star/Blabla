package com.saathchalo.rides;

import com.saathchalo.config.AppConfig;
import com.saathchalo.notifications.Notice;
import com.saathchalo.notifications.Notifier;
import com.saathchalo.rides.internal.Booking;
import com.saathchalo.rides.internal.Ride;
import com.saathchalo.user.MemberProfiles;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The notices drivers and passengers get about bookings: new requests, answers, cancellations and "rate your trip". */
@Component
class RideNotices {

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH);

	private final Notifier notifier;

	private final MemberProfiles members;

	RideNotices(Notifier notifier, MemberProfiles members) {
		this.notifier = notifier;
		this.members = members;
	}

	/** To the driver: a seat was booked straight away, or a request waits for an answer. */
	void newBooking(Ride ride, Booking b) {
		String who = name(b.getPassengerId());
		if (b.getStatus() == BookingStatus.CONFIRMED) {
			notifier.send(ride.getDriverId(), Notice.trips("checkCircle", who + " booked " + seats(b.getSeats()), stretch(ride, b),
					link(ride)));
		}
		else if (b.getStatus() == BookingStatus.REQUESTED) {
			notifier.send(ride.getDriverId(), Notice.trips("user", who + " asked for " + seats(b.getSeats()),
					stretch(ride, b) + ". Please accept or decline soon.", link(ride)));
		}
	}

	/** To the passenger: the driver's answer. */
	void answered(Ride ride, Booking b, boolean accepted) {
		String driver = name(ride.getDriverId());
		notifier.send(b.getPassengerId(), accepted
				? Notice.trips("checkCircle", driver + " accepted your request", stretch(ride, b) + ". See you there!", link(ride))
				: Notice.trips("close", driver + " declined your request", stretch(ride, b) + ". Try another ride on this route.",
						"/search"));
	}

	/** To the driver: a passenger cancelled. */
	void passengerCancelled(Ride ride, Booking b) {
		notifier.send(ride.getDriverId(), Notice.trips("close", name(b.getPassengerId()) + " cancelled their booking",
				seats(b.getSeats()) + " free again on " + stretch(ride, b), link(ride)));
	}

	/** To every passenger with a live booking: the ride was cancelled by the driver or by our team. */
	void rideCancelled(Ride ride, List<Booking> cancelled, String reason, boolean byStaff) {
		String who = byStaff ? "Our team" : name(ride.getDriverId());
		for (Booking b : cancelled) {
			notifier.send(b.getPassengerId(), Notice.trips("close", who + " cancelled your ride " + RideViews.route(ride),
					when(ride, b) + (reason == null || reason.isBlank() ? "" : ". Reason: " + reason.strip())
							+ ". Anything you paid online is refunded.",
					link(ride)));
		}
	}

	/** After a completed trip: the passenger rates the driver. */
	void rateDriver(UUID passengerId, UUID driverId, UUID rideId) {
		notifier.send(passengerId, Notice.reminder("star", "How was your trip with " + name(driverId) + "?",
				"A quick rating helps everyone travel with people they can trust.", "/rides/" + rideId));
	}

	/** After a completed trip: the driver rates the passengers (one notice per ride). */
	void ratePassengers(UUID driverId, List<UUID> passengerIds, UUID rideId) {
		String first = name(passengerIds.getFirst());
		String who = passengerIds.size() == 1 ? first
				: first + " and " + (passengerIds.size() - 1) + (passengerIds.size() == 2 ? " other" : " others");
		notifier.send(driverId, Notice.reminder("star", "How was your trip with " + who + "?",
				"A quick rating helps everyone travel with people they can trust.", "/rides/" + rideId));
	}

	/** To the passenger: the driver didn't answer in time. */
	void requestExpired(UUID passengerId) {
		notifier.send(passengerId, Notice.trips("clock", "Your booking request expired",
				"The driver didn't answer in time. Find another ride going your way.", "/search"));
	}

	private String name(UUID id) {
		return members.card(id).displayName();
	}

	private static String stretch(Ride ride, Booking b) {
		return ride.getStops().get(b.getFromPosition()).getName() + " → " + ride.getStops().get(b.getToPosition()).getName() + ", "
				+ when(ride, b);
	}

	private static String when(Ride ride, Booking b) {
		return WHEN.format(ride.timeAt(b.getFromPosition()).atZone(AppConfig.ZONE));
	}

	private static String seats(int n) {
		return n == 1 ? "1 seat" : n + " seats";
	}

	private static String link(Ride ride) {
		return "/rides/" + ride.getId();
	}
}
