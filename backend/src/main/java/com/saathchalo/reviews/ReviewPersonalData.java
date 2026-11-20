package com.saathchalo.reviews;

import com.saathchalo.common.PersonalData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reviews the member wrote and received. On erasure, reviews about the member are deleted; reviews they wrote stay
 * (they count towards other members' ratings) but show "Former member" as the author.
 */
@Component
class ReviewPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	ReviewPersonalData(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "reviews";
	}

	@Override
	public int order() {
		return 50;
	}

	@Override
	public Object export(UUID memberId) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("written", PersonalData.rows(jdbc.queryForList("""
				SELECT coalesce(u.first_name, 'Other member') AS "about", r.rating, r.comment, r.created_at AS written
				FROM reviews r LEFT JOIN users u ON u.id = r.subject_id
				WHERE r.author_id = ? ORDER BY r.created_at DESC""", memberId)));
		out.put("received", PersonalData.rows(jdbc.queryForList("""
				SELECT coalesce(u.first_name, 'Other member') AS "from", r.subject_role AS "youWere", r.rating, r.comment,
				       r.created_at AS written, r.hidden_at IS NOT NULL AS "removedByStaff"
				FROM reviews r LEFT JOIN users u ON u.id = r.author_id
				WHERE r.subject_id = ? ORDER BY r.created_at DESC""", memberId)));
		return out;
	}

	@Override
	public void erase(UUID memberId) {
		jdbc.update("DELETE FROM reviews WHERE subject_id = ?", memberId);
	}
}
