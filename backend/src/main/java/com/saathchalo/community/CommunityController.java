package com.saathchalo.community;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.community.CommunityDtos.CodeRequest;
import com.saathchalo.community.CommunityDtos.EventCard;
import com.saathchalo.community.CommunityDtos.EventView;
import com.saathchalo.community.CommunityDtos.InterestsRequest;
import com.saathchalo.community.CommunityDtos.SquadCard;
import com.saathchalo.community.CommunityDtos.SquadRequest;
import com.saathchalo.community.CommunityDtos.SquadView;
import com.saathchalo.community.CommunityDtos.VibeView;
import com.saathchalo.features.RequiresFeature;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Squads and event rides (under "community"), and vibe match (its own switch). */
@RestController
@RequestMapping("/api/community")
public class CommunityController {

	private final CommunityService community;

	public CommunityController(CommunityService community) {
		this.community = community;
	}

	@GetMapping("/squads")
	@RequiresFeature("community.squads")
	public List<SquadCard> squads() {
		return community.mySquads(CurrentUser.require());
	}

	@PostMapping("/squads")
	@RequiresFeature("community.squads")
	@ResponseStatus(HttpStatus.CREATED)
	public SquadView createSquad(@Valid @RequestBody SquadRequest request) {
		return community.createSquad(CurrentUser.require(), request);
	}

	@PostMapping("/squads/join")
	@RequiresFeature("community.squads")
	public SquadView joinSquad(@Valid @RequestBody CodeRequest request) {
		return community.joinSquad(CurrentUser.require(), request.code());
	}

	@GetMapping("/squads/{id}")
	@RequiresFeature("community.squads")
	public SquadView squad(@PathVariable UUID id) {
		return community.squad(CurrentUser.require(), id);
	}

	@PostMapping("/squads/{id}/leave")
	@RequiresFeature("community.squads")
	public ResponseEntity<Void> leaveSquad(@PathVariable UUID id) {
		community.leaveSquad(CurrentUser.require(), id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/events")
	@RequiresFeature("community.events")
	public List<EventCard> events() {
		return community.upcomingEvents();
	}

	@GetMapping("/events/{id}")
	@RequiresFeature("community.events")
	public EventView event(@PathVariable UUID id) {
		return community.event(id);
	}

	@GetMapping("/vibe/me")
	@RequiresFeature("vibe_match")
	public VibeView vibe() {
		return community.vibe(CurrentUser.require());
	}

	@PutMapping("/vibe/me")
	@RequiresFeature("vibe_match")
	public VibeView saveInterests(@Valid @RequestBody InterestsRequest request) {
		return community.saveInterests(CurrentUser.require(), request.interests());
	}

	/** Vibe scores (5–99) between the member and these people, e.g. the drivers in search results. */
	@GetMapping("/vibe")
	@RequiresFeature("vibe_match")
	public Map<UUID, Integer> scores(@RequestParam List<UUID> ids) {
		return community.scores(CurrentUser.require(), ids);
	}
}
