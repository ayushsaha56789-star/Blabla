package com.saathchalo.features;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every feature switch in SaathChalo. New features add their switch here; the database row is created at start-up. */
public final class FeatureCatalog {

	public static final String MAINTENANCE = "maintenance_mode";

	public static final String SIGNUPS = "signups";

	private static final List<FeatureDefinition> ALL = List.of(
			// key, parent, area, label, description, phase, built, critical, global, defaultOn
			def(MAINTENANCE, null, "core", "Maintenance mode",
					"Closes the member website with a \"back soon\" page. Staff can still use this console.", 2, true, true, true, false),
			def(SIGNUPS, null, "core", "New sign-ups",
					"Lets new phone numbers create an account. Existing members can always log in.", 2, true, true, true, true),
			def("rides", null, "core", "Carpooling", "Sharing rides between cities. Turning it off hides ride search and offering.",
					1, true, true, false, true),
			def("rides.booking", "rides", "core", "Find and book rides", "Ride search for passengers (booking arrives in Phase 3).",
					1, true, false, false, true),
			def("rides.publish", "rides", "core", "Offer a ride", "Drivers offer seats in their car (the full form arrives in Phase 3).",
					1, true, false, false, true),
			def("rides.ladies_only", "rides", "core", "Ladies-only rides", "Women offer and find rides only with other women.",
					3, false, false, false, false),

			def("chat", null, "trust", "Chat", "Messages between co-travellers.", 4, false, false, false, false),
			def("reviews", null, "trust", "Reviews", "Ratings after completed trips, from both sides.", 4, false, false, false, false),
			def("safety", null, "trust", "Safety tools", "Turns the safety tools below on or off together.", 4, false, false, false, false),
			def("safety.sos", "safety", "trust", "SOS button", "Shares live location with emergency contacts and alerts support.",
					4, false, false, false, false),
			def("safety.trip_share", "safety", "trust", "Share my trip", "A time-limited link family can open to follow a trip.",
					4, false, false, false, false),

			def("payments", null, "payments", "Payments", "Turns paying for rides on or off.", 5, false, true, false, false),
			def("payments.online", "payments", "payments", "Online payments", "UPI and cards through Razorpay.", 5, false, true, false, false),
			def("payments.cash", "payments", "payments", "Pay the driver directly", "Cash or UPI straight to the driver.",
					5, false, false, false, false),
			def("wallet", null, "payments", "SaathChalo credits", "Credits from referrals and refunds, spendable only in the app.",
					5, false, false, false, false),

			def("rentals", null, "rentals", "Car rentals", "Members rent cars from owners.", 6, false, false, false, false),
			def("rentals.self_drive", "rentals", "rentals", "Self-drive rentals", "The renter drives the car.", 6, false, false, false, false),
			def("rentals.with_driver", "rentals", "rentals", "Cars with driver", "The owner or a driver partner drives.",
					6, false, false, false, false),
			def("rentals.instant_book", "rentals", "rentals", "Instant booking", "Renters book without waiting for the owner.",
					6, false, false, false, false),
			def("drivers", null, "rentals", "Driver partners", "Licensed drivers who serve the areas they choose.",
					7, false, false, false, false),
			def("drivers.register", "drivers", "rentals", "Become a driver partner", "Drivers sign up with their licence and areas.",
					7, false, false, false, false),
			def("drivers.hire", "drivers", "rentals", "Hire a driver", "Members book a driver for their own car.", 7, false, false, false, false),

			def("games", null, "games", "Games", "Free chess and ludo for members.", 8, false, false, false, false),
			def("games.chess", "games", "games", "Chess", "Chess against the computer or other members.", 8, false, false, false, false),
			def("games.ludo", "games", "games", "Ludo", "Ludo for 2 to 4 players.", 8, false, false, false, false),
			def("games.vs_computer", "games", "games", "Play against the computer", "Easy, medium or hard computer opponents.",
					8, false, false, false, false),
			def("games.online", "games", "games", "Play online", "With friends by invite, or quick match.", 8, false, false, false, false),
			def("games.ride_lobby", "games", "games", "Play with co-travellers", "A game room for the members of a ride.",
					8, false, false, false, false),
			def("games.leaderboards", "games", "games", "Game leaderboards", "Ratings and rankings from online games.",
					8, false, false, false, false),

			def("rewards", null, "social", "Rewards", "XP, levels, badges, streaks and challenges.", 9, false, false, false, false),
			def("rewards.referrals", "rewards", "social", "Referrals", "Invite friends and both get ride credits.", 9, false, false, false, false),
			def("community", null, "social", "Squads and events", "Groups for colleges and offices, and rides to events.",
					9, false, false, false, false),
			def("community.squads", "community", "social", "Squads", "Private groups verified by email domain.", 9, false, false, false, false),
			def("community.events", "community", "social", "Event rides", "Pages for concerts, fests and matches.", 9, false, false, false, false),
			def("vibe_match", null, "social", "Vibe match", "A match score from music, chattiness and interests.", 9, false, false, false, false),

			def("promotions", null, "promotions", "Promotions", "Members promote their business, always labelled Sponsored.",
					10, false, false, false, false),
			def("promotions.self_serve", "promotions", "promotions", "Create promotions", "Members create and pay for promotions.",
					10, false, false, false, false),
			def("promotions.in_search", "promotions", "promotions", "Promotions in search", "Sponsored posts between search results.",
					10, false, false, false, false),
			def("promotions.in_games", "promotions", "promotions", "Promotions in game lobbies", "Sponsored posts in game lobbies.",
					10, false, false, false, false));

	private static final Map<String, FeatureDefinition> BY_KEY;

	static {
		Map<String, FeatureDefinition> map = new LinkedHashMap<>();
		for (FeatureDefinition d : ALL) {
			if (d.parent() != null && !map.containsKey(d.parent())) {
				throw new IllegalStateException("Parent must be listed before " + d.key());
			}
			map.put(d.key(), d);
		}
		BY_KEY = Map.copyOf(map);
	}

	private FeatureCatalog() {
	}

	public static List<FeatureDefinition> all() {
		return ALL;
	}

	public static Optional<FeatureDefinition> find(String key) {
		return Optional.ofNullable(key == null ? null : BY_KEY.get(key));
	}

	private static FeatureDefinition def(String key, String parent, String area, String label, String description,
			int phase, boolean built, boolean critical, boolean global, boolean defaultOn) {
		return new FeatureDefinition(key, parent, area, label, description, phase, built, critical, global, defaultOn);
	}
}
