package com.saathchalo.admin;

import com.saathchalo.access.AccessService;
import com.saathchalo.access.Perm;
import com.saathchalo.access.StaffMembership;
import com.saathchalo.admin.AdminDtos.AddStaffRequest;
import com.saathchalo.admin.AdminDtos.ChangeRoleRequest;
import com.saathchalo.admin.AdminDtos.StaffRow;
import com.saathchalo.auth.RequiresConfirmation;
import com.saathchalo.auth.StaffSessionService;
import com.saathchalo.auth.StaffTwoFactorService;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.PhoneNumbers;
import com.saathchalo.user.MemberDirectory;
import com.saathchalo.user.MemberSummary;
import com.saathchalo.user.UserService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize(Perm.STAFF_MANAGE)
public class AdminStaffController {

	private final AdminContext context;

	private final AccessService access;

	private final UserService users;

	private final MemberDirectory members;

	private final StaffTwoFactorService twoFactor;

	private final StaffSessionService sessions;

	public AdminStaffController(AdminContext context, AccessService access, UserService users, MemberDirectory members,
			StaffTwoFactorService twoFactor, StaffSessionService sessions) {
		this.context = context;
		this.access = access;
		this.users = users;
		this.members = members;
		this.twoFactor = twoFactor;
		this.sessions = sessions;
	}

	@GetMapping
	public List<StaffRow> list() {
		UUID me = context.current().userId();
		List<StaffMembership> staff = access.allStaff();
		List<UUID> ids = staff.stream().map(StaffMembership::userId).toList();
		Map<UUID, MemberSummary> summaries = members.summaries(ids);
		Set<UUID> withTotp = twoFactor.enabledAmong(ids);
		Map<UUID, Instant> lastActive = sessions.lastActive(ids);
		return staff.stream().map(s -> row(s, summaries.get(s.userId()), withTotp.contains(s.userId()),
				lastActive.get(s.userId()), me)).toList();
	}

	/** The person must have logged in to SaathChalo at least once with this number. */
	@PostMapping
	@RequiresConfirmation
	public StaffRow add(@Valid @RequestBody AddStaffRequest request) {
		AdminContext.Staff me = context.current();
		String phone = PhoneNumbers.normalizeIndianMobile(request.phone());
		UUID userId = users.idByPhone(phone)
			.orElseThrow(() -> ApiException.notFound("member_not_found",
					"No member uses this number yet. Ask them to log in to SaathChalo once, then add them."));
		StaffMembership added = access.add(userId, request.role(), me.actor(), users.displayName(userId));
		return row(added, members.summaries(List.of(userId)).get(userId), false, null, me.userId());
	}

	@PutMapping("/{userId}")
	@RequiresConfirmation
	public StaffRow changeRole(@PathVariable UUID userId, @Valid @RequestBody ChangeRoleRequest request) {
		AdminContext.Staff me = context.current();
		notYourself(me, userId, "change your own role");
		StaffMembership changed = access.changeRole(userId, request.role(), me.actor(), users.displayName(userId));
		return row(changed, members.summaries(List.of(userId)).get(userId), twoFactor.isEnabled(userId),
				sessions.lastActive(List.of(userId)).get(userId), me.userId());
	}

	@DeleteMapping("/{userId}")
	@RequiresConfirmation
	public ResponseEntity<Void> remove(@PathVariable UUID userId) {
		AdminContext.Staff me = context.current();
		notYourself(me, userId, "remove yourself from the staff");
		access.remove(userId, me.actor(), users.displayName(userId));
		return ResponseEntity.noContent().build();
	}

	/** For a staff member who lost their phone: they set up two-step verification again at next sign-in. */
	@PostMapping("/{userId}/reset-2fa")
	@RequiresConfirmation
	public ResponseEntity<Void> resetTwoFactor(@PathVariable UUID userId) {
		AdminContext.Staff me = context.current();
		notYourself(me, userId, "reset your own two-step verification here (use your recovery codes instead)");
		if (access.membership(userId).isEmpty()) {
			throw ApiException.notFound("not_staff", "That person is not on the staff.");
		}
		twoFactor.reset(userId, me.actor(), users.displayName(userId));
		return ResponseEntity.noContent().build();
	}

	private static void notYourself(AdminContext.Staff me, UUID target, String what) {
		if (me.userId().equals(target)) {
			throw ApiException.badRequest("self_change", "You can't " + what + ". Ask another super admin to do it.");
		}
	}

	private static StaffRow row(StaffMembership s, MemberSummary m, boolean totp, Instant lastActive, UUID me) {
		return new StaffRow(s.userId(), m == null ? "Unknown member" : m.displayName(), m == null ? null : m.maskedPhone(),
				m == null ? null : m.avatarUrl(), s.roleCode(), s.roleName(), totp, s.addedAt(), lastActive,
				s.userId().equals(me));
	}
}
