package com.saathchalo.user;

import com.saathchalo.config.AccountStatus;
import com.saathchalo.config.AccountStatusPort;
import com.saathchalo.features.AudienceProfile;
import com.saathchalo.features.AudienceProfileProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Answers "may this member use SaathChalo, and which features apply to them?" for every request. Results are cached
 * for a few seconds and dropped immediately when staff change a restriction.
 */
@Service
public class MemberAccessService implements AccountStatusPort, AudienceProfileProvider {

	private static final Duration CACHE_FOR = Duration.ofSeconds(30);

	private record Snapshot(AccountStatus status, AudienceProfile profile, Instant expiresAt) {
	}

	private final UserRepository users;

	private final MemberRestrictionRepository restrictions;

	private final Clock clock;

	private final Map<UUID, Snapshot> cache = new ConcurrentHashMap<>();

	public MemberAccessService(UserRepository users, MemberRestrictionRepository restrictions, Clock clock) {
		this.users = users;
		this.restrictions = restrictions;
		this.clock = clock;
	}

	@Override
	public AccountStatus statusOf(UUID userId) {
		return snapshot(userId).status();
	}

	/**
	 * Always read fresh: gender and city change when members edit their profile, and a stale value would show them
	 * the wrong features. (Only the "is this account suspended?" answer is cached; staff changes evict it.)
	 */
	@Override
	public AudienceProfile profileOf(UUID userId) {
		return load(userId, clock.instant()).profile();
	}

	public void evict(UUID userId) {
		cache.remove(userId);
	}

	private Snapshot snapshot(UUID userId) {
		Instant now = clock.instant();
		Snapshot cached = cache.get(userId);
		if (cached != null && cached.expiresAt().isAfter(now)) {
			return cached;
		}
		Snapshot fresh = load(userId, now);
		if (cache.size() > 50_000) {
			cache.clear();
		}
		cache.put(userId, fresh);
		return fresh;
	}

	private Snapshot load(UUID userId, Instant now) {
		List<MemberRestriction> active = restrictions.findUnlifted(userId).stream().filter(r -> r.isActive(now)).toList();
		AccountStatus status = active.stream()
			.filter(r -> r.getArea().equals(RestrictionArea.ALL.key()))
			.max(Comparator.comparing(r -> r.getEndsAt() == null ? Instant.MAX : r.getEndsAt()))
			.map(r -> new AccountStatus(true, r.getReason(), r.getEndsAt()))
			.orElse(AccountStatus.ACTIVE);
		Set<String> areas = new LinkedHashSet<>();
		active.forEach(r -> areas.add(r.getArea()));
		AudienceProfile profile = users.findById(userId)
			.map(u -> new AudienceProfile(u.getId(), u.getGender() == null ? null : u.getGender().name(), u.isIdVerified(),
					u.isBetaTester(), u.getCity(), Set.copyOf(areas)))
			.orElse(AudienceProfile.ANONYMOUS);
		return new Snapshot(status, profile, now.plus(CACHE_FOR));
	}
}
