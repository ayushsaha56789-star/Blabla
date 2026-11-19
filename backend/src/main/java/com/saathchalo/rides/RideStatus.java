package com.saathchalo.rides;

public enum RideStatus {
	/** Published and not yet finished. */
	SCHEDULED,
	/** Cancelled by the driver or by staff. */
	CANCELLED,
	/** Finished (set automatically a few hours after the planned arrival). */
	COMPLETED
}
