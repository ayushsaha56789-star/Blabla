package com.saathchalo.config;

import java.time.Instant;

/** Whether a member may use SaathChalo right now. */
public record AccountStatus(boolean suspended, String reason, Instant until, boolean closing) {

	public static final AccountStatus ACTIVE = new AccountStatus(false, null, null, false);

	/** The member asked to delete their account: logging in again (which cancels that) is all it can do. */
	public static final AccountStatus CLOSING = new AccountStatus(false, null, null, true);
}
