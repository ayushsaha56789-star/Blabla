package com.saathchalo.features;

/**
 * A switch the owner can flip. {@code parent}: the child is only on when the parent is on. {@code built}: false
 * until its phase is finished (members then see a "coming soon" page). {@code critical}: changing it needs the 2FA
 * code again. {@code global}: applies to everyone at once, so it has no audience.
 */
public record FeatureDefinition(String key, String parent, String area, String label, String description, int phase,
		boolean built, boolean critical, boolean global, boolean defaultOn) {
}
