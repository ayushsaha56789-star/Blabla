package com.saathchalo.payments;

import static com.saathchalo.payments.Ledger.Account.CREDITS;
import static com.saathchalo.payments.Ledger.Account.GOODWILL;

import com.saathchalo.common.PersonalData;
import com.saathchalo.payments.Ledger.Line;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Payments, refunds, credits, earnings and payouts. Money records are kept after an account is erased (tax law asks
 * for about 8 years); they point to an anonymised account. Leftover credits are forfeited, as the terms say.
 */
@Component
class PaymentsPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	private final Ledger ledger;

	PaymentsPersonalData(JdbcTemplate jdbc, Ledger ledger) {
		this.jdbc = jdbc;
		this.ledger = ledger;
	}

	@Override
	public String section() {
		return "money";
	}

	@Override
	public int order() {
		return 35;
	}

	@Override
	public Object export(UUID memberId) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("creditsPaise", ledger.credits(memberId));
		out.put("earningsPaise", ledger.earnings(memberId));
		out.put("payments", PersonalData.rows(jdbc.queryForList("""
				SELECT booking_id AS "bookingId", seat_paise AS "seatPaise", fee_paise AS "feePaise",
				       credits_paise AS "creditsPaise", online_paise AS "onlinePaise", status, outcome,
				       created_at AS created, paid_at AS paid
				FROM payments WHERE payer_id = ? ORDER BY created_at DESC""", memberId)));
		out.put("refunds", PersonalData.rows(jdbc.queryForList("""
				SELECT r.online_paise AS "onlinePaise", r.credits_paise AS "creditsPaise", r.status, r.reason,
				       r.created_at AS created, r.processed_at AS processed
				FROM refunds r JOIN payments p ON p.id = r.payment_id WHERE p.payer_id = ? ORDER BY r.created_at DESC""", memberId)));
		out.put("creditsAndEarnings", ledger.history(memberId, 1000));
		out.put("payouts", PersonalData.rows(jdbc.queryForList("""
				SELECT amount_paise AS "amountPaise", method, status, reference, created_at AS created, paid_at AS paid
				FROM payouts WHERE driver_id = ? ORDER BY created_at DESC""", memberId)));
		return out;
	}

	@Override
	public String leavingBlocker(UUID memberId) {
		long owed = ledger.earnings(memberId);
		return owed > 0 ? "You have " + WalletService.rupees(owed) + " in ride earnings waiting. Ask us for your payout "
				+ "(Help centre → \"Money or prices\"), then delete your account." : null;
	}

	@Override
	public void erase(UUID memberId) {
		long credits = ledger.credits(memberId);
		ledger.post("CREDITS_FORFEITED", null, "Account deleted", Line.debit(CREDITS, memberId, credits),
				Line.credit(GOODWILL, credits));
	}
}
