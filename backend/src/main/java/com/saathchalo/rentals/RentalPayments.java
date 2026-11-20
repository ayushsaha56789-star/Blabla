package com.saathchalo.rentals;

import com.saathchalo.config.AppProperties;
import com.saathchalo.payments.PayableKind;
import com.saathchalo.rentals.internal.RentalBooking;
import com.saathchalo.rentals.internal.RentalBookingRepository;
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
 * Rentals as something paid for. The rent reaches the owner after the return; the deposit comes back to the renter,
 * minus agreed charges (extra kilometres, fuel, damage). Cancelling: everything back in good time, before the owner
 * accepted, or when the owner or staff cancel; later, part of the rent (the deposit always comes back).
 */
@Component
class RentalPayments implements PayableKind {

	static final String KIND = "RENTAL";

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
		.withZone(ZoneId.of("Asia/Kolkata"));

	private final RentalBookingRepository bookings;

	private final ListingService listings;

	private final AppProperties.Payments config;

	private final Clock clock;

	RentalPayments(RentalBookingRepository bookings, ListingService listings, AppProperties props, Clock clock) {
		this.bookings = bookings;
		this.listings = listings;
		this.config = props.payments();
		this.clock = clock;
	}

	@Override
	public String kind() {
		return KIND;
	}

	@Override
	public boolean onPaid(UUID bookingId) {
		RentalBooking b = bookings.lockById(bookingId).orElse(null);
		Instant now = clock.instant();
		if (b == null || !RentalBooking.ACCEPTED.equals(b.getStatus()) || b.getPayBy() == null || !b.getPayBy().isAfter(now)) {
			return false;
		}
		b.paid(now);
		return true;
	}

	@Override
	public boolean awaitingPayment(UUID bookingId) {
		return bookings.findById(bookingId)
			.map(b -> RentalBooking.ACCEPTED.equals(b.getStatus()) && b.getPayBy() != null && b.getPayBy().isAfter(clock.instant()))
			.orElse(false);
	}

	@Override
	public Share share(UUID bookingId, Amounts a) {
		RentalBooking b = bookings.findById(bookingId).orElse(null);
		if (b == null) {
			return a.everythingBack("The booking no longer exists");
		}
		return switch (b.getStatus()) {
			case RentalBooking.COMPLETED -> {
				long kept = Math.min(b.getExtraCharges() * 100L, a.depositPaise());
				yield a.split(a.depositPaise() - kept, a.basePaise() + kept,
						kept > 0 ? "Rental completed; ₹" + kept / 100 + " kept from the deposit" : "Rental completed; deposit back");
			}
			case RentalBooking.DECLINED -> a.everythingBack("The owner declined the request");
			case RentalBooking.EXPIRED -> a.everythingBack("The booking lapsed");
			case RentalBooking.CANCELLED -> "RENTER".equals(b.getCancelledBy())
					? renterCancels(a, b, b.getCancelledAt() == null ? clock.instant() : b.getCancelledAt())
					: a.everythingBack("The booking was cancelled by the owner or SaathChalo");
			default -> null;
		};
	}

	@Override
	public Share ifCancelledNow(UUID bookingId, Amounts a) {
		return bookings.findById(bookingId).map(b -> renterCancels(a, b, clock.instant())).orElse(null);
	}

	@Override
	public Optional<Description> describe(UUID bookingId) {
		return bookings.findById(bookingId).map(b -> {
			String car = listings.cards(java.util.List.of(listings.load(b.getListingId())), null, null, null).getFirst().car().name();
			String place = listings.load(b.getListingId()).getPlaceName();
			return new Description(car + " in " + place + ", " + WHEN.format(b.getStartsAt()) + " – " + WHEN.format(b.getEndsAt()),
					car + " · " + place, b.getStartsAt());
		});
	}

	/** The deposit always comes back; the rent too in good time, otherwise part of it. */
	private Share renterCancels(Amounts a, RentalBooking b, Instant when) {
		if (b.getDecidedAt() == null) {
			return a.everythingBack("Cancelled before the owner accepted");
		}
		Duration notice = Duration.between(when, b.getStartsAt());
		if (notice.compareTo(Duration.ofHours(config.fullRefundHours())) >= 0) {
			return a.everythingBack("Cancelled at least " + config.fullRefundHours() + " hours before pickup");
		}
		long rentBack = a.basePaise() * config.lateCancelRefundPercent() / 100;
		return a.split(a.depositPaise() + rentBack, a.basePaise() - rentBack, "Cancelled less than " + config.fullRefundHours()
				+ " hours before pickup: the deposit and " + config.lateCancelRefundPercent() + "% of the rent back, the rest to the owner");
	}
}
