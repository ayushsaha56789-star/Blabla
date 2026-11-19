package com.saathchalo.config;

import java.time.Instant;

/** Whether a member may use SaathChalo right now. */
public record AccountStatus(boolean suspended, String reason, Instant until) {

	public static final AccountStatus ACTIVE = new AccountStatus(false, null, null);
}
