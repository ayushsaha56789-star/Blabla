package com.saathchalo.chat;

import com.saathchalo.common.PersonalData;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Messages in the member's ride chats. On erasure the member's own messages are deleted; the other person keeps
 * theirs (chats disappear six months after the ride anyway).
 */
@Component
class ChatPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	ChatPersonalData(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "chats";
	}

	@Override
	public int order() {
		return 40;
	}

	@Override
	public Object export(UUID memberId) {
		return PersonalData.rows(jdbc.queryForList("""
				SELECT c.ride_id AS "rideId",
				       CASE WHEN m.sender_id = ? THEN 'you' ELSE coalesce(u.first_name, 'Other member') END AS "from",
				       m.body, m.created_at AS sent
				FROM chat_messages m
				JOIN conversations c ON c.id = m.conversation_id
				LEFT JOIN users u ON u.id = m.sender_id
				WHERE (c.driver_id = ? OR c.passenger_id = ?) AND m.hidden_at IS NULL
				ORDER BY c.ride_id, m.created_at""", memberId, memberId, memberId));
	}

	@Override
	public void erase(UUID memberId) {
		jdbc.update("DELETE FROM chat_messages WHERE sender_id = ?", memberId);
	}
}
