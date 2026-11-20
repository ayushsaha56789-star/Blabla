package com.saathchalo.support;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.support.SupportDtos.NewTicket;
import com.saathchalo.support.SupportDtos.Reply;
import com.saathchalo.support.SupportDtos.TicketSummary;
import com.saathchalo.support.SupportDtos.TicketView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Help requests. Always available (no switch): members must always be able to reach us, including grievances. */
@RestController
@RequestMapping("/api/support/tickets")
public class SupportController {

	private final SupportService support;

	public SupportController(SupportService support) {
		this.support = support;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TicketView create(@Valid @RequestBody NewTicket request) {
		return support.create(CurrentUser.require(), request);
	}

	@GetMapping
	public List<TicketSummary> mine() {
		return support.mine(CurrentUser.require());
	}

	/** How many requests have a reply waiting for the member. */
	@GetMapping("/answered")
	public Map<String, Long> answered() {
		return Map.of("count", support.answered(CurrentUser.require()));
	}

	@GetMapping("/{id}")
	public TicketView get(@PathVariable UUID id) {
		return support.get(CurrentUser.require(), id);
	}

	@PostMapping("/{id}/messages")
	public TicketView reply(@PathVariable UUID id, @Valid @RequestBody Reply request) {
		return support.reply(CurrentUser.require(), id, request.body());
	}

	@PostMapping("/{id}/close")
	public TicketView close(@PathVariable UUID id) {
		return support.close(CurrentUser.require(), id);
	}
}
