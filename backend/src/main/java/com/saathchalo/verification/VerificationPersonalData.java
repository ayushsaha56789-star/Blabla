package com.saathchalo.verification;

import com.saathchalo.common.PersonalData;
import com.saathchalo.files.DocumentVault;
import com.saathchalo.verification.internal.IdCheck;
import com.saathchalo.verification.internal.IdCheckRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** ID checks (the decisions, not the photos) and pending email codes. Photos and rows are deleted on erasure. */
@Component
class VerificationPersonalData implements PersonalData {

	private final IdCheckRepository checks;

	private final DocumentVault vault;

	private final JdbcTemplate jdbc;

	VerificationPersonalData(IdCheckRepository checks, DocumentVault vault, JdbcTemplate jdbc) {
		this.checks = checks;
		this.vault = vault;
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "idChecks";
	}

	@Override
	public int order() {
		return 90;
	}

	@Override
	public Object export(UUID memberId) {
		return checks.findByUserIdOrderBySubmittedAtDesc(memberId).stream().map(c -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("document", c.getDocumentType());
			row.put("status", c.getStatus());
			row.put("sent", c.getSubmittedAt().toString());
			row.put("decided", c.getDecidedAt() == null ? null : c.getDecidedAt().toString());
			row.put("reason", c.getRejectReason());
			row.put("note", c.getRejectNote());
			row.put("photoStillKept", c.getFileName() != null);
			return row;
		}).toList();
	}

	@Override
	public void erase(UUID memberId) {
		checks.findByUserIdOrderBySubmittedAtDesc(memberId).stream().map(IdCheck::getFileName).forEach(vault::deleteQuietly);
		jdbc.update("DELETE FROM id_checks WHERE user_id = ?", memberId);
		jdbc.update("DELETE FROM email_codes WHERE user_id = ?", memberId);
	}
}
