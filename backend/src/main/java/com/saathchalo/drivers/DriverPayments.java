package com.saathchalo.drivers;

import com.saathchalo.config.AppProperties;
import com.saathchalo.drivers.internal.DriverTrip;
import com.saathchalo.drivers.internal.DriverTripRepository;
import com.saathchalo.payments.PayableKind;
import com.saathchalo.user.MemberProfiles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Hired-driver trips as something paid for. The fare reaches the driver once the trip is over. Cancelling: everything
 * back before the driver accepted, in good time, when the driver hadn't set off by the start time, or when the driver
 * or staff cancel; later, part of the fare goes to the driver (the same settings as rides).
 */
@Component
class DriverPayments implements PayableKind {

	static final String KIND = "DRIVER";

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
		.withZone(ZoneId.of("Asia/Kolkata"));

	private final DriverTripRepository trips;

	private final MemberProfiles members;

	private final AppProperties.Payments config;

	private final Clock clock;

	DriverPayments(DriverTripRepository trips, MemberProfiles members, AppProperties props, Clock clock) {
		this.trips = trips;
		this.members = members;
		this.config = props.payments();
		this.clock = clock;
	}

	@Override
	public String kind() {
		return KIND;
	}

	@Override
	public boolean onPaid(UUID tripId) {
		DriverTrip t = trips.lockById(tripId).orElse(null);
		Instant now = clock.instant();
		if (t == null || !DriverTrip.ACCEPTED.equals(t.getStatus()) || t.getPayBy() == null || !t.getPayBy().isAfter(now)) {
			return false;
		}
		t.paid(now);
		return true;
	}

	@Override
	public boolean awaitingPayment(UUID tripId) {
		return trips.findById(tripId)
			.map(t -> DriverTrip.ACCEPTED.equals(t.getStatus()) && t.getPayBy() != null && t.getPayBy().isAfter(clock.instant()))
			.orElse(false);
	}

	@Override
	public Share share(UUID tripId, Amounts a) {
		DriverTrip t = trips.findById(tripId).orElse(null);
		if (t == null) {
			return a.everythingBack("The trip no longer exists");
		}
		return switch (t.getStatus()) {
			case DriverTrip.COMPLETED -> a.split(0, a.basePaise(), "Trip completed");
			case DriverTrip.DECLINED -> a.everythingBack("The driver declined the request");
			case DriverTrip.EXPIRED -> a.everythingBack("The request lapsed");
			case DriverTrip.CANCELLED -> "CUSTOMER".equals(t.getCancelledBy())
					? customerCancels(a, t, t.getCancelledAt() == null ? clock.instant() : t.getCancelledAt())
					: a.everythingBack("SYSTEM".equals(t.getCancelledBy()) ? "The trip never started"
							: "The trip was cancelled by the driver or SaathChalo");
			default -> null;
		};
	}

	@Override
	public Share ifCancelledNow(UUID tripId, Amounts a) {
		return trips.findById(tripId).map(t -> customerCancels(a, t, clock.instant())).orElse(null);
	}

	@Override
	public Optional<Description> describe(UUID tripId) {
		return trips.findById(tripId).map(t -> {
			String driver = members.card(t.getDriverId()).displayName();
			return new Description("Driver " + driver + " in " + t.getPlaceName() + ", " + WHEN.format(t.getStartsAt()),
					"Hired driver · " + t.getPlaceName(), t.getStartsAt());
		});
	}

	private Share customerCancels(Amounts a, DriverTrip t, Instant when) {
		if (t.getDecidedAt() == null) {
			return a.everythingBack("Cancelled before the driver accepted");
		}
		if (!when.isBefore(t.getStartsAt()) && t.getOnTheWayAt() == null) {
			return a.everythingBack("The driver hadn't set off by the start time");
		}
		Duration notice = Duration.between(when, t.getStartsAt());
		if (notice.compareTo(Duration.ofHours(config.fullRefundHours())) >= 0) {
			return a.everythingBack("Cancelled at least " + config.fullRefundHours() + " hours before the trip");
		}
		long back = a.basePaise() * config.lateCancelRefundPercent() / 100;
		return a.split(back, a.basePaise() - back, "Cancelled less than " + config.fullRefundHours() + " hours before the trip: "
				+ config.lateCancelRefundPercent() + "% back, the rest to the driver");
	}
}
