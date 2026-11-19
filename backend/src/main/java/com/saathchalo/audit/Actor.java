package com.saathchalo.audit;

import java.util.UUID;

/** Who did something: a staff member or member ({@code id} + a readable label), or the system itself. */
public record Actor(UUID id, String label) {

	public static final Actor SYSTEM = new Actor(null, "SaathChalo (automatic)");
}
