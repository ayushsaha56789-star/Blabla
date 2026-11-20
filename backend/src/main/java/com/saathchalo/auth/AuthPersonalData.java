package com.saathchalo.auth;

import com.saathchalo.common.PersonalData;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Login sessions: when each one started and ended. Never the tokens themselves. */
@Component
class AuthPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	AuthPersonalData(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "logins";
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public Object export(UUID memberId) {
		return PersonalData.rows(jdbc.queryForList("""
				SELECT created_at AS started, expires_at AS expires, revoked_at AS ended
				FROM refresh_tokens WHERE user_id = ? ORDER BY created_at DESC""", memberId));
	}

	@Override
	public void erase(UUID memberId) {
		jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", memberId);
		jdbc.update("DELETE FROM staff_sessions WHERE user_id = ?", memberId);
		jdbc.update("DELETE FROM staff_recovery_codes WHERE user_id = ?", memberId);
		jdbc.update("DELETE FROM staff_totp WHERE user_id = ?", memberId);
	}
}
