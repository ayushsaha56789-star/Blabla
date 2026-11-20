package com.saathchalo.user;

import com.saathchalo.common.PersonalData;
import com.saathchalo.files.AvatarStorage;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The profile, the members this member blocked, and any restrictions staff placed on the account. */
@Component
class UserPersonalData implements PersonalData {

	private final UserRepository users;

	private final AvatarStorage avatars;

	private final MemberAccessService access;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	UserPersonalData(UserRepository users, AvatarStorage avatars, MemberAccessService access, JdbcTemplate jdbc, Clock clock) {
		this.users = users;
		this.avatars = avatars;
		this.access = access;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public String section() {
		return "profile";
	}

	/** First in the file, and erased last (other modules may still look the member up while erasing). */
	@Override
	public int order() {
		return 0;
	}

	@Override
	public Object export(UUID memberId) {
		Map<String, Object> out = new LinkedHashMap<>();
		users.findById(memberId).ifPresent(u -> {
			out.put("phone", u.getPhone());
			out.put("firstName", u.getFirstName());
			out.put("lastName", u.getLastName());
			out.put("email", u.getEmail());
			out.put("gender", u.getGender());
			out.put("dateOfBirth", u.getDateOfBirth() == null ? null : u.getDateOfBirth().toString());
			out.put("city", u.getCity());
			out.put("bio", u.getBio());
			out.put("hasPhoto", u.getAvatarPath() != null);
			out.put("chattiness", u.getPrefChat());
			out.put("musicOk", u.isPrefMusic());
			out.put("smokingOk", u.isPrefSmoking());
			out.put("petsOk", u.isPrefPets());
			out.put("phoneVerified", u.isPhoneVerified());
			out.put("idVerified", u.isIdVerified());
			out.put("memberSince", u.getCreatedAt().toString());
		});
		out.put("blockedMembers", PersonalData.rows(jdbc.queryForList("""
				SELECT coalesce(u.first_name, 'Former member') AS member, b.created_at AS since
				FROM blocks b LEFT JOIN users u ON u.id = b.blocked_id
				WHERE b.blocker_id = ? ORDER BY b.created_at DESC""", memberId)));
		out.put("restrictionsByStaff", PersonalData.rows(jdbc.queryForList("""
				SELECT area, reason, starts_at AS starts, ends_at AS ends, lifted_at AS lifted
				FROM member_restrictions WHERE user_id = ? ORDER BY starts_at DESC""", memberId)));
		return out;
	}

	@Override
	public void erase(UUID memberId) {
		jdbc.update("DELETE FROM blocks WHERE blocker_id = ? OR blocked_id = ?", memberId, memberId);
		users.findById(memberId).ifPresent(u -> {
			avatars.deleteQuietly(u.getAvatarPath());
			u.erase(clock.instant());
		});
		access.evict(memberId);
	}
}
