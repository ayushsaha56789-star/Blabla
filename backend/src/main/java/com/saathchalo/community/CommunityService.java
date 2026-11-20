package com.saathchalo.community;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.community.CommunityDtos.EventCard;
import com.saathchalo.community.CommunityDtos.EventRequest;
import com.saathchalo.community.CommunityDtos.EventView;
import com.saathchalo.community.CommunityDtos.RideCard;
import com.saathchalo.community.CommunityDtos.SquadCard;
import com.saathchalo.community.CommunityDtos.SquadKind;
import com.saathchalo.community.CommunityDtos.SquadRequest;
import com.saathchalo.community.CommunityDtos.SquadView;
import com.saathchalo.community.CommunityDtos.VibeView;
import com.saathchalo.geo.Place;
import com.saathchalo.geo.PlaceDirectory;
import com.saathchalo.rides.RideLookup;
import com.saathchalo.rides.RideLookup.RideBrief;
import com.saathchalo.user.ChatPreference;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import com.saathchalo.user.MemberProfiles.Preferences;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Squads (private groups that see each other's rides), events with the rides going to them, and vibe match (a score
 * from travel preferences and shared interests; the answers themselves are never shown to others).
 */
@Service
public class CommunityService {

	static final int MAX_SQUADS = 10;

	private static final String CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private final MemberProfiles members;

	private final RideLookup rides;

	private final PlaceDirectory places;

