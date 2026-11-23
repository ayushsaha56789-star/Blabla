package com.saathchalo.promotions;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.FeatureService;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.promotions.PromotionDtos.MyPromotion;
import com.saathchalo.promotions.PromotionDtos.Options;
import com.saathchalo.promotions.PromotionDtos.PromoCard;
import com.saathchalo.promotions.PromotionDtos.PromotionRequest;
import com.saathchalo.promotions.PromotionDtos.Slot;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Promotions: seeing them (logged-in members only), and creating your own ({@code promotions.self_serve}). */
@RestController
@RequestMapping("/api/promotions")
@RequiresFeature("promotions")
public class PromotionController {

	private final PromotionService promotions;

	private final FeatureService features;

	public PromotionController(PromotionService promotions, FeatureService features) {
		this.promotions = promotions;
		this.features = features;
	}

	/** Sponsored cards for a slot. Search and games slots have their own switches; when off, the list is empty. */
	@GetMapping("/feed")
	public List<PromoCard> feed(@RequestParam(defaultValue = "DISCOVER") Slot slot, @RequestParam(required = false) List<Long> placeIds,
			@RequestParam(defaultValue = "1") int limit) {
		UUID me = CurrentUser.require();
		String needs = switch (slot) {
			case SEARCH -> "promotions.in_search";
			case GAMES -> "promotions.in_games";
			case DISCOVER -> "promotions";
		};
		return features.isOnFor(needs, me) ? promotions.feed(me, slot, placeIds, limit) : List.of();
	}

	@PostMapping("/{id}/click")
	public ResponseEntity<Void> click(@PathVariable UUID id) {
		promotions.click(CurrentUser.require(), id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/options")
	public Options options() {
		return promotions.options();
	}

	@GetMapping("/mine")
	@RequiresFeature("promotions.self_serve")
	public List<MyPromotion> mine() {
		return promotions.mine(CurrentUser.require());
	}

	@PostMapping
	@RequiresFeature("promotions.self_serve")
	@ResponseStatus(HttpStatus.CREATED)
	public MyPromotion create(@Valid @RequestBody PromotionRequest request) {
		return promotions.create(CurrentUser.require(), request);
	}

	@PutMapping("/{id}")
	@RequiresFeature("promotions.self_serve")
	public MyPromotion update(@PathVariable UUID id, @Valid @RequestBody PromotionRequest request) {
		return promotions.update(CurrentUser.require(), id, request);
	}

	@PostMapping("/{id}/image")
	@RequiresFeature("promotions.self_serve")
	public MyPromotion image(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
		return promotions.image(CurrentUser.require(), id, file);
	}

	@PostMapping("/{id}/submit")
	@RequiresFeature("promotions.self_serve")
	public MyPromotion submit(@PathVariable UUID id) {
		return promotions.submit(CurrentUser.require(), id);
	}

	@PostMapping("/{id}/stop")
	@RequiresFeature("promotions.self_serve")
	public MyPromotion stop(@PathVariable UUID id) {
		return promotions.stop(CurrentUser.require(), id);
	}

	@DeleteMapping("/{id}")
	@RequiresFeature("promotions.self_serve")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		promotions.delete(CurrentUser.require(), id);
		return ResponseEntity.noContent().build();
	}
}
