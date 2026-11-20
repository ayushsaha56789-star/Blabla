package com.saathchalo.rentals;

import com.saathchalo.rentals.internal.Listing;
import java.time.Duration;
import java.time.Instant;

/** Prices and limits for renting. Money in rupees. */
final class RentalRules {

	/** Hourly rentals last at least this long. */
	static final int MIN_HOURS = 4;

	static final int MAX_DAYS = 30;

	static final int MAX_DAYS_AHEAD = 90;

	/** A request the owner doesn't answer lapses after this long (or at the start time). */
	static final Duration ANSWER_WITHIN = Duration.ofHours(24);

	/** Once accepted, an online booking must be paid within this long (and before the start). */
	static final Duration PAY_WITHIN = Duration.ofHours(24);

	/** After the return, the renter has this long to accept the charges or raise a dispute. */
	static final Duration RESPOND_WITHIN = Duration.ofHours(48);

	private RentalRules() {
	}

	/** Whole hours, rounded up. */
	static long hours(Instant from, Instant to) {
		long minutes = Duration.between(from, to).toMinutes();
		return (minutes + 59) / 60;
	}

	/** Hourly when the listing has an hourly price and it's shorter than a day; otherwise whole days. */
	static boolean hourly(Listing l, long hours) {
		return l.getHourPrice() != null && hours < 24;
	}

	static long days(long hours) {
		return Math.max(1, (hours + 23) / 24);
	}

	/** The rent for a period: by the hour (at least {@link #MIN_HOURS}, never more than a day's price) or by the day. */
	static int rent(Listing l, long hours) {
		if (hourly(l, hours)) {
			return (int) Math.min((long) l.getDayPrice(), Math.max(MIN_HOURS, hours) * l.getHourPrice());
		}
		return (int) (days(hours) * l.getDayPrice());
	}

	/** Kilometres included: a day's allowance per (started) day. */
	static int kmIncluded(Listing l, long hours) {
		return (int) (days(hours) * l.getKmPerDay());
	}

	/** Why a period can't be booked, or null. Shorter than a day needs an hourly price. */
	static String periodProblem(Listing l, Instant from, Instant to, Instant now) {
		if (!to.isAfter(from)) {
			return "The return time must be after the pickup time.";
		}
		if (from.isBefore(now.plus(Duration.ofHours(1)))) {
			return "Pickup must be at least an hour from now.";
		}
		if (from.isAfter(now.plus(Duration.ofDays(MAX_DAYS_AHEAD)))) {
			return "You can book up to " + MAX_DAYS_AHEAD + " days ahead.";
		}
		long hours = hours(from, to);
		if (hours > MAX_DAYS * 24L) {
			return "You can rent for at most " + MAX_DAYS + " days at a time.";
		}
		if (l.getHourPrice() == null && hours < 24) {
			return "This car is rented by the day: choose at least 24 hours.";
		}
		if (hours < MIN_HOURS) {
			return "Rent for at least " + MIN_HOURS + " hours.";
		}
		return null;
	}
}
