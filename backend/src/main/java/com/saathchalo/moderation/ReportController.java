package com.saathchalo.moderation;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.moderation.ModerationDtos.ReportReceipt;
import com.saathchalo.moderation.ModerationDtos.ReportRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Reporting is always available (no switch): it is how members tell us about harm. */
@RestController
public class ReportController {

	private final ReportService reports;

	public ReportController(ReportService reports) {
		this.reports = reports;
	}

	@PostMapping("/api/reports")
	@ResponseStatus(HttpStatus.CREATED)
	public ReportReceipt report(@Valid @RequestBody ReportRequest request) {
		return reports.submit(CurrentUser.require(), request);
	}
}
