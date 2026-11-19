package com.saathchalo.user;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.Masking;
import com.saathchalo.common.PageResult;
import com.saathchalo.config.AppConfig;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Members as staff see and manage them in the console. Every change is written to the audit log. */
@Service
public class MemberDirectory {

	public record RestrictionView(UUID id, String area, String areaLabel, String reason, Instant startsAt,
			Instant endsAt, UUID createdBy, Instant liftedAt, UUID liftedBy, boolean active) {
	}

	public record MemberDetails(UUID id, String displayName, String firstName, String lastName, String maskedPhone,
			String avatarUrl, Gender gender, Integer age, String city, String bio, Instant memberSince,
			boolean phoneVerified, boolean idVerified, boolean betaTester, boolean profileComplete, boolean suspended,
			List<RestrictionView> restrictions) {
	}

	public record DailyCount(LocalDate day, long count) {
	}

	public record MemberStats(long total, long joinedToday, long joinedLast7Days, List<DailyCount> lastDays) {
	}

	public record AreaOption(String key, String label) {
	}

	private final UserRepository users;

	private final MemberRestrictionRepository restrictions;

	private final MemberAccessService access;

	private final UserService userService;

	private final AuditService audit;

	private final ApplicationEventPublisher events;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public MemberDirectory(UserRepository users, MemberRestrictionRepository restrictions, MemberAccessService access,
			UserService userService, AuditService audit, ApplicationEventPublisher events, JdbcTemplate jdbc, Clock clock) {
		this.users = users;
		this.restrictions = restrictions;
		this.access = access;
		this.userService = userService;
		this.audit = audit;
		this.events = events;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public List<AreaOption> areas() {
		return Arrays.stream(RestrictionArea.values()).map(a -> new AreaOption(a.key(), a.label())).toList();
	}

	/** Search by name, or by 3+ digits of the phone number. Newest members first. */
	@Transactional(readOnly = true)
	public PageResult<MemberSummary> search(String query, int page, int size) {
		PageRequest pageable = PageRequest.of(Math.max(0, page), PageResult.clampSize(size));
		String q = query == null ? "" : query.strip();
		String digits = q.replaceAll("[\\s\\-+]", "");
		Page<User> result;
		if (q.isEmpty()) {
			result = users.findAllByOrderByCreatedAtDesc(pageable);
		}
		else if (digits.matches("\\d{3,}")) {
			result = users.searchByPhone("%" + digits + "%", pageable);
		}
		else {
			String like = "%" + q.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
			result = users.searchByName(like, pageable);
		}
		Map<UUID, List<MemberRestriction>> active = activeRestrictions(result.getContent().stream().map(User::getId).toList());
		List<MemberSummary> items = result.getContent().stream()
			.map(u -> summary(u, active.getOrDefault(u.getId(), List.of())))
			.toList();
		return new PageResult<>(items, result.getTotalElements(), pageable.getPageNumber(), pageable.getPageSize());
	}

	@Transactional(readOnly = true)
	public Map<UUID, MemberSummary> summaries(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<UUID, List<MemberRestriction>> active = activeRestrictions(ids);
		Map<UUID, MemberSummary> out = new LinkedHashMap<>();
		users.findAllById(ids).forEach(u -> out.put(u.getId(), summary(u, active.getOrDefault(u.getId(), List.of()))));
		return out;
	}

	@Transactional(readOnly = true)
	public MemberDetails details(UUID id) {
		User u = load(id);
		Instant now = clock.instant();
		List<RestrictionView> history = restrictions.findTop30ByUserIdOrderByStartsAtDesc(id).stream()
			.map(r -> view(r, now))
			.toList();
		boolean suspended = history.stream().anyMatch(r -> r.active() && r.area().equals(RestrictionArea.ALL.key()));
		return new MemberDetails(u.getId(), u.displayName(), u.getFirstName(), u.getLastName(), Masking.phone(u.getPhone()),
				UserService.avatarUrl(u), u.getGender(), userService.age(u.getDateOfBirth()), u.getCity(), u.getBio(),
				u.getCreatedAt(), u.isPhoneVerified(), u.isIdVerified(), u.isBetaTester(), u.isProfileComplete(),
				suspended, history);
	}

	/** Shows a member's full phone number to a permitted staff member, and records that it was shown. */
	@Transactional
	public String revealPhone(UUID id, Actor actor) {
		User u = load(id);
		audit.record(actor, "member.phone_revealed", "member", id.toString(),
				"Viewed the phone number of " + u.displayName(), null);
		return u.getPhone();
	}

	@Transactional
	public RestrictionView restrict(UUID id, String areaKey, String reason, Instant until, Actor actor) {
		User u = load(id);
		RestrictionArea area = RestrictionArea.ofKey(areaKey);
		String why = reason == null ? "" : reason.strip();
		if (why.length() < 3 || why.length() > 500) {
			throw ApiException.badRequest("reason_required", "Please give a short reason (3 to 500 characters). The member will see it.");
		}
		Instant now = clock.instant();
		if (until != null && !until.isAfter(now)) {
			throw ApiException.badRequest("until_in_past", "The end date must be in the future.");
		}
		boolean alreadyActive = restrictions.findUnlifted(id).stream()
			.anyMatch(r -> r.isActive(now) && r.getArea().equals(area.key()));
		if (alreadyActive) {
			throw ApiException.conflict("already_restricted", u.displayName() + " is already restricted from this. Lift it first to change it.");
		}
		MemberRestriction saved = restrictions.save(new MemberRestriction(id, area.key(), why, now, until, actor.id()));
		String period = until == null ? "until further notice" : "until " + until;
		if (area == RestrictionArea.ALL) {
			audit.record(actor, "member.suspended", "member", id.toString(), "Suspended " + u.displayName() + " " + period,
					details(area, why, until));
			events.publishEvent(new MemberSuspended(id));
		}
		else {
			audit.record(actor, "member.restricted", "member", id.toString(),
					"Restricted " + u.displayName() + " from " + area.label().toLowerCase(Locale.ROOT) + " " + period,
					details(area, why, until));
		}
		access.evict(id);
		return view(saved, now);
	}

	@Transactional
	public RestrictionView lift(UUID id, UUID restrictionId, Actor actor) {
		User u = load(id);
		MemberRestriction r = restrictions.findById(restrictionId)
			.filter(x -> x.getUserId().equals(id))
			.orElseThrow(() -> ApiException.notFound("restriction_not_found", "That restriction doesn't exist."));
		Instant now = clock.instant();
		if (r.getLiftedAt() != null) {
			return view(r, now);
		}
		r.lift(actor.id(), now);
		RestrictionArea area = RestrictionArea.ofKey(r.getArea());
		audit.record(actor, "member.restriction_lifted", "member", id.toString(),
				area == RestrictionArea.ALL ? "Ended the suspension of " + u.displayName()
						: "Let " + u.displayName() + " use " + area.label().toLowerCase(Locale.ROOT) + " again",
				Map.of("area", area.key()));
		access.evict(id);
		return view(r, now);
	}

	@Transactional
	public MemberDetails setBetaTester(UUID id, boolean betaTester, Actor actor) {
		User u = load(id);
		if (u.isBetaTester() != betaTester) {
			u.setBetaTester(betaTester);
			audit.record(actor, "member.beta_tester", "member", id.toString(),
					(betaTester ? "Made " : "Removed ") + u.displayName() + (betaTester ? " a beta tester" : " from beta testers"),
					Map.of("betaTester", betaTester));
			access.evict(id);
		}
		return details(id);
	}

	@Transactional(readOnly = true)
	public MemberStats stats(int days) {
		int span = Math.max(1, Math.min(days, 90));
		LocalDate today = LocalDate.now(clock.withZone(AppConfig.ZONE));
		LocalDate first = today.minusDays(span - 1L);
		Instant from = first.atStartOfDay(AppConfig.ZONE).toInstant();
		Map<LocalDate, Long> counts = new HashMap<>();
		jdbc.query("""
				SELECT (created_at AT TIME ZONE 'Asia/Kolkata')::date AS day, count(*) AS n
				FROM users WHERE created_at >= ? GROUP BY day""",
				rs -> { counts.put(rs.getObject("day", LocalDate.class), rs.getLong("n")); }, Timestamp.from(from));
		List<DailyCount> series = new ArrayList<>();
		for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
			series.add(new DailyCount(d, counts.getOrDefault(d, 0L)));
		}
		long last7 = series.stream().filter(c -> !c.day().isBefore(today.minusDays(6))).mapToLong(DailyCount::count).sum();
		Long total = jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
		return new MemberStats(total == null ? 0 : total, counts.getOrDefault(today, 0L), last7, series);
	}

