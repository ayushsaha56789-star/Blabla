package com.saathchalo.user;

import com.saathchalo.common.ApiException;
import com.saathchalo.user.RatingProvider.Rating;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What other parts of SaathChalo (rides now; chat and reviews later) may know about members. */
@Service
public class MemberProfiles {

	/**
	 * How a member appears next to a ride or a booking. Never a phone number, email or birth date. {@code rating} is
	 * null until someone has reviewed them.
	 */
	public record MemberCard(UUID id, String displayName, String avatarUrl, boolean phoneVerified, boolean idVerified,
			Instant memberSince, Double rating, int reviewCount) {
	}

	/** The facts ride rules need about a member. */
	public record Traveller(UUID id, String displayName, Gender gender, boolean profileComplete) {

		public boolean isWoman() {
			return gender == Gender.FEMALE;
		}
	}

	private static final MemberCard DELETED = new MemberCard(null, "Deleted member", null, false, false, null, null, 0);

	private final UserRepository users;

	private final ObjectProvider<RatingProvider> ratings;

	public MemberProfiles(UserRepository users, ObjectProvider<RatingProvider> ratings) {
		this.users = users;
		this.ratings = ratings;
	}

	@Transactional(readOnly = true)
	public Map<UUID, MemberCard> cards(Collection<UUID> ids) {
		List<UUID> distinct = ids.stream().distinct().toList();
		Map<UUID, Rating> stars = ratingsOf(distinct);
		Map<UUID, MemberCard> cards = new LinkedHashMap<>();
		users.findAllById(distinct).forEach(u -> cards.put(u.getId(), card(u, stars.get(u.getId()))));
		return cards;
	}

	@Transactional(readOnly = true)
	public MemberCard card(UUID id) {
		return users.findById(id).map(u -> card(u, ratingsOf(List.of(id)).get(id))).orElse(DELETED);
	}

	/** Ratings for these members (empty when reviews aren't available). */
	public Map<UUID, Rating> ratingsOf(Collection<UUID> ids) {
		RatingProvider provider = ratings.getIfAvailable();
		return provider == null || ids.isEmpty() ? Map.of() : provider.ratings(ids);
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

	/** Several members at once (for filters such as "women drivers only"). */
	@Transactional(readOnly = true)
	public Map<UUID, Traveller> travellers(Collection<UUID> ids) {
		Map<UUID, Traveller> out = new LinkedHashMap<>();
		users.findAllById(ids.stream().distinct().toList())
			.forEach(u -> out.put(u.getId(), new Traveller(u.getId(), u.displayName(), u.getGender(), u.isProfileComplete())));
		return out;
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

	private static MemberCard card(User u, Rating rating) {
		return new MemberCard(u.getId(), u.displayName(), UserService.avatarUrl(u), u.isPhoneVerified(), u.isIdVerified(),
				u.getCreatedAt(), rating == null ? null : rating.average(), rating == null ? 0 : rating.count());
	}
}
