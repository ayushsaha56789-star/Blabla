package com.saathchalo.features;

import com.saathchalo.common.CurrentUser;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/features")
public class FeatureController {

	/** The member-facing switches that are on for the caller, plus whether maintenance mode is on. */
	public record FeaturesResponse(List<String> enabled, boolean maintenance) {
	}

	private final FeatureService features;

	public FeatureController(FeatureService features) {
		this.features = features;
	}

	@GetMapping
	public FeaturesResponse enabled() {
		return new FeaturesResponse(features.enabledKeys(CurrentUser.id().orElse(null)), features.isMaintenance());
	}
}
