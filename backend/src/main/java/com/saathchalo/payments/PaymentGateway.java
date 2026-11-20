package com.saathchalo.payments;

/**
 * The payment service SaathChalo uses (Razorpay in production; a pretend one in development and tests). Amounts are
 * in paise. Card and UPI details are entered on the gateway's own checkout, never in SaathChalo.
 */
public interface PaymentGateway {

	/** RAZORPAY or FAKE, as stored on payments. */
	String name();

	/** The public key the checkout needs in the browser. */
	String publicKey();

	/** An order to pay {@code amountPaise}; {@code receipt} is our own reference (at most 40 characters). */
	String createOrder(long amountPaise, String receipt);

	/** The checkout's proof that it took the money for this order. */
	boolean isGenuinePayment(String orderId, String paymentId, String signature);

	/** Whether a webhook body really came from the gateway. */
	boolean isGenuineWebhook(String body, String signature);

	/** Starts a refund. */
	GatewayRefund refund(String paymentId, long amountPaise, String receipt);

	/** Whether this gateway can send payout links (Razorpay needs a RazorpayX account for that). */
	boolean canSendPayoutLinks();

	/** Sends the driver a link where they choose their own UPI ID or bank account; we never see those details. */
	GatewayPayout payoutLink(String name, String phone, long amountPaise, String receipt, String description);

	/** {@code processed}: the money is on its way; otherwise a webhook confirms later. */
	record GatewayRefund(String id, boolean processed) {
	}

	/** {@code paid}: the driver already has the money (only the pretend gateway does that). */
	record GatewayPayout(String id, String url, boolean paid) {
	}

	/** The gateway said no or couldn't be reached. The message is safe to show. */
	class GatewayException extends RuntimeException {

		public GatewayException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
