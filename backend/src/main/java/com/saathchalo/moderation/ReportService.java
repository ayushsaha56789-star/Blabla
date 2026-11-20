package com.saathchalo.moderation;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.chat.ChatService;
import com.saathchalo.chat.ChatService.ReportedMessage;
import com.saathchalo.common.ApiException;
import com.saathchalo.moderation.ModerationDtos.Action;
import com.saathchalo.moderation.ModerationDtos.Reason;
import com.saathchalo.moderation.ModerationDtos.ReportReceipt;
import com.saathchalo.moderation.ModerationDtos.ReportRequest;
import com.saathchalo.moderation.ModerationDtos.ReportRow;
import com.saathchalo.moderation.ModerationDtos.TargetType;
import com.saathchalo.moderation.internal.Report;
import com.saathchalo.moderation.internal.ReportRepository;
import com.saathchalo.reviews.ReviewService;
import com.saathchalo.reviews.ReviewService.ReportedReview;
import com.saathchalo.rides.RideLookup;
import com.saathchalo.rides.RideLookup.RideParticipants;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import com.saathchalo.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports from members, and the moderation queue staff work through. Under the IT Rules 2021 a complaint must be
 * acknowledged within 24 hours and resolved within 15 days, so the queue shows both deadlines.
 */
@Service
public class ReportService {

	private static final Logger log = LoggerFactory.getLogger(ReportService.class);

	static final int MAX_PER_DAY = 10;

	static final Duration ACKNOWLEDGE_WITHIN = Duration.ofHours(24);