	private Map<UUID, List<MemberRestriction>> activeRestrictions(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Instant now = clock.instant();
		Map<UUID, List<MemberRestriction>> map = new HashMap<>();
		restrictions.findUnliftedFor(ids).stream()
			.filter(r -> r.isActive(now))
			.forEach(r -> map.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r));
		return map;
	}

	private static MemberSummary summary(User u, List<MemberRestriction> active) {
		boolean suspended = active.stream().anyMatch(r -> r.getArea().equals(RestrictionArea.ALL.key()));
		List<String> areas = active.stream().map(MemberRestriction::getArea).distinct().toList();
		return new MemberSummary(u.getId(), u.displayName(), Masking.phone(u.getPhone()), UserService.avatarUrl(u),
				u.getGender(), u.getCity(), u.getCreatedAt(), u.isBetaTester(), suspended, areas);
	}

	private static RestrictionView view(MemberRestriction r, Instant now) {
		RestrictionArea area = RestrictionArea.ofKey(r.getArea());
		return new RestrictionView(r.getId(), area.key(), area.label(), r.getReason(), r.getStartsAt(), r.getEndsAt(),
				r.getCreatedBy(), r.getLiftedAt(), r.getLiftedBy(), r.isActive(now));
	}

	private static Map<String, Object> details(RestrictionArea area, String reason, Instant until) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("area", area.key());
		m.put("reason", reason);
		m.put("until", until == null ? null : until.toString());
		return m;
	}

	private User load(UUID id) {
		return users.findById(id).orElseThrow(() -> ApiException.notFound("user_not_found", "We couldn't find that member."));
	}
}
