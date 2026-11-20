package com.saathchalo.rides;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
	/** Paying online: the seats are held for a few minutes until the payment comes through. */
	AWAITING_PAYMENT,
	/** Waiting for the driver to accept (rides without instant booking). Holds the seats meanwhile. */
	REQUESTED,
	CONFIRMED,
	DECLINED,
	CANCELLED,
	/** The driver didn't answer in time, or the ride left first. */
	EXPIRED,
	COMPLETED;

	/** Bookings that hold seats. */
	public static final Set<BookingStatus> LIVE = EnumSet.of(AWAITING_PAYMENT, REQUESTED, CONFIRMED);

	public boolean isLive() {
		return LIVE.contains(this);
	}
}
