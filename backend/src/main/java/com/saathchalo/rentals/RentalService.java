package com.saathchalo.rentals;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.features.FeatureService;
import com.saathchalo.files.DocumentVault;
import com.saathchalo.payments.PaymentDtos.CheckoutView;
import com.saathchalo.payments.PaymentService;
import com.saathchalo.payments.PaymentService.StartPayment;
import com.saathchalo.rentals.RentalDtos.CheckView;
import com.saathchalo.rentals.RentalDtos.Fuel;
import com.saathchalo.rentals.RentalDtos.ListingCard;
import com.saathchalo.rentals.RentalDtos.Mode;
import com.saathchalo.rentals.RentalDtos.MyRentals;
import com.saathchalo.rentals.RentalDtos.RentRequest;
import com.saathchalo.rentals.RentalDtos.RentalView;
import com.saathchalo.rentals.internal.Listing;
import com.saathchalo.rentals.internal.RentalBooking;
import com.saathchalo.rentals.internal.RentalBookingRepository;
import com.saathchalo.user.BlockService;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import com.saathchalo.user.UserService;
import com.saathchalo.verification.IdCheckService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Renting a car: request (or instant booking), the owner's answer, paying, the pickup and return checks, and settling
 * up. The money itself is handled by the payments module ({@link RentalPayments} tells it how to share it out).
 */
@Service
public class RentalService {

	static final int MAX_CHECK_PHOTOS = 6;

	static final int MAX_OPEN_REQUESTS = 5;

	private final RentalBookingRepository bookings;

	private final ListingService listings;

	private final MemberProfiles members;

	private final UserService users;

	private final IdCheckService idChecks;

	private final BlockService blocks;

	private final PaymentService payments;

	private final FeatureService features;

	private final DocumentVault vault;

	private final AuditService audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final RentalNotices notices;

