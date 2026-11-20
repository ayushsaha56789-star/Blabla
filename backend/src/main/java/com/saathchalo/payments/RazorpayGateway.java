package com.saathchalo.payments;

import com.saathchalo.common.Crypto;
import com.saathchalo.config.AppProperties;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Razorpay over its REST API (no SDK needed). Keys come from the environment. */
class RazorpayGateway implements PaymentGateway {

	private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);

	private final String keyId;

	private final byte[] keySecret;

	private final byte[] webhookSecret;

	private final String payoutAccount;

	private final RestClient api;

	RazorpayGateway(AppProperties.Payments config) {
		this.keyId = config.razorpayKeyId();
		this.keySecret = config.razorpayKeySecret().getBytes(StandardCharsets.UTF_8);
		this.webhookSecret = config.razorpayWebhookSecret().getBytes(StandardCharsets.UTF_8);
		this.payoutAccount = config.razorpayxAccountNumber();
		JdkClientHttpRequestFactory http = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		http.setReadTimeout(Duration.ofSeconds(20));
		this.api = RestClient.builder()
			.requestFactory(http)
			.baseUrl("https://api.razorpay.com/v1")
			.defaultHeaders(h -> h.setBasicAuth(config.razorpayKeyId(), config.razorpayKeySecret()))
			.build();
	}

	@Override
	public String name() {
		return "RAZORPAY";
	}

	@Override
	public String publicKey() {
		return keyId;
	}

	@Override
	public String createOrder(long amountPaise, String receipt) {
		Map<String, Object> body = Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt);
		return (String) post("/orders", body, "start the payment").get("id");
	}

	@Override
	public boolean isGenuinePayment(String orderId, String paymentId, String signature) {
		return signature != null && Crypto.constantTimeEquals(Crypto.hmacSha256Hex(keySecret, orderId + "|" + paymentId), signature);
	}

	@Override
	public boolean isGenuineWebhook(String body, String signature) {
		return signature != null && webhookSecret.length > 0
				&& Crypto.constantTimeEquals(Crypto.hmacSha256Hex(webhookSecret, body), signature);
	}

	@Override
	public GatewayRefund refund(String paymentId, long amountPaise, String receipt) {
		Map<String, Object> res = post("/payments/" + paymentId + "/refund",
				Map.of("amount", amountPaise, "receipt", receipt, "speed", "normal"), "start the refund");
		return new GatewayRefund((String) res.get("id"), "processed".equals(res.get("status")));
	}

	@Override
	public boolean canSendPayoutLinks() {
		return payoutAccount != null && !payoutAccount.isBlank();
	}

	@Override
	public GatewayPayout payoutLink(String name, String phone, long amountPaise, String receipt, String description) {
		Map<String, Object> contact = new LinkedHashMap<>();
		contact.put("name", name);
		contact.put("contact", phone);
		contact.put("type", "vendor");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("account_number", payoutAccount);
		body.put("contact", contact);
		body.put("amount", amountPaise);
		body.put("currency", "INR");
		body.put("purpose", "payout");
		body.put("description", description);
		body.put("receipt", receipt);
		body.put("send_sms", true);
		Map<String, Object> res = post("/payout-links", body, "send the payout link");
		return new GatewayPayout((String) res.get("id"), (String) res.get("short_url"), false);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> post(String path, Map<String, Object> body, String what) {
		try {
			Map<String, Object> res = api.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
				.body(Map.class);
			if (res == null || res.get("id") == null) {
				throw new GatewayException("Razorpay didn't answer as expected. Please try again.", null);
			}
			return res;
		}
		catch (RestClientException e) {
			// Never log keys or full responses; the status line is enough to investigate.
			log.warn("Razorpay call to {} failed: {}", path, e.getClass().getSimpleName());
			throw new GatewayException("We couldn't " + what + " with Razorpay. Please try again in a moment.", e);
		}
	}
}