	static final Duration RESOLVE_WITHIN = Duration.ofDays(15);

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)
		.withZone(ZoneId.of("Asia/Kolkata"));

	private final ReportRepository reports;

	private final MemberProfiles members;

	private final UserService users;

	private final RideLookup rides;

	private final ChatService chat;

	private final ReviewService reviews;

	private final AuditService audit;

	private final Clock clock;

	public ReportService(ReportRepository reports, MemberProfiles members, UserService users, RideLookup rides,
			ChatService chat, ReviewService reviews, AuditService audit, Clock clock) {
		this.reports = reports;
		this.members = members;
		this.users = users;
		this.rides = rides;
		this.chat = chat;
		this.reviews = reviews;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public ReportReceipt submit(UUID me, ReportRequest req) {
		Instant now = clock.instant();
		if (reports.countByReporterIdAndCreatedAtAfter(me, now.minus(Duration.ofDays(1))) >= MAX_PER_DAY) {
			throw ApiException.tooManyRequests("slow_down", "You've sent a lot of reports today. Our team is working through them.");
		}
		UUID reported;
		String snapshot;
		switch (req.targetType()) {
			case MEMBER -> {
				var profile = users.publicProfile(req.targetId());
				reported = profile.id();
				snapshot = profile.displayName() + (profile.bio() == null ? "" : " — bio: " + profile.bio());
			}
			case RIDE -> {
				RideParticipants ride = rides.participants(req.targetId()).orElseThrow(ReportService::notFound);
				reported = ride.ride().driverId();
				snapshot = "Ride " + ride.ride().fromName() + " → " + ride.ride().toName() + ", "
						+ WHEN.format(ride.ride().departureAt());
			}
			case MESSAGE -> {
				ReportedMessage m = chat.messageForReport(me, req.targetId()).orElseThrow(ReportService::notFound);
				reported = m.senderId();
				snapshot = m.body();
			}
			case REVIEW -> {
				ReportedReview r = reviews.reviewForReport(req.targetId()).orElseThrow(ReportService::notFound);
				reported = r.authorId();
				snapshot = r.rating() + "★" + (r.comment() == null ? "" : ": " + r.comment());
			}
			default -> throw ReportService.notFound();
		}
		if (me.equals(reported)) {
			throw ApiException.badRequest("self_report", "You can't report yourself.");
		}
		String type = req.targetType().name();
		if (reports.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(me, type, req.targetId(), Report.OPEN)) {
			throw ApiException.conflict("already_reported", "You've already reported this. Our team is looking into it.");
		}
		String details = req.details() == null || req.details().isBlank() ? null : req.details().strip();
		Report saved = reports.save(new Report(me, type, req.targetId(), reported, req.reason().name(), details,
				snapshot == null ? null : snapshot.substring(0, Math.min(snapshot.length(), 2000)), now));
		log.info("Report {} received: {} about a {}", saved.getId(), req.reason(), type.toLowerCase(Locale.ROOT));
		return new ReportReceipt(saved.getId(), now);
	}

	// ----- Staff -------------------------------------------------------------------------------------------------

	/** Open reports oldest first (deadlines), or the 100 most recently closed. */
	@Transactional(readOnly = true)
	public List<ReportRow> queue(boolean open) {
		List<Report> list = open ? reports.findByStatusOrderByCreatedAtAsc(Report.OPEN)
				: reports.findByStatusNotOrderByResolvedAtDesc(Report.OPEN, PageRequest.of(0, 100));
		return rows(list);
	}

	@Transactional(readOnly = true)
	public long openCount() {
		return reports.countByStatus(Report.OPEN);
	}

	/** Open reports past their 15-day deadline, or not even acknowledged within 24 hours. */
	@Transactional(readOnly = true)
	public long overdueCount() {
		Instant now = clock.instant();
		return reports.findByStatusOrderByCreatedAtAsc(Report.OPEN).stream().filter(r -> overdue(r, now)).count();
	}

	@Transactional
	public ReportRow acknowledge(UUID reportId, Actor actor) {
		Report r = find(reportId);
		r.acknowledge(actor.id(), clock.instant());
		return rows(List.of(r)).getFirst();
	}

	@Transactional
	public ReportRow resolve(UUID reportId, Action action, String resolution, Actor actor) {
		Report r = find(reportId);
		if (!Report.OPEN.equals(r.getStatus())) {
			throw ApiException.conflict("already_closed", "This report is already closed.");
		}
		Instant now = clock.instant();
		String note = resolution.strip();
		List<Report> sameTarget = List.of(r);
		if (action == Action.REMOVE_CONTENT) {
			boolean removed = switch (TargetType.valueOf(r.getTargetType())) {
				case MESSAGE -> chat.hideMessage(r.getTargetId());
				case REVIEW -> reviews.hideReview(r.getTargetId());
				default -> throw ApiException.badRequest("not_removable",
						"Only messages and reviews can be removed. Restrict the member or cancel the ride instead.");
			};
			if (!removed) {
				throw ApiException.conflict("content_gone", "That content no longer exists. Close the report as \"action taken\".");
			}
			// Everyone who reported the same thing gets the same outcome.
			sameTarget = reports.findByTargetTypeAndTargetIdAndStatus(r.getTargetType(), r.getTargetId(), Report.OPEN);
		}
		String status = action == Action.DISMISS ? Report.DISMISSED : Report.ACTIONED;
		for (Report each : sameTarget) {
			each.close(status, note, actor.id(), now);
		}
		audit.record(actor, action == Action.DISMISS ? "report.dismissed" : "report.actioned", "report", reportId.toString(),
				(action == Action.DISMISS ? "Dismissed" : action == Action.REMOVE_CONTENT ? "Removed the content of" : "Acted on")
						+ " a report about a " + r.getTargetType().toLowerCase(Locale.ROOT),
				Map.of("reason", r.getReason(), "action", action.name(), "note", note, "reportsClosed", sameTarget.size()));
		return rows(List.of(r)).getFirst();
	}

	private Report find(UUID id) {
		return reports.findById(id).orElseThrow(() -> ApiException.notFound("report_not_found", "We couldn't find that report."));
	}

	private boolean overdue(Report r, Instant now) {
		return Report.OPEN.equals(r.getStatus()) && ((r.getAcknowledgedAt() == null && now.isAfter(r.getCreatedAt().plus(ACKNOWLEDGE_WITHIN)))
				|| now.isAfter(r.getCreatedAt().plus(RESOLVE_WITHIN)));
	}

	private List<ReportRow> rows(List<Report> list) {
		Instant now = clock.instant();
		Set<UUID> people = new HashSet<>();
		list.forEach(r -> {
			if (r.getReporterId() != null) people.add(r.getReporterId());
			if (r.getReportedUserId() != null) people.add(r.getReportedUserId());
			if (r.getHandledBy() != null) people.add(r.getHandledBy());
		});
		Map<UUID, MemberCard> cards = members.cards(people);
		Map<UUID, Long> counts = new HashMap<>();
		List<UUID> reported = list.stream().map(Report::getReportedUserId).filter(id -> id != null).distinct().toList();
		if (!reported.isEmpty()) {
			reports.countAboutMembers(reported).forEach(row -> counts.put((UUID) row[0], (Long) row[1]));
		}
		return list.stream().map(r -> new ReportRow(r.getId(), r.getStatus(), TargetType.valueOf(r.getTargetType()), r.getTargetId(),
				"RIDE".equals(r.getTargetType()) ? r.getTargetId() : null, Reason.valueOf(r.getReason()), r.getDetails(),
				r.getSnapshot(), card(cards, r.getReporterId()), card(cards, r.getReportedUserId()),
				counts.getOrDefault(r.getReportedUserId(), 0L), r.getCreatedAt(), r.getAcknowledgedAt(),
				r.getCreatedAt().plus(ACKNOWLEDGE_WITHIN), r.getCreatedAt().plus(RESOLVE_WITHIN), overdue(r, now),
				r.getResolvedAt(), r.getHandledBy() == null ? null : card(cards, r.getHandledBy()).displayName(), r.getResolution()))
			.toList();
	}

	private static MemberCard card(Map<UUID, MemberCard> cards, UUID id) {
		return id == null ? MemberProfiles.deleted() : cards.getOrDefault(id, MemberProfiles.deleted());
	}

	private static ApiException notFound() {
		return ApiException.notFound("not_found", "We couldn't find what you're reporting. It may have been removed.");
	}
}
