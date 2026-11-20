package com.saathchalo.community;

import com.saathchalo.common.PersonalData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Squads joined, interests for vibe match. On erasure the member leaves every squad and their interests go. */
@Component
class CommunityPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	private final CommunityService community;

	CommunityPersonalData(JdbcTemplate jdbc, CommunityService community) {
		this.jdbc = jdbc;
		this.community = community;
	}

	@Override
	public String section() {
		return "community";
	}

	@Override
	public Object export(UUID memberId) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("squads", PersonalData.rows(jdbc.queryForList("""
				SELECT s.name, s.kind, m.role, m.joined_at AS joined FROM squad_members m JOIN squads s ON s.id = m.squad_id
				WHERE m.user_id = ? ORDER BY m.joined_at""", memberId)));
		out.put("interests", jdbc.queryForList("SELECT interests FROM vibe_profiles WHERE user_id = ?", String.class, memberId));
		return out;
	}

	@Override
	public void erase(UUID memberId) {
		List<UUID> squads = jdbc.queryForList("SELECT squad_id FROM squad_members WHERE user_id = ?", UUID.class, memberId);
		squads.forEach(id -> community.leaveSquad(memberId, id));
		jdbc.update("DELETE FROM vibe_profiles WHERE user_id = ?", memberId);
		jdbc.update("UPDATE events SET created_by = NULL WHERE created_by = ?", memberId);
	}
}
