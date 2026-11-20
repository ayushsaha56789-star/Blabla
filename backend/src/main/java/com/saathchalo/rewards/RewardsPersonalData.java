package com.saathchalo.rewards;

import com.saathchalo.common.PersonalData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The member's invite code, who invited them, and their leaderboard nickname. XP and badges aren't stored. */
@Component
class RewardsPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	RewardsPersonalData(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public String section() {
		return "rewards";
	}

	@Override
	public Object export(UUID memberId) {
		List<Map<String, Object>> rows = PersonalData.rows(jdbc.queryForList("""
				SELECT referral_code AS "inviteCode", referred_at AS "joinedWithInviteAt", referral_status AS "inviteReward",
				       on_board AS "onEcoLeaderboard", nickname, created_at AS "since"
				FROM reward_profiles WHERE user_id = ?""", memberId));
		Integer invited = jdbc.queryForObject("SELECT count(*) FROM reward_profiles WHERE referred_by = ?", Integer.class, memberId);
		return Map.of("profile", rows.isEmpty() ? Map.of() : rows.getFirst(), "friendsInvited", invited == null ? 0 : invited);
	}

	@Override
	public void erase(UUID memberId) {
		jdbc.update("UPDATE reward_profiles SET referred_by = NULL WHERE referred_by = ?", memberId);
		jdbc.update("DELETE FROM reward_profiles WHERE user_id = ?", memberId);
	}
}
