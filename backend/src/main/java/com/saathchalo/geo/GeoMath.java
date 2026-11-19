package com.saathchalo.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * Distance and driving-time estimates. Without a routing engine, road distance is the straight-line distance times a
 * typical Indian detour factor, and time assumes an average highway speed including short breaks. Both are shown to
 * members as "about".
 */
public final class GeoMath {

	public static final double ROAD_FACTOR = 1.25;

	public static final double AVERAGE_KMH = 52;

	/** Extra minutes for picking up or dropping off at each stop along the way. */
	public static final int MINUTES_PER_STOP = 5;

	private static final double EARTH_RADIUS_KM = 6371.0088;

	/** One stretch between two consecutive stops. */
	public record Leg(int km, int minutes) {
	}

	/** Totals for a whole route, plus per-stop distance and time from the start (first stop = 0, 0). */
	public record RouteEstimate(int distanceKm, int durationMin, List<Leg> legs, List<Integer> kmFromStart,
			List<Integer> minutesFromStart) {
	}

	private GeoMath() {
	}

	public static double straightKm(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
	}

	public static double straightKm(Place a, Place b) {
		return straightKm(a.lat(), a.lng(), b.lat(), b.lng());
	}

	public static int roadKm(Place a, Place b) {
		return (int) Math.max(1, Math.round(straightKm(a, b) * ROAD_FACTOR));
	}

	/** Driving minutes for a distance, rounded to 5 minutes. */
	public static int driveMinutes(int km) {
		return (int) Math.max(5, Math.round(km / AVERAGE_KMH * 60 / 5.0) * 5);
	}

	public static RouteEstimate estimate(List<Place> stops) {
		if (stops.size() < 2) {
			throw new IllegalArgumentException("A route needs at least two places");
		}
		List<Leg> legs = new ArrayList<>();
		List<Integer> km = new ArrayList<>(List.of(0));
		List<Integer> minutes = new ArrayList<>(List.of(0));
		int totalKm = 0;
		int totalMin = 0;
		for (int i = 1; i < stops.size(); i++) {
			int legKm = roadKm(stops.get(i - 1), stops.get(i));
			int legMin = driveMinutes(legKm);
			legs.add(new Leg(legKm, legMin));
			totalKm += legKm;
			totalMin += legMin + (i > 1 ? MINUTES_PER_STOP : 0);
			km.add(totalKm);
			minutes.add(totalMin);
		}
		return new RouteEstimate(totalKm, totalMin, List.copyOf(legs), List.copyOf(km), List.copyOf(minutes));
	}
}
