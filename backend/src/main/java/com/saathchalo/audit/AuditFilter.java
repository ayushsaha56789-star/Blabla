package com.saathchalo.audit;

import java.util.UUID;

/** All fields optional. {@code action} may end with "." to match a whole group, e.g. "staff.". */
public record AuditFilter(String action, UUID actorId, String targetType, String targetId, String text) {

	public static AuditFilter forTarget(String targetType, String targetId) {
		return new AuditFilter(null, null, targetType, targetId, null);
	}
}
