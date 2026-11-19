package com.saathchalo.admin;

import com.saathchalo.access.AccessService;
import com.saathchalo.access.Perm;
import com.saathchalo.access.Permission;
import com.saathchalo.access.StaffMembership;
import com.saathchalo.admin.AdminDtos.BetaTesterRequest;
import com.saathchalo.admin.AdminDtos.MemberDetailResponse;
import com.saathchalo.admin.AdminDtos.MemberRow;
import com.saathchalo.admin.AdminDtos.PhoneResponse;
import com.saathchalo.admin.AdminDtos.RestrictRequest;
import com.saathchalo.audit.AuditEntryView;
import com.saathchalo.audit.AuditFilter;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.PageResult;
import com.saathchalo.config.AppConfig;
import com.saathchalo.user.MemberDirectory;
import com.saathchalo.user.MemberDirectory.MemberDetails;
import com.saathchalo.user.MemberDirectory.RestrictionView;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMembersController {

	private final AdminContext context;

	private final MemberDirectory members;

	private final AccessService access;

	private final AuditService audit;

	public AdminMembersController(AdminContext context, MemberDirectory members, AccessService access, AuditService audit) {
		this.context = context;
		this.members = members;
		this.access = access;
		this.audit = audit;
	}

	@GetMapping
	@PreAuthorize(Perm.USERS_VIEW)
	public PageResult<MemberRow> search(@RequestParam(defaultValue = "") String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		PageResult<com.saathchalo.user.MemberSummary> result = members.search(q, page, size);
		Map<UUID, StaffMembership> staff = staffById();
		List<MemberRow> rows = result.items().stream().map(m -> {
			StaffMembership s = staff.get(m.id());
			return new MemberRow(m.id(), m.displayName(), m.maskedPhone(), m.avatarUrl(), m.gender(), m.city(),
					m.memberSince(), m.betaTester(), m.suspended(), m.restrictedAreas(), s == null ? null : s.roleCode(),
					s == null ? null : s.roleName());
		}).toList();
		return new PageResult<>(rows, result.total(), result.page(), result.size());
	}

	@GetMapping("/{id}")
	@PreAuthorize(Perm.USERS_VIEW)
	public MemberDetailResponse details(@PathVariable UUID id) {
		return detailResponse(id, members.details(id));
	}

	@PostMapping("/{id}/contact")
	@PreAuthorize(Perm.USERS_VIEW_CONTACT)
	public PhoneResponse revealPhone(@PathVariable UUID id) {
		return new PhoneResponse(members.revealPhone(id, context.current().actor()));
	}

	@PostMapping("/{id}/restrictions")
	@PreAuthorize(Perm.USERS_RESTRICT)
	public RestrictionView restrict(@PathVariable UUID id, @Valid @RequestBody RestrictRequest request) {
		AdminContext.Staff me = context.current();
		guardTarget(me, id);
		// A chosen end date means "until the end of that day" in India.
		Instant until = request.until() == null ? null
				: request.until().plusDays(1).atStartOfDay(AppConfig.ZONE).toInstant();
		return members.restrict(id, request.area(), request.reason(), until, me.actor());
	}

	@DeleteMapping("/{id}/restrictions/{restrictionId}")
	@PreAuthorize(Perm.USERS_RESTRICT)
	public RestrictionView lift(@PathVariable UUID id, @PathVariable UUID restrictionId) {
		AdminContext.Staff me = context.current();
		guardTarget(me, id);
		return members.lift(id, restrictionId, me.actor());
	}

	@PutMapping("/{id}/beta-tester")
	@PreAuthorize(Perm.FEATURES_MANAGE)
	public MemberDetailResponse betaTester(@PathVariable UUID id, @RequestBody BetaTesterRequest request) {
		return detailResponse(id, members.setBetaTester(id, request.betaTester(), context.current().actor()));
	}

	/** Staff can't act on themselves; only a super admin can restrict staff; nobody can restrict a super admin. */
	private void guardTarget(AdminContext.Staff me, UUID target) {
		if (me.userId().equals(target)) {
			throw ApiException.badRequest("self_restriction", "You can't restrict your own account.");
		}
		Optional<StaffMembership> targetStaff = access.membership(target);
		if (targetStaff.map(StaffMembership::isSuperAdmin).orElse(false)) {
			throw ApiException.forbidden("protected_member", "A super admin can't be restricted.");
		}
		if (targetStaff.isPresent() && !me.membership().isSuperAdmin()) {
			throw ApiException.forbidden("protected_member", "Only the super admin can restrict staff members.");
		}
	}

	private MemberDetailResponse detailResponse(UUID id, MemberDetails details) {
		Optional<StaffMembership> staff = access.membership(id);
		List<AuditEntryView> activity = context.current().can(Permission.AUDIT_VIEW.code())
				? audit.search(AuditFilter.forTarget("member", id.toString()), 0, 25).items()
				: List.of();
		return new MemberDetailResponse(details, staff.map(StaffMembership::roleCode).orElse(null),
				staff.map(StaffMembership::roleName).orElse(null), activity, members.areas());
	}

	private Map<UUID, StaffMembership> staffById() {
		return access.allStaff().stream().collect(Collectors.toMap(StaffMembership::userId, Function.identity()));
	}
}
