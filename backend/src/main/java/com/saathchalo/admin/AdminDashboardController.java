package com.saathchalo.admin;

import com.saathchalo.access.AccessService;
import com.saathchalo.access.Perm;
import com.saathchalo.access.Permission;
import com.saathchalo.admin.AdminDtos.Dashboard;
import com.saathchalo.audit.AuditFilter;
import com.saathchalo.audit.AuditService;
import com.saathchalo.features.FeatureCatalog;
import com.saathchalo.features.FeatureService;
import com.saathchalo.moderation.ReportService;
import com.saathchalo.rides.RideAdmin;
import com.saathchalo.safety.SosService;
import com.saathchalo.support.SupportService;
import com.saathchalo.verification.IdCheckService;
import com.saathchalo.user.MemberDirectory;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

	private final AdminContext context;

	private final MemberDirectory members;

	private final AccessService access;

	private final FeatureService features;

	private final AuditService audit;

	private final RideAdmin rides;

	private final SosService sos;

	private final ReportService reports;

	private final SupportService support;

	private final IdCheckService idChecks;

	public AdminDashboardController(AdminContext context, MemberDirectory members, AccessService access,
			FeatureService features, AuditService audit, RideAdmin rides, SosService sos,
			ReportService reports, SupportService support, IdCheckService idChecks) {
		this.context = context;
		this.members = members;
		this.access = access;
		this.features = features;
		this.audit = audit;
		this.rides = rides;
		this.sos = sos;
		this.reports = reports;
		this.support = support;
		this.idChecks = idChecks;
	}

	@GetMapping
	@PreAuthorize(Perm.USERS_VIEW)
	public Dashboard dashboard() {
		boolean canSeeAudit = context.current().can(Permission.AUDIT_VIEW.code());
		long total = FeatureCatalog.all().stream().filter(d -> !d.global()).count();
		return new Dashboard(members.stats(14), rides.stats(), sos.openCount(), reports.openCount(), reports.overdueCount(),
				support.waitingForStaff(), idChecks.pendingCount(),
				access.countStaff(), features.countOn(), total,
				features.isMaintenance(),
				features.signupsOpen(),
				canSeeAudit ? audit.search(new AuditFilter(null, null, null, null, null), 0, 8).items() : List.of());
	}
}
