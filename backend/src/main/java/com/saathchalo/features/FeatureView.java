package com.saathchalo.features;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A switch as shown in the console: its definition plus its current state. */
public record FeatureView(String key, String parent, String area, String label, String description, int phase,
		boolean built, boolean critical, boolean global, boolean enabled, Audience audience, List<String> cities,
		Instant updatedAt, UUID updatedBy) {
}
