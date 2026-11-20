package com.saathchalo.admin;

import com.saathchalo.access.Perm;
import com.saathchalo.community.CommunityDtos.EventCard;
import com.saathchalo.community.CommunityDtos.EventRequest;
import com.saathchalo.community.CommunityService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Events (concerts, fests, matches) that members can find rides to. */
@RestController
@RequestMapping("/api/admin/events")
public class AdminEventsController {

	private final AdminContext context;

	private final CommunityService community;

	public AdminEventsController(AdminContext context, CommunityService community) {
		this.context = context;
		this.community = community;
	}

	@GetMapping
	@PreAuthorize(Perm.CONTENT_MODERATE)
	public List<EventCard> events() {
		return community.allEvents();
	}

	@PostMapping
	@PreAuthorize(Perm.CONTENT_MODERATE)
	@ResponseStatus(HttpStatus.CREATED)
	public EventCard create(@Valid @RequestBody EventRequest request) {
		return community.createEvent(request, context.current().actor());
	}

	@PutMapping("/{id}")
	@PreAuthorize(Perm.CONTENT_MODERATE)
	public EventCard update(@PathVariable UUID id, @Valid @RequestBody EventRequest request) {
		return community.updateEvent(id, request, context.current().actor());
	}

	@PostMapping("/{id}/cancel")
	@PreAuthorize(Perm.CONTENT_MODERATE)
	public EventCard cancel(@PathVariable UUID id) {
		return community.cancelEvent(id, context.current().actor());
	}
}
