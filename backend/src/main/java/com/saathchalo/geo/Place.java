package com.saathchalo.geo;

/** An Indian city or town (GeoNames id, display name, state, position, population). */
public record Place(long id, String name, String state, double lat, double lng, int population) {

	/** "Gurugram, Haryana". */
	public String label() {
		return state.isEmpty() ? name : name + ", " + state;
	}
}
