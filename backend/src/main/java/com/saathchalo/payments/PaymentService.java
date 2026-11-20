package com.saathchalo.payments;

import static com.saathchalo.payments.Ledger.Account.CREDITS;
import static com.saathchalo.payments.Ledger.Account.EARNINGS;
import static com.saathchalo.payments.Ledger.Account.ESCROW;
import static com.saathchalo.payments.Ledger.Account.FEES;
import static com.saathchalo.payments.Ledger.Account.GATEWAY;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.Crypto;
import com.saathchalo.config.AppProperties;
import com.saathchalo.features.FeatureService;
import com.saathchalo.payments.Ledger.Line;
import com.saathchalo.payments.PayableKind.Amounts;
import com.saathchalo.payments.PayableKind.Share;
import com.saathchalo.payments.PaymentDtos.CheckoutRequest;
import com.saathchalo.payments.PaymentDtos.CheckoutView;
import com.saathchalo.payments.PaymentDtos.ConfirmRequest;
import com.saathchalo.payments.PaymentDtos.PaymentOptions;
import com.saathchalo.payments.PaymentDtos.PaymentView;
import com.saathchalo.payments.PaymentDtos.Prefill;
import com.saathchalo.payments.PaymentDtos.RefundPreview;
import com.saathchalo.payments.PaymentDtos.RefundView;
import com.saathchalo.payments.PaymentGateway.GatewayException;
import com.saathchalo.payments.internal.Payment;
import com.saathchalo.payments.internal.PaymentRepository;
import com.saathchalo.payments.internal.Refund;
import com.saathchalo.payments.internal.RefundRepository;
import com.saathchalo.rides.BookingService;
import com.saathchalo.rides.BookingService.PendingBooking;
import com.saathchalo.rides.OnlinePayments;
import com.saathchalo.rides.RideDtos.BookRequest;
import com.saathchalo.user.UserDtos.MeResponse;
import com.saathchalo.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Paying online: checkout, confirming the gateway's answer, and what happens to the money afterwards. Each kind of
 * thing paid for ({@link PayableKind}: ride seats, rentals) decides how its money is shared out once it's over. Every
 * movement is in the {@link Ledger}.
 */
@Service
public class PaymentService implements OnlinePayments {

	/** The gateway can't take less than ₹1. */
	static final long MIN_ONLINE_PAISE = 100;

	/** What another module asks to be paid for (rentals). Amounts in paise; the fee is added on {@code base}. */
	public record StartPayment(String kind, UUID itemId, UUID payerId, UUID payeeId, long basePaise, long depositPaise,
			Instant holdUntil, boolean useCredits) {
	}

	private final PaymentRepository payments;

	private final RefundRepository refunds;

	private final BookingService bookings;

	private final Map<String, PayableKind> kinds;

	private final Ledger ledger;

	private final PaymentsConfig.Gateways gateways;

	private final FeatureService features;

	private final UserService users;

	private final WalletService wallets;

	private final AppProperties.Payments config;

	private final TransactionTemplate tx;

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final Clock clock;

