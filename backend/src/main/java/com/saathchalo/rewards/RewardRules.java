package com.saathchalo.rewards;

import com.saathchalo.rewards.RewardDtos.Badge;
import com.saathchalo.rides.RideLookup.SharedTrip;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * How rewards are worked out from completed trips. Nothing is stored: the same trips always give the same XP, level,
 * badges, streak and CO₂, so there is nothing to game or lose.
 */
final class RewardRules {

	static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

	/** An average Indian car's tailpipe CO₂ per km: each shared passenger-km is a car-km not driven. */
	static final double CO2_KG_PER_KM = 0.12;

	static final int XP_TRIP = 50;

	static final int XP_DRIVER_BONUS = 20;

	static final int XP_PER_PERSON = 5;

	static final int XP_REFERRAL = 100;

	static final int XP_CHALLENGE = 100;

	/** The monthly challenge: this many shared rides in a calendar month. */
	static final int CHALLENGE_TRIPS = 3;

	static final int[] LEVELS = { 0, 100, 250, 500, 1000, 2000, 3500, 5500, 8000, 12000 };

	static final String[] LEVEL_NAMES = { "New rider", "Co-pilot", "Road buddy", "Route regular", "Highway hero", "Trailblazer",
			"Road legend", "Long hauler", "Saath superstar", "Chalo champion" };

	private RewardRules() {
	}

	record Stats(int trips, int driven, int km, int passengerKm, int people, int rupees, double co2Kg, int streak, int bestStreak,
			int monthTrips, int challengesDone, int longestKm, boolean night, boolean early) {
	}

	static Stats stats(List<SharedTrip> trips, ZonedDateTime now) {
		int km = 0;
		int passengerKm = 0;
		int rupees = 0;
		int driven = 0;
		int longest = 0;
		boolean night = false;
		boolean early = false;
		Set<UUID> people = new HashSet<>();
		Set<LocalDate> weeks = new TreeSet<>();
		Map<YearMonth, Integer> perMonth = new HashMap<>();
		for (SharedTrip t : trips) {
			ZonedDateTime at = t.departureAt().atZone(INDIA);
			km += t.km();
			passengerKm += t.passengerKm();
			rupees += t.rupees();
			driven += t.asDriver() ? 1 : 0;
			longest = Math.max(longest, t.km());
			int hour = at.getHour();
			night |= hour >= 22 || hour < 5;
			early |= hour >= 5 && hour < 7;
			people.addAll(t.mates());
			weeks.add(at.toLocalDate().with(DayOfWeek.MONDAY));
			perMonth.merge(YearMonth.from(at), 1, Integer::sum);
		}
		int challenges = (int) perMonth.values().stream().filter(n -> n >= CHALLENGE_TRIPS).count();
		int month = perMonth.getOrDefault(YearMonth.from(now), 0);
		return new Stats(trips.size(), driven, km, passengerKm, people.size(), rupees, Math.round(passengerKm * CO2_KG_PER_KM * 10) / 10.0,
				currentStreak(weeks, now.toLocalDate()), bestStreak(weeks), month, challenges, longest, night, early);
	}

	/** Weeks in a row with a shared ride, up to this week (or last week, if this week has none yet). */
	static int currentStreak(Set<LocalDate> weeks, LocalDate today) {
		LocalDate week = today.with(DayOfWeek.MONDAY);
		if (!weeks.contains(week)) {
			week = week.minusWeeks(1);
		}
		int n = 0;
		while (weeks.contains(week)) {
			n++;
			week = week.minusWeeks(1);
		}
		return n;
	}

	static int bestStreak(Set<LocalDate> weeks) {
		int best = 0;
		int run = 0;
		LocalDate previous = null;
		for (LocalDate w : new TreeSet<>(weeks)) {
			run = previous != null && previous.plusWeeks(1).equals(w) ? run + 1 : 1;
			best = Math.max(best, run);
			previous = w;
		}
		return best;
	}

	static int xp(Stats s, int referrals) {
		return s.trips() * XP_TRIP + s.driven() * XP_DRIVER_BONUS + s.people() * XP_PER_PERSON + referrals * XP_REFERRAL
				+ s.challengesDone() * XP_CHALLENGE;
	}

	/** 0-based level index for this much XP. */
	static int level(int xp) {
		int level = 0;
		while (level + 1 < LEVELS.length && xp >= LEVELS[level + 1]) {
			level++;
		}
		return level;
	}

	static List<Badge> badges(Stats s, int referrals) {
		return List.of(
				badge("FIRST_RIDE", "First ride", "Share your first ride", s.trips(), 1),
				badge("REGULAR", "Regular", "Share 10 rides", s.trips(), 10),
				badge("CAPTAIN", "Captain", "Drive 5 shared rides", s.driven(), 5),
				badge("ECO_HERO", "Eco hero", "Save 50 kg of CO₂", (int) s.co2Kg(), 50),
				badge("ROAD_TRIPPER", "Road tripper", "Share a ride of 300 km or more", s.longestKm(), 300),
				badge("NIGHT_OWL", "Night owl", "Share a ride leaving between 10 pm and 5 am", s.night() ? 1 : 0, 1),
				badge("EARLY_BIRD", "Early bird", "Share a ride leaving between 5 and 7 am", s.early() ? 1 : 0, 1),
				badge("SOCIAL", "Social butterfly", "Travel with 10 different people", s.people(), 10),
				badge("ON_A_ROLL", "On a roll", "Share rides 4 weeks in a row", s.bestStreak(), 4),
				badge("AMBASSADOR", "Ambassador", "Invite a friend who takes their first ride", referrals, 1));
	}

	private static Badge badge(String key, String name, String how, int progress, int goal) {
		return new Badge(key, name, how, progress >= goal, Math.min(progress, goal), goal);
	}
}
