package com.saathchalo.user;

import com.saathchalo.common.ApiException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What other parts of SaathChalo (rides now; chat and reviews later) may know about members. */
@Service
public class MemberProfiles {

	/** How a member appears next to a ride or a booking. Never a phone number, email or birth date. */
	public record MemberCard(UUID id, String displayName, String avatarUrl, boolean phoneVerified, boolean idVerified,
			Instant memberSince) {
	}

	/** The facts ride rules need about a member. */
	public record Traveller(UUID id, String displayName, Gender gender, boolean profileComplete) {

		public boolean isWoman() {
			return gender == Gender.FEMALE;
		}
	}

	private static final MemberCard DELETED = new MemberCard(null, "Deleted member", null, false, false, null);

	private final UserRepository users;

	public MemberProfiles(UserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public Map<UUID, MemberCard> cards(Collection<UUID> ids) {
		Map<UUID, MemberCard> cards = new LinkedHashMap<>();
		users.findAllById(ids.stream().distinct().toList()).forEach(u -> cards.put(u.getId(), card(u)));
		return cards;
	}

	@Transactional(readOnly = true)
	public MemberCard card(UUID id) {
		return users.findById(id).map(MemberProfiles::card).orElse(DELETED);
	}

	/** The member making a request. A token for a member who no longer exists means the session is stale. */
	@Transactional(readOnly = true)
	public Traveller traveller(UUID id) {
		return users.findById(id)
			.map(u -> new Traveller(u.getId(), u.displayName(), u.getGender(), u.isProfileComplete()))
			.orElseThrow(() -> ApiException.unauthorized("session_invalid", "Please log in again."));
	}

	@Transactional(readOnly = true)
	public Optional<Traveller> findTraveller(UUID id) {
		return users.findById(id).map(u -> new Traveller(u.getId(), u.displayName(), u.getGender(), u.isProfileComplete()));
	}

	/**
	 * A member's phone number. Only for sharing between a driver and a passenger whose booking is confirmed — the
	 * caller is responsible for that check.
	 */
	@Transactional(readOnly = true)
	public Optional<String> phone(UUID id) {
		return users.findById(id).map(User::getPhone);
	}

	public static MemberCard deleted() {
		return DELETED;
	}

	private static MemberCard card(User u) {
		return new MemberCard(u.getId(), u.displayName(), UserService.avatarUrl(u), u.isPhoneVerified(), u.isIdVerified(),
				u.getCreatedAt());
	}
}
