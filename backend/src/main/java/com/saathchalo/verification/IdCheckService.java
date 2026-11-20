package com.saathchalo.verification;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.Masking;
import com.saathchalo.common.RateLimiter;
import com.saathchalo.files.DocumentVault;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import com.saathchalo.user.UserDtos.MeResponse;
import com.saathchalo.user.UserService;
import com.saathchalo.user.UserService.Identity;
import com.saathchalo.verification.VerificationDtos.DocumentType;
import com.saathchalo.verification.VerificationDtos.IdCheckView;
import com.saathchalo.verification.VerificationDtos.RejectReason;
import com.saathchalo.verification.VerificationDtos.StaffIdCheck;
import com.saathchalo.verification.VerificationDtos.VerificationStatus;
import com.saathchalo.verification.internal.IdCheck;
import com.saathchalo.verification.internal.IdCheckRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** ID checks: members send a photo of an ID, staff compare it with the profile and approve or reject it. */
@Service
public class IdCheckService {

	/** Photos are kept this long after the decision, in case the member questions it, then deleted. */
	static final Duration KEEP_PHOTO = Duration.ofDays(30);

	static final int MAX_PER_DAY = 3;

	private final IdCheckRepository checks;

	private final DocumentVault vault;

	private final UserService users;

	private final MemberProfiles members;

	private final AuditService audit;

	private final RateLimiter limiter;

	private final Clock clock;

	public IdCheckService(IdCheckRepository checks, DocumentVault vault, UserService users, MemberProfiles members,
			AuditService audit, RateLimiter limiter, Clock clock) {
		this.checks = checks;
		this.vault = vault;
		this.users = users;
		this.members = members;
		this.audit = audit;
		this.limiter = limiter;
		this.clock = clock;
	}

	// ----- Members -----

	@Transactional(readOnly = true)
	public VerificationStatus status(UUID me) {
		MeResponse profile = users.me(me);
		IdCheckView latest = checks.findFirstByUserIdOrderBySubmittedAtDesc(me).map(IdCheckService::view).orElse(null);
		return new VerificationStatus(profile.phoneVerified(), Masking.email(profile.email()), profile.emailVerified(),
				profile.idVerified(), latest);
	}

	@Transactional
	public IdCheckView submit(UUID me, DocumentType type, MultipartFile photo) {
		if (type == null) {
			throw ApiException.badRequest("document_type_missing", "Choose which ID this is.");
		}
		MeResponse profile = users.me(me);
		if (profile.idVerified()) {
			throw ApiException.conflict("already_verified", "Your ID is already verified.");
		}
		if (profile.firstName() == null || profile.firstName().isBlank()) {
			throw ApiException.badRequest("name_missing", "Add your name to your profile first, as it appears on your ID.");
		}
		if (checks.existsByUserIdAndStatus(me, IdCheck.PENDING)) {
			throw ApiException.conflict("check_pending", "We're already checking your ID. We'll update you here soon.");
		}
		Instant now = clock.instant();
		// Sent checks count towards the daily limit; failed uploads (say, a blurry or wrong file) only towards a looser one.
		if (checks.countByUserIdAndSubmittedAtAfter(me, now.minus(Duration.ofDays(1))) >= MAX_PER_DAY
				|| !limiter.tryAcquire("id-upload:" + me, 10, Duration.ofDays(1))) {
			throw ApiException.tooManyRequests("slow_down", "You've sent several photos today. Please try again tomorrow.");
		}
		String file = vault.store(photo);
		return view(checks.save(new IdCheck(me, type.name(), file, now)));
	}

	// ----- Staff -----

	/** {@code status}: PENDING (oldest first), APPROVED or REJECTED (newest first). */
	@Transactional(readOnly = true)
	public List<StaffIdCheck> queue(String status) {
		List<IdCheck> list = IdCheck.PENDING.equals(status)
				? checks.findByStatusOrderBySubmittedAtAsc(status, PageRequest.of(0, 200))
				: checks.findByStatusOrderByDecidedAtDesc(status, PageRequest.of(0, 100));
		Set<UUID> people = new HashSet<>();
		list.forEach(c -> {
			people.add(c.getUserId());
			if (c.getDecidedBy() != null) people.add(c.getDecidedBy());
		});
		Map<UUID, MemberCard> cards = members.cards(people);
		return list.stream().map(c -> staffView(c, cards)).toList();
	}

