package com.saathchalo.admin;

import com.saathchalo.access.Perm;
import com.saathchalo.audit.AuditEntryView;
import com.saathchalo.audit.AuditFilter;
import com.saathchalo.audit.AuditService;
import com.saathchalo.audit.ChainCheck;
import com.saathchalo.common.PageResult;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize(Perm.AUDIT_VIEW)
public class AdminAuditController {

	private final AuditService audit;

	public AdminAuditController(AuditService audit) {
		this.audit = audit;
	}

	@GetMapping
	public PageResult<AuditEntryView> search(@RequestParam(required = false) String action,
			@RequestParam(required = false) UUID actorId, @RequestParam(required = false) String targetType,
			@RequestParam(required = false) String targetId, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
		return audit.search(new AuditFilter(action, actorId, targetType, targetId, q), page, size);
	}

	/** Re-computes the hash chain to prove no entry was changed or removed. */
	@GetMapping("/verify")
	public ChainCheck verify() {
		return audit.verifyChain();
	}
}
