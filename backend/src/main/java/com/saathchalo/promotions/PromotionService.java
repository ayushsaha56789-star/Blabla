package com.saathchalo.promotions;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.files.PromoImageStorage;
import com.saathchalo.files.PublicImages;
import com.saathchalo.geo.Place;
import com.saathchalo.geo.PlaceDirectory;
import com.saathchalo.notifications.Notice;
import com.saathchalo.notifications.Notifier;
import com.saathchalo.promotions.PromotionDtos.AdminPromotionRow;
import com.saathchalo.promotions.PromotionDtos.Category;
import com.saathchalo.promotions.PromotionDtos.City;
import com.saathchalo.promotions.PromotionDtos.MyPromotion;
import com.saathchalo.promotions.PromotionDtos.Options;
import com.saathchalo.promotions.PromotionDtos.PromoCard;
import com.saathchalo.promotions.PromotionDtos.PromotionRequest;
import com.saathchalo.promotions.PromotionDtos.Slot;
import com.saathchalo.user.BlockService;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Members' promotions: create, check automatically, send to a moderator, run between two dates in chosen towns (or
 * all of India), always labelled "Sponsored", at most three times a day per member, never in chats. Owners see only
 * totals (views, clicks), never who saw them. Free during the launch offer ({@code app.promotions.free-until}).
 */
@Service
public class PromotionService {

	static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

	private static final Set<String> ACTIVE = Set.of("IN_REVIEW", "APPROVED");

	private final JdbcTemplate jdbc;

	private final MemberProfiles members;

	private final BlockService blocks;

	private final PlaceDirectory places;

	private final PromoImageStorage images;

	private final AuditService audit;

	private final Clock clock;

	private final Notifier notifier;

	private final LocalDate freeUntil;

	public PromotionService(JdbcTemplate jdbc, MemberProfiles members, BlockService blocks, PlaceDirectory places,
			PromoImageStorage images, AuditService audit, Clock clock, @Value("${app.promotions.free-until:}") String freeUntil,
			Notifier notifier) {
		this.notifier = notifier;
		this.jdbc = jdbc;
		this.members = members;
		this.blocks = blocks;
		this.places = places;
		this.images = images;
		this.audit = audit;
		this.clock = clock;
		this.freeUntil = freeUntil == null || freeUntil.isBlank() ? null : LocalDate.parse(freeUntil.strip());
	}

	public Options options() {
		return new Options(List.of(Category.values()), free(), freeUntil, PromotionRules.MAX_DAYS);
	}

	// ----- Owners -----

	@Transactional(readOnly = true)
	public List<MyPromotion> mine(UUID me) {
		return mine(jdbc.query("SELECT * FROM promotions WHERE owner_id = ? ORDER BY created_at DESC", (rs, n) -> row(rs), me));
	}

	@Transactional
	public MyPromotion create(UUID me, PromotionRequest req) {
		requireVerified(me);
		UUID id = UUID.randomUUID();
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.update("""
				INSERT INTO promotions (id, owner_id, business, title, body, link_url, category, all_india, starts_on, ends_on, status,
				                        created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)""", id, me, req.business().strip(), req.title().strip(), req.body().strip(),
				blankToNull(req.linkUrl()), req.category().name(), Boolean.TRUE.equals(req.allIndia()), Date.valueOf(req.startsOn()),
				Date.valueOf(req.endsOn()), now, now);
		saveCities(id, req);
		return one(id, me);
	}

	/** Changing a promotion sends it back to draft (it needs checking again). */
	@Transactional
	public MyPromotion update(UUID me, UUID id, PromotionRequest req) {
		Map<String, Object> p = own(me, id);
		if (Set.of("TAKEN_DOWN").contains(p.get("status"))) {
			throw ApiException.conflict("taken_down", "This promotion was taken down by our team and can't be changed.");
		}
		jdbc.update("""
				UPDATE promotions SET business = ?, title = ?, body = ?, link_url = ?, category = ?, all_india = ?, starts_on = ?, ends_on = ?,
				                      status = 'DRAFT', updated_at = ? WHERE id = ?""", req.business().strip(), req.title().strip(),
				req.body().strip(), blankToNull(req.linkUrl()), req.category().name(), Boolean.TRUE.equals(req.allIndia()),
				Date.valueOf(req.startsOn()), Date.valueOf(req.endsOn()), Timestamp.from(clock.instant()), id);
		jdbc.update("DELETE FROM promotion_cities WHERE promotion_id = ?", id);
		saveCities(id, req);
		return one(id, me);
	}

