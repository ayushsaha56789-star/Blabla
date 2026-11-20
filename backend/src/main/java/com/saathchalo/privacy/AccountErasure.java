package com.saathchalo.privacy;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.FieldEncryptor;
import com.saathchalo.common.PersonalData;
import com.saathchalo.user.UserService;
import com.saathchalo.user.UserService.RegistrationRecord;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Erases accounts whose 30 days to change their mind are over. Each module removes or anonymises its own data (see
 * {@link PersonalData}); the sign-up details are kept sealed for 180 days, as India's IT Rules 2021 ask, then removed.
 */
@Component
public class AccountErasure {

	private static final Logger log = LoggerFactory.getLogger(AccountErasure.class);

	static final Duration KEEP_SIGNUP_DETAILS = Duration.ofDays(180);

	private final UserService users;

	/** Profile last: other modules may still look the member up while they erase. */
	private final List<PersonalData> sections;

	private final JdbcTemplate jdbc;

	private final FieldEncryptor encryptor;

	private final ObjectMapper json;

	private final AuditService audit;

	private final TransactionTemplate tx;

	private final Clock clock;

	public AccountErasure(UserService users, List<PersonalData> sections, JdbcTemplate jdbc, FieldEncryptor encryptor,
			ObjectMapper json, AuditService audit, PlatformTransactionManager transactions, Clock clock) {
		this.users = users;
		this.sections = sections.stream().sorted(Comparator.comparingInt(PersonalData::order).reversed()).toList();
		this.jdbc = jdbc;
		this.encryptor = encryptor;
		this.json = json;
		this.audit = audit;
		this.tx = new TransactionTemplate(transactions);
		this.clock = clock;
	}

	@Scheduled(fixedDelay = 3_600_000, initialDelay = 600_000)
	public void run() {
		eraseDueAccounts();
		int purged = jdbc.update("DELETE FROM closed_accounts WHERE purge_after < ?", Timestamp.from(clock.instant()));
		if (purged > 0) {
			log.info("Removed the sealed sign-up details of {} accounts closed more than 180 days ago", purged);
		}
	}

	/** Each account in its own transaction, so one failure doesn't hold up the rest. */
	public int eraseDueAccounts() {
		int erased = 0;
		for (UUID id : users.dueForErasure(100)) {
			try {
				tx.executeWithoutResult(status -> erase(id));
				erased++;
			} catch (RuntimeException e) {
				log.error("Couldn't erase account {}; will try again next hour", id, e);
			}
		}
		if (erased > 0) {
			log.info("Erased {} accounts whose 30 days were over", erased);
		}
		return erased;
	}

	private void erase(UUID id) {
		Instant now = clock.instant();
		users.registrationRecord(id).ifPresent(r -> seal(id, r, now));
		sections.forEach(s -> s.erase(id));
		audit.record(Actor.SYSTEM, "member.erased", "member", id.toString(),
				"Erased an account 30 days after the member asked to delete it", null);
	}

	private void seal(UUID id, RegistrationRecord r, Instant now) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("phone", r.phone());
		details.put("firstName", r.firstName());
		details.put("lastName", r.lastName());
		details.put("email", r.email());
		jdbc.update("""
				INSERT INTO closed_accounts (user_id, details_enc, joined_at, closed_at, purge_after) VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (user_id) DO NOTHING""",
				id, encryptor.encrypt(json.writeValueAsString(details)), Timestamp.from(r.joinedAt()), Timestamp.from(now),
				Timestamp.from(now.plus(KEEP_SIGNUP_DETAILS)));
	}
}
