package com.saathchalo.rides;

import java.util.EnumSet;
import java.util.Set;

public enum BookingStatus {
	/** Waiting for the driver to accept (rides without instant booking). Holds the seats meanwhile. */
	REQUESTED,
	CONFIRMED,
	DECLINED,
	CANCELLED,
	/** The driver didn't answer in time, or the ride left first. */
	EXPIRED,
	COMPLETED;

	/** Bookings that hold seats. */
	public static final Set<BookingStatus> LIVE = EnumSet.of(REQUESTED, CONFIRMED);

	public boolean isLive() {
		return LIVE.contains(this);
	}
}
