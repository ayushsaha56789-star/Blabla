package com.saathchalo.features;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoint showing how a real feature API is protected by its switch. */
@RestController
@RequiresFeature("games")
class TestGamesController {

	@GetMapping("/api/test/games")
	Map<String, String> play() {
		return Map.of("game", "on");
	}
}