	@Transactional
	public MyPromotion image(UUID me, UUID id, MultipartFile file) {
		Map<String, Object> p = own(me, id);
		String old = (String) p.get("image_path");
		String path = images.store(file);
		jdbc.update("UPDATE promotions SET image_path = ?, status = CASE WHEN status = 'TAKEN_DOWN' THEN status ELSE 'DRAFT' END, "
				+ "updated_at = ? WHERE id = ?", path, Timestamp.from(clock.instant()), id);
		images.deleteQuietly(old);
		return one(id, me);
	}

	/** The automatic checks, then the moderator's queue. */
	@Transactional
	public MyPromotion submit(UUID me, UUID id) {
		Map<String, Object> p = own(me, id);
		if (!Set.of("DRAFT", "REJECTED").contains(p.get("status"))) {
			throw ApiException.conflict("not_draft", "This promotion is already with our team or running.");
		}
		if (!free()) {
			throw ApiException.conflict("paid_coming_soon", "The free launch offer has ended. Paid promotions are coming soon.");
		}
		Integer active = jdbc.queryForObject("SELECT count(*) FROM promotions WHERE owner_id = ? AND status IN ('IN_REVIEW', 'APPROVED') "
				+ "AND ends_on >= ?", Integer.class, me, Date.valueOf(today()));
		if (active != null && active >= PromotionRules.MAX_ACTIVE) {
			throw ApiException.conflict("too_many", "You can run up to " + PromotionRules.MAX_ACTIVE + " promotions at a time.");
		}
		String problem = PromotionRules.bannedContent((String) p.get("business"), (String) p.get("title"), (String) p.get("body"),
				(String) p.get("link_url"));
		if (problem == null) {
			problem = PromotionRules.linkProblem((String) p.get("link_url"));
		}
		if (problem == null) {
			problem = PromotionRules.datesProblem(((Date) p.get("starts_on")).toLocalDate(), ((Date) p.get("ends_on")).toLocalDate(), today());
		}
		if (problem == null && !Boolean.TRUE.equals(p.get("all_india")) && cities(List.of(id)).getOrDefault(id, List.of()).isEmpty()) {
			problem = "Choose the towns to show it in, or all of India.";
		}
		if (problem != null) {
			throw ApiException.badRequest("not_allowed", problem);
		}
		List<String> flags = PromotionRules.flags((String) p.get("title"), (String) p.get("body"));
		jdbc.update("UPDATE promotions SET status = 'IN_REVIEW', flags = ?, review_note = NULL, price_paise = 0, updated_at = ? WHERE id = ?",
				flags.isEmpty() ? null : String.join("; ", flags), Timestamp.from(clock.instant()), id);
		return one(id, me);
	}

	@Transactional
	public MyPromotion stop(UUID me, UUID id) {
		own(me, id);
		jdbc.update("UPDATE promotions SET status = 'STOPPED', updated_at = ? WHERE id = ? AND status IN ('IN_REVIEW', 'APPROVED')",
				Timestamp.from(clock.instant()), id);
		return one(id, me);
	}

	@Transactional
	public void delete(UUID me, UUID id) {
		Map<String, Object> p = own(me, id);
		if (ACTIVE.contains(p.get("status"))) {
			throw ApiException.conflict("active", "Stop the promotion first.");
		}
		jdbc.update("DELETE FROM promotions WHERE id = ?", id);
		images.deleteQuietly((String) p.get("image_path"));
	}

	// ----- Showing promotions -----

