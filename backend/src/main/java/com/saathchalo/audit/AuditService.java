package com.saathchalo.audit;

import com.saathchalo.common.Crypto;
import com.saathchalo.common.PageResult;
import com.saathchalo.common.RequestInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Tamper-evident record of sensitive actions. Rows are append-only (enforced by database triggers) and each row
 * stores {@code hash = SHA-256(previous hash | this row's fields)}, so editing or inserting rows out of band breaks
 * the chain and {@link #verifyChain()} reports where.
 */
@Service
public class AuditService {

	static final String GENESIS = "0".repeat(64);

	/** Serialises writers so every row sees the true previous hash. */
	private static final long CHAIN_LOCK = 7_710_001L;

	private static final char SEP = '\u001F';

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final Clock clock;

	public AuditService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	/** Records an action as part of the caller's transaction: if the action is rolled back, so is the record. */
	@Transactional
	public void record(Actor actor, String action, String targetType, String targetId, String summary,
			Map<String, ?> details) {
		insert(actor, action, targetType, targetId, summary, details);
	}

	/** Records an action in its own transaction, so it is kept even when the caller fails (e.g. a wrong code). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordSeparately(Actor actor, String action, String targetType, String targetId, String summary,
			Map<String, ?> details) {
		insert(actor, action, targetType, targetId, summary, details);
	}

	@Transactional(readOnly = true)
	public PageResult<AuditEntryView> search(AuditFilter filter, int page, int size) {
		int pageSize = PageResult.clampSize(size);
		int pageNo = Math.max(0, page);
		StringBuilder where = new StringBuilder(" WHERE 1=1");
		List<Object> args = new ArrayList<>();
		if (filter.action() != null && !filter.action().isBlank()) {
			if (filter.action().endsWith(".")) {
				where.append(" AND action LIKE ?");
				args.add(filter.action() + "%");
			}
			else {
				where.append(" AND action = ?");
				args.add(filter.action());
			}
		}
		if (filter.actorId() != null) {
			where.append(" AND actor_id = ?");
			args.add(filter.actorId());
		}
		if (filter.targetType() != null && !filter.targetType().isBlank()) {
			where.append(" AND target_type = ?");
			args.add(filter.targetType());
		}
		if (filter.targetId() != null && !filter.targetId().isBlank()) {
			where.append(" AND target_id = ?");
			args.add(filter.targetId());
		}
		if (filter.text() != null && !filter.text().isBlank()) {
			where.append(" AND (summary ILIKE ? OR actor_label ILIKE ?)");
			String like = "%" + filter.text().strip().replace("%", "\\%").replace("_", "\\_") + "%";
			args.add(like);
			args.add(like);
		}
		Long total = jdbc.queryForObject("SELECT count(*) FROM audit_log" + where, Long.class, args.toArray());
		List<Object> pageArgs = new ArrayList<>(args);
		pageArgs.add(pageSize);
		pageArgs.add((long) pageNo * pageSize);
		List<AuditEntryView> items = jdbc.query("SELECT * FROM audit_log" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
				(rs, n) -> view(rs), pageArgs.toArray());
		return new PageResult<>(items, total == null ? 0 : total, pageNo, pageSize);
	}

	@Transactional(readOnly = true)
	public ChainCheck verifyChain() {
		String[] previous = { GENESIS };
		long[] checked = { 0 };
		Long[] broken = { null };
		jdbc.query("SELECT * FROM audit_log ORDER BY id", rs -> {
			if (broken[0] != null) {
				return;
			}
			checked[0]++;
			String expected = hash(previous[0], rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
					(UUID) rs.getObject("actor_id"), rs.getString("actor_label"), rs.getString("action"),
					rs.getString("target_type"), rs.getString("target_id"), rs.getString("summary"),
					rs.getString("details"), rs.getString("ip"), rs.getString("request_id"));
			if (!previous[0].equals(rs.getString("prev_hash")) || !expected.equals(rs.getString("hash"))) {
				broken[0] = rs.getLong("id");
				return;
			}
			previous[0] = rs.getString("hash");
		});
		return new ChainCheck(broken[0] == null, checked[0], broken[0]);
	}

	private void insert(Actor actor, String action, String targetType, String targetId, String summary,
			Map<String, ?> details) {
		Actor who = actor == null ? Actor.SYSTEM : actor;
		String label = truncate(who.label() == null ? "Unknown" : who.label(), 120);
		String text = truncate(summary, 500);
		String detailsJson = details == null || details.isEmpty() ? null : json.writeValueAsString(new TreeMap<>(details));
		Instant at = clock.instant().truncatedTo(ChronoUnit.MICROS);
		String ip = RequestInfo.clientIp();
		String requestId = RequestInfo.requestId();

		jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null, CHAIN_LOCK);
		String previous = jdbc.query("SELECT hash FROM audit_log ORDER BY id DESC LIMIT 1",
				rs -> rs.next() ? rs.getString(1) : GENESIS);
		String hash = hash(previous, at, who.id(), label, action, targetType, targetId, text, detailsJson, ip, requestId);
		jdbc.update("""
				INSERT INTO audit_log (occurred_at, actor_id, actor_label, action, target_type, target_id, summary,
				                       details, ip, request_id, prev_hash, hash)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
				at.atOffset(java.time.ZoneOffset.UTC), who.id(), label, action, targetType, targetId, text, detailsJson,
				ip, requestId, previous, hash);
	}

	static String hash(String previous, Instant at, UUID actorId, String actorLabel, String action, String targetType,
			String targetId, String summary, String details, String ip, String requestId) {
		StringBuilder sb = new StringBuilder(previous);
		for (Object field : new Object[] { at, actorId, actorLabel, action, targetType, targetId, summary, details, ip,
				requestId }) {
			sb.append(SEP).append(field == null ? "" : field.toString());
		}
		return Crypto.sha256Hex(sb.toString());
	}

	private static AuditEntryView view(ResultSet rs) throws SQLException {
		return new AuditEntryView(rs.getLong("id"), rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
				(UUID) rs.getObject("actor_id"), rs.getString("actor_label"), rs.getString("action"),
				rs.getString("target_type"), rs.getString("target_id"), rs.getString("summary"), rs.getString("details"),
				rs.getString("ip"), rs.getString("request_id"));
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}
}
