package com.saathchalo.admin;

import com.saathchalo.access.Perm;
import com.saathchalo.moderation.ModerationDtos.ReportRow;
import com.saathchalo.moderation.ModerationDtos.ResolveRequest;
import com.saathchalo.moderation.ReportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The moderation queue: members' reports, with the legal deadlines. Closing a report is audited. */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportsController {

	private final AdminContext context;

	private final ReportService reports;

	public AdminReportsController(AdminContext context, ReportService reports) {
		this.context = context;
		this.reports = reports;
	}

	@GetMapping
	@PreAuthorize(Perm.CONTENT_MODERATE)
	public List<ReportRow> queue(@RequestParam(defaultValue = "true") boolean open) {
		return reports.queue(open);
	}

	@PostMapping("/{id}/acknowledge")
	@PreAuthorize(Perm.CONTENT_MODERATE)
	public ReportRow acknowledge(@PathVariable UUID id) {
		return reports.acknowledge(id, context.current().actor());
	}

	@PostMapping("/{id}/resolve")
	@PreAuthorize(Perm.CONTENT_MODERATE)
	public ReportRow resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request) {
		return reports.resolve(id, request.action(), request.resolution(), context.current().actor());
	}
}