	private final AuditService audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	public CommunityService(MemberProfiles members, RideLookup rides, PlaceDirectory places, AuditService audit, JdbcTemplate jdbc,
			Clock clock) {
		this.members = members;
		this.rides = rides;
		this.places = places;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// ----- Squads -----

	@Transactional(readOnly = true)
	public List<SquadCard> mySquads(UUID me) {
		return jdbc.query("""
				SELECT s.id, s.name, s.kind, s.email_domain, s.code, s.owner_id,
				       (SELECT count(*) FROM squad_members x WHERE x.squad_id = s.id) AS members
				FROM squads s JOIN squad_members m ON m.squad_id = s.id WHERE m.user_id = ? ORDER BY s.name""",
				(rs, n) -> new SquadCard(rs.getObject("id", UUID.class), rs.getString("name"), SquadKind.valueOf(rs.getString("kind")),
						rs.getString("email_domain"), rs.getInt("members"), rs.getString("code"), me.equals(rs.getObject("owner_id", UUID.class))),
				me);
	}

	@Transactional
	public SquadView createSquad(UUID me, SquadRequest req) {
		if (mySquads(me).size() >= MAX_SQUADS) {
			throw ApiException.conflict("too_many_squads", "You can be in up to " + MAX_SQUADS + " squads.");
		}
		String domain = cleanDomain(req.emailDomain());
		if (domain != null) {
			requireEmailAt(me, domain);
		}
		UUID id = UUID.randomUUID();
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.update("INSERT INTO squads (id, name, kind, email_domain, code, owner_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", id,
				req.name().strip(), req.kind().name(), domain, newCode(), me, now);
		jdbc.update("INSERT INTO squad_members (squad_id, user_id, role, joined_at) VALUES (?, ?, 'OWNER', ?)", id, me, now);
		return squad(me, id);
	}

	@Transactional
	public SquadView joinSquad(UUID me, String code) {
		List<Map<String, Object>> found = jdbc.queryForList("SELECT id, email_domain FROM squads WHERE code = ?",
				code.strip().toUpperCase(Locale.ROOT));
		if (found.isEmpty()) {
			throw ApiException.notFound("squad_not_found", "There's no squad with that code. Check it with your friend.");
		}
		UUID id = (UUID) found.getFirst().get("id");
		String domain = (String) found.getFirst().get("email_domain");
		if (isMember(id, me)) {
			return squad(me, id);
		}
		if (mySquads(me).size() >= MAX_SQUADS) {
			throw ApiException.conflict("too_many_squads", "You can be in up to " + MAX_SQUADS + " squads.");
		}
		if (domain != null) {
			requireEmailAt(me, domain);
		}
		jdbc.update("INSERT INTO squad_members (squad_id, user_id, role, joined_at) VALUES (?, ?, 'MEMBER', ?) ON CONFLICT DO NOTHING", id,
				me, Timestamp.from(clock.instant()));
		return squad(me, id);
	}

	@Transactional(readOnly = true)
	public SquadView squad(UUID me, UUID id) {
		if (!isMember(id, me)) {
			throw ApiException.notFound("squad_not_found", "We couldn't find that squad.");
		}
		SquadCard card = mySquads(me).stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
		List<UUID> memberIds = jdbc.queryForList("SELECT user_id FROM squad_members WHERE squad_id = ? ORDER BY joined_at", UUID.class, id);
		Map<UUID, MemberCard> cards = members.cards(memberIds);
		List<MemberCard> people = memberIds.stream().map(m -> cards.getOrDefault(m, MemberProfiles.deleted())).toList();
		return new SquadView(card, people, rideCards(rides.upcomingByDrivers(memberIds, 20)));
	}

	/** Leaving; when the owner leaves, the longest-standing member takes over (an empty squad is deleted). */
	@Transactional
	public void leaveSquad(UUID me, UUID id) {
		if (!isMember(id, me)) {
			throw ApiException.notFound("squad_not_found", "We couldn't find that squad.");
		}
		jdbc.update("DELETE FROM squad_members WHERE squad_id = ? AND user_id = ?", id, me);
		List<UUID> left = jdbc.queryForList("SELECT user_id FROM squad_members WHERE squad_id = ? ORDER BY joined_at", UUID.class, id);
		if (left.isEmpty()) {
			jdbc.update("DELETE FROM squads WHERE id = ?", id);
		}
		else {
			Integer owners = jdbc.queryForObject("SELECT count(*) FROM squad_members WHERE squad_id = ? AND role = 'OWNER'", Integer.class, id);
			if (owners == null || owners == 0) {
				jdbc.update("UPDATE squad_members SET role = 'OWNER' WHERE squad_id = ? AND user_id = ?", id, left.getFirst());
				jdbc.update("UPDATE squads SET owner_id = ? WHERE id = ?", left.getFirst(), id);
			}
		}
	}

	// ----- Events -----

	@Transactional(readOnly = true)
	public List<EventCard> upcomingEvents() {
		return jdbc.query("SELECT * FROM events WHERE status = 'LIVE' AND starts_at > ? ORDER BY starts_at LIMIT 50", (rs, n) -> eventCard(rs),
				Timestamp.from(clock.instant().minus(Duration.ofHours(6))));
	}

	@Transactional(readOnly = true)
	public EventView event(UUID id) {
		List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM events WHERE id = ?", id);
		if (rows.isEmpty()) {
			throw ApiException.notFound("event_not_found", "We couldn't find that event.");
		}
		Map<String, Object> e = rows.getFirst();
		Instant starts = ((Timestamp) e.get("starts_at")).toInstant();
		Instant from = starts.minus(Duration.ofHours(48));
		Instant now = clock.instant();
		List<RideBrief> going = "LIVE".equals(e.get("status")) && starts.isAfter(now)
				? rides.goingNear((double) e.get("lat"), (double) e.get("lng"), 25, from.isAfter(now) ? from : now, starts)
				: List.of();
		return new EventView(eventCard(e), (String) e.get("description"), rideCards(going));
	}

	@Transactional(readOnly = true)
	public List<EventCard> allEvents() {
		return jdbc.query("SELECT * FROM events ORDER BY starts_at DESC LIMIT 200", (rs, n) -> eventCard(rs));
	}

	@Transactional
	public EventCard createEvent(EventRequest req, Actor actor) {
		Place place = place(req.placeId());
		UUID id = UUID.randomUUID();
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.update("""
				INSERT INTO events (id, title, venue, place_id, place_name, state, lat, lng, starts_at, description, status, created_by,
				                    created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'LIVE', ?, ?, ?)""", id, req.title().strip(), req.venue().strip(), place.id(), place.name(),
				place.state(), place.lat(), place.lng(), Timestamp.from(req.startsAt()), blankToNull(req.description()), actor.id(), now, now);
		audit.record(actor, "event.created", "event", id.toString(), "Added the event " + req.title().strip(), null);
		return one(id);
	}

	@Transactional
	public EventCard updateEvent(UUID id, EventRequest req, Actor actor) {
		Place place = place(req.placeId());
		int n = jdbc.update("""
				UPDATE events SET title = ?, venue = ?, place_id = ?, place_name = ?, state = ?, lat = ?, lng = ?, starts_at = ?,
				                  description = ?, updated_at = ? WHERE id = ?""", req.title().strip(), req.venue().strip(), place.id(),
				place.name(), place.state(), place.lat(), place.lng(), Timestamp.from(req.startsAt()), blankToNull(req.description()),
				Timestamp.from(clock.instant()), id);
		if (n == 0) {
			throw ApiException.notFound("event_not_found", "We couldn't find that event.");
		}
		audit.record(actor, "event.updated", "event", id.toString(), "Changed the event " + req.title().strip(), null);
		return one(id);
	}

	@Transactional
	public EventCard cancelEvent(UUID id, Actor actor) {
		if (jdbc.update("UPDATE events SET status = 'CANCELLED', updated_at = ? WHERE id = ?", Timestamp.from(clock.instant()), id) == 0) {
			throw ApiException.notFound("event_not_found", "We couldn't find that event.");
		}
		audit.record(actor, "event.cancelled", "event", id.toString(), "Cancelled an event", null);
		return one(id);
	}

	// ----- Vibe match -----

	@Transactional(readOnly = true)
	public VibeView vibe(UUID me) {
		return new VibeView(List.copyOf(interests(List.of(me)).getOrDefault(me, Set.of())), CommunityDtos.INTERESTS);
	}

	@Transactional
	public VibeView saveInterests(UUID me, List<String> chosen) {
		List<String> clean = chosen.stream().filter(CommunityDtos.INTERESTS::contains).distinct().limit(8).toList();
		jdbc.update("""
				INSERT INTO vibe_profiles (user_id, interests, updated_at) VALUES (?, ?, ?)
				ON CONFLICT (user_id) DO UPDATE SET interests = EXCLUDED.interests, updated_at = EXCLUDED.updated_at""", me,
				String.join(",", clean), Timestamp.from(clock.instant()));
		return vibe(me);
	}

	/** How well the member would get on with each of these people, 5–99. */
	@Transactional(readOnly = true)
	public Map<UUID, Integer> scores(UUID me, Collection<UUID> others) {
		List<UUID> ids = others.stream().filter(o -> !o.equals(me)).distinct().limit(60).toList();
		Map<UUID, Integer> out = new HashMap<>();
		if (ids.isEmpty()) {
			return out;
		}
		List<UUID> everyone = new java.util.ArrayList<>(ids);
		everyone.add(me);
		Map<UUID, Preferences> prefs = members.preferences(everyone);
		Map<UUID, Set<String>> likes = interests(everyone);
		Preferences mine = prefs.get(me);
		if (mine == null) {
			return out;
		}
		for (UUID other : ids) {
			Preferences theirs = prefs.get(other);
			if (theirs != null) {
				out.put(other, score(mine, theirs, likes.getOrDefault(me, Set.of()), likes.getOrDefault(other, Set.of())));
			}
		}
		return out;
	}

	/** Same chat style and music, and the same on smoking and pets, plus shared interests. */
	static int score(Preferences a, Preferences b, Set<String> likesA, Set<String> likesB) {
		int s = 50;
		if (a.chat() == b.chat()) {
			s += 15;
		}
		else if ((a.chat() == ChatPreference.QUIET && b.chat() == ChatPreference.CHATTY)
				|| (a.chat() == ChatPreference.CHATTY && b.chat() == ChatPreference.QUIET)) {
			s -= 15;
		}
		s += a.music() == b.music() ? 10 : -5;
		s += a.smoking() == b.smoking() ? 5 : -20;
		s += a.pets() == b.pets() ? 3 : -5;
		long shared = likesA.stream().filter(likesB::contains).count();
		s += (int) Math.min(24, shared * 8);
		return Math.max(5, Math.min(99, s));
	}

	// ----- Inside -----

	private Map<UUID, Set<String>> interests(Collection<UUID> ids) {
		Map<UUID, Set<String>> out = new HashMap<>();
		jdbc.query("SELECT user_id, interests FROM vibe_profiles WHERE user_id = ANY (?)", rs -> {
			String csv = rs.getString("interests");
			out.put(rs.getObject("user_id", UUID.class), csv.isBlank() ? Set.of() : new LinkedHashSet<>(List.of(csv.split(","))));
		}, (Object) ids.toArray(new UUID[0]));
		return out;
	}

	private List<RideCard> rideCards(List<RideBrief> list) {
		Map<UUID, MemberCard> drivers = members.cards(list.stream().map(RideBrief::driverId).toList());
		return list.stream()
			.map(r -> new RideCard(r.id(), r.fromName(), r.toName(), r.departureAt(), r.ladiesOnly(),
					drivers.getOrDefault(r.driverId(), MemberProfiles.deleted())))
			.toList();
	}

	private boolean isMember(UUID squad, UUID me) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM squad_members WHERE squad_id = ? AND user_id = ?)",
				Boolean.class, squad, me));
	}

	private void requireEmailAt(UUID me, String domain) {
		String email = members.verifiedEmail(me).map(e -> e.toLowerCase(Locale.ROOT)).orElse(null);
		if (email == null || !(email.endsWith("@" + domain) || email.endsWith("." + domain))) {
			throw ApiException.forbidden("email_domain",
					"This squad is for people with a confirmed @" + domain + " email. Add and confirm it on your profile first.");
		}
	}

	/** "@IITD.ac.in " → "iitd.ac.in"; empty → null. */
	static String cleanDomain(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String d = raw.strip().toLowerCase(Locale.ROOT).replaceFirst("^@", "");
		if (!d.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+")) {
			throw ApiException.badRequest("bad_domain", "Write the email domain like iitd.ac.in or infosys.com.");
		}
		if (Set.of("gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "icloud.com", "rediffmail.com").contains(d)) {
			throw ApiException.badRequest("public_domain", "Use a college or company domain, not a public one like " + d + ".");
		}
		return d;
	}

	private Place place(long placeId) {
		return places.find(placeId).orElseThrow(() -> ApiException.badRequest("place_not_found", "Choose the town from the list."));
	}

	private EventCard one(UUID id) {
		return jdbc.query("SELECT * FROM events WHERE id = ?", (rs, n) -> eventCard(rs), id).getFirst();
	}

	private static EventCard eventCard(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new EventCard(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("venue"), rs.getLong("place_id"),
				rs.getString("place_name"), rs.getString("state"), rs.getTimestamp("starts_at").toInstant(), rs.getString("status"));
	}

	private static EventCard eventCard(Map<String, Object> e) {
		return new EventCard((UUID) e.get("id"), (String) e.get("title"), (String) e.get("venue"), (long) e.get("place_id"),
				(String) e.get("place_name"), (String) e.get("state"), ((Timestamp) e.get("starts_at")).toInstant(), (String) e.get("status"));
	}

	private String newCode() {
		for (int attempt = 0; attempt < 20; attempt++) {
			StringBuilder s = new StringBuilder();
			for (int i = 0; i < 6; i++) {
				s.append(CODE_LETTERS.charAt(random.nextInt(CODE_LETTERS.length())));
			}
			if (jdbc.queryForList("SELECT 1 FROM squads WHERE code = ?", s.toString()).isEmpty()) {
				return s.toString();
			}
		}
		throw new IllegalStateException("Could not find a free squad code");
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}
}