	public PaymentService(PaymentRepository payments, RefundRepository refunds, BookingService bookings,
			List<PayableKind> kinds, Ledger ledger, PaymentsConfig.Gateways gateways, FeatureService features,
			UserService users, WalletService wallets, AppProperties props, PlatformTransactionManager transactions,
			JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
		this.payments = payments;
		this.refunds = refunds;
		this.bookings = bookings;
		this.kinds = kinds.stream().collect(Collectors.toMap(PayableKind::kind, Function.identity()));
		this.ledger = ledger;
		this.gateways = gateways;
		this.features = features;
		this.users = users;
		this.wallets = wallets;
		this.config = props.payments();
		this.tx = new TransactionTemplate(transactions);
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	// ----- What members can do -----

	@Override
	public boolean availableFor(UUID memberId) {
		return gateways.active().isPresent() && features.isOnFor("payments.online", memberId);
	}

	@Transactional(readOnly = true)
	public PaymentOptions options(UUID me) {
		boolean online = availableFor(me);
		boolean credits = online && features.isOnFor("wallet", me);
		boolean cash = !online || features.isOnFor("payments.cash", me);
		return new PaymentOptions(online, cash, credits, credits ? ledger.credits(me) : 0, config.serviceFeePercent(),
				gateways.active().map(PaymentGateway::name).orElse(null), config.fullRefundHours(),
				config.lateCancelRefundPercent());
	}

	/** A ride seat: holds the seats, then starts paying (the gateway order is created outside the ride's lock). */
	public CheckoutView checkout(UUID me, CheckoutRequest req) {
		PaymentGateway gateway = requireOnline(me);
		boolean useCredits = Boolean.TRUE.equals(req.useCredits()) && features.isOnFor("wallet", me);
		Payment p = tx.execute(status -> {
			PendingBooking booking = bookings.bookAwaitingPayment(req.rideId(), me,
					new BookRequest(req.fromPosition(), req.toPosition(), req.seats(), req.message()));
			return create(RidePayments.KIND, booking.bookingId(), me, booking.driverId(), booking.total() * 100L, 0,
					booking.holdUntil(), useCredits, gateway);
		});
		return started(p, gateway);
	}

	/**
	 * Something another module wants paid (a rental). Returns the unfinished payment if there is one already, so
	 * pressing "Pay" twice never charges twice.
	 */
	public CheckoutView startPayment(StartPayment req) {
		PaymentGateway gateway = requireOnline(req.payerId());
		Payment existing = payments.findByBookingId(req.itemId()).orElse(null);
		if (existing != null) {
			if (Payment.CREATED.equals(existing.getStatus())) {
				return resume(req.payerId(), existing.getId());
			}
			if (Payment.PAID.equals(existing.getStatus())) {
				throw ApiException.conflict("already_paid", "This is already paid.");
			}
			throw ApiException.conflict("payment_closed", "This payment can't be made any more.");
		}
		boolean useCredits = req.useCredits() && features.isOnFor("wallet", req.payerId());
		Payment p = tx.execute(status -> create(req.kind(), req.itemId(), req.payerId(), req.payeeId(), req.basePaise(),
				req.depositPaise(), req.holdUntil(), useCredits, gateway));
		return started(p, gateway);
	}

	/** Back to an unfinished payment (say, the checkout was closed): makes sure it has an order, and returns it. */
	public CheckoutView resume(UUID me, UUID paymentId) {
		Payment p = payments.findById(paymentId).filter(x -> x.getPayerId().equals(me)).orElseThrow(PaymentService::notFound);
		if (!Payment.CREATED.equals(p.getStatus()) || !p.getHoldUntil().isAfter(clock.instant())) {
			throw ApiException.conflict("payment_closed", "This payment can't be completed any more. Please book again.");
		}
		PaymentGateway gateway = gatewayFor(p);
		String orderId = p.getGatewayOrderId() != null ? p.getGatewayOrderId() : createOrder(p.getId(), gateway);
		return checkoutView(p, gateway, orderId);
	}

	/** The checkout says the money was taken: check its signature, then the item goes ahead. */
	@Transactional
	public PaymentView confirm(UUID me, UUID paymentId, ConfirmRequest req) {
		Payment p = payments.lockById(paymentId).filter(x -> x.getPayerId().equals(me)).orElseThrow(PaymentService::notFound);
		if (Payment.PAID.equals(p.getStatus())) {
			return view(p);
		}
		PaymentGateway gateway = gatewayFor(p);
		if (p.getGatewayOrderId() == null
				|| !gateway.isGenuinePayment(p.getGatewayOrderId(), req.gatewayPaymentId(), req.signature())) {
			throw ApiException.badRequest("payment_unverified",
					"We couldn't verify this payment. Please try again, or write to us if money left your account.");
		}
		if (Payment.EXPIRED.equals(p.getStatus())) {
			lateCapture(p, req.gatewayPaymentId());
		}
		else {
			markPaid(p, req.gatewayPaymentId());
		}
		return view(p);
	}

	/** Development only: the pretend checkout's answer, as the real checkout would give it. */
	@Transactional(readOnly = true)
	public ConfirmRequest pretendToPay(UUID me, UUID paymentId) {
		Payment p = payments.findById(paymentId).filter(x -> x.getPayerId().equals(me)).orElseThrow(PaymentService::notFound);
		if (!(gateways.active().orElse(null) instanceof FakeGateway fake) || p.getGatewayOrderId() == null) {
			throw notFound();
		}
		String[] paid = fake.pay(p.getGatewayOrderId());
		return new ConfirmRequest(paid[0], paid[1]);
	}

	/** Payments for these items that the member made, or receives (as driver or owner). */
	@Transactional(readOnly = true)
	public List<PaymentView> forBookings(UUID me, Collection<UUID> itemIds) {
		List<Payment> list = payments.findByBookingIdIn(itemIds).stream()
			.filter(p -> p.getPayerId().equals(me) || p.getDriverId().equals(me))
			.toList();
		Map<UUID, List<RefundView>> refundsBy = refundsOf(list.stream().map(Payment::getId).toList());
		return list.stream().map(p -> view(p, refundsBy.getOrDefault(p.getId(), List.of()))).toList();
	}

	/** What the payer would get back by cancelling now. */
	@Transactional(readOnly = true)
	public RefundPreview refundPreview(UUID me, UUID itemId) {
		Payment p = payments.findByBookingId(itemId).filter(x -> x.getPayerId().equals(me)).orElse(null);
		if (p == null || !Payment.PAID.equals(p.getStatus()) || p.getOutcome() != null) {
			return new RefundPreview(false, 0, 0, 0, "You haven't paid anything online for this.");
		}
		Share s = kindOf(p).ifCancelledNow(itemId, amounts(p));
		if (s == null) {
			return new RefundPreview(false, 0, 0, 0, "This can't be cancelled now.");
		}
		long back = s.backOnline() + s.backCredits();
		return new RefundPreview(true, s.backOnline(), s.backCredits(), p.total() - back, s.reason());
	}

	// ----- After the item is over (called by Settlement, each in its own transaction) -----

	/** Settles or refunds a paid item once it's over. Does nothing while it's still going. */
	@Transactional
	public void settle(UUID paymentId) {
		Payment p = payments.lockById(paymentId).orElse(null);
		if (p == null || !Payment.PAID.equals(p.getStatus()) || p.getOutcome() != null) {
			return;
		}
		Share s = kindOf(p).share(p.getBookingId(), amounts(p));
		if (s != null) {
			close(p, s, null);
		}
	}

	/** An unpaid checkout whose hold is over: gives back any credits it held. */
	@Transactional
	public void expireIfUnpaid(UUID paymentId) {
		Payment p = payments.lockById(paymentId).orElse(null);
		if (p == null || !Payment.CREATED.equals(p.getStatus()) || kindOf(p).awaitingPayment(p.getBookingId())) {
			return; // Still expected (the item's own housekeeping hasn't caught up yet): the member may still pay.
		}
		ledger.post("CREDITS_RELEASE", p.getId(), "Unfinished payment: credits back", Line.debit(ESCROW, p.getCreditsPaise()),
				Line.credit(CREDITS, p.getPayerId(), p.getCreditsPaise()));
		p.expired(clock.instant());
	}

	/** Sends a pending refund's online part through the gateway. Failures are retried, then left for staff. */
	@Transactional
	public void sendRefund(UUID refundId) {
		Refund r = refunds.lockById(refundId).orElse(null);
		if (r == null || !r.needsSending()) {
			return;
		}
		Payment p = payments.findById(r.getPaymentId()).orElseThrow();
		try {
			PaymentGateway.GatewayRefund done = gatewayFor(p).refund(p.getGatewayPaymentId(), r.getOnlinePaise(), r.getId().toString());
			r.sent(done.id(), done.processed(), clock.instant());
		}
		catch (GatewayException | ApiException e) {
			r.failedAttempt(e.getMessage());
		}
	}

	// ----- Gateway notifications -----

	/** A webhook from the gateway. Each event is handled once, even if it's delivered again. */
	@Transactional
	public void webhook(String body, String signature, String eventId) {
		PaymentGateway gateway = gateways.active().orElseThrow(PaymentService::notFound);
		if (!gateway.isGenuineWebhook(body, signature)) {
			throw ApiException.badRequest("bad_signature", "The webhook signature doesn't match.");
		}
		JsonNode root = json.readTree(body);
		String event = root.path("event").asString("");
		String id = eventId == null || eventId.isBlank() ? Crypto.sha256Hex(body) : eventId;
		int fresh = jdbc.update("INSERT INTO payment_events (event_id, type, received_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
				id, event, java.sql.Timestamp.from(clock.instant()));
		if (fresh == 0) {
			return;
		}
		switch (event) {
			case "payment.captured", "order.paid" -> {
				JsonNode payment = root.at("/payload/payment/entity");
				onCaptured(payment.path("order_id").asString(""), payment.path("id").asString(""));
			}
			case "refund.processed", "refund.failed" -> {
				String refundId = root.at("/payload/refund/entity/id").asString("");
				refunds.findByGatewayRefundId(refundId).ifPresent(r -> {
					if (event.equals("refund.processed")) {
						r.processed(clock.instant());
					}
					else {
						r.failed("The gateway reported the refund failed");
					}
				});
			}
			case "payout_link.processed" -> wallets.onPayoutLink(root.at("/payload/payout_link/entity/id").asString(""), true);
			case "payout_link.cancelled", "payout_link.expired", "payout_link.rejected" ->
				wallets.onPayoutLink(root.at("/payload/payout_link/entity/id").asString(""), false);
			default -> {
				// Other events aren't needed.
			}
		}
	}

	private void onCaptured(String orderId, String gatewayPaymentId) {
		payments.findByGatewayOrderId(orderId).flatMap(found -> payments.lockById(found.getId())).ifPresent(p -> {
			if (Payment.CREATED.equals(p.getStatus())) {
				markPaid(p, gatewayPaymentId);
			}
			else if (Payment.EXPIRED.equals(p.getStatus())) {
				lateCapture(p, gatewayPaymentId);
			}
		});
	}

	// ----- Staff -----

	@Transactional
	public void retryRefund(UUID refundId) {
		Refund r = refunds.lockById(refundId)
			.orElseThrow(() -> ApiException.notFound("refund_not_found", "We couldn't find that refund."));
		if (!Refund.FAILED.equals(r.getStatus())) {
			throw ApiException.conflict("refund_not_failed", "Only failed refunds can be tried again.");
		}
		r.retry();
	}

	// ----- Inside -----

	private PaymentGateway requireOnline(UUID me) {
		if (!availableFor(me)) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "online_payments_unavailable",
					"Paying online isn't available right now.");
		}
		return gateways.active().orElseThrow();
	}

	/** Records the payment (and holds any credits it uses). Credits covering everything pay it at once. */
	private Payment create(String kind, UUID itemId, UUID payer, UUID payee, long basePaise, long depositPaise,
			Instant holdUntil, boolean useCredits, PaymentGateway gateway) {
		long fee = Math.round(basePaise * config.serviceFeePercent() / 100.0);
		long total = basePaise + fee + depositPaise;
		long credits = 0;
		if (useCredits) {
			credits = Math.min(ledger.lockCredits(payer), total);
			if (credits < total && total - credits < MIN_ONLINE_PAISE) {
				credits = Math.max(0, total - MIN_ONLINE_PAISE);
			}
		}
		Payment p = payments.save(new Payment(kind, itemId, payer, payee, basePaise, fee, depositPaise, credits,
				credits == total ? "CREDITS" : gateway.name(), clock.instant(), holdUntil));
		ledger.post("CREDITS_HOLD", p.getId(), "Credits used", Line.debit(CREDITS, payer, credits), Line.credit(ESCROW, credits));
		if (credits == total) {
			markPaid(p, null);
		}
		return p;
	}

	private CheckoutView started(Payment p, PaymentGateway gateway) {
		if (Payment.PAID.equals(p.getStatus())) {
			return checkoutView(p, null, null);
		}
		return checkoutView(p, gateway, createOrder(p.getId(), gateway));
	}

	/** The money arrived. If the item can't take it any more (it came too late), everything is refunded. */
	private void markPaid(Payment p, String gatewayPaymentId) {
		p.paid(gatewayPaymentId, clock.instant());
		ledger.post("PAYMENT", p.getId(), "Paid online", Line.debit(GATEWAY, p.getOnlinePaise()), Line.credit(ESCROW, p.getOnlinePaise()));
		if (!kindOf(p).onPaid(p.getBookingId())) {
			close(p, amounts(p).everythingBack("The payment arrived after it could still be used"), null);
		}
	}

	/** Money for a checkout we had already given up on: take it in, and send it straight back. */
	private void lateCapture(Payment p, String gatewayPaymentId) {
		p.paid(gatewayPaymentId, clock.instant());
		long online = p.getOnlinePaise();
		ledger.post("PAYMENT", p.getId(), "Late payment", Line.debit(GATEWAY, online), Line.credit(ESCROW, online));
		ledger.post("REFUND", p.getId(), "Late payment refunded", Line.debit(ESCROW, online), Line.credit(GATEWAY, online));
		refunds.save(new Refund(p.getId(), online, 0, "The payment arrived after it could still be used", null, clock.instant()));
		p.close(Payment.REFUNDED, clock.instant());
	}

	/** Posts the closing journal, records any refund and closes the payment. */
	private void close(Payment p, Share s, UUID staffId) {
		long back = s.backOnline() + s.backCredits();
		if (back + s.toPayee() + s.toFees() != p.total()) {
			throw new IllegalStateException("A share must add up to the payment");
		}
		boolean settled = back == 0;
		ledger.post(settled ? "SETTLE" : "REFUND", p.getId(), s.reason(), Line.debit(ESCROW, p.total()),
				Line.credit(GATEWAY, s.backOnline()), Line.credit(CREDITS, p.getPayerId(), s.backCredits()),
				Line.credit(EARNINGS, p.getDriverId(), s.toPayee()), Line.credit(FEES, s.toFees()));
		if (back > 0) {
			refunds.save(new Refund(p.getId(), s.backOnline(), s.backCredits(), s.reason(), staffId, clock.instant()));
		}
		p.close(settled ? Payment.SETTLED : back == p.total() ? Payment.REFUNDED : Payment.SPLIT, clock.instant());
	}

	static Amounts amounts(Payment p) {
		return new Amounts(p.getSeatPaise(), p.getFeePaise(), p.getDepositPaise(), p.getCreditsPaise(), p.getOnlinePaise());
	}

	PayableKind kindOf(Payment p) {
		PayableKind kind = kinds.get(p.getKind());
		if (kind == null) {
			throw new IllegalStateException("No payable kind " + p.getKind());
		}
		return kind;
	}

	private String createOrder(UUID paymentId, PaymentGateway gateway) {
		Payment p = payments.findById(paymentId).orElseThrow();
		String orderId;
		try {
			orderId = gateway.createOrder(p.getOnlinePaise(), p.getId().toString());
		}
		catch (GatewayException e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "gateway_unavailable", e.getMessage())
				.with("paymentId", p.getId().toString());
		}
		tx.executeWithoutResult(status -> payments.findById(paymentId).ifPresent(fresh -> {
			fresh.orderCreated(orderId);
			payments.save(fresh);
		}));
		return orderId;
	}

	private PaymentGateway gatewayFor(Payment p) {
		PaymentGateway gateway = gateways.active().orElse(null);
		if (gateway == null || !gateway.name().equals(p.getGateway())) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "online_payments_unavailable",
					"Online payments are switched off right now. Please try again later.");
		}
		return gateway;
	}

	private CheckoutView checkoutView(Payment p, PaymentGateway gateway, String orderId) {
		MeResponse me = users.me(p.getPayerId());
		String name = ((me.firstName() == null ? "" : me.firstName()) + " " + (me.lastName() == null ? "" : me.lastName())).strip();
		String description = kindOf(p).describe(p.getBookingId()).map(PayableKind.Description::text).orElse("SaathChalo");
		return new CheckoutView(p.getId(), p.getBookingId(), p.getKind(), p.getStatus(), p.getSeatPaise(), p.getFeePaise(),
				p.getDepositPaise(), p.getCreditsPaise(), p.getOnlinePaise(), p.getGateway(), gateway == null ? null : gateway.publicKey(),
				orderId, p.getHoldUntil(), description, new Prefill(name.isEmpty() ? null : name, me.email(), me.phone()));
	}

	PaymentView view(Payment p) {
		return view(p, refundsOf(List.of(p.getId())).getOrDefault(p.getId(), List.of()));
	}

	private PaymentView view(Payment p, List<RefundView> refundViews) {
		boolean tooLate = refundViews.stream().anyMatch(r -> r.reason().startsWith("The payment arrived after"));
		return new PaymentView(p.getId(), p.getBookingId(), p.getKind(), p.getStatus(), p.getOutcome(), p.getSeatPaise(),
				p.getFeePaise(), p.getDepositPaise(), p.getCreditsPaise(), p.getOnlinePaise(), p.getGateway(), p.getHoldUntil(),
				p.getPaidAt(), !tooLate, refundViews);
	}

	Map<UUID, List<RefundView>> refundsOf(Collection<UUID> paymentIds) {
		if (paymentIds.isEmpty()) {
			return Map.of();
		}
		return refunds.findByPaymentIdInOrderByCreatedAtAsc(paymentIds).stream()
			.collect(Collectors.groupingBy(Refund::getPaymentId, Collectors.mapping(PaymentService::refundView, Collectors.toList())));
	}

	static RefundView refundView(Refund r) {
		return new RefundView(r.getId(), r.getOnlinePaise(), r.getCreditsPaise(), r.getStatus(), r.getReason(), r.getCreatedAt(),
				r.getProcessedAt());
	}

	private static ApiException notFound() {
		return ApiException.notFound("payment_not_found", "We couldn't find that payment.");
	}
}
