package com.saathchalo.payments;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Something members pay for: a seat on a ride (here in payments) or a rental (implemented by the rentals module).
 * The payments module takes the money, keeps it in escrow and, once the item is over, shares it out as the item's
 * module decides. Amounts are in paise.
 */
public interface PayableKind {

	/** RIDE, RENTAL, … as stored on payments. */
	String kind();

	/**
	 * The money arrived (called inside the payment's transaction). False when the item can't take it any more, for
	 * example a seat that was released: everything is then refunded.
	 */
	boolean onPaid(UUID itemId);

	/** True while the item still expects this payment, so an unfinished checkout shouldn't be given up yet. */
	boolean awaitingPayment(UUID itemId);

	/** How the money is shared out once the item is over; null while it's still going. */
	Share share(UUID itemId, Amounts amounts);

	/** What cancelling now would give back, for the "cancel?" question. */
	Share ifCancelledNow(UUID itemId, Amounts amounts);

	/** What it is, in one line ("Seat Delhi → Jaipur, 21 Nov, 7:30 AM"), and when it starts. */
	Optional<Description> describe(UUID itemId);

	/** {@code base}: the seat price or the rent; {@code deposit}: refundable, never a fee. */
	record Amounts(long basePaise, long feePaise, long depositPaise, long creditsPaise, long onlinePaise) {

		public long total() {
			return basePaise + feePaise + depositPaise;
		}

		/** Everything back, each part where it came from. */
		public Share everythingBack(String reason) {
			return new Share(onlinePaise, creditsPaise, 0, 0, reason);
		}

		/**
		 * {@code back} goes to the payer (online first, then credits), {@code toPayee} to the driver or owner, and the
		 * fee to SaathChalo.
		 */
		public Share split(long back, long toPayee, String reason) {
			long backOnline = Math.min(back, onlinePaise);
			return new Share(backOnline, back - backOnline, toPayee, total() - back - toPayee, reason);
		}
	}

	/** Must add up to the whole payment. */
	record Share(long backOnline, long backCredits, long toPayee, long toFees, String reason) {
	}

	record Description(String text, String route, Instant startsAt) {
	}
}
