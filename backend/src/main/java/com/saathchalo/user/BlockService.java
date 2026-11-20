package com.saathchalo.user;

import com.saathchalo.common.ApiException;
import com.saathchalo.user.MemberProfiles.MemberCard;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Members can block each other. Blocking works both ways: neither can message the other or book the other's rides,
 * and their rides don't show up in each other's searches. The blocked member isn't told.
 */
@Service
public class BlockService {

	private final JdbcTemplate jdbc;

	private final UserRepository users;

	private final MemberProfiles members;

	private final Clock clock;

	public BlockService(JdbcTemplate jdbc, UserRepository users, MemberProfiles members, Clock clock) {
		this.jdbc = jdbc;
		this.users = users;
		this.members = members;
		this.clock = clock;
	}

	@Transactional
	public void block(UUID me, UUID other) {
		if (me.equals(other)) {
			throw ApiException.badRequest("self_block", "You can't block yourself.");
		}
		if (!users.existsById(other)) {
			throw ApiException.notFound("user_not_found", "We couldn't find that member.");
		}
		jdbc.update("INSERT INTO blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", me, other,
				Timestamp.from(clock.instant()));
	}

	@Transactional
	public void unblock(UUID me, UUID other) {
		jdbc.update("DELETE FROM blocks WHERE blocker_id = ? AND blocked_id = ?", me, other);
	}

	/** True if either member has blocked the other. */
	@Transactional(readOnly = true)
	public boolean blockedEitherWay(UUID a, UUID b) {
		if (a == null || b == null) {
			return false;
		}
		Boolean found = jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM blocks WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?))
				""", Boolean.class, a, b, b, a);
		return Boolean.TRUE.equals(found);
	}

	/** Everyone this member blocked, or who blocked them (for filtering search results). */
	@Transactional(readOnly = true)
	public Set<UUID> blockedEitherWay(UUID me) {
		if (me == null) {
			return Set.of();
		}
		return new HashSet<>(jdbc.queryForList("""
				SELECT blocked_id FROM blocks WHERE blocker_id = ?
				UNION SELECT blocker_id FROM blocks WHERE blocked_id = ?
				""", UUID.class, me, me));
	}

	/** The members this member has blocked (they can unblock them). */
	@Transactional(readOnly = true)
	public List<MemberCard> myBlocks(UUID me) {
		List<UUID> ids = jdbc.queryForList("SELECT blocked_id FROM blocks WHERE blocker_id = ? ORDER BY created_at DESC", UUID.class, me);
		Map<UUID, MemberCard> cards = members.cards(ids);
		return ids.stream().map(id -> cards.getOrDefault(id, MemberProfiles.deleted())).toList();
	}
}
