package com.saathchalo.admin;

import com.saathchalo.access.Perm;
import com.saathchalo.common.ApiException;
import com.saathchalo.promotions.PromotionDtos.AdminPromotionRow;
import com.saathchalo.promotions.PromotionDtos.StaffNote;
import com.saathchalo.promotions.PromotionService;
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

/** Checking members' promotions before they go live, and taking one down. */
@RestController
@RequestMapping("/api/admin/promotions")
public class AdminPromotionsController {

	private final AdminContext context;

	private final PromotionService promotions;

	public AdminPromotionsController(AdminContext context, PromotionService promotions) {
		this.context = context;
		this.promotions = promotions;
	}

	/** {@code status}: IN_REVIEW (waiting), LIVE or ALL. */
	@GetMapping
	@PreAuthorize(Perm.CONTENT_MODERATE)
	public List<AdminPromotionRow> list(@RequestParam(defaultValue = "IN_REVIEW") String status) {
		String s = status.toUpperCase(Locale.ROOT);
		if (!Set.of("IN_REVIEW", "LIVE", "ALL").contains(s)) {
			throw ApiException.badRequest("bad_filter", "Choose waiting, live or all.");
		}
		return promotions.forStaff(s);
	}

	@PostMapping("/{id}/approve")
	@PreAuthorize(Perm.CONTENT_MODERATE)
	public AdminPromotionRow approve(@PathVariable UUID id) {
		return promotions.approve(id, context.current().actor());
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize(Perm.CONTENT_MODERATE)
	public AdminPromotionRow reject(@PathVariable UUID id, @Valid @RequestBody StaffNote request) {
		return promotions.reject(id, request.note(), context.current().actor());
	}

	@PostMapping("/{id}/take-down")
	@PreAuthorize(Perm.CONTENT_MODERATE)
	public AdminPromotionRow takeDown(@PathVariable UUID id, @Valid @RequestBody StaffNote request) {
		return promotions.takeDown(id, request.note(), context.current().actor());
	}
}
