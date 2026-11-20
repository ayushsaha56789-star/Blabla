package com.saathchalo.payments;

import com.saathchalo.common.PersonalData;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The double-entry ledger. Every money movement is one journal whose debits equal its credits. Members' credits and
 * earnings are their own accounts; their balances are also kept in {@code wallets}, which can never go below zero
 * (a database check), so nobody can spend money they don't have.
 */
@Component
class Ledger {

	/**
	 * GATEWAY: money at Razorpay. BANK: money paid out by hand. ESCROW: held for trips not yet over. CREDITS: members'
	 * SaathChalo credits. EARNINGS: owed to drivers. FEES: SaathChalo's service fees. GOODWILL: credits given away.
	 */
	enum Account {
		GATEWAY, BANK, ESCROW, CREDITS, EARNINGS, FEES, GOODWILL
	}

	record Line(Account account, UUID member, long debit, long credit) {

		static Line debit(Account account, long paise) {
			return new Line(account, null, paise, 0);
		}

		static Line credit(Account account, long paise) {
			return new Line(account, null, 0, paise);
		}

		static Line debit(Account account, UUID member, long paise) {
			return new Line(account, member, paise, 0);
		}

		static Line credit(Account account, UUID member, long paise) {
			return new Line(account, member, 0, paise);
		}
	}

	private final JdbcTemplate jdbc;

	private final Clock clock;

	Ledger(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Records one balanced journal. Zero lines are skipped. Must run inside the caller's transaction. */
	void post(String kind, UUID paymentId, String memo, Line... lines) {
		long debits = 0;
		long credits = 0;
		for (Line l : lines) {
			if (l.debit() < 0 || l.credit() < 0) {
				throw new IllegalArgumentException("Ledger amounts can't be negative");
			}
			debits += l.debit();
			credits += l.credit();
		}
		if (debits != credits) {
			throw new IllegalStateException("Unbalanced " + kind + " journal: " + debits + " vs " + credits);
		}
		if (debits == 0) {
			return;
		}
		UUID journal = UUID.randomUUID();
		Timestamp now = Timestamp.from(clock.instant());
		for (Line l : lines) {
			if (l.debit() == 0 && l.credit() == 0) {
				continue;
			}
			jdbc.update("""
					INSERT INTO ledger_entries (journal_id, kind, account, member_id, debit_paise, credit_paise, payment_id, memo, created_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", journal, kind, l.account().name(), l.member(), l.debit(), l.credit(),
					paymentId, memo, now);
			if (l.member() != null) {
				long delta = l.credit() - l.debit();
				boolean credit = l.account() == Account.CREDITS;
				// Create the wallet first, then change it: the "never below zero" check applies to the result.
				jdbc.update("INSERT INTO wallets (member_id, credits_paise, earnings_paise, updated_at) VALUES (?, 0, 0, ?) "
						+ "ON CONFLICT (member_id) DO NOTHING", l.member(), now);
				jdbc.update("UPDATE wallets SET credits_paise = credits_paise + ?, earnings_paise = earnings_paise + ?, updated_at = ? "
						+ "WHERE member_id = ?", credit ? delta : 0, credit ? 0 : delta, now, l.member());
			}
		}
	}

	/** The member's credits, locking their wallet until the transaction ends (so two checkouts can't spend them twice). */
	long lockCredits(UUID member) {
		List<Long> rows = jdbc.queryForList("SELECT credits_paise FROM wallets WHERE member_id = ? FOR UPDATE", Long.class, member);
		return rows.isEmpty() ? 0 : rows.getFirst();
	}

	long credits(UUID member) {
		List<Long> rows = jdbc.queryForList("SELECT credits_paise FROM wallets WHERE member_id = ?", Long.class, member);
		return rows.isEmpty() ? 0 : rows.getFirst();
	}

	long earnings(UUID member) {
		List<Long> rows = jdbc.queryForList("SELECT earnings_paise FROM wallets WHERE member_id = ?", Long.class, member);
		return rows.isEmpty() ? 0 : rows.getFirst();
	}

	/** Each account's balance, positive for money held or owed (debit balance for assets, credit balance otherwise). */
	Map<Account, Long> balances() {
		Map<Account, Long> out = new EnumMap<>(Account.class);
		for (Account a : Account.values()) {
			out.put(a, 0L);
		}
		jdbc.query("SELECT account, sum(debit_paise) - sum(credit_paise) AS net FROM ledger_entries GROUP BY account", rs -> {
			Account a = Account.valueOf(rs.getString("account"));
			long net = rs.getLong("net");
			boolean asset = a == Account.GATEWAY || a == Account.BANK || a == Account.GOODWILL;
			out.put(a, asset ? net : -net);
		});
		return out;
	}

	/** True when every journal balances (it always should; this is the console's health check). */
	boolean journalsBalanced() {
		Long bad = jdbc.queryForObject("""
				SELECT count(*) FROM (SELECT journal_id FROM ledger_entries GROUP BY journal_id
				                      HAVING sum(debit_paise) <> sum(credit_paise)) j""", Long.class);
		return bad != null && bad == 0;
	}

	/** True when the wallets agree with the ledger. */
	boolean walletsMatch() {
		Long bad = jdbc.queryForObject("""
				SELECT count(*) FROM wallets w
				LEFT JOIN (SELECT member_id,
				                  sum(CASE WHEN account = 'CREDITS' THEN credit_paise - debit_paise ELSE 0 END) AS credits,
				                  sum(CASE WHEN account = 'EARNINGS' THEN credit_paise - debit_paise ELSE 0 END) AS earnings
				           FROM ledger_entries WHERE member_id IS NOT NULL GROUP BY member_id) l ON l.member_id = w.member_id
				WHERE w.credits_paise <> coalesce(l.credits, 0) OR w.earnings_paise <> coalesce(l.earnings, 0)""", Long.class);
		return bad != null && bad == 0;
	}

	/** The member's own lines (credits and earnings), newest first. */
	List<Map<String, Object>> history(UUID member, int limit) {
		return PersonalData.rows(jdbc.queryForList("""
				SELECT created_at AS "at", kind, account, credit_paise - debit_paise AS "amountPaise", memo, payment_id AS "paymentId"
				FROM ledger_entries WHERE member_id = ? ORDER BY id DESC LIMIT ?""", member, limit));
	}
}
