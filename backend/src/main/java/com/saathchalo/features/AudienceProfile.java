package com.saathchalo.features;

import java.util.Set;
import java.util.UUID;

/** What feature switches need to know about a viewer. {@code restrictedAreas}: e.g. "games", or "ALL" if suspended. */
public record AudienceProfile(UUID userId, String gender, boolean idVerified, boolean betaTester, String city,
		Set<String> restrictedAreas) {

	public static final AudienceProfile ANONYMOUS = new AudienceProfile(null, null, false, false, null, Set.of());

	public boolean restrictedFrom(String featureKey) {
		for (String area : restrictedAreas) {
			if (area.equals("ALL") || featureKey.equals(area) || featureKey.startsWith(area + ".")) {
				return true;
			}
		}
		return false;
	}
}
