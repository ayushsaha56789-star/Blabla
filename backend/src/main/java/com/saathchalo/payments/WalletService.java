package com.saathchalo.payments;

import static com.saathchalo.payments.Ledger.Account.BANK;
import static com.saathchalo.payments.Ledger.Account.CREDITS;
import static com.saathchalo.payments.Ledger.Account.EARNINGS;
import static com.saathchalo.payments.Ledger.Account.GATEWAY;
import static com.saathchalo.payments.Ledger.Account.GOODWILL;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.payments.Ledger.Account;
import com.saathchalo.payments.Ledger.Line;
import com.saathchalo.payments.PaymentDtos.AdminPaymentRow;
import com.saathchalo.payments.PaymentDtos.AdminSummary;
import com.saathchalo.payments.PaymentDtos.OwedRow;
import com.saathchalo.payments.PaymentDtos.PayoutView;
import com.saathchalo.payments.PaymentDtos.RefundView;
import com.saathchalo.payments.PaymentDtos.WalletView;
import com.saathchalo.payments.PaymentGateway.GatewayException;
import com.saathchalo.payments.PaymentGateway.GatewayPayout;
import com.saathchalo.payments.internal.Payment;
import com.saathchalo.payments.internal.PaymentRepository;
import com.saathchalo.payments.internal.Payout;
import com.saathchalo.payments.internal.PayoutRepository;
import com.saathchalo.payments.internal.Refund;
import com.saathchalo.payments.internal.RefundRepository;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SaathChalo credits, drivers' earnings and payouts, and the staff's view of the money. */
@Service
public class WalletService {

	private final Ledger ledger;

	private final PaymentRepository payments;

	private final RefundRepository refunds;

	private final PayoutRepository payouts;

	private final MemberProfiles members;

	private final Map<String, PayableKind> kinds;

	private final PaymentsConfig.Gateways gateways;

