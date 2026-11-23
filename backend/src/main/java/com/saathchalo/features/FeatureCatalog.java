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
			def("rides.booking", "rides", "core", "Find and book rides", "Passengers search rides and book seats.",
					3, true, false, false, true),
			def("rides.publish", "rides", "core", "Offer a ride", "Drivers add their car and offer seats on their trips.",
					3, true, false, false, true),
			def("rides.ladies_only", "rides", "core", "Ladies-only rides",
					"Women drivers can offer rides that only women can see and book.", 3, true, false, false, true),

			def("chat", null, "trust", "Chat", "Drivers and passengers message each other about a ride, without sharing numbers.",
					4, true, false, false, true),
			def("reviews", null, "trust", "Reviews", "Drivers and passengers rate each other after a completed trip.", 4, true, false,
					false, true),
			def("safety", null, "trust", "Safety tools", "Turns the safety tools below on or off together.", 4, true, false, false, true),
			def("safety.sos", "safety", "trust", "SOS button", "Alerts support and texts the member's emergency contacts, with a one-tap call to 112.",
					4, true, false, false, true),
			def("safety.trip_share", "safety", "trust", "Share my trip", "A time-limited link family can open to follow a trip, with live location if the member allows.",
					4, true, false, false, true),
			def("verification", null, "trust", "Verification", "Turns the verification checks below on or off together.", 4, true, false, false,
					true),
			def("verification.email", "verification", "trust", "Email check", "Members confirm their email address with a 6-digit code.",
					4, true, false, false, true),
			def("verification.id", "verification", "trust", "ID check",
					"Members upload a photo of an ID; staff check it and the member gets an 'ID verified' badge.", 4, true, false, false, true),

			def("payments", null, "payments", "Payments", "Turns the payment options below on or off together.", 5, true, true,
					false, true),
			def("payments.online", "payments", "payments", "Online payments",
					"Pay for seats by UPI or card through Razorpay; the money reaches the driver after the trip. Needs the Razorpay keys.",
					5, true, true, false, false),
			def("payments.cash", "payments", "payments", "Pay the driver directly",
					"Cash or UPI straight to the driver on the day. Stays available whenever online payments aren't.", 5, true, false,
					false, true),
			def("wallet", null, "payments", "SaathChalo credits",
					"Credits (from refunds, goodwill and later referrals) that pay for seats online. They can't be withdrawn.",
					5, true, false, false, false),

			def("rentals", null, "rentals", "Car rentals",
					"Members rent cars from owners near them. Check the rent-a-cab rules for your state before switching it on.", 6, true,
					false, false, false),
			def("rentals.self_drive", "rentals", "rentals", "Self-drive rentals",
					"The renter drives. Needs a verified driving licence and the owner's minimum age.", 6, true, false, false, true),
			def("rentals.with_driver", "rentals", "rentals", "Cars with driver", "The owner (or later a driver partner) drives.",
					6, true, false, false, true),
			def("rentals.instant_book", "rentals", "rentals", "Instant booking",
					"Renters book listings marked instant without waiting for the owner.", 6, true, false, false, true),
			def("rentals.unverified", "rentals", "rentals", "Unchecked listings go live",
					"Listings are bookable before staff check their documents (they show as not verified).", 6, true, false, true,
					false),
			def("drivers", null, "rentals", "Driver partners",
					"Licensed drivers, checked by staff, drive members' own cars in the areas they choose.", 7, true, false, false, false),
			def("drivers.register", "drivers", "rentals", "Become a driver partner",
					"Members with a checked driving licence sign up with their areas and rates.", 7, true, false, false, true),
			def("drivers.hire", "drivers", "rentals", "Hire a driver",
					"Members book a driver for their own car: local by the hour or outstation by the day.", 7, true, false, false, true),

			def("games", null, "games", "Games",
					"Free chess and ludo for members. No money, prizes or betting of any kind.", 8, true, false, false, false),
			def("games.chess", "games", "games", "Chess", "Chess against the computer or other members.", 8, true, false, false, true),
			def("games.ludo", "games", "games", "Ludo", "Ludo for 2 to 4 players.", 8, true, false, false, true),
			def("games.vs_computer", "games", "games", "Play against the computer", "Easy, medium or hard computer opponents.",
					8, true, false, false, true),
			def("games.online", "games", "games", "Play online", "With friends by room code, or a quick match.", 8, true, false, false, true),
			def("games.ride_lobby", "games", "games", "Play with co-travellers", "A game room for the people on a ride.",
					8, true, false, false, true),
			def("games.leaderboards", "games", "games", "Game leaderboards", "Ratings and rankings from online games between members.",
					8, true, false, false, true),

			def("rewards", null, "social", "Rewards",
					"XP, levels, badges, streaks, challenges and CO₂ saved, worked out from completed trips.", 9, true, false, false, false),
			def("rewards.referrals", "rewards", "social", "Referrals",
					"Invite friends; both get ride credits after the friend's first completed ride.", 9, true, false, false, true),
			def("community", null, "social", "Squads and events", "Groups for colleges and offices, and rides to events.",
					9, true, false, false, false),
			def("community.squads", "community", "social", "Squads", "Private groups, optionally checked by a college or work email.",
					9, true, false, false, true),
			def("community.events", "community", "social", "Event rides", "Pages for concerts, fests and matches with the rides going there.",
					9, true, false, false, true),
			def("vibe_match", null, "social", "Vibe match", "A match score from chat style, music, smoking, pets and interests.",
					9, true, false, false, false),

			def("promotions", null, "promotions", "Promotions",
					"Members promote their business, always labelled Sponsored and checked by a moderator first.", 10, true, false, false, false),
			def("promotions.self_serve", "promotions", "promotions", "Create promotions",
					"Members with a confirmed email or ID create promotions (free during the launch offer).", 10, true, false, false, true),
			def("promotions.in_search", "promotions", "promotions", "Promotions in search", "One sponsored card between ride search results.",
					10, true, false, false, true),
			def("promotions.in_games", "promotions", "promotions", "Promotions in game lobbies", "One sponsored card on the games page.",
					10, true, false, false, true));

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
