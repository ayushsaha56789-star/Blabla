package com.saathchalo.admin;

import com.saathchalo.access.Perm;
import com.saathchalo.admin.AdminDtos.FeatureRow;
import com.saathchalo.admin.AdminDtos.FeatureUpdateRequest;
import com.saathchalo.auth.StaffConfirmations;
import com.saathchalo.features.FeatureService;
import com.saathchalo.features.FeatureView;
import com.saathchalo.user.MemberDirectory;
import com.saathchalo.user.MemberSummary;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/features")
@PreAuthorize(Perm.FEATURES_MANAGE)
public class AdminFeaturesController {

	private final AdminContext context;

	private final FeatureService features;

	private final MemberDirectory members;

	private final StaffConfirmations confirmations;

	public AdminFeaturesController(AdminContext context, FeatureService features, MemberDirectory members,
			StaffConfirmations confirmations) {
		this.context = context;
		this.features = features;
		this.members = members;
		this.confirmations = confirmations;
	}

	@GetMapping
	public List<FeatureRow> list() {
		List<FeatureView> all = features.list();
		Map<UUID, MemberSummary> names = members.summaries(all.stream().map(FeatureView::updatedBy)
			.filter(Objects::nonNull).distinct().toList());
		return all.stream().map(f -> row(f, names)).toList();
	}

	/** Critical switches (maintenance, sign-ups, carpooling, payments) also need a fresh 2FA code. */
	@PutMapping("/{key}")
	public FeatureRow update(@PathVariable String key, @RequestBody FeatureUpdateRequest request,
			HttpServletRequest http) {
		if (features.definition(key).critical()) {
			confirmations.check(http);
		}
		AdminContext.Staff me = context.current();
		FeatureView updated = features.update(key, request.enabled(), request.audience(), request.cities(), me.actor());
		return row(updated, Map.of(me.userId(), new MemberSummary(me.userId(), me.displayName(), null, null, null,
				null, null, false, false, List.of())));
	}

	private static FeatureRow row(FeatureView f, Map<UUID, MemberSummary> names) {
		MemberSummary by = f.updatedBy() == null ? null : names.get(f.updatedBy());
		return new FeatureRow(f.key(), f.parent(), f.area(), f.label(), f.description(), f.phase(), f.built(),
				f.critical(), f.global(), f.enabled(), f.audience(), f.cities(), f.updatedAt(),
				by == null ? null : by.displayName());
	}
}
