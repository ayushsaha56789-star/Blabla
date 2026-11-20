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
import com.saathchalo.rides.BookingStatus;
import com.saathchalo.rides.OnlinePayments;
import com.saathchalo.rides.RideDtos.BookRequest;
import com.saathchalo.rides.RideLookup;
import com.saathchalo.rides.RideLookup.BookingFacts;
import com.saathchalo.user.UserDtos.MeResponse;
import com.saathchalo.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
 * Paying for seats online: checkout, confirming the gateway's answer, and what happens to the money afterwards (the
 * driver gets it once the trip is over, or the passenger gets some or all of it back). Every movement is in the
 * {@link Ledger}.
 */
@Service
public class PaymentService implements OnlinePayments {

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
		.withZone(ZoneId.of("Asia/Kolkata"));

	/** The gateway can't take less than ₹1. */
	static final long MIN_ONLINE_PAISE = 100;

	private final PaymentRepository payments;

	private final RefundRepository refunds;

	private final BookingService bookings;

	private final RideLookup rides;

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

	public PaymentService(PaymentRepository payments, RefundRepository refunds, BookingService bookings, RideLookup rides,
			Ledger ledger, PaymentsConfig.Gateways gateways, FeatureService features, UserService users, WalletService wallets,
			AppProperties props, PlatformTransactionManager transactions, JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
		this.payments = payments;
		this.refunds = refunds;
		this.bookings = bookings;
		this.rides = rides;
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

	/**
	 * Holds the seats and starts paying. The gateway order is created after the seats are held (outside the ride's
	 * lock); if that fails, {@link #resume} tries again.
	 */
	public CheckoutView checkout(UUID me, CheckoutRequest req) {
		if (!availableFor(me)) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "online_payments_unavailable",
					"Paying online isn't available right now. You can pay the driver directly.");
		}
		PaymentGateway gateway = gateways.active().orElseThrow();
		boolean useCredits = Boolean.TRUE.equals(req.useCredits()) && features.isOnFor("wallet", me);
		record Started(Payment payment, PendingBooking booking) {
		}
		Started started = tx.execute(status -> {
			PendingBooking booking = bookings.bookAwaitingPayment(req.rideId(), me,
					new BookRequest(req.fromPosition(), req.toPosition(), req.seats(), req.message()));
			Instant now = clock.instant();
			long seat = booking.total() * 100L;
			long fee = Math.round(seat * config.serviceFeePercent() / 100.0);
			long total = seat + fee;
			long credits = 0;
			if (useCredits) {
				credits = Math.min(ledger.lockCredits(me), total);
				if (credits < total && total - credits < MIN_ONLINE_PAISE) {
					credits = Math.max(0, total - MIN_ONLINE_PAISE);
				}
			}
			Payment p = payments.save(new Payment(booking.bookingId(), me, booking.driverId(), seat, fee, credits,
					credits == total ? "CREDITS" : gateway.name(), now, booking.holdUntil()));
			ledger.post("CREDITS_HOLD", p.getId(), "Credits used for a seat", Line.debit(CREDITS, me, credits),
					Line.credit(ESCROW, credits));
			if (credits == total) {
				markPaid(p, null);
			}
			return new Started(p, booking);
		});
		Payment p = started.payment();
		if (Payment.PAID.equals(p.getStatus())) {
			return checkoutView(p, started.booking(), null, null);
		}
		return checkoutView(p, started.booking(), gateway, createOrder(p.getId(), gateway));
	}

	/** Back to an unfinished payment (say, the checkout was closed): makes sure it has an order, and returns it. */
	public CheckoutView resume(UUID me, UUID paymentId) {
		Payment p = payments.findById(paymentId).filter(x -> x.getPayerId().equals(me)).orElseThrow(PaymentService::notFound);
		if (!Payment.CREATED.equals(p.getStatus()) || !p.getHoldUntil().isAfter(clock.instant())) {
			throw ApiException.conflict("payment_closed", "This payment can't be completed any more. Please book again.");
		}
		PaymentGateway gateway = gatewayFor(p);
		String orderId = p.getGatewayOrderId() != null ? p.getGatewayOrderId() : createOrder(p.getId(), gateway);
		BookingFacts f = rides.bookingFacts(List.of(p.getBookingId())).get(p.getBookingId());
		PendingBooking booking = new PendingBooking(p.getBookingId(), f.rideId(), f.driverId(), f.seats(), f.pricePerSeat(),
				f.fromName(), f.toName(), f.pickupAt(), p.getHoldUntil());
		return checkoutView(p, booking, gateway, orderId);
	}

	/** The checkout says the money was taken: check its signature, then the booking goes ahead. */
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

	/** Payments for these bookings that the member made (as passenger) or receives (as driver). */
	@Transactional(readOnly = true)
	public List<PaymentView> forBookings(UUID me, Collection<UUID> bookingIds) {
		List<Payment> list = payments.findByBookingIdIn(bookingIds).stream()
			.filter(p -> p.getPayerId().equals(me) || p.getDriverId().equals(me))
			.toList();
		Map<UUID, List<RefundView>> refundsBy = refundsOf(list.stream().map(Payment::getId).toList());
		return list.stream().map(p -> view(p, refundsBy.getOrDefault(p.getId(), List.of()))).toList();
	}

	/** What the passenger would get back by cancelling now. */
	@Transactional(readOnly = true)
	public RefundPreview refundPreview(UUID me, UUID bookingId) {
		Payment p = payments.findByBookingId(bookingId).filter(x -> x.getPayerId().equals(me)).orElse(null);
		if (p == null || !Payment.PAID.equals(p.getStatus()) || p.getOutcome() != null) {
			return new RefundPreview(false, 0, 0, 0, "You haven't paid anything online for this seat.");
		}
		BookingFacts f = rides.bookingFacts(List.of(bookingId)).get(bookingId);
		Split s = passengerCancels(p, f == null ? null : f.decidedAt(), f == null ? null : f.pickupAt(), clock.instant());
		long back = s.backOnline() + s.backCredits();
		return new RefundPreview(true, s.backOnline(), s.backCredits(), p.total() - back, s.reason());
	}

	// ----- After the trip (called by Settlement, each in its own transaction) -----

	/** Settles or refunds a paid booking once it's over. Does nothing while the trip is still on. */
	@Transactional
	public void settle(UUID paymentId) {
		Payment p = payments.lockById(paymentId).orElse(null);
		if (p == null || !Payment.PAID.equals(p.getStatus()) || p.getOutcome() != null) {
			return;
		}
		BookingFacts f = rides.bookingFacts(List.of(p.getBookingId())).get(p.getBookingId());
		Split s = f == null ? full(p, "The booking no longer exists") : split(p, f);
		if (s != null) {
			close(p, s, null);
		}
	}

	/** An unpaid checkout whose hold is over: gives back any credits it held. */
	@Transactional
	public void expireIfUnpaid(UUID paymentId) {
		Payment p = payments.lockById(paymentId).orElse(null);
		if (p == null || !Payment.CREATED.equals(p.getStatus())) {
			return;
		}
		BookingFacts f = rides.bookingFacts(List.of(p.getBookingId())).get(p.getBookingId());
		if (f != null && f.status() == BookingStatus.AWAITING_PAYMENT) {
			return; // The rides housekeeping hasn't released the seats yet; the member may still pay.
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

	/** The money arrived. If the seats are no longer held (it came too late), everything is refunded. */
	private void markPaid(Payment p, String gatewayPaymentId) {
		p.paid(gatewayPaymentId, clock.instant());
		ledger.post("PAYMENT", p.getId(), "Seat paid online", Line.debit(GATEWAY, p.getOnlinePaise()),
				Line.credit(ESCROW, p.getOnlinePaise()));
		if (!bookings.paymentReceived(p.getBookingId())) {
			close(p, full(p, "The payment arrived after the seat was released"), null);
		}
	}

	/** Money for a checkout we had already given up on: take it in, and send it straight back. */
	private void lateCapture(Payment p, String gatewayPaymentId) {
		p.paid(gatewayPaymentId, clock.instant());
		long online = p.getOnlinePaise();
		ledger.post("PAYMENT", p.getId(), "Late payment", Line.debit(GATEWAY, online), Line.credit(ESCROW, online));
		ledger.post("REFUND", p.getId(), "Late payment refunded", Line.debit(ESCROW, online), Line.credit(GATEWAY, online));
		refunds.save(new Refund(p.getId(), online, 0, "The payment arrived after the seat was released", null, clock.instant()));
		p.close(Payment.REFUNDED, clock.instant());
	}

	/** How the money of a finished booking is shared out. */
	record Split(long backOnline, long backCredits, long toDriver, long toFees, String reason) {
	}

	/** Null while the trip is still on. */
	Split split(Payment p, BookingFacts f) {
		return switch (f.status()) {
			case AWAITING_PAYMENT, REQUESTED, CONFIRMED -> null;
			case COMPLETED -> new Split(0, 0, p.getSeatPaise(), p.getFeePaise(), "Trip completed");
			case DECLINED -> full(p, "The driver declined the request");
			case EXPIRED -> full(p, "The request wasn't answered in time");
			case CANCELLED -> "PASSENGER".equals(f.cancelledBy())
					? passengerCancels(p, f.decidedAt(), f.pickupAt(), f.cancelledAt() == null ? clock.instant() : f.cancelledAt())
					: full(p, "The ride was cancelled");
		};
	}

	/** The passenger cancels at {@code when}: everything back in good time or before the seat was confirmed. */
	Split passengerCancels(Payment p, Instant confirmedAt, Instant pickupAt, Instant when) {
		Duration notice = pickupAt == null ? Duration.ZERO : Duration.between(when, pickupAt);
		if (confirmedAt == null) {
			return full(p, "Cancelled before the driver accepted");
		}
		if (notice.compareTo(Duration.ofHours(config.fullRefundHours())) >= 0) {
			return full(p, "Cancelled at least " + config.fullRefundHours() + " hours before pickup");
		}
		long back = p.getSeatPaise() * config.lateCancelRefundPercent() / 100;
		long backOnline = Math.min(back, p.getOnlinePaise());
		return new Split(backOnline, back - backOnline, p.getSeatPaise() - back, p.getFeePaise(),
				"Cancelled less than " + config.fullRefundHours() + " hours before pickup: " + config.lateCancelRefundPercent()
						+ "% of the seat price back, the rest to the driver");
	}

	static Split full(Payment p, String reason) {
		return new Split(p.getOnlinePaise(), p.getCreditsPaise(), 0, 0, reason);
	}

	/** Posts the closing journal, records any refund and closes the payment. */
	private void close(Payment p, Split s, UUID staffId) {
		long back = s.backOnline() + s.backCredits();
		boolean settled = back == 0;
		ledger.post(settled ? "SETTLE" : "REFUND", p.getId(), s.reason(), Line.debit(ESCROW, p.total()),
				Line.credit(GATEWAY, s.backOnline()), Line.credit(CREDITS, p.getPayerId(), s.backCredits()),
				Line.credit(EARNINGS, p.getDriverId(), s.toDriver()), Line.credit(FEES, s.toFees()));
		if (back > 0) {
			refunds.save(new Refund(p.getId(), s.backOnline(), s.backCredits(), s.reason(), staffId, clock.instant()));
		}
		p.close(settled ? Payment.SETTLED : back == p.total() ? Payment.REFUNDED : Payment.SPLIT, clock.instant());
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

	private CheckoutView checkoutView(Payment p, PendingBooking b, PaymentGateway gateway, String orderId) {
		MeResponse me = users.me(p.getPayerId());
		String name = ((me.firstName() == null ? "" : me.firstName()) + " " + (me.lastName() == null ? "" : me.lastName())).strip();
		String description = "Seat " + b.fromName() + " → " + b.toName() + ", " + WHEN.format(b.pickupAt());
		return new CheckoutView(p.getId(), p.getBookingId(), p.getStatus(), p.getSeatPaise(), p.getFeePaise(),
				p.getCreditsPaise(), p.getOnlinePaise(), p.getGateway(), gateway == null ? null : gateway.publicKey(), orderId,
				p.getHoldUntil(), description, new Prefill(name.isEmpty() ? null : name, me.email(), me.phone()));
	}

	PaymentView view(Payment p) {
		return view(p, refundsOf(List.of(p.getId())).getOrDefault(p.getId(), List.of()));
	}

	private PaymentView view(Payment p, List<RefundView> refundViews) {
		boolean refundedForLateness = refundViews.stream().anyMatch(r -> r.reason().startsWith("The payment arrived after"));
		return new PaymentView(p.getId(), p.getBookingId(), p.getStatus(), p.getOutcome(), p.getSeatPaise(), p.getFeePaise(),
				p.getCreditsPaise(), p.getOnlinePaise(), p.getGateway(), p.getHoldUntil(), p.getPaidAt(), !refundedForLateness,
				refundViews);
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
