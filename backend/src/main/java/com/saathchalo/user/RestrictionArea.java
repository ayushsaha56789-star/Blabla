package com.saathchalo.user;

import com.saathchalo.common.ApiException;
import java.util.Arrays;

/** Parts of SaathChalo a member can be kept out of. {@code ALL} suspends the whole account. */
public enum RestrictionArea {

	ALL("ALL", "Whole account (suspended)"),
	RIDES_PUBLISH("rides.publish", "Offering rides"),
	RIDES_BOOKING("rides.booking", "Booking rides"),
	CHAT("chat", "Chat"),
	REVIEWS("reviews", "Writing reviews"),
	RENTALS("rentals", "Car rentals"),
	DRIVERS("drivers", "Driver partners"),
	GAMES("games", "Games"),
	PROMOTIONS("promotions", "Promotions"),
	COMMUNITY("community", "Squads and events");

	private final String key;

	private final String label;

	RestrictionArea(String key, String label) {
		this.key = key;
		this.label = label;
	}

	public String key() {
		return key;
	}

	public String label() {
		return label;
	}

	public static RestrictionArea ofKey(String key) {
		return Arrays.stream(values())
			.filter(a -> a.key.equals(key))
			.findFirst()
			.orElseThrow(() -> ApiException.badRequest("unknown_area", "Please choose what to restrict."));
	}
}
