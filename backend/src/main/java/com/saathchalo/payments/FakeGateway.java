package com.saathchalo.payments;

import com.saathchalo.common.Crypto;
import java.nio.charset.StandardCharsets;

/**
 * A pretend gateway for development and tests: no money moves. It signs payments and webhooks the way Razorpay does,
 * so the same checks run as in production. {@link PaymentsConfig} refuses to use it outside the dev and test profiles.
 */
class FakeGateway implements PaymentGateway {

	private final byte[] secret;

	FakeGateway(byte[] dataKey) {
		this.secret = Crypto.hmacSha256Hex(dataKey, "fake-payment-gateway").getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public String name() {
		return "FAKE";
	}

	@Override
	public String publicKey() {
		return "fake_key";
	}

	@Override
	public String createOrder(long amountPaise, String receipt) {
		return "order_fake_" + Crypto.randomHex(8);
	}

	/** What the pretend checkout returns when the member pays: a payment id and its signature. */
	String[] pay(String orderId) {
		String paymentId = "pay_fake_" + Crypto.randomHex(8);
		return new String[] { paymentId, sign(orderId + "|" + paymentId) };
	}

	String sign(String text) {
		return Crypto.hmacSha256Hex(secret, text);
	}

	@Override
	public boolean isGenuinePayment(String orderId, String paymentId, String signature) {
		return signature != null && Crypto.constantTimeEquals(sign(orderId + "|" + paymentId), signature);
	}

	@Override
	public boolean isGenuineWebhook(String body, String signature) {
		return signature != null && Crypto.constantTimeEquals(sign(body), signature);
	}

	@Override
	public GatewayRefund refund(String paymentId, long amountPaise, String receipt) {
		return new GatewayRefund("rfnd_fake_" + Crypto.randomHex(8), true);
	}

	@Override
	public boolean canSendPayoutLinks() {
		return true;
	}

	@Override
	public GatewayPayout payoutLink(String name, String phone, long amountPaise, String receipt, String description) {
		return new GatewayPayout("poutlk_fake_" + Crypto.randomHex(8), null, true);
	}
}
