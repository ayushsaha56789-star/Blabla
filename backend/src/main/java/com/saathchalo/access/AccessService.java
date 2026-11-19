package com.saathchalo.access;

import com.saathchalo.access.internal.StaffMember;
import com.saathchalo.access.internal.StaffMemberRepository;
import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.PhoneNumbers;
import com.saathchalo.config.AppProperties;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff roles and who holds them. Member details (names, phones) live in the user module and are passed in. */
@Service
public class AccessService {

	private static final Logger log = LoggerFactory.getLogger(AccessService.class);

	private final StaffMemberRepository staff;

	private final JdbcTemplate jdbc;

	private final AuditService audit;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	private final String bootstrapPhone;

	private volatile Map<String, StaffRoleView> rolesByCode;

	public AccessService(StaffMemberRepository staff, JdbcTemplate jdbc, AuditService audit,
			ApplicationEventPublisher events, Clock clock, AppProperties props) {
		this.staff = staff;
		this.jdbc = jdbc;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
		this.bootstrapPhone = normalizeOrNull(props.bootstrap() == null ? null : props.bootstrap().superAdminPhone());
	}

	public List<StaffRoleView> roles() {
		return roleMap().values().stream().sorted(Comparator.comparingInt(StaffRoleView::rank).reversed()).toList();
	}

	public StaffRoleView role(String code) {
		StaffRoleView role = code == null ? null : roleMap().get(code);
		if (role == null) {
			throw ApiException.badRequest("unknown_role", "Please choose a valid role.");
		}
		return role;
	}

	@Transactional(readOnly = true)
	public Optional<StaffMembership> membership(UUID userId) {
		return staff.findById(userId).map(this::toMembership);
	}

	public Set<String> permissionsOf(UUID userId) {
		return membership(userId).map(StaffMembership::permissions).orElse(Set.of());
	}

	@Transactional(readOnly = true)
	public List<StaffMembership> allStaff() {
		return staff.findAll().stream()
			.map(this::toMembership)
			.sorted(Comparator.comparingInt(StaffMembership::rank).reversed().thenComparing(StaffMembership::addedAt))
			.toList();
	}

	public long countStaff() {
		return staff.count();
	}

	@Transactional
	public StaffMembership add(UUID userId, String roleCode, Actor actor, String memberLabel) {
		StaffRoleView role = role(roleCode);
		if (staff.existsById(userId)) {
			throw ApiException.conflict("already_staff", memberLabel + " is already on the staff.");
		}
		StaffMember member = staff.save(new StaffMember(userId, role.code(), actor.id(), clock.instant()));
		audit.record(actor, "staff.added", "member", userId.toString(),
				"Added " + memberLabel + " to the staff as " + role.name(), Map.of("role", role.code()));
		return toMembership(member);
	}

	@Transactional
	public StaffMembership changeRole(UUID userId, String roleCode, Actor actor, String memberLabel) {
		StaffRoleView role = role(roleCode);
		StaffMember member = staff.findById(userId)
			.orElseThrow(() -> ApiException.notFound("not_staff", memberLabel + " is not on the staff."));
		String before = member.getRoleCode();
		if (before.equals(role.code())) {
			return toMembership(member);
		}
		if (StaffRoleView.SUPER_ADMIN.equals(before) && staff.countByRoleCode(StaffRoleView.SUPER_ADMIN) <= 1) {
			throw ApiException.conflict("last_super_admin",
					"SaathChalo needs at least one super admin. Make someone else super admin first.");
		}
		member.changeRole(role.code(), clock.instant());
		audit.record(actor, "staff.role_changed", "member", userId.toString(),
				"Changed " + memberLabel + " from " + role(before).name() + " to " + role.name(),
				Map.of("before", before, "after", role.code()));
		return toMembership(member);
	}

	@Transactional
	public void remove(UUID userId, Actor actor, String memberLabel) {
		StaffMember member = staff.findById(userId)
			.orElseThrow(() -> ApiException.notFound("not_staff", memberLabel + " is not on the staff."));
		if (StaffRoleView.SUPER_ADMIN.equals(member.getRoleCode())
				&& staff.countByRoleCode(StaffRoleView.SUPER_ADMIN) <= 1) {
			throw ApiException.conflict("last_super_admin",
					"SaathChalo needs at least one super admin, so the last one can't be removed.");
		}
		staff.delete(member);
		audit.record(actor, "staff.removed", "member", userId.toString(),
				"Removed " + memberLabel + " (" + role(member.getRoleCode()).name() + ") from the staff",
				Map.of("role", member.getRoleCode()));
		events.publishEvent(new StaffAccessRevoked(userId));
	}

	/**
	 * Makes the configured owner number super admin the first time it logs in, if there is no super admin yet.
	 * @return true if this login created the super admin
	 */
	@Transactional
	public boolean bootstrapSuperAdmin(UUID userId, String phone, String memberLabel) {
		if (bootstrapPhone == null || !bootstrapPhone.equals(phone)
				|| staff.countByRoleCode(StaffRoleView.SUPER_ADMIN) > 0) {
			return false;
		}
		jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null, 7_710_002L);
		if (staff.countByRoleCode(StaffRoleView.SUPER_ADMIN) > 0) {
			return false;
		}
		staff.findById(userId).ifPresentOrElse(m -> m.changeRole(StaffRoleView.SUPER_ADMIN, clock.instant()),
				() -> staff.save(new StaffMember(userId, StaffRoleView.SUPER_ADMIN, null, clock.instant())));
		audit.record(Actor.SYSTEM, "staff.bootstrap", "member", userId.toString(),
				memberLabel + " became super admin (owner number from server settings)", null);
		log.info("Owner account {} is now super admin", userId);
		return true;
	}

	private StaffMembership toMembership(StaffMember member) {
		StaffRoleView role = role(member.getRoleCode());
		return new StaffMembership(member.getUserId(), role.code(), role.name(), role.rank(),
				Set.copyOf(role.permissions()), member.getAddedAt(), member.getAddedBy());
	}

	/** Roles are fixed in Phase 2 (a custom-roles screen may come later), so they are read once. */
	private Map<String, StaffRoleView> roleMap() {
		Map<String, StaffRoleView> cached = rolesByCode;
		if (cached != null) {
			return cached;
		}
		Map<String, Set<String>> permissions = new LinkedHashMap<>();
		jdbc.query("SELECT role_code, permission FROM staff_role_permissions ORDER BY permission",
				rs -> { permissions.computeIfAbsent(rs.getString(1), k -> new LinkedHashSet<>()).add(rs.getString(2)); });
		Map<String, StaffRoleView> loaded = new LinkedHashMap<>();
		jdbc.query("SELECT code, name, description, rank FROM staff_roles ORDER BY rank DESC",
				rs -> {
					String code = rs.getString("code");
					loaded.put(code, new StaffRoleView(code, rs.getString("name"), rs.getString("description"),
							rs.getInt("rank"), List.copyOf(permissions.getOrDefault(code, Set.of()))));
				});
		rolesByCode = Map.copyOf(loaded);
		return rolesByCode;
	}

	private static String normalizeOrNull(String phone) {
		if (phone == null || phone.isBlank()) {
			return null;
		}
		try {
			return PhoneNumbers.normalizeIndianMobile(phone);
		}
		catch (ApiException e) {
			log.warn("app.bootstrap.super-admin-phone is not a valid Indian mobile number; ignoring it");
			return null;
		}
	}

}
