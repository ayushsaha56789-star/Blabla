package com.saathchalo.admin;

import com.saathchalo.access.Perm;
import com.saathchalo.auth.RequiresConfirmation;
import com.saathchalo.payments.PaymentDtos.AdminPaymentRow;
import com.saathchalo.payments.PaymentDtos.AdminSummary;
import com.saathchalo.payments.PaymentDtos.CreditsRequest;
import com.saathchalo.payments.PaymentDtos.OwedRow;
import com.saathchalo.payments.PaymentDtos.PayoutRequest;
import com.saathchalo.payments.PaymentDtos.PayoutView;
import com.saathchalo.payments.PaymentDtos.WalletView;
import com.saathchalo.payments.PaymentService;
import com.saathchalo.payments.WalletService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Money in the console: totals and health checks, payments, refunds, credits and driver payouts. */
@RestController
public class AdminPaymentsController {

	private final AdminContext context;

	private final WalletService wallets;

	private final PaymentService payments;

	public AdminPaymentsController(AdminContext context, WalletService wallets, PaymentService payments) {
		this.context = context;
		this.wallets = wallets;
		this.payments = payments;
	}

	@GetMapping("/api/admin/payments/summary")
	@PreAuthorize(Perm.PAYMENTS_REFUND)
	public AdminSummary summary() {
		return wallets.summary();
	}

	@GetMapping("/api/admin/payments")
	@PreAuthorize(Perm.PAYMENTS_REFUND)
	public List<AdminPaymentRow> payments(@RequestParam(defaultValue = "all") String filter) {
		return wallets.recent(filter);
	}

	@PostMapping("/api/admin/refunds/{id}/retry")
	@PreAuthorize(Perm.PAYMENTS_REFUND)
	public void retryRefund(@PathVariable UUID id) {
		payments.retryRefund(id);
	}

	@GetMapping("/api/admin/payouts/owed")
	@PreAuthorize(Perm.PAYMENTS_REFUND)
	public List<OwedRow> owed() {
		return wallets.owed();
	}

	@GetMapping("/api/admin/payouts")
	@PreAuthorize(Perm.PAYMENTS_REFUND)
	public List<PayoutView> payouts() {
		return wallets.recentPayouts();
	}

	@PostMapping("/api/admin/payouts")
	@PreAuthorize(Perm.PAYOUTS_APPROVE)
	@RequiresConfirmation
	public PayoutView payout(@Valid @RequestBody PayoutRequest request) {
		return wallets.payout(request.driverId(), request.method(), request.reference(), context.current().actor());
	}

	@GetMapping("/api/admin/members/{id}/wallet")
	@PreAuthorize(Perm.USERS_VIEW)
	public WalletView memberWallet(@PathVariable UUID id) {
		return wallets.memberWallet(id);
	}

	@PostMapping("/api/admin/members/{id}/credits")
	@PreAuthorize(Perm.PAYMENTS_REFUND)
	@RequiresConfirmation
	public WalletView grantCredits(@PathVariable UUID id, @Valid @RequestBody CreditsRequest request) {
		return wallets.grantCredits(id, request.amountPaise(), request.reason(), context.current().actor());
	}
}
