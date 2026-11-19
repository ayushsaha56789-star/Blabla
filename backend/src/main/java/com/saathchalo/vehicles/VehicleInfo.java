package com.saathchalo.vehicles;

import java.util.UUID;

/** A car as other parts of SaathChalo see it. The registration number is only available through {@link VehicleService#plate}. */
public record VehicleInfo(UUID id, UUID ownerId, String make, String model, String color, int seats, String plateHint,
		boolean active) {

	/** "Maruti Suzuki Swift". */
	public String name() {
		return make + " " + model;
	}
}
