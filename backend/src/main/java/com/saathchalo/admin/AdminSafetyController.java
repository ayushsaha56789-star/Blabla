package com.saathchalo.admin;

import com.saathchalo.access.Perm;
import com.saathchalo.safety.SafetyDtos.ResolveRequest;
import com.saathchalo.safety.SafetyDtos.SosRow;
import com.saathchalo.safety.SosService;
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

/** SOS alerts for staff on duty: take charge of one, then resolve it with a note. Every step is audited. */
@RestController
@RequestMapping("/api/admin/safety/sos")
public class AdminSafetyController {

	private final AdminContext context;

	private final SosService sos;

	public AdminSafetyController(AdminContext context, SosService sos) {
		this.context = context;
		this.sos = sos;
	}

	@GetMapping
	@PreAuthorize(Perm.SUPPORT_TICKETS)
	public List<SosRow> alerts(@RequestParam(defaultValue = "true") boolean open) {
		return sos.alerts(open);
	}

	@PostMapping("/{id}/acknowledge")
	@PreAuthorize(Perm.SUPPORT_TICKETS)
	public SosRow acknowledge(@PathVariable UUID id) {
		return sos.acknowledge(id, context.current().actor());
	}

	@PostMapping("/{id}/resolve")
	@PreAuthorize(Perm.SUPPORT_TICKETS)
	public SosRow resolve(@PathVariable UUID id, @Valid @RequestBody(required = false) ResolveRequest request) {
		return sos.resolve(id, request == null ? null : request.notes(), context.current().actor());
	}
}
