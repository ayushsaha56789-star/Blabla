package com.saathchalo.access;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record StaffMembership(UUID userId, String roleCode, String roleName, int rank, Set<String> permissions,
		Instant addedAt, UUID addedBy) {

	public boolean isSuperAdmin() {
		return StaffRoleView.SUPER_ADMIN.equals(roleCode);
	}

	public boolean can(String permission) {
		return permissions.contains(permission);
	}
}
