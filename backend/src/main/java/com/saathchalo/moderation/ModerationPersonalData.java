package com.saathchalo.moderation;

import com.saathchalo.common.PersonalData;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reports the member made. Reports other members made about them are left out, to protect the people who made
 * them. Moderation records stay after erasure; they no longer point to a name or phone number.
 */
@Component
class ModerationPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	ModerationPersonalData(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "reportsYouMade";
	}

	@Override
	public int order() {
		return 70;
	}

	@Override
	public Object export(UUID memberId) {
		return PersonalData.rows(jdbc.queryForList("""
				SELECT target_type AS "about", reason, details, status, created_at AS made, resolved_at AS closed
				FROM reports WHERE reporter_id = ? ORDER BY created_at DESC""", memberId));
	}

	@Override
	public void erase(UUID memberId) {
		// Nothing to delete: see the class comment.
	}
}
