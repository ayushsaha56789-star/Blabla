package com.saathchalo.admin;

import com.saathchalo.access.AccessService;
import com.saathchalo.access.StaffMembership;
import com.saathchalo.audit.Actor;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.CurrentUser;
import com.saathchalo.user.UserService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The staff member behind the current console request. */
@Component
public class AdminContext {

	public record Staff(UUID userId, String displayName, StaffMembership membership, Actor actor) {

		public boolean can(String permission) {
			return membership.can(permission);
		}
	}

	private final AccessService access;

	private final UserService users;

	public AdminContext(AccessService access, UserService users) {
		this.access = access;
		this.users = users;
	}

	public Staff current() {
		UUID id = CurrentUser.require();
		StaffMembership membership = access.membership(id)
			.orElseThrow(() -> ApiException.forbidden("not_staff", "This account is not on the SaathChalo staff."));
		String name = users.displayName(id);
		return new Staff(id, name, membership, new Actor(id, name + " · " + membership.roleName()));
	}
}
