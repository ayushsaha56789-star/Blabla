package com.saathchalo.support;

import com.saathchalo.common.PersonalData;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Help requests and the conversation with support. Deleted on erasure. */
@Component
class SupportPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	SupportPersonalData(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "helpRequests";
	}

	@Override
	public int order() {
		return 80;
	}

	@Override
	public Object export(UUID memberId) {
		return PersonalData.rows(jdbc.queryForList("""
				SELECT t.subject, t.category, t.status, t.created_at AS opened,
				       CASE WHEN m.from_staff THEN 'SaathChalo support' ELSE 'you' END AS "from", m.body, m.created_at AS sent
				FROM support_tickets t JOIN ticket_messages m ON m.ticket_id = t.id
				WHERE t.user_id = ? ORDER BY t.created_at DESC, m.created_at""", memberId));
	}

	@Override
	public void erase(UUID memberId) {
		jdbc.update("DELETE FROM support_tickets WHERE user_id = ?", memberId);
	}
}
