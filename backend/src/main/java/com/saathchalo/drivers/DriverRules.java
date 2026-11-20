package com.saathchalo.drivers;

import com.saathchalo.drivers.DriverDtos.Quote;
import com.saathchalo.drivers.DriverDtos.TripKind;
import com.saathchalo.drivers.internal.DriverProfile;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** The rules of hiring a driver: how long, how far ahead, the price, and the time limits on each step. */
final class DriverRules {

	static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

	static final int MIN_HOURS = 2;

	static final int MAX_HOURS = 12;

	static final int MAX_DAYS = 15;

	/** The driver needs time to get there. */
	static final Duration MIN_NOTICE = Duration.ofHours(1);

	static final Duration MAX_AHEAD = Duration.ofDays(60);

	/** A request waits this long for the driver (never past half an hour before the start). */
	static final Duration ANSWER_WITHIN = Duration.ofHours(3);

	/** An accepted trip paid online must be paid this soon (and a quarter of an hour before the start). */
	static final Duration PAY_WITHIN = Duration.ofHours(2);

	static final Duration ON_THE_WAY_FROM = Duration.ofHours(3);

	static final Duration START_FROM = Duration.ofHours(1);

	/** Time a driver needs between two trips. */
	static final Duration GAP = Duration.ofHours(1);

	/** A trip still running this long after its booked end is finished by itself. */
	static final Duration FINISH_BY = Duration.ofHours(12);

	/** Reviews are open this long after the trip. */
	static final Duration REVIEW_WITHIN = Duration.ofDays(14);

	static final int MAX_AREAS = 5;

	static final int MAX_WRONG_CODES = 5;

	static final int MAX_OPEN_REQUESTS = 5;

	private DriverRules() {
	}

	static int units(TripKind kind, Integer hours, Integer days) {
		return kind == TripKind.LOCAL ? (hours == null ? 0 : hours) : (days == null ? 0 : days);
	}

	static Instant end(TripKind kind, Instant from, Integer hours, Integer days) {
		return kind == TripKind.LOCAL ? from.plus(Duration.ofHours(units(kind, hours, days)))
				: from.plus(Duration.ofDays(units(kind, hours, days)));
	}

	/** Why this trip can't be booked as asked, in plain words, or null. */
	static String periodProblem(TripKind kind, Instant from, Integer hours, Integer days, Instant now) {
		if (kind == TripKind.LOCAL && (hours == null || hours < MIN_HOURS || hours > MAX_HOURS)) {
			return "Local trips are " + MIN_HOURS + " to " + MAX_HOURS + " hours.";
		}
		if (kind == TripKind.OUTSTATION && (days == null || days < 1 || days > MAX_DAYS)) {
			return "Outstation trips are 1 to " + MAX_DAYS + " days.";
		}
		if (from.isBefore(now.plus(MIN_NOTICE))) {
			return "Book at least an hour ahead, so the driver can get there.";
		}
		if (from.isAfter(now.plus(MAX_AHEAD))) {
			return "You can book up to 60 days ahead.";
		}
		return null;
	}

	static Quote quote(DriverProfile d, TripKind kind, Instant from, Integer hours, Integer days) {
		int units = units(kind, hours, days);
		Instant to = end(kind, from, hours, days);
		if (kind == TripKind.LOCAL) {
			int byHour = units * d.getHourPrice();
			boolean dayPrice = d.getDayPrice() < byHour;
			int base = dayPrice ? d.getDayPrice() : byHour;
			int night = touchesNight(from, to) ? d.getNightCharge() : 0;
			return new Quote(true, null, kind, from, to, units, base, dayPrice, night, 0, base + night, d.getHourPrice());
		}
		int base = units * d.getOutstationPrice();
		int allowance = units * d.getAllowance();
		return new Quote(true, null, kind, from, to, units, base, false, 0, allowance, base + allowance,
				d.getOutstationPrice() + d.getAllowance());
	}

	/** Whether any part of the trip falls between 10 pm and 6 am (India time). */
	static boolean touchesNight(Instant from, Instant to) {
		LocalDate day = from.atZone(INDIA).toLocalDate().minusDays(1);
		LocalDate last = to.atZone(INDIA).toLocalDate();
		for (; !day.isAfter(last); day = day.plusDays(1)) {
			ZonedDateTime nightStarts = day.atTime(22, 0).atZone(INDIA);
			ZonedDateTime nightEnds = day.plusDays(1).atTime(6, 0).atZone(INDIA);
			if (from.isBefore(nightEnds.toInstant()) && to.isAfter(nightStarts.toInstant())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * How far a trip ran over: extra hours for a local trip (a quarter of an hour's grace), extra days for an
	 * outstation trip (two hours' grace). Rounded up.
	 */
	static int extraUnits(TripKind kind, Instant bookedEnd, Instant ended) {
		Duration over = Duration.between(bookedEnd, ended);
		if (kind == TripKind.LOCAL) {
			if (over.compareTo(Duration.ofMinutes(15)) <= 0) {
				return 0;
			}
			return (int) ((over.toMinutes() + 59) / 60);
		}
		if (over.compareTo(Duration.ofHours(2)) <= 0) {
			return 0;
		}
		return (int) ((over.toHours() + 23) / 24);
	}
}
