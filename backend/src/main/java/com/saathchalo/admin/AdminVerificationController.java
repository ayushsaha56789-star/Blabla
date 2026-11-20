package com.saathchalo.admin;

import com.saathchalo.access.Perm;
import com.saathchalo.common.ApiException;
import com.saathchalo.verification.IdCheckService;
import com.saathchalo.verification.VerificationDtos.RejectRequest;
import com.saathchalo.verification.VerificationDtos.StaffIdCheck;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ID checks: staff compare the photo with the profile, then approve or reject. */
@RestController
@RequestMapping("/api/admin/verifications")
public class AdminVerificationController {

	private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

	private final AdminContext context;

	private final IdCheckService ids;

	public AdminVerificationController(AdminContext context, IdCheckService ids) {
		this.context = context;
		this.ids = ids;
	}

	@GetMapping
	@PreAuthorize(Perm.VERIFICATION_REVIEW)
	public List<StaffIdCheck> queue(@RequestParam(defaultValue = "PENDING") String status) {
		String s = status.toUpperCase(Locale.ROOT);
		if (!STATUSES.contains(s)) {
			throw ApiException.badRequest("bad_filter", "Choose pending, approved or rejected.");
		}
		return ids.queue(s);
	}

	/** The photo itself: never cached, and every view is in the audit log. */
	@GetMapping("/{id}/photo")
	@PreAuthorize(Perm.VERIFICATION_REVIEW)
	public ResponseEntity<byte[]> photo(@PathVariable UUID id) {
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.body(ids.photo(id, context.current().actor()));
	}

	@PostMapping("/{id}/approve")
	@PreAuthorize(Perm.VERIFICATION_REVIEW)
	public StaffIdCheck approve(@PathVariable UUID id) {
		return ids.approve(id, context.current().actor());
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize(Perm.VERIFICATION_REVIEW)
	public StaffIdCheck reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
		return ids.reject(id, request.reason(), request.note(), context.current().actor());
	}
}
