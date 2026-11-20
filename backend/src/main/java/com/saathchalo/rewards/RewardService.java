package com.saathchalo.rewards;

import com.saathchalo.audit.Actor;
import com.saathchalo.common.ApiException;
import com.saathchalo.features.FeatureService;
import com.saathchalo.payments.WalletService;
import com.saathchalo.rewards.RewardDtos.Board;
import com.saathchalo.rewards.RewardDtos.BoardRow;
import com.saathchalo.rewards.RewardDtos.Challenge;
import com.saathchalo.rewards.RewardDtos.Impact;
import com.saathchalo.rewards.RewardDtos.Leaderboard;
import com.saathchalo.rewards.RewardDtos.Referral;
import com.saathchalo.rewards.RewardDtos.RewardsView;
import com.saathchalo.rewards.RewardRules.Stats;
import com.saathchalo.rides.RideLookup;
import com.saathchalo.rides.RideLookup.SharedTrip;
import com.saathchalo.user.BlockService;
import com.saathchalo.user.MemberProfiles;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rewards worked out from completed trips (XP, level, badges, streak, challenge, CO₂), plus invite codes: when an
 * invited friend completes their first ride with someone other than the person who invited them, both get credits.
 */
@Service
public class RewardService {

	private static final Logger log = LoggerFactory.getLogger(RewardService.class);

	private static final String CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	/** Invite codes can be entered during a member's first two weeks. */
	static final Duration CLAIM_WITHIN = Duration.ofDays(14);

	private final RideLookup rides;

	private final MemberProfiles members;

	private final BlockService blocks;

	private final WalletService wallets;

	private final FeatureService features;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final long referralPaise;

	private final SecureRandom random = new SecureRandom();

	public RewardService(RideLookup rides, MemberProfiles members, BlockService blocks, WalletService wallets, FeatureService features,
			JdbcTemplate jdbc, Clock clock, @Value("${app.rewards.referral-credit-paise:5000}") long referralPaise) {
		this.rides = rides;
		this.members = members;
		this.blocks = blocks;
		this.wallets = wallets;
		this.features = features;
		this.jdbc = jdbc;
		this.clock = clock;
		this.referralPaise = referralPaise;
	}

	@Transactional
	public RewardsView me(UUID me) {
		Map<String, Object> profile = profile(me);
		ZonedDateTime now = clock.instant().atZone(RewardRules.INDIA);
		Stats s = RewardRules.stats(rides.sharedTrips(me), now);
		int invited = count("SELECT count(*) FROM reward_profiles WHERE referred_by = ?", me);
		int rewarded = count("SELECT count(*) FROM reward_profiles WHERE referred_by = ? AND referral_status = 'REWARDED'", me);
		int xp = RewardRules.xp(s, rewarded);
		int level = RewardRules.level(xp);
		boolean last = level + 1 >= RewardRules.LEVELS.length;
		boolean invitedByFriend = profile.get("referred_by") != null;
		boolean canClaim = !invitedByFriend && features.isOnFor("rewards.referrals", me)
				&& members.card(me).memberSince().isAfter(clock.instant().minus(CLAIM_WITHIN));
		String month = now.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
		return new RewardsView(xp, level + 1, RewardRules.LEVEL_NAMES[level], RewardRules.LEVELS[level],
				last ? null : RewardRules.LEVELS[level + 1], last ? null : RewardRules.LEVEL_NAMES[level + 1],
				new Impact(s.trips(), s.driven(), s.km(), s.people(), s.rupees(), s.co2Kg()), s.streak(), s.bestStreak(),
				new Challenge("Share " + RewardRules.CHALLENGE_TRIPS + " rides in " + month, Math.min(s.monthTrips(), RewardRules.CHALLENGE_TRIPS),
						RewardRules.CHALLENGE_TRIPS, s.monthTrips() >= RewardRules.CHALLENGE_TRIPS),
				RewardRules.badges(s, rewarded),
				new Referral((String) profile.get("referral_code"), invited, rewarded, (int) (referralPaise / 100), canClaim, invitedByFriend,
						(String) profile.get("referral_status")),
				new Board(Boolean.TRUE.equals(profile.get("on_board")), (String) profile.get("nickname")));
	}

	/** Enters a friend's invite code. Both get credits once this member completes a first ride with someone else. */
	@Transactional
	public RewardsView claim(UUID me, String code) {
		Map<String, Object> profile = profile(me);
		if (profile.get("referred_by") != null) {
			throw ApiException.conflict("already_invited", "You've already used an invite code.");
		}
		if (!members.card(me).memberSince().isAfter(clock.instant().minus(CLAIM_WITHIN))) {
			throw ApiException.conflict("too_late", "Invite codes work in your first two weeks on SaathChalo.");
		}
		List<UUID> owner = jdbc.queryForList("SELECT user_id FROM reward_profiles WHERE referral_code = ?", UUID.class,
				code.strip().toUpperCase(Locale.ROOT));
		if (owner.isEmpty()) {
			throw ApiException.notFound("invite_not_found", "We don't know that invite code. Check it with your friend.");
		}
		UUID friend = owner.getFirst();
		if (friend.equals(me)) {
			throw ApiException.badRequest("own_code", "That's your own code. Share it with friends instead!");
		}
		if (blocks.blockedEitherWay(me, friend)) {
			throw ApiException.forbidden("blocked", "You can't use this invite code.");
		}
		jdbc.update("UPDATE reward_profiles SET referred_by = ?, referral_status = 'PENDING', referred_at = ? WHERE user_id = ?", friend,
				Timestamp.from(clock.instant()), me);
		return me(me);
	}

