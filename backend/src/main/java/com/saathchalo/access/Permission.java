package com.saathchalo.access;

import java.util.Arrays;
import java.util.Optional;

/** Everything a staff member can be allowed to do. Roles are bundles of these (table staff_role_permissions). */
public enum Permission {

	FEATURES_MANAGE("features.manage", "Turn features on and off"),
	STAFF_MANAGE("staff.manage", "Add or remove staff and change roles"),
	SETTINGS_MANAGE("settings.manage", "Change prices, commission and app settings"),
	PAYOUTS_APPROVE("payouts.approve", "Approve payouts"),
	USERS_VIEW("users.view", "See members"),
	USERS_VIEW_CONTACT("users.view_contact", "Reveal a member's phone number"),
	USERS_RESTRICT("users.restrict", "Suspend or restrict members"),
	VERIFICATION_REVIEW("verification.review", "Review ID and licence checks"),
	CONTENT_MODERATE("content.moderate", "Moderate chats, reviews and promotions"),
	RENTALS_MANAGE("rentals.manage", "Approve rental listings and settle disputes"),
	PAYMENTS_REFUND("payments.refund", "Refund payments"),
	AUDIT_VIEW("audit.view", "Read the audit log"),
	SUPPORT_TICKETS("support.tickets", "Answer help tickets");

	private final String code;

	private final String label;

	Permission(String code, String label) {
		this.code = code;
		this.label = label;
	}

	public String code() {
		return code;
	}

	public String label() {
		return label;
	}

	public static Optional<Permission> ofCode(String code) {
		return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
	}
}