	public RentalService(RentalBookingRepository bookings, ListingService listings, MemberProfiles members, UserService users,
			IdCheckService idChecks, BlockService blocks, PaymentService payments, FeatureService features, DocumentVault vault,
			AuditService audit, JdbcTemplate jdbc, Clock clock, RentalNotices notices) {
		this.notices = notices;
		this.bookings = bookings;
		this.listings = listings;
		this.members = members;
		this.users = users;
		this.idChecks = idChecks;
		this.blocks = blocks;
		this.payments = payments;
		this.features = features;
		this.vault = vault;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// ----- Renters -----

	@Transactional
	public RentalView rent(UUID renter, RentRequest req) {
		Listing l = listings.load(req.listingId());
		Instant now = clock.instant();
		if (!Listing.LIVE.equals(l.getStatus())) {
			throw ApiException.conflict("listing_unavailable", "This car isn't available for rent right now.");
		}
		if (l.getOwnerId().equals(renter)) {
			throw ApiException.badRequest("own_car", "This is your own car.");
		}
		if (!members.traveller(renter).profileComplete()) {
			throw ApiException.forbidden("profile_incomplete", "Add your first name and gender to your profile before renting.");
		}
		if (blocks.blockedEitherWay(renter, l.getOwnerId())) {
			throw ApiException.forbidden("blocked", "You can't rent this car.");
		}
		boolean selfDrive = req.mode() == Mode.SELF_DRIVE;
		if (selfDrive ? !l.isSelfDrive() || !features.isOnFor("rentals.self_drive", renter)
				: !l.isWithDriver() || !features.isOnFor("rentals.with_driver", renter)) {
			throw ApiException.badRequest("mode_unavailable", selfDrive ? "This car isn't offered for self-drive."
					: "This car isn't offered with a driver.");
		}
		if (selfDrive) {
			if (!idChecks.licenceVerified(renter)) {
				throw ApiException.forbidden("licence_needed",
						"Self-drive needs a verified driving licence. Add it on your profile under Verification.");
			}
			LocalDate born = users.identity(renter).map(UserService.Identity::dateOfBirth).orElse(null);
			if (born == null || Period.between(born, LocalDate.now(ZoneId.of("Asia/Kolkata"))).getYears() < l.getMinAge()) {
				throw ApiException.forbidden("too_young", born == null ? "Add your date of birth to your profile first."
						: "The owner rents this car for self-drive to people aged " + l.getMinAge() + " or more.");
			}
		}
		String problem = RentalRules.periodProblem(l, req.from(), req.to(), now);
		if (problem != null) {
			throw ApiException.badRequest("bad_period", problem);
		}
		if (bookings.countByRenterIdAndStatusIn(renter, Set.of(RentalBooking.REQUESTED)) >= MAX_OPEN_REQUESTS) {
			throw ApiException.tooManyRequests("too_many_requests", "You have several requests waiting. Wait for an answer first.");
		}
		if (!listings.isFree(l.getId(), req.from(), req.to())) {
			throw ApiException.conflict("not_available", "The car isn't free for all of that time. Try other dates.");
		}
		long hours = RentalRules.hours(req.from(), req.to());
		RentalBooking b = new RentalBooking(l.getId(), renter, l.getOwnerId(), req.mode().name(), req.from(), req.to(),
				RentalRules.rent(l, hours), selfDrive ? l.getDeposit() : 0, RentalRules.kmIncluded(l, hours), l.getExtraKmPrice(),
				payments.availableFor(renter), req.message() == null || req.message().isBlank() ? null : req.message().strip(), now);
		if (l.isInstant() && features.isOnFor("rentals.instant_book", renter)) {
			b.accept(now, payBy(b, now));
		}
		saveHolding(b, "The car was just booked for that time. Try other dates.");
		notices.requested(b);
		return view(b, renter);
	}

	/** Starts (or resumes) the online payment for an accepted booking. */
	public CheckoutView pay(UUID renter, UUID id, boolean useCredits) {
		RentalBooking b = bookings.findById(id).filter(x -> x.getRenterId().equals(renter)).orElseThrow(RentalService::notFound);
		if (!RentalBooking.ACCEPTED.equals(b.getStatus()) || !b.isPayOnline()) {
			throw ApiException.conflict("nothing_to_pay", "There's nothing to pay online for this booking.");
		}
		if (b.getPayBy() == null || !b.getPayBy().isAfter(clock.instant())) {
			throw ApiException.conflict("too_late_to_pay", "The time to pay has passed. Please book again.");
		}
		return payments.startPayment(new StartPayment(RentalPayments.KIND, b.getId(), renter, b.getOwnerId(), b.getRent() * 100L,
				b.getDeposit() * 100L, b.getPayBy(), useCredits));
	}

	/** The renter or the owner cancels before pickup. Refunds follow the rules (see {@link RentalPayments}). */
	@Transactional
	public RentalView cancel(UUID me, UUID id) {
		RentalBooking b = lock(id);
		boolean renter = b.getRenterId().equals(me);
		if (!renter && !b.getOwnerId().equals(me)) {
			throw notFound();
		}
		if (!Set.of(RentalBooking.REQUESTED, RentalBooking.ACCEPTED, RentalBooking.PAID).contains(b.getStatus())) {
			throw ApiException.conflict("cant_cancel", "This booking can't be cancelled now.");
		}
		b.cancel(renter ? "RENTER" : "OWNER", clock.instant());
		notices.cancelled(b, renter ? "RENTER" : "OWNER");
		return view(b, me);
	}

	/** The renter accepts the return (and any charges): the money is settled. */
	@Transactional
	public RentalView acceptReturn(UUID renter, UUID id) {
		RentalBooking b = lock(id);
		if (!b.getRenterId().equals(renter)) {
			throw notFound();
		}
		if (!RentalBooking.RETURNED.equals(b.getStatus())) {
			throw ApiException.conflict("not_returned", "The car hasn't been returned yet.");
		}
		b.complete(clock.instant());
		return view(b, renter);
	}

	@Transactional
	public RentalView dispute(UUID renter, UUID id, String note) {
		RentalBooking b = lock(id);
		if (!b.getRenterId().equals(renter)) {
			throw notFound();
		}
		if (!RentalBooking.RETURNED.equals(b.getStatus())) {
			throw ApiException.conflict("not_returned", "You can question the charges once the car is returned.");
		}
		b.dispute(note.strip());
		return view(b, renter);
	}

	// ----- Owners -----

	@Transactional
	public RentalView accept(UUID owner, UUID id) {
		RentalBooking b = lock(id);
		if (!b.getOwnerId().equals(owner)) {
			throw notFound();
		}
		if (!RentalBooking.REQUESTED.equals(b.getStatus())) {
			throw ApiException.conflict("not_requested", "This request was already answered or cancelled.");
		}
		Instant now = clock.instant();
		if (!b.getStartsAt().isAfter(now)) {
			throw ApiException.conflict("too_late", "The start time of this request has passed.");
		}
		b.accept(now, payBy(b, now));
		saveHolding(b, "You already have a booking for part of that time.");
		notices.answered(b, true);
		return view(b, owner);
	}

	@Transactional
	public RentalView decline(UUID owner, UUID id) {
		RentalBooking b = lock(id);
		if (!b.getOwnerId().equals(owner)) {
			throw notFound();
		}
		if (!RentalBooking.REQUESTED.equals(b.getStatus())) {
			throw ApiException.conflict("not_requested", "This request was already answered or cancelled.");
		}
		b.decline(clock.instant());
		notices.answered(b, false);
		return view(b, owner);
	}

	/** The owner hands the car over, recording odometer, fuel and photos. */
	@Transactional
	public RentalView pickup(UUID me, UUID id, int odometer, Fuel fuel, String notes, List<MultipartFile> photos) {
		RentalBooking b = lock(id);
		String role = roleOf(b, me);
		if ("OWNER".equals(role)) {
			String ready = b.isPayOnline() ? RentalBooking.PAID : RentalBooking.ACCEPTED;
			if (!ready.equals(b.getStatus())) {
				throw ApiException.conflict("not_ready", b.isPayOnline() && RentalBooking.ACCEPTED.equals(b.getStatus())
						? "The renter hasn't paid yet." : "This booking isn't ready for pickup.");
			}
			if (clock.instant().isBefore(b.getStartsAt().minus(java.time.Duration.ofHours(3)))) {
				throw ApiException.conflict("too_early", "Hand the car over closer to the pickup time (from 3 hours before).");
			}
			saveCheck(b, "PICKUP", role, odometer, fuel, notes, photos);
			b.pickedUp(clock.instant());
		}
		else {
			// The renter adds their own record of the car's state (optional, but it helps with disputes).
			if (!RentalBooking.PICKED_UP.equals(b.getStatus())) {
				throw ApiException.conflict("not_picked_up", "You can add your pickup photos once the owner has handed the car over.");
			}
			saveCheck(b, "PICKUP", role, odometer, fuel, notes, photos);
		}
		return view(b, me);
	}

	/**
	 * The owner takes the car back: odometer, fuel and photos. Extra kilometres are charged automatically; other
	 * charges (fuel, damage) need a note. Everything is kept from the deposit, never more than it.
	 */
	@Transactional
	public RentalView returnCar(UUID owner, UUID id, int odometer, Fuel fuel, String notes, int otherCharges, String chargesNote,
			List<MultipartFile> photos) {
		RentalBooking b = lock(id);
		if (!b.getOwnerId().equals(owner)) {
			throw notFound();
		}
		if (!RentalBooking.PICKED_UP.equals(b.getStatus())) {
			throw ApiException.conflict("not_picked_up", "The car hasn't been picked up.");
		}
		Integer start = startOdometer(b.getId());
		if (start != null && odometer < start) {
			throw ApiException.badRequest("odometer_too_low", "The odometer can't be lower than at pickup (" + start + " km).");
		}
		if (otherCharges < 0) {
			throw ApiException.badRequest("bad_charges", "Charges can't be negative.");
		}
		if (otherCharges > 0 && (chargesNote == null || chargesNote.isBlank())) {
			throw ApiException.badRequest("note_missing", "Explain the other charges (fuel or damage), with photos.");
		}
		saveCheck(b, "RETURN", "OWNER", odometer, fuel, notes, photos);
		int extraKm = start == null ? 0 : extraKmCharge(b, odometer - start);
		int charges = Math.min(b.getDeposit(), extraKm + otherCharges);
		String note = otherCharges > 0 ? chargesNote.strip() : null;
		b.returned(charges, note, clock.instant());
		return view(b, owner);
	}

	// ----- Everyone involved -----

	@Transactional(readOnly = true)
	public MyRentals mine(UUID me) {
		List<RentalBooking> renting = bookings.findByRenterIdOrderByStartsAtDesc(me, PageRequest.of(0, 100));
		List<RentalBooking> lending = bookings.findByOwnerIdOrderByStartsAtDesc(me, PageRequest.of(0, 200));
		return new MyRentals(views(renting, me), views(lending, me));
	}

	@Transactional(readOnly = true)
	public RentalView get(UUID me, UUID id) {
		RentalBooking b = bookings.findById(id).orElseThrow(RentalService::notFound);
		roleOf(b, me);
		return view(b, me);
	}

	/** A check photo, for the renter, the owner or (through the console, audited) staff. */
	@Transactional(readOnly = true)
	public byte[] photo(UUID me, UUID id, String fileName) {
		RentalBooking b = bookings.findById(id).orElseThrow(RentalService::notFound);
		if (me != null) {
			roleOf(b, me);
		}
		boolean belongs = jdbc.queryForList("SELECT photos FROM rental_checks WHERE booking_id = ?", String.class, id).stream()
			.flatMap(p -> Arrays.stream(p.split(","))).anyMatch(fileName::equals);
		if (!belongs) {
			throw ApiException.notFound("photo_not_found", "We couldn't find that photo.");
		}
		return vault.read(fileName).orElseThrow(() -> ApiException.notFound("photo_not_found", "We couldn't find that photo."));
	}

	// ----- Staff -----

	@Transactional(readOnly = true)
	public List<RentalView> forStaff(String filter) {
		List<RentalBooking> list = "disputes".equals(filter)
				? bookings.findByStatusOrderByCreatedAtDesc(RentalBooking.DISPUTED, PageRequest.of(0, 200))
				: bookings.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100));
		return views(list, null);
	}

	@Transactional(readOnly = true)
	public long disputesOpen() {
		return bookings.countByStatus(RentalBooking.DISPUTED);
	}

	@Transactional
	public byte[] staffPhoto(UUID id, String fileName, Actor actor) {
		byte[] jpeg = photo(null, id, fileName);
		audit.record(actor, "rental.photo_viewed", "rental_booking", id.toString(), "Viewed a rental check photo", null);
		return jpeg;
	}

	/** Staff settle a dispute: how much of the deposit the owner keeps, and why. */
	@Transactional
	public RentalView resolve(UUID id, int extraCharges, String resolution, Actor actor) {
		RentalBooking b = lock(id);
		if (!RentalBooking.DISPUTED.equals(b.getStatus())) {
			throw ApiException.conflict("not_disputed", "This booking has no open dispute.");
		}
		if (extraCharges > b.getDeposit()) {
			throw ApiException.badRequest("too_much", "Charges can't be more than the deposit (₹" + b.getDeposit() + ").");
		}
		b.resolve(extraCharges, resolution.strip(), clock.instant());
		audit.record(actor, "rental.dispute_resolved", "rental_booking", id.toString(),
				"Settled a rental dispute: ₹" + extraCharges + " from the deposit to the owner", Map.of("resolution", resolution.strip()));
		return view(b, null);
	}

	/** Staff cancel a booking (say, after a safety report). Everything is refunded. */
	@Transactional
	public RentalView staffCancel(UUID id, Actor actor) {
		RentalBooking b = lock(id);
		if (!Set.of(RentalBooking.REQUESTED, RentalBooking.ACCEPTED, RentalBooking.PAID).contains(b.getStatus())) {
			throw ApiException.conflict("cant_cancel", "Only bookings before pickup can be cancelled.");
		}
		b.cancel("STAFF", clock.instant());
		audit.record(actor, "rental.cancelled", "rental_booking", id.toString(), "Cancelled a rental booking", null);
		notices.cancelled(b, "STAFF");
		return view(b, null);
	}

	// ----- Inside -----

	/** An online booking must be paid within a day of being accepted, and an hour before the start. */
	private static Instant payBy(RentalBooking b, Instant now) {
		Instant byStart = b.getStartsAt().minus(java.time.Duration.ofHours(1));
		Instant inADay = now.plus(RentalRules.PAY_WITHIN);
		Instant by = byStart.isBefore(inADay) ? byStart : inADay;
		return by.isAfter(now) ? by : b.getStartsAt();
	}

	/** Saves a booking that holds the car; the database refuses it if it overlaps another (double booking). */
	private void saveHolding(RentalBooking b, String clash) {
		try {
			bookings.saveAndFlush(b);
		}
		catch (DataIntegrityViolationException e) {
			throw ApiException.conflict("not_available", clash);
		}
	}

	private int extraKmCharge(RentalBooking b, int kmDriven) {
		return Math.max(0, kmDriven - b.getKmIncluded()) * b.getExtraKmPrice();
	}

	private Integer startOdometer(UUID bookingId) {
		List<Integer> rows = jdbc.queryForList("SELECT odometer FROM rental_checks WHERE booking_id = ? AND stage = 'PICKUP' AND by_role = 'OWNER'",
				Integer.class, bookingId);
		return rows.isEmpty() ? null : rows.getFirst();
	}

	private void saveCheck(RentalBooking b, String stage, String role, int odometer, Fuel fuel, String notes, List<MultipartFile> photos) {
		if (fuel == null) {
			throw ApiException.badRequest("fuel_missing", "Choose the fuel level.");
		}
		if (odometer < 0 || odometer > 2_000_000) {
			throw ApiException.badRequest("bad_odometer", "Enter the odometer reading in km.");
		}
		List<MultipartFile> files = photos == null ? List.of() : photos.stream().filter(f -> f != null && !f.isEmpty()).toList();
		if (files.size() > MAX_CHECK_PHOTOS) {
			throw ApiException.badRequest("too_many_photos", "Up to " + MAX_CHECK_PHOTOS + " photos.");
		}
		if ("OWNER".equals(role) && files.isEmpty()) {
			throw ApiException.badRequest("photos_missing", "Add at least one photo of the car (all four sides is best).");
		}
		List<String> stored = new ArrayList<>();
		files.forEach(f -> stored.add(vault.store(f)));
		int updated = jdbc.update("""
				INSERT INTO rental_checks (id, booking_id, stage, by_role, odometer, fuel, notes, photos, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (booking_id, stage, by_role) DO NOTHING""",
				UUID.randomUUID(), b.getId(), stage, role, odometer, fuel.name(), notes == null || notes.isBlank() ? null : notes.strip(),
				String.join(",", stored), Timestamp.from(clock.instant()));
		if (updated == 0) {
			stored.forEach(vault::deleteQuietly);
			throw ApiException.conflict("already_recorded", "This was already recorded.");
		}
	}

	private RentalBooking lock(UUID id) {
		return bookings.lockById(id).orElseThrow(RentalService::notFound);
	}

	private static String roleOf(RentalBooking b, UUID me) {
		if (b.getRenterId().equals(me)) {
			return "RENTER";
		}
		if (b.getOwnerId().equals(me)) {
			return "OWNER";
		}
		throw notFound();
	}

	RentalView view(RentalBooking b, UUID viewer) {
		return views(List.of(b), viewer).getFirst();
	}

	private List<RentalView> views(List<RentalBooking> list, UUID viewer) {
		if (list.isEmpty()) {
			return List.of();
		}
		Set<UUID> listingIds = list.stream().map(RentalBooking::getListingId).collect(Collectors.toSet());
		Map<UUID, Listing> byId = new java.util.HashMap<>();
		listingIds.forEach(id -> byId.put(id, listings.load(id)));
		Map<UUID, ListingCard> cards = listings.cards(byId.values(), null, null, null).stream()
			.collect(Collectors.toMap(ListingCard::id, c -> c));
		Set<UUID> people = new HashSet<>();
		list.forEach(b -> {
			people.add(b.getRenterId());
			people.add(b.getOwnerId());
		});
		Map<UUID, MemberCard> memberCards = members.cards(people);
		Map<UUID, List<CheckView>> checks = checks(list.stream().map(RentalBooking::getId).toList());
		return list.stream().map(b -> {
			String role = viewer == null ? "STAFF" : b.getRenterId().equals(viewer) ? "RENTER" : "OWNER";
			boolean agreed = Set.of(RentalBooking.ACCEPTED, RentalBooking.PAID, RentalBooking.PICKED_UP, RentalBooking.RETURNED,
					RentalBooking.DISPUTED).contains(b.getStatus());
			String otherPhone = agreed && viewer != null
					? members.phone("RENTER".equals(role) ? b.getOwnerId() : b.getRenterId()).orElse(null) : null;
			List<CheckView> cs = checks.getOrDefault(b.getId(), List.of());
			Integer kmDriven = kmDriven(cs);
			return new RentalView(b.getId(), role, cards.get(b.getListingId()), Mode.valueOf(b.getMode()), b.getStartsAt(), b.getEndsAt(),
					b.getRent(), b.getDeposit(), b.getKmIncluded(), b.getExtraKmPrice(), b.isPayOnline(), b.getPayBy(), b.getStatus(),
					memberCards.getOrDefault(b.getRenterId(), MemberProfiles.deleted()),
					memberCards.getOrDefault(b.getOwnerId(), MemberProfiles.deleted()), otherPhone,
					agreed || viewer == null ? byId.get(b.getListingId()).getPickupAddress() : null, b.getMessage(), cs, kmDriven,
					kmDriven == null ? 0 : extraKmCharge(b, kmDriven), b.getExtraCharges(), b.getExtraNote(), b.getDisputeNote(),
					b.getResolution(), b.getCreatedAt(), b.getDecidedAt(), b.getPickedUpAt(), b.getReturnedAt(),
					b.getReturnedAt() == null ? null : b.getReturnedAt().plus(RentalRules.RESPOND_WITHIN), b.getCompletedAt(),
					b.getCancelledAt(), b.getCancelledBy());
		}).toList();
	}

	private static Integer kmDriven(List<CheckView> checks) {
		Integer start = null;
		Integer end = null;
		for (CheckView c : checks) {
			if ("OWNER".equals(c.byRole()) && "PICKUP".equals(c.stage())) {
				start = c.odometer();
			}
			if ("OWNER".equals(c.byRole()) && "RETURN".equals(c.stage())) {
				end = c.odometer();
			}
		}
		return start == null || end == null ? null : end - start;
	}

	private Map<UUID, List<CheckView>> checks(List<UUID> bookingIds) {
		Map<UUID, List<CheckView>> out = new java.util.HashMap<>();
		jdbc.query("""
				SELECT booking_id, stage, by_role, odometer, fuel, notes, photos, created_at FROM rental_checks
				WHERE booking_id = ANY (?) ORDER BY created_at""", rs -> {
			UUID bookingId = rs.getObject("booking_id", UUID.class);
			String photoList = rs.getString("photos");
			List<String> urls = photoList == null || photoList.isBlank() ? List.of()
					: Arrays.stream(photoList.split(",")).map(f -> "/api/rentals/bookings/" + bookingId + "/photos/" + f).toList();
			out.computeIfAbsent(bookingId, k -> new ArrayList<>()).add(new CheckView(rs.getString("stage"), rs.getString("by_role"),
					rs.getInt("odometer"), Fuel.valueOf(rs.getString("fuel")), rs.getString("notes"), urls,
					rs.getTimestamp("created_at").toInstant()));
		}, (Object) bookingIds.toArray(new UUID[0]));
		return out;
	}

	static ApiException notFound() {
		return ApiException.notFound("rental_not_found", "We couldn't find that booking.");
	}
}
