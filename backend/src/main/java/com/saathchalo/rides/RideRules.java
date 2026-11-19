package com.saathchalo.rides;

import com.saathchalo.config.AppProperties;
import com.saathchalo.geo.GeoMath.RouteEstimate;
import com.saathchalo.rides.RideDtos.Quote;
import com.saathchalo.rides.internal.Booking;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Seat counting and pricing. Pure functions, so they are easy to test. */
final class RideRules {

	private RideRules() {
	}

	/**
	 * Free seats on each stretch between consecutive stops. A booking from stop 1 to stop 3 uses a seat on stretches 1
	 * and 2 only, so the same seat can be sold again for stretch 0 or stretch 3.
	 */
	static int[] seatsLeftByLeg(int seatsTotal, int legs, Collection<Booking> liveBookings) {
		int[] left = new int[legs];
		Arrays.fill(left, seatsTotal);
		for (Booking b : liveBookings) {
			for (int leg = b.getFromPosition(); leg < b.getToPosition() && leg < legs; leg++) {
				left[leg] -= b.getSeats();
			}
		}
		for (int i = 0; i < legs; i++) {
			left[i] = Math.max(0, left[i]);
		}
		return left;
	}

	static int seatsLeft(int[] byLeg, int from, int to) {
		int min = Integer.MAX_VALUE;
		for (int leg = from; leg < to; leg++) {
			min = Math.min(min, byLeg[leg]);
		}
		return min == Integer.MAX_VALUE ? 0 : min;
	}

	/**
	 * Price per seat for part of a route: the driver's whole-route price shared by distance, rounded to ₹10 (at least
	 * ₹10, at most the whole-route price). The whole route always costs exactly the driver's price.
	 */
	static int segmentPrice(int fullPrice, List<Integer> kmFromStart, int from, int to) {
		int last = kmFromStart.size() - 1;
		int totalKm = kmFromStart.get(last);
		if ((from == 0 && to == last) || totalKm <= 0) {
			return fullPrice;
		}
		double share = (kmFromStart.get(to) - kmFromStart.get(from)) / (double) totalKm;
		int price = (int) Math.round(fullPrice * share / 10.0) * 10;
		return Math.clamp(price, Math.min(10, fullPrice), fullPrice);
	}

	static List<List<Integer>> priceMatrix(int fullPrice, List<Integer> kmFromStart) {
		int n = kmFromStart.size();
		List<List<Integer>> rows = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			List<Integer> row = new ArrayList<>(n);
			for (int j = 0; j < n; j++) {
				row.add(j > i ? segmentPrice(fullPrice, kmFromStart, i, j) : 0);
			}
			rows.add(row);
		}
		return rows;
	}

	static Quote quote(RouteEstimate route, AppProperties.Rides config) {
		int km = route.distanceKm();
		int min = config.minPrice();
		int max = Math.max(min, (int) Math.ceil(km * config.maxPricePerKm() / 10.0) * 10);
		int suggested = Math.clamp((long) Math.round(km * config.suggestedPricePerKm() / 10.0) * 10, min, max);
		return new Quote(km, route.durationMin(), suggested, min, max, route.kmFromStart(), route.minutesFromStart());
	}
}