	private final AuditService audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public WalletService(Ledger ledger, PaymentRepository payments, RefundRepository refunds, PayoutRepository payouts,
			MemberProfiles members, List<PayableKind> kinds, PaymentsConfig.Gateways gateways, AuditService audit,
			JdbcTemplate jdbc, Clock clock) {
		this.ledger = ledger;
		this.payments = payments;
		this.refunds = refunds;
		this.payouts = payouts;
		this.members = members;
		this.kinds = kinds.stream().collect(Collectors.toMap(PayableKind::kind, k -> k));
		this.gateways = gateways;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// ----- Members -----

	@Transactional(readOnly = true)
	public WalletView wallet(UUID me) {
		List<Payout> list = payouts.findByDriverIdOrderByCreatedAtDesc(me, PageRequest.of(0, 50));
		Long paidOut = jdbc.queryForObject("SELECT coalesce(sum(amount_paise), 0) FROM payouts WHERE driver_id = ? AND status = 'PAID'",
				Long.class, me);
		return new WalletView(ledger.credits(me), ledger.earnings(me), paidOut == null ? 0 : paidOut, ledger.history(me, 100),
				payoutViews(list));
	}

	// ----- Staff -----

	/** Goodwill credits, say after a bad experience. Recorded in the audit log. */
	@Transactional
	public WalletView grantCredits(UUID memberId, long paise, String reason, Actor actor) {
		if (members.card(memberId).id() == null) {
			throw ApiException.notFound("member_not_found", "We couldn't find that member.");
		}
		ledger.post("GOODWILL", null, reason.strip(), Line.debit(GOODWILL, paise), Line.credit(CREDITS, memberId, paise));
		audit.record(actor, "credits.granted", "member", memberId.toString(),
				"Gave " + rupees(paise) + " in SaathChalo credits: " + reason.strip(), null);
		return wallet(memberId);
	}

	/**
	 * Pays a driver everything they're owed: by a Razorpay payout link (they choose their own UPI ID or bank account
	 * on Razorpay's page, so we never see it), or by hand outside the app, recorded with a reference.
	 */
	@Transactional
	public PayoutView payout(UUID driverId, String method, String reference, Actor actor) {
		String how = method == null ? "" : method.toUpperCase(Locale.ROOT);
		if (!how.equals(Payout.LINK) && !how.equals(Payout.MANUAL)) {
			throw ApiException.badRequest("bad_method", "Choose a payout link or a payment made by hand.");
		}
		String ref = reference == null || reference.isBlank() ? null : reference.strip();
		if (how.equals(Payout.MANUAL) && ref == null) {
			throw ApiException.badRequest("reference_missing", "Add the UPI or bank reference, so the payment can be traced.");
		}
		List<Long> owedRows = jdbc.queryForList("SELECT earnings_paise FROM wallets WHERE member_id = ? FOR UPDATE", Long.class, driverId);
		long owed = owedRows.isEmpty() ? 0 : owedRows.getFirst();
		if (owed <= 0) {
			throw ApiException.conflict("nothing_owed", "This driver isn't owed anything right now.");
		}
		Instant now = clock.instant();
		Payout p = payouts.save(new Payout(driverId, owed, how, ref, actor.id(), now));
		ledger.post("PAYOUT", null, "Payout to the driver", Line.debit(EARNINGS, driverId, owed),
				Line.credit(how.equals(Payout.LINK) ? GATEWAY : BANK, owed));
		if (how.equals(Payout.LINK)) {
			PaymentGateway gateway = gateways.active().filter(PaymentGateway::canSendPayoutLinks)
				.orElseThrow(() -> ApiException.conflict("links_unavailable",
						"Payout links need a RazorpayX account. Pay the driver by hand and record it instead."));
			String phone = members.phone(driverId)
				.orElseThrow(() -> ApiException.conflict("no_phone", "This member has no phone number. Pay them by hand instead."));
			try {
				GatewayPayout link = gateway.payoutLink(members.card(driverId).displayName(), phone, owed, p.getId().toString(),
						"SaathChalo ride earnings");
				p.linkSent(link.id(), link.url());
				if (link.paid()) {
					p.paid(now);
				}
			}
			catch (GatewayException e) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "gateway_unavailable", e.getMessage());
			}
		}
		audit.record(actor, "payout.created", "member", driverId.toString(),
				"Paid out " + rupees(owed) + (how.equals(Payout.LINK) ? " by payout link" : " by hand"),
				ref == null ? null : Map.of("reference", ref));
		return payoutViews(List.of(p)).getFirst();
	}

	/** A payout link was used (the driver has the money) or not (the money stays owed to them). */
	@Transactional
	public void onPayoutLink(String gatewayPayoutId, boolean processed) {
		payouts.findByGatewayPayoutId(gatewayPayoutId).ifPresent(p -> {
			if (processed && !Payout.PAID.equals(p.getStatus())) {
				p.paid(clock.instant());
			}
			else if (!processed && Payout.SENT.equals(p.getStatus())) {
				p.cancelled();
				ledger.post("PAYOUT_CANCELLED", null, "Payout link not used", Line.debit(GATEWAY, p.getAmountPaise()),
						Line.credit(EARNINGS, p.getDriverId(), p.getAmountPaise()));
			}
		});
	}

	@Transactional(readOnly = true)
	public AdminSummary summary() {
		Map<Account, Long> b = ledger.balances();
		Long expectedEscrow = jdbc.queryForObject("""
				SELECT coalesce(sum(CASE WHEN status = 'CREATED' THEN credits_paise
				                         WHEN status = 'PAID' AND outcome IS NULL THEN seat_paise + fee_paise + deposit_paise
				                         ELSE 0 END), 0)
				FROM payments""", Long.class);
		return new AdminSummary(gateways.active().isPresent(), gateways.active().map(PaymentGateway::name).orElse(null),
				b.get(Account.ESCROW), b.get(Account.EARNINGS), b.get(Account.CREDITS), b.get(Account.FEES), b.get(Account.GOODWILL),
				refunds.countByStatus(Refund.PENDING), refunds.countByStatus(Refund.FAILED), ledger.journalsBalanced(),
				ledger.walletsMatch(), expectedEscrow != null && expectedEscrow.longValue() == b.get(Account.ESCROW));
	}

	/** {@code filter}: all (latest first) or problems (failed refunds and payments still open after the trip). */
	@Transactional(readOnly = true)
	public List<AdminPaymentRow> recent(String filter) {
		List<Payment> list;
		if ("problems".equals(filter)) {
			Set<UUID> ids = refunds.findByStatusOrderByCreatedAtAsc(Refund.FAILED, PageRequest.of(0, 200)).stream()
				.map(Refund::getPaymentId).collect(Collectors.toSet());
			list = payments.findAllById(ids);
		}
		else {
			list = payments.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100));
		}
		Set<UUID> people = new HashSet<>();
		list.forEach(p -> {
			people.add(p.getPayerId());
			people.add(p.getDriverId());
		});
		Map<UUID, MemberCard> cards = members.cards(people);
		Map<UUID, List<RefundView>> refundViews = refunds.findByPaymentIdInOrderByCreatedAtAsc(list.stream().map(Payment::getId).toList())
			.stream()
			.collect(Collectors.groupingBy(Refund::getPaymentId, Collectors.mapping(PaymentService::refundView, Collectors.toList())));
		return list.stream().map(p -> {
			PayableKind kind = kinds.get(p.getKind());
			PayableKind.Description d = kind == null ? null : kind.describe(p.getBookingId()).orElse(null);
			return new AdminPaymentRow(p.getId(), p.getBookingId(), p.getKind(), cards.getOrDefault(p.getPayerId(), MemberProfiles.deleted()),
					cards.getOrDefault(p.getDriverId(), MemberProfiles.deleted()), d == null ? null : d.route(),
					d == null ? null : d.startsAt(), p.getSeatPaise(), p.getFeePaise(), p.getDepositPaise(), p.getCreditsPaise(),
					p.getOnlinePaise(), p.getGateway(), p.getStatus(), p.getOutcome(), p.getCreatedAt(), p.getPaidAt(),
					refundViews.getOrDefault(p.getId(), List.of()));
		}).toList();
	}

	/** Drivers who are owed money, most first. */
	@Transactional(readOnly = true)
	public List<OwedRow> owed() {
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT w.member_id, w.earnings_paise,
				       (SELECT max(created_at) FROM payouts p WHERE p.driver_id = w.member_id) AS last_payout
				FROM wallets w WHERE w.earnings_paise > 0 ORDER BY w.earnings_paise DESC LIMIT 200""");
		Map<UUID, MemberCard> cards = members.cards(rows.stream().map(r -> (UUID) r.get("member_id")).toList());
		return rows.stream().map(r -> new OwedRow(cards.getOrDefault((UUID) r.get("member_id"), MemberProfiles.deleted()),
				((Number) r.get("earnings_paise")).longValue(),
				r.get("last_payout") == null ? null : ((Timestamp) r.get("last_payout")).toInstant())).toList();
	}

	@Transactional(readOnly = true)
	public List<PayoutView> recentPayouts() {
		return payoutViews(payouts.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)));
	}

	/** A member's balances and recent lines, for the console's member page. */
	@Transactional(readOnly = true)
	public WalletView memberWallet(UUID memberId) {
		return wallet(memberId);
	}

	private List<PayoutView> payoutViews(List<Payout> list) {
		Map<UUID, MemberCard> cards = members.cards(list.stream().map(Payout::getDriverId).toList());
		return list.stream().map(p -> new PayoutView(p.getId(), cards.getOrDefault(p.getDriverId(), MemberProfiles.deleted()),
				p.getAmountPaise(), p.getMethod(), p.getStatus(), p.getLinkUrl(), p.getReference(), p.getCreatedAt(), p.getPaidAt()))
			.toList();
	}

	static String rupees(long paise) {
		return paise % 100 == 0 ? "₹" + paise / 100 : String.format(Locale.ENGLISH, "₹%.2f", paise / 100.0);
	}
}