	/** Shows the member on the eco leaderboard, by nickname only. */
	@Transactional
	public RewardsView board(UUID me, boolean on, String nickname) {
		profile(me);
		String name = nickname == null ? null : nickname.strip().replaceAll("\\s+", " ");
		if (on) {
			if (name == null || name.length() < 3) {
				throw ApiException.badRequest("nickname_missing", "Pick a nickname of at least 3 characters.");
			}
			if (!name.matches("[\\p{L}\\p{N} ._-]{3,24}") || name.matches(".*\\d{5,}.*")) {
				throw ApiException.badRequest("bad_nickname", "Use letters, numbers, spaces, dots or dashes (and no phone numbers).");
			}
		}
		jdbc.update("UPDATE reward_profiles SET on_board = ?, nickname = ? WHERE user_id = ?", on, name == null || name.isEmpty() ? null : name, me);
		return me(me);
	}

	/** This month's eco champions: CO₂ saved by members who chose to be shown, by nickname. */
	@Transactional(readOnly = true)
	public Leaderboard leaderboard(UUID me) {
		ZonedDateTime now = clock.instant().atZone(RewardRules.INDIA);
		YearMonth month = YearMonth.from(now);
		Instant since = month.atDay(1).atStartOfDay(RewardRules.INDIA).toInstant();
		List<BoardRow> rows = new ArrayList<>();
		for (Map<String, Object> p : jdbc.queryForList("SELECT user_id, nickname FROM reward_profiles WHERE on_board LIMIT 500")) {
			UUID id = (UUID) p.get("user_id");
			List<SharedTrip> trips = rides.sharedTrips(id).stream().filter(t -> !t.departureAt().isBefore(since)).toList();
			int pkm = trips.stream().mapToInt(SharedTrip::passengerKm).sum();
			if (!trips.isEmpty()) {
				rows.add(new BoardRow(0, (String) p.get("nickname"), Math.round(pkm * RewardRules.CO2_KG_PER_KM * 10) / 10.0, trips.size(),
						id.equals(me)));
			}
		}
		rows.sort(Comparator.comparingDouble(BoardRow::co2Kg).reversed());
		List<BoardRow> ranked = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			BoardRow r = rows.get(i);
			ranked.add(new BoardRow(i + 1, r.nickname(), r.co2Kg(), r.trips(), r.you()));
		}
		BoardRow mine = ranked.stream().filter(BoardRow::you).findFirst().orElse(null);
		return new Leaderboard(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
				ranked.subList(0, Math.min(20, ranked.size())), mine);
	}

	/** Invited friends whose reward hasn't been paid yet: [friend, inviter]. */
	List<UUID[]> pendingReferrals() {
		return jdbc.query("SELECT user_id, referred_by FROM reward_profiles WHERE referral_status = 'PENDING' LIMIT 500",
				(rs, n) -> new UUID[] { rs.getObject("user_id", UUID.class), rs.getObject("referred_by", UUID.class) });
	}

	/** Pays both sides of a referral once the friend has completed a ride with someone other than the inviter. */
	@Transactional
	public boolean settle(UUID friend, UUID inviter) {
		if (!features.isOnFor("rewards.referrals", friend)) {
			return false;
		}
		boolean rode = rides.sharedTrips(friend).stream().anyMatch(t -> !t.mates().contains(inviter));
		if (!rode) {
			return false;
		}
		int updated = jdbc.update("""
				UPDATE reward_profiles SET referral_status = 'REWARDED', rewarded_at = ? WHERE user_id = ? AND referral_status = 'PENDING'""",
				Timestamp.from(clock.instant()), friend);
		if (updated == 0) {
			return false;
		}
		wallets.grantCredits(friend, referralPaise, "Welcome reward: you joined with a friend's invite", Actor.SYSTEM);
		wallets.grantCredits(inviter, referralPaise, "Invite reward: your friend took their first ride", Actor.SYSTEM);
		log.info("Referral rewarded: member {} invited by {}", friend, inviter);
		return true;
	}

	/** The member's rewards row, made on first visit with an invite code like "PRIYA7K2Q". */
	private Map<String, Object> profile(UUID me) {
		List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM reward_profiles WHERE user_id = ?", me);
		if (!rows.isEmpty()) {
			return rows.getFirst();
		}
		String base = members.card(me).displayName().replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
		base = base.isEmpty() ? "SAATH" : base.substring(0, Math.min(6, base.length()));
		for (int attempt = 0; attempt < 20; attempt++) {
			StringBuilder code = new StringBuilder(base);
			for (int i = 0; i < 4; i++) {
				code.append(CODE_LETTERS.charAt(random.nextInt(CODE_LETTERS.length())));
			}
			int added = jdbc.update("""
					INSERT INTO reward_profiles (user_id, referral_code, created_at) VALUES (?, ?, ?)
					ON CONFLICT DO NOTHING""", me, code.toString(), Timestamp.from(clock.instant()));
			if (added == 1 || !jdbc.queryForList("SELECT 1 FROM reward_profiles WHERE user_id = ?", me).isEmpty()) {
				return jdbc.queryForList("SELECT * FROM reward_profiles WHERE user_id = ?", me).getFirst();
			}
		}
		throw new IllegalStateException("Could not make an invite code");
	}

	private int count(String sql, UUID id) {
		Integer n = jdbc.queryForObject(sql, Integer.class, id);
		return n == null ? 0 : n;
	}
}
