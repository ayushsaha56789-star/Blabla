package com.saathchalo.features;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class FeatureRulesTest {

	@Test
	void restrictionsCoverAFeatureAndEverythingUnderIt() {
		AudienceProfile p = new AudienceProfile(null, "FEMALE", false, false, null, Set.of("games"));
		assertThat(p.restrictedFrom("games")).isTrue();
		assertThat(p.restrictedFrom("games.chess")).isTrue();
		assertThat(p.restrictedFrom("gamesx")).isFalse();
		assertThat(p.restrictedFrom("rides.booking")).isFalse();
		AudienceProfile suspended = new AudienceProfile(null, null, false, false, null, Set.of("ALL"));
		assertThat(suspended.restrictedFrom("rides.booking")).isTrue();
	}

	@Test
	void everyChildSwitchHasAParentWithTheSamePrefix() {
		for (FeatureDefinition d : FeatureCatalog.all()) {
			if (d.parent() != null) {
				assertThat(d.key()).startsWith(d.parent() + ".");
				assertThat(FeatureCatalog.find(d.parent())).isPresent();
			}
		}
	}
}
