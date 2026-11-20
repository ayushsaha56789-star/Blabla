package com.saathchalo.payments;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.payments.PaymentDtos.CheckoutRequest;
import com.saathchalo.payments.PaymentDtos.CheckoutView;
import com.saathchalo.payments.PaymentDtos.ConfirmRequest;
import com.saathchalo.payments.PaymentDtos.PaymentOptions;
import com.saathchalo.payments.PaymentDtos.PaymentView;
import com.saathchalo.payments.PaymentDtos.RefundPreview;
import com.saathchalo.payments.PaymentDtos.WalletView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

	private final PaymentService payments;

	private final WalletService wallets;

	public PaymentController(PaymentService payments, WalletService wallets) {
		this.payments = payments;
		this.wallets = wallets;
	}

	@GetMapping("/api/payments/options")
	public PaymentOptions options() {
		return payments.options(CurrentUser.require());
	}

	@PostMapping("/api/payments/checkout")
	@ResponseStatus(HttpStatus.CREATED)
	@RequiresFeature("payments.online")
	public CheckoutView checkout(@Valid @RequestBody CheckoutRequest request) {
		return payments.checkout(CurrentUser.require(), request);
	}

	@PostMapping("/api/payments/{id}/resume")
	public CheckoutView resume(@PathVariable UUID id) {
		return payments.resume(CurrentUser.require(), id);
	}

	@PostMapping("/api/payments/{id}/confirm")
	public PaymentView confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmRequest request) {
		return payments.confirm(CurrentUser.require(), id, request);
	}

	/** Development only: what the pretend checkout answers when the member pays. */
	@PostMapping("/api/payments/{id}/pretend-pay")
	public ConfirmRequest pretendPay(@PathVariable UUID id) {
		return payments.pretendToPay(CurrentUser.require(), id);
	}

	@GetMapping("/api/payments/bookings")
	public List<PaymentView> forBookings(@RequestParam List<UUID> ids) {
		if (ids.size() > 200) {
			throw ApiException.badRequest("too_many", "Ask for at most 200 bookings at a time.");
		}
		return payments.forBookings(CurrentUser.require(), ids);
	}

	@GetMapping("/api/payments/bookings/{bookingId}/refund-preview")
	public RefundPreview refundPreview(@PathVariable UUID bookingId) {
		return payments.refundPreview(CurrentUser.require(), bookingId);
	}

	@GetMapping("/api/wallet")
	public WalletView wallet() {
		return wallets.wallet(CurrentUser.require());
	}

	/** Public on purpose: the gateway calls it. Only signed events are accepted, and each is handled once. */
	@PostMapping("/api/payments/webhook")
	public void webhook(@RequestBody String body, @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
			@RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
		payments.webhook(body, signature, eventId);
	}
}