	/**
	 * Live promotions for this member in this slot, for the given towns (search) or their profile town, or all of
	 * India; at most three times a day each, never their own. Each one returned counts as a view.
	 */
	@Transactional
	public List<PromoCard> feed(UUID me, Slot slot, List<Long> placeIds, int limit) {
		LocalDate today = today();
		List<Long> ids = placeIds == null ? List.of() : placeIds.stream().limit(4).toList();
		String city = ids.isEmpty() ? members.city(me).orElse("") : "";
		UUID[] blocked = blocks.blockedEitherWay(me).toArray(new UUID[0]);
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT p.* FROM promotions p
				WHERE p.status = 'APPROVED' AND p.starts_on <= ? AND p.ends_on >= ? AND p.owner_id <> ? AND NOT (p.owner_id = ANY (?))
				  AND (p.all_india OR EXISTS (SELECT 1 FROM promotion_cities c WHERE c.promotion_id = p.id
				        AND (c.place_id = ANY (?) OR lower(c.place_name) = lower(?))))
				  AND COALESCE((SELECT v.seen FROM promotion_views v WHERE v.promotion_id = p.id AND v.user_id = ? AND v.day = ?), 0) < ?
				ORDER BY random() LIMIT ?""", Date.valueOf(today), Date.valueOf(today), me, blocked, ids.toArray(new Long[0]), city, me,
				Date.valueOf(today), PromotionRules.DAILY_CAP, Math.max(1, Math.min(limit, slot == Slot.DISCOVER ? 20 : 2)));
		List<PromoCard> out = new ArrayList<>();
		for (Map<String, Object> p : rows) {
			UUID id = (UUID) p.get("id");
			jdbc.update("""
					INSERT INTO promotion_views (promotion_id, user_id, day, seen) VALUES (?, ?, ?, 1)
					ON CONFLICT (promotion_id, user_id, day) DO UPDATE SET seen = promotion_views.seen + 1""", id, me, Date.valueOf(today));
			jdbc.update("UPDATE promotions SET views = views + 1 WHERE id = ?", id);
			out.add(card(p));
		}
		return out;
	}

	/** Counts a click, once a day per member, and only on a promotion they were shown. */
	@Transactional
	public void click(UUID me, UUID id) {
		int first = jdbc.update("UPDATE promotion_views SET clicked = TRUE WHERE promotion_id = ? AND user_id = ? AND day = ? AND NOT clicked",
				id, me, Date.valueOf(today()));
		if (first == 1) {
			jdbc.update("UPDATE promotions SET clicks = clicks + 1 WHERE id = ?", id);
		}
	}

	// ----- Staff -----

	/** {@code status}: IN_REVIEW (oldest first), LIVE, or ALL. */
	@Transactional(readOnly = true)
	public List<AdminPromotionRow> forStaff(String status) {
		String sql = switch (status) {
			case "IN_REVIEW" -> "SELECT * FROM promotions WHERE status = 'IN_REVIEW' ORDER BY updated_at LIMIT 200";
			case "LIVE" -> "SELECT * FROM promotions WHERE status = 'APPROVED' AND ends_on >= current_date ORDER BY starts_on LIMIT 200";
			default -> "SELECT * FROM promotions WHERE status <> 'DRAFT' ORDER BY updated_at DESC LIMIT 200";
		};
		List<Map<String, Object>> rows = jdbc.query(sql, (rs, n) -> row(rs));
		List<MyPromotion> views = mine(rows);
		Map<UUID, MemberCard> owners = members.cards(rows.stream().map(r -> (UUID) r.get("owner_id")).toList());
		List<AdminPromotionRow> out = new ArrayList<>();
		for (int i = 0; i < rows.size(); i++) {
			out.add(new AdminPromotionRow(views.get(i), owners.getOrDefault((UUID) rows.get(i).get("owner_id"), MemberProfiles.deleted()),
					(String) rows.get(i).get("flags")));
		}
		return out;
	}

	@Transactional(readOnly = true)
	public long waiting() {
		Long n = jdbc.queryForObject("SELECT count(*) FROM promotions WHERE status = 'IN_REVIEW'", Long.class);
		return n == null ? 0 : n;
	}

	@Transactional
	public AdminPromotionRow approve(UUID id, Actor actor) {
		decide(id, "IN_REVIEW", "APPROVED", null, actor, "promotion.approved", "Approved a promotion");
		return tell(forOne(id), "Your promotion is approved", null);
	}

	@Transactional
	public AdminPromotionRow reject(UUID id, String note, Actor actor) {
		decide(id, "IN_REVIEW", "REJECTED", note.strip(), actor, "promotion.rejected", "Sent a promotion back");
		return tell(forOne(id), "Your promotion needs a change", note.strip());
	}

	@Transactional
	public AdminPromotionRow takeDown(UUID id, String note, Actor actor) {
		decide(id, "APPROVED", "TAKEN_DOWN", note.strip(), actor, "promotion.taken_down", "Took a promotion down");
		return tell(forOne(id), "Your promotion was taken down", note.strip());
	}

	/** Lets the owner know what our team decided, with our note when there is one. */
	private AdminPromotionRow tell(AdminPromotionRow row, String title, String note) {
		String headline = "\"" + row.promotion().card().title() + "\"";
		notifier.send(row.owner().id(), Notice.account("megaphone", title,
				note == null ? headline + " runs on the dates you chose." : headline + ": " + note,
				"/promotions/" + row.promotion().card().id()));
		return row;
	}

	/** Viewing counts older than a week go (they're only for the daily limit). */
	@Transactional
	public void tidy() {
		jdbc.update("DELETE FROM promotion_views WHERE day < ?", Date.valueOf(today().minusDays(7)));
	}

	// ----- Inside -----

	private void decide(UUID id, String from, String to, String note, Actor actor, String action, String summary) {
		int n = jdbc.update("UPDATE promotions SET status = ?, review_note = ?, reviewed_by = ?, reviewed_at = ?, updated_at = ? "
				+ "WHERE id = ? AND status = ?", to, note, actor.id(), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), id, from);
		if (n == 0) {
			throw ApiException.conflict("not_waiting", "This promotion isn't in that state any more.");
		}
		audit.record(actor, action, "promotion", id.toString(), summary, note == null ? null : Map.of("note", note));
	}

	private AdminPromotionRow forOne(UUID id) {
		List<Map<String, Object>> rows = jdbc.query("SELECT * FROM promotions WHERE id = ?", (rs, n) -> row(rs), id);
		Map<String, Object> r = rows.getFirst();
		return new AdminPromotionRow(mine(rows).getFirst(), members.card((UUID) r.get("owner_id")), (String) r.get("flags"));
	}

	private void saveCities(UUID id, PromotionRequest req) {
		if (Boolean.TRUE.equals(req.allIndia()) || req.placeIds() == null) {
			return;
		}
		for (Long placeId : req.placeIds().stream().distinct().limit(PromotionRules.MAX_CITIES).toList()) {
			Place place = places.find(placeId)
				.orElseThrow(() -> ApiException.badRequest("place_not_found", "Choose the towns from the list."));
			jdbc.update("INSERT INTO promotion_cities (promotion_id, place_id, place_name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", id,
					place.id(), place.name());
		}
	}

	private Map<UUID, List<City>> cities(List<UUID> ids) {
		Map<UUID, List<City>> out = new HashMap<>();
		if (ids.isEmpty()) {
			return out;
		}
		jdbc.query("SELECT promotion_id, place_id, place_name FROM promotion_cities WHERE promotion_id = ANY (?) ORDER BY place_name", rs -> {
			out.computeIfAbsent(rs.getObject("promotion_id", UUID.class), k -> new ArrayList<>())
				.add(new City(rs.getLong("place_id"), rs.getString("place_name")));
		}, (Object) ids.toArray(new UUID[0]));
		return out;
	}

	private List<MyPromotion> mine(List<Map<String, Object>> rows) {
		Map<UUID, List<City>> towns = cities(rows.stream().map(r -> (UUID) r.get("id")).toList());
		LocalDate today = today();
		return rows.stream().map(p -> {
			LocalDate starts = ((Date) p.get("starts_on")).toLocalDate();
			LocalDate ends = ((Date) p.get("ends_on")).toLocalDate();
			boolean live = "APPROVED".equals(p.get("status")) && !today.isBefore(starts) && !today.isAfter(ends);
			return new MyPromotion(card(p), Boolean.TRUE.equals(p.get("all_india")), towns.getOrDefault((UUID) p.get("id"), List.of()), starts,
					ends, (String) p.get("status"), live, (String) p.get("review_note"), (long) p.get("views"), (long) p.get("clicks"),
					(long) p.get("price_paise"), ((Timestamp) p.get("updated_at")).toInstant());
		}).toList();
	}

	private static PromoCard card(Map<String, Object> p) {
		return new PromoCard((UUID) p.get("id"), (String) p.get("business"), (String) p.get("title"), (String) p.get("body"),
				(String) p.get("link_url"), Category.valueOf((String) p.get("category")), PublicImages.url((String) p.get("image_path")));
	}

	private Map<String, Object> own(UUID me, UUID id) {
		List<Map<String, Object>> rows = jdbc.query("SELECT * FROM promotions WHERE id = ? AND owner_id = ? FOR UPDATE", (rs, n) -> row(rs),
				id, me);
		if (rows.isEmpty()) {
			throw ApiException.notFound("promotion_not_found", "We couldn't find that promotion.");
		}
		return rows.getFirst();
	}

	private MyPromotion one(UUID id, UUID me) {
		return mine(jdbc.query("SELECT * FROM promotions WHERE id = ? AND owner_id = ?", (rs, n) -> row(rs), id, me)).getFirst();
	}

	private static Map<String, Object> row(ResultSet rs) throws SQLException {
		Map<String, Object> m = new HashMap<>();
		for (String c : Arrays.asList("id", "owner_id", "business", "title", "body", "link_url", "category", "image_path", "all_india",
				"starts_on", "ends_on", "status", "flags", "review_note", "price_paise", "views", "clicks", "updated_at")) {
			m.put(c, rs.getObject(c));
		}
		return m;
	}

	/** Promotions need a member people can trust: an approved ID or a confirmed email. */
	private void requireVerified(UUID me) {
		if (!members.card(me).idVerified() && members.verifiedEmail(me).isEmpty()) {
			throw ApiException.forbidden("verification_needed",
					"To promote a business, confirm your email or ID first (Profile → Verification).");
		}
	}

	private boolean free() {
		return freeUntil == null || !today().isAfter(freeUntil);
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(INDIA));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}
}
