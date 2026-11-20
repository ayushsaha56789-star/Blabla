package com.saathchalo.games;

import com.saathchalo.common.PersonalData;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Games played, ratings and in-game chat. On erasure the ratings and chat go, and the member's seats in past games
 * become "Former member", so the other players' history still adds up.
 */
@Component
class GamesPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	private final Clock clock;

	GamesPersonalData(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public String section() {
		return "games";
	}

	@Override
	public Object export(UUID memberId) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("ratings", PersonalData.rows(jdbc.queryForList(
				"SELECT game, rating, played, won FROM game_ratings WHERE user_id = ? ORDER BY game", memberId)));
		out.put("games", PersonalData.rows(jdbc.queryForList("""
				SELECT r.id, r.game, r.mode, r.status, r.result, p.outcome, p.rating_change AS "ratingChange", p.joined_at AS joined
				FROM game_players p JOIN game_rooms r ON r.id = p.room_id WHERE p.user_id = ? ORDER BY p.joined_at DESC""", memberId)));
		out.put("messages", PersonalData.rows(jdbc.queryForList(
				"SELECT room_id AS game, phrase, created_at AS \"at\" FROM game_messages WHERE user_id = ? ORDER BY id", memberId)));
		return out;
	}

	@Override
	public void erase(UUID memberId) {
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.update("""
				UPDATE game_rooms SET status = 'ABANDONED', result = 'The host left SaathChalo', finished_at = ?, updated_at = ?
				WHERE host_id = ? AND status = 'WAITING'""", now, now, memberId);
		jdbc.update("DELETE FROM game_messages WHERE user_id = ?", memberId);
		jdbc.update("DELETE FROM game_ratings WHERE user_id = ?", memberId);
		jdbc.update("UPDATE game_players SET user_id = NULL, name = 'Former member' WHERE user_id = ?", memberId);
	}
}
