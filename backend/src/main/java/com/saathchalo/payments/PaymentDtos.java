package com.saathchalo.payments;

import com.saathchalo.user.MemberProfiles.MemberCard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Amounts are in paise (₹1 = 100 paise). */
public final class PaymentDtos {

	private PaymentDtos() {
	}

	/** What the website may offer at booking time. {@code online}: the switch is on and a gateway is set up. */
	public record PaymentOptions(boolean online, boolean cash, boolean credits, long creditsPaise, double serviceFeePercent,
			String gateway, int fullRefundHours, int lateCancelRefundPercent) {
	}

	public record CheckoutRequest(
			@NotNull(message = "Choose a ride") UUID rideId,
			@NotNull(message = "Please choose where you get in") @Min(0) Integer fromPosition,
			@NotNull(message = "Please choose where you get out") @Min(1) Integer toPosition,
			@NotNull(message = "How many seats?")
			@Min(value = 1, message = "Book at least 1 seat") @Max(value = 8, message = "At most 8 seats") Integer seats,
			@Size(max = 300, message = "Keep your message under 300 characters") String message,
			Boolean useCredits) {
	}

	/** What the checkout needs. {@code orderId} is null when credits paid it all (then {@code status} is PAID). */
	public record CheckoutView(UUID paymentId, UUID bookingId, String status, long seatPaise, long feePaise,
			long creditsPaise, long onlinePaise, String gateway, String keyId, String orderId, Instant holdUntil,
			String description, Prefill prefill) {
	}

	public record Prefill(String name, String email, String contact) {
	}

	public record ConfirmRequest(@NotBlank(message = "Missing payment id") String gatewayPaymentId,
			@NotBlank(message = "Missing payment signature") String signature) {
	}

	public record RefundView(UUID id, long onlinePaise, long creditsPaise, String status, String reason,
			Instant createdAt, Instant processedAt) {
	}

	/** {@code seatHeld}: false when the money came too late to keep the seat (it's then refunded in full). */
	public record PaymentView(UUID id, UUID bookingId, String status, String outcome, long seatPaise, long feePaise,
			long creditsPaise, long onlinePaise, String gateway, Instant holdUntil, Instant paidAt, boolean seatHeld,
			List<RefundView> refunds) {
	}

	/** What cancelling now would give back. */
	public record RefundPreview(boolean paid, long backOnlinePaise, long backCreditsPaise, long keptPaise, String explanation) {
	}

	/** A member's wallet: credits, and for drivers what they earned and were paid. */
	public record WalletView(long creditsPaise, long earningsPaise, long paidOutPaise, List<Map<String, Object>> history,
			List<PayoutView> payouts) {
	}

	public record PayoutView(UUID id, MemberCard driver, long amountPaise, String method, String status, String linkUrl,
			String reference, Instant createdAt, Instant paidAt) {
	}

	// ----- Staff -----

	public record AdminSummary(boolean onlineReady, String gateway, long heldForTripsPaise, long owedToDriversPaise,
			long creditsOutstandingPaise, long feesEarnedPaise, long goodwillPaise, long refundsPending, long refundsFailed,
			boolean ledgerBalanced, boolean walletsMatch, boolean escrowMatches) {
	}

	public record AdminPaymentRow(UUID id, UUID bookingId, MemberCard payer, MemberCard driver, String route,
			Instant departureAt, long seatPaise, long feePaise, long creditsPaise, long onlinePaise, String gateway,
			String status, String outcome, Instant createdAt, Instant paidAt, List<RefundView> refunds) {
	}

	public record OwedRow(MemberCard driver, long earningsPaise, Instant lastPayoutAt) {
	}

	public record PayoutRequest(@NotNull(message = "Choose a driver") UUID driverId,
			@NotBlank(message = "Choose how to pay") String method,
			@Size(max = 120, message = "Keep the reference under 120 characters") String reference) {
	}

	public record CreditsRequest(
			@NotNull(message = "How much?") @Min(value = 100, message = "At least ₹1")
			@Max(value = 500_000, message = "At most ₹5,000 at a time") Long amountPaise,
			@NotBlank(message = "Say why, for the record") @Size(max = 200, message = "Keep it under 200 characters") String reason) {
	}

	public record StaffRefundRequest(
			@NotBlank(message = "Say why, for the record") @Size(max = 200, message = "Keep it under 200 characters") String reason) {
	}
}
