package com.saathchalo.access;

/** Ready-made {@code @PreAuthorize} expressions, one per {@link Permission}. */
public final class Perm {

	private Perm() {
	}

	public static final String FEATURES_MANAGE = "hasAuthority('PERM_features.manage')";

	public static final String STAFF_MANAGE = "hasAuthority('PERM_staff.manage')";

	public static final String USERS_VIEW = "hasAuthority('PERM_users.view')";

	public static final String USERS_VIEW_CONTACT = "hasAuthority('PERM_users.view_contact')";

	public static final String USERS_RESTRICT = "hasAuthority('PERM_users.restrict')";

	public static final String AUDIT_VIEW = "hasAuthority('PERM_audit.view')";

	public static final String CONTENT_MODERATE = "hasAuthority('PERM_content.moderate')";

	public static final String SUPPORT_TICKETS = "hasAuthority('PERM_support.tickets')";

	public static final String VERIFICATION_REVIEW = "hasAuthority('PERM_verification.review')";
}