	@Transactional(readOnly = true)
	public long pendingCount() {
		return checks.countByStatus(IdCheck.PENDING);
	}

	/** The decrypted photo. Every view is recorded in the audit log. */
	@Transactional
	public byte[] photo(UUID checkId, Actor actor) {
		IdCheck c = find(checkId);
		byte[] jpeg = vault.read(c.getFileName())
			.orElseThrow(() -> ApiException.notFound("photo_deleted", "This photo has been deleted, as planned after the decision."));
		audit.record(actor, "id_check.photo_viewed", "member", c.getUserId().toString(), "Viewed an ID photo", null);
		return jpeg;
	}

	@Transactional
	public StaffIdCheck approve(UUID checkId, Actor actor) {
		IdCheck c = pending(checkId);
		c.approve(actor.id(), clock.instant());
		users.setIdVerified(c.getUserId(), true);
		audit.record(actor, "id_check.approved", "member", c.getUserId().toString(),
				"Approved an ID check (" + c.getDocumentType() + ")", null);
		return staffView(c);
	}

	@Transactional
	public StaffIdCheck reject(UUID checkId, RejectReason reason, String note, Actor actor) {
		IdCheck c = pending(checkId);
		Instant now = clock.instant();
		c.reject(actor.id(), reason.name(), note == null || note.isBlank() ? null : note.strip(), now);
		if (reason == RejectReason.AADHAAR_NOT_MASKED) {
			// We must not keep a full Aadhaar number: delete the photo straight away.
			vault.deleteQuietly(c.getFileName());
			c.photoDeleted(now);
		}
		audit.record(actor, "id_check.rejected", "member", c.getUserId().toString(),
				"Rejected an ID check (" + c.getDocumentType() + "): " + reason.name(), null);
		return staffView(c);
	}

	/** Deletes photos {@link #KEEP_PHOTO} after the decision. The decision itself is kept. */
	@Transactional
	public int deleteOldPhotos() {
		Instant now = clock.instant();
		List<IdCheck> old = checks.findPhotosToDelete(now.minus(KEEP_PHOTO));
		old.forEach(c -> {
			vault.deleteQuietly(c.getFileName());
			c.photoDeleted(now);
		});
		return old.size();
	}

	private IdCheck find(UUID id) {
		return checks.findById(id).orElseThrow(() -> ApiException.notFound("check_not_found", "We couldn't find that ID check."));
	}

	private IdCheck pending(UUID id) {
		IdCheck c = find(id);
		if (!c.isPending()) {
			throw ApiException.conflict("already_decided", "Someone has already decided on this ID check.");
		}
		return c;
	}

	private StaffIdCheck staffView(IdCheck c) {
		Set<UUID> people = new HashSet<>(List.of(c.getUserId()));
		if (c.getDecidedBy() != null) people.add(c.getDecidedBy());
		return staffView(c, members.cards(people));
	}

	private StaffIdCheck staffView(IdCheck c, Map<UUID, MemberCard> cards) {
		Identity who = users.identity(c.getUserId()).orElse(new Identity(null, null, null, null));
		String fullName = ((who.firstName() == null ? "" : who.firstName()) + " " + (who.lastName() == null ? "" : who.lastName())).strip();
		Integer age = who.dateOfBirth() == null ? null : Period.between(who.dateOfBirth(), LocalDate.now(clock)).getYears();
		MemberCard decider = c.getDecidedBy() == null ? null : cards.get(c.getDecidedBy());
		return new StaffIdCheck(c.getId(), cards.getOrDefault(c.getUserId(), MemberProfiles.deleted()), fullName.isEmpty() ? null : fullName,
				who.gender(), who.dateOfBirth(), age, DocumentType.valueOf(c.getDocumentType()), c.getStatus(), c.getSubmittedAt(),
				c.getDecidedAt(), decider == null ? null : decider.displayName(),
				c.getRejectReason() == null ? null : RejectReason.valueOf(c.getRejectReason()), c.getRejectNote(), c.getFileName() != null);
	}

	static IdCheckView view(IdCheck c) {
		return new IdCheckView(c.getId(), DocumentType.valueOf(c.getDocumentType()), c.getStatus(), c.getSubmittedAt(),
				c.getDecidedAt(), c.getRejectReason() == null ? null : RejectReason.valueOf(c.getRejectReason()), c.getRejectNote());
	}
}
