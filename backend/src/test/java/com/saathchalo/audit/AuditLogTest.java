package com.saathchalo.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saathchalo.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

class AuditLogTest extends IntegrationTest {

	@Autowired
	private AuditService audit;

	@Test
	void entriesCannotBeChangedOrRemoved() {
		audit.record(new Actor(UUID.randomUUID(), "Test"), "test.entry", "test", "1", "A test entry", Map.of("n", 1));
		assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET summary = 'changed' WHERE action = 'test.entry'"))
			.isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log WHERE action = 'test.entry'"))
			.isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_log"))
			.isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
	}

	@Test
	void theHashChainRevealsAForgedEntry() {
		audit.record(Actor.SYSTEM, "test.chain", "test", "2", "Before the forgery", null);
		assertThat(audit.verifyChain().intact()).isTrue();

		// Someone with direct database access slips in a fake entry.
		Long forged = jdbc.queryForObject("""
				INSERT INTO audit_log (occurred_at, actor_label, action, summary, prev_hash, hash)
				VALUES (now(), 'Nobody', 'staff.added', 'Fake entry', (SELECT hash FROM audit_log ORDER BY id DESC LIMIT 1),
				        repeat('a', 64)) RETURNING id""", Long.class);
		ChainCheck check = audit.verifyChain();
		assertThat(check.intact()).isFalse();
		assertThat(check.firstBrokenId()).isEqualTo(forged);

		// Clean up so other tests see an intact chain (only the table owner can bypass the triggers).
		jdbc.execute("ALTER TABLE audit_log DISABLE TRIGGER audit_log_no_update_or_delete");
		try {
			jdbc.update("DELETE FROM audit_log WHERE id = ?", forged);
		}
		finally {
			jdbc.execute("ALTER TABLE audit_log ENABLE TRIGGER audit_log_no_update_or_delete");
		}
		assertThat(audit.verifyChain().intact()).isTrue();
	}
}
