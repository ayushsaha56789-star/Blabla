package com.saathchalo.admin;

import com.saathchalo.access.Perm;
import com.saathchalo.common.ApiException;
import com.saathchalo.support.SupportDtos.Reply;
import com.saathchalo.support.SupportDtos.StaffTicketRow;
import com.saathchalo.support.SupportDtos.StaffTicketView;
import com.saathchalo.support.SupportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Help requests for support staff. */
@RestController
@RequestMapping("/api/admin/support/tickets")
public class AdminSupportController {

	private static final Set<String> STATUSES = Set.of("OPEN", "ANSWERED", "CLOSED", "ALL");

	private final AdminContext context;

	private final SupportService support;

	public AdminSupportController(AdminContext context, SupportService support) {
		this.context = context;
		this.support = support;
	}

	@GetMapping
	@PreAuthorize(Perm.SUPPORT_TICKETS)
	public List<StaffTicketRow> queue(@RequestParam(defaultValue = "OPEN") String status) {
		String s = status.toUpperCase(Locale.ROOT);
		if (!STATUSES.contains(s)) {
			throw ApiException.badRequest("bad_filter", "Choose open, answered, closed or all.");
		}
		return support.queue("ALL".equals(s) ? null : s);
	}

	@GetMapping("/{id}")
	@PreAuthorize(Perm.SUPPORT_TICKETS)
	public StaffTicketView view(@PathVariable UUID id) {
		return support.staffView(id);
	}

	@PostMapping("/{id}/messages")
	@PreAuthorize(Perm.SUPPORT_TICKETS)
	public StaffTicketView reply(@PathVariable UUID id, @Valid @RequestBody Reply request) {
		return support.staffReply(id, request.body(), context.current().actor());
	}

	@PostMapping("/{id}/close")
	@PreAuthorize(Perm.SUPPORT_TICKETS)
	public StaffTicketView close(@PathVariable UUID id) {
		return support.staffClose(id);
	}
}
