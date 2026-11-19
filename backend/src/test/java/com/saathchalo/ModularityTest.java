package com.saathchalo;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Fails the build if a module reaches into another module's internals or modules depend on each other in a cycle. */
class ModularityTest {

	@Test
	void moduleBoundariesAreRespected() {
		ApplicationModules.of(SaathChaloApplication.class).verify();
	}
}
