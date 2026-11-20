package com.saathchalo.payments;

import com.saathchalo.config.AppProperties;
import com.saathchalo.rides.BookingService;
import com.saathchalo.rides.BookingStatus;
import com.saathchalo.rides.RideLookup;
import com.saathchalo.rides.RideLookup.BookingFacts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Seats on rides: the money reaches the driver after the trip; refunds follow the cancellation rules. */
@Component
class RidePayments implements PayableKind {

	static final String KIND = "RIDE";

	static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
		.withZone(ZoneId.of("Asia/Kolkata"));

	private final BookingService bookings;

	private final RideLookup rides;

	private final AppProperties.Payments config;

	private final Clock clock;

	RidePayments(BookingService bookings, RideLookup rides, AppProperties props, Clock clock) {
		this.bookings = bookings;
		this.rides = rides;
		this.config = props.payments();
		this.clock = clock;
	}

	@Override
	public String kind() {
		return KIND;
	}

	@Override
	public boolean onPaid(UUID bookingId) {
		return bookings.paymentReceived(bookingId);
	}

	@Override
	public boolean awaitingPayment(UUID bookingId) {
		return facts(bookingId).map(f -> f.status() == BookingStatus.AWAITING_PAYMENT).orElse(false);
	}

	@Override
	public Share share(UUID bookingId, Amounts a) {
		BookingFacts f = facts(bookingId).orElse(null);
		if (f == null) {
			return a.everythingBack("The booking no longer exists");
		}
		return switch (f.status()) {
			case AWAITING_PAYMENT, REQUESTED, CONFIRMED -> null;
			case COMPLETED -> a.split(0, a.basePaise(), "Trip completed");
			case DECLINED -> a.everythingBack("The driver declined the request");
			case EXPIRED -> a.everythingBack("The request wasn't answered in time");
			case CANCELLED -> "PASSENGER".equals(f.cancelledBy())
					? passengerCancels(a, f, f.cancelledAt() == null ? clock.instant() : f.cancelledAt())
					: a.everythingBack("The ride was cancelled");
		};
	}

	@Override
	public Share ifCancelledNow(UUID bookingId, Amounts a) {
		return facts(bookingId).map(f -> passengerCancels(a, f, clock.instant())).orElse(null);
	}

	@Override
	public Optional<Description> describe(UUID bookingId) {
		return facts(bookingId).map(f -> new Description("Seat " + f.fromName() + " → " + f.toName() + ", " + WHEN.format(f.pickupAt()),
				f.fromName() + " → " + f.toName(), f.pickupAt()));
	}

	/** Everything back in good time, or before the driver accepted; later, part of the seat price. */
	private Share passengerCancels(Amounts a, BookingFacts f, Instant when) {
		if (f.decidedAt() == null) {
			return a.everythingBack("Cancelled before the driver accepted");
		}
		Duration notice = f.pickupAt() == null ? Duration.ZERO : Duration.between(when, f.pickupAt());
		if (notice.compareTo(Duration.ofHours(config.fullRefundHours())) >= 0) {
			return a.everythingBack("Cancelled at least " + config.fullRefundHours() + " hours before pickup");
		}
		long back = a.basePaise() * config.lateCancelRefundPercent() / 100;
		return a.split(back, a.basePaise() - back, "Cancelled less than " + config.fullRefundHours() + " hours before pickup: "
				+ config.lateCancelRefundPercent() + "% of the seat price back, the rest to the driver");
	}

	private Optional<BookingFacts> facts(UUID bookingId) {
		return Optional.ofNullable(rides.bookingFacts(List.of(bookingId)).get(bookingId));
	}
}
