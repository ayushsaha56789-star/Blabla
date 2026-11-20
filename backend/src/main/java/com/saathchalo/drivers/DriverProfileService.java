package com.saathchalo.drivers;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.FieldEncryptor;
import com.saathchalo.drivers.DriverDtos.AdminDriverRow;
import com.saathchalo.drivers.DriverDtos.AreaRequest;
import com.saathchalo.drivers.DriverDtos.AreaView;
import com.saathchalo.drivers.DriverDtos.BlockRequest;
import com.saathchalo.drivers.DriverDtos.BlockView;
import com.saathchalo.drivers.DriverDtos.DriverCard;
import com.saathchalo.drivers.DriverDtos.DriverDetail;
import com.saathchalo.drivers.DriverDtos.DriverRequest;
import com.saathchalo.drivers.DriverDtos.LicenceClass;
import com.saathchalo.drivers.DriverDtos.LicenceNumber;
import com.saathchalo.drivers.DriverDtos.MyDriverProfile;
import com.saathchalo.drivers.DriverDtos.Quote;
import com.saathchalo.drivers.DriverDtos.Rates;
import com.saathchalo.drivers.DriverDtos.ReviewView;
import com.saathchalo.drivers.DriverDtos.Sort;
import com.saathchalo.drivers.DriverDtos.TripKind;
import com.saathchalo.drivers.internal.DriverProfile;
import com.saathchalo.drivers.internal.DriverProfileRepository;
import com.saathchalo.drivers.internal.DriverTrip;
import com.saathchalo.drivers.internal.DriverTripRepository;
import com.saathchalo.files.DocumentVault;
import com.saathchalo.geo.Place;
import com.saathchalo.geo.PlaceDirectory;
import com.saathchalo.user.BlockService;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import com.saathchalo.user.MemberProfiles.Traveller;
import com.saathchalo.verification.IdCheckService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Driver partners: signing up (licence, areas, rates, time off), the staff check, and how members find a driver.
 * Trips themselves are in {@link DriverTripService}.
 */
@Service
public class DriverProfileService {

	private static final String SEARCH = """
			SELECT p.user_id, min(ST_Distance(a.location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)) / 1000.0 AS km
			FROM driver_profiles p JOIN driver_areas a ON a.driver_id = p.user_id
			WHERE p.status = 'LIVE' AND p.taking_bookings
			  AND ST_DWithin(a.location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, a.radius_km * 1000)
			  AND (p.automatic OR NOT ?) AND (p.manual OR ?)
			  AND (p.outstation OR NOT ?)
			  AND p.licence_valid_until >= ?
			  AND NOT EXISTS (SELECT 1 FROM driver_trips t WHERE t.driver_id = p.user_id
			      AND t.status IN ('ACCEPTED', 'PAID', 'ON_THE_WAY', 'STARTED') AND t.period && tstzrange(?, ?, '[)'))
			  AND NOT EXISTS (SELECT 1 FROM driver_blocks b WHERE b.driver_id = p.user_id AND b.period && tstzrange(?, ?, '[)'))
			GROUP BY p.user_id""";

	private final DriverProfileRepository profiles;

	private final DriverTripRepository trips;

	private final IdCheckService idChecks;

	private final MemberProfiles members;

	private final BlockService blocks;

	private final PlaceDirectory places;

	private final FieldEncryptor encryptor;

	private final DocumentVault vault;

	private final AuditService audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public DriverProfileService(DriverProfileRepository profiles, DriverTripRepository trips, IdCheckService idChecks,
			MemberProfiles members, BlockService blocks, PlaceDirectory places, FieldEncryptor encryptor, DocumentVault vault,
			AuditService audit, JdbcTemplate jdbc, Clock clock) {
		this.profiles = profiles;
		this.trips = trips;
		this.idChecks = idChecks;
		this.members = members;
		this.blocks = blocks;
		this.places = places;
		this.encryptor = encryptor;
		this.vault = vault;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// ----- The driver -----

	@Transactional(readOnly = true)
	public Optional<MyDriverProfile> mine(UUID me) {
		return profiles.findById(me).filter(p -> !DriverProfile.REMOVED.equals(p.getStatus())).map(this::mine);
	}

	/** Signs up, or changes the details. New licence details send a live driver back for checking. */
	@Transactional
	public MyDriverProfile save(UUID me, DriverRequest req) {
		if (!members.traveller(me).profileComplete()) {
			throw ApiException.forbidden("profile_incomplete", "Add your first name and gender to your profile first.");
		}
		Instant now = clock.instant();
		DriverProfile p = profiles.lockById(me).orElse(null);
		boolean isNew = p == null;
		if (isNew) {
			p = new DriverProfile(me, now);
		}
		else if (DriverProfile.REMOVED.equals(p.getStatus())) {
			p.restart(now);
		}
		String typed = cleanLicence(req.licenceNumber());
		String saved = isNew ? null : decrypt(p.getLicenceNumber());
		if (typed == null && saved == null) {
			throw ApiException.badRequest("licence_number_missing", "Enter your driving licence number.");
		}
		if (typed != null && !typed.matches("[A-Z0-9]{8,20}")) {
			throw ApiException.badRequest("bad_licence_number", "A licence number has 8 to 20 letters and digits, like MH1420110062821.");
		}
		if (!req.licenceValidUntil().isAfter(LocalDate.now(DriverRules.INDIA))) {
			throw ApiException.badRequest("licence_expired", "Your licence must still be valid. Renew it first.");
		}
		boolean manual = !Boolean.FALSE.equals(req.manual());
		boolean automatic = !Boolean.FALSE.equals(req.automatic());
		if (!manual && !automatic) {
			throw ApiException.badRequest("no_gearbox", "Choose cars with gears, automatic cars, or both.");
		}
		if (req.dayPrice() < req.hourPrice()) {
			throw ApiException.badRequest("day_price_low", "Your price for a day can't be lower than for one hour.");
		}
		String number = typed != null ? typed : saved;
		boolean licenceChanged = !isNew && (!number.equals(saved) || !req.licenceClass().name().equals(p.getLicenceClass())
				|| !req.licenceValidUntil().equals(p.getLicenceValidUntil()));
		p.licence(encryptor.encrypt(number), req.licenceClass().name(), req.licenceValidUntil(), now);
		p.details(req.experienceYears(), String.join(",", cleanLanguages(req.languages())), manual, automatic,
				!Boolean.FALSE.equals(req.outstation()), req.hourPrice(), req.dayPrice(), req.outstationPrice(),
				req.allowance() == null ? 0 : req.allowance(), req.nightCharge() == null ? 0 : req.nightCharge(), blankToNull(req.about()), now);
		if (licenceChanged && DriverProfile.LIVE.equals(p.getStatus())) {
			p.submit(now);
		}
		profiles.save(p);
		return mine(p);
	}

	@Transactional
	public MyDriverProfile addArea(UUID me, AreaRequest req) {
		DriverProfile p = own(me);
		Place place = places.find(req.placeId())
			.orElseThrow(() -> ApiException.badRequest("place_not_found", "Choose a town from the list."));
		Long others = jdbc.queryForObject("SELECT count(*) FROM driver_areas WHERE driver_id = ? AND place_id <> ?", Long.class,
				me, place.id());
		if (others != null && others >= DriverRules.MAX_AREAS) {
			throw ApiException.badRequest("too_many_areas", "You can add up to " + DriverRules.MAX_AREAS + " areas.");
		}
		jdbc.update("""
				INSERT INTO driver_areas (id, driver_id, place_id, place_name, state, lat, lng, radius_km, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (driver_id, place_id) DO UPDATE SET radius_km = EXCLUDED.radius_km""",
				UUID.randomUUID(), me, place.id(), place.name(), place.state(), place.lat(), place.lng(), req.radiusKm(),
				Timestamp.from(clock.instant()));
		return mine(p);
	}

	@Transactional
	public MyDriverProfile removeArea(UUID me, UUID areaId) {
		DriverProfile p = own(me);
		jdbc.update("DELETE FROM driver_areas WHERE id = ? AND driver_id = ?", areaId, me);
		return mine(p);
	}

	/** An optional police verification certificate: stored encrypted, seen only by checking staff. */
	@Transactional
	public MyDriverProfile policeCertificate(UUID me, MultipartFile file) {
		DriverProfile p = own(me);
		String old = p.getPoliceCertificate();
		p.policeCertificate(vault.store(file), clock.instant());
		if (old != null) {
			vault.deleteQuietly(old);
		}
		return mine(p);
	}

	@Transactional
	public MyDriverProfile removePoliceCertificate(UUID me) {
		DriverProfile p = own(me);
		String old = p.getPoliceCertificate();
		p.policeCertificate(null, clock.instant());
		if (old != null) {
			vault.deleteQuietly(old);
		}
		return mine(p);
	}

	@Transactional
	public MyDriverProfile submit(UUID me) {
		DriverProfile p = own(me);
		if (DriverProfile.IN_REVIEW.equals(p.getStatus())) {
			throw ApiException.conflict("already_sent", "Your details are with our team already.");
		}
		if (DriverProfile.LIVE.equals(p.getStatus())) {
			throw ApiException.conflict("already_live", "You're already live.");
		}
		String licence = idChecks.licenceStatus(me);
		if (!"APPROVED".equals(licence) && !"PENDING".equals(licence)) {
			throw ApiException.badRequest("licence_not_checked",
					"First send your driving licence for checking: Profile → Verification → Driving licence.");
		}
		if (areas(me).isEmpty()) {
			throw ApiException.badRequest("areas_missing", "Add at least one area where you drive.");
		}
		if (members.card(me).avatarUrl() == null) {
			throw ApiException.badRequest("photo_missing", "Add a clear profile photo first, so members know who's coming.");
		}
		p.submit(clock.instant());
		return mine(p);
	}

	@Transactional
	public MyDriverProfile takingBookings(UUID me, boolean on) {
		DriverProfile p = own(me);
		p.takingBookings(on, clock.instant());
		return mine(p);
	}

	@Transactional
	public MyDriverProfile addBlock(UUID me, BlockRequest req) {
		DriverProfile p = own(me);
		Instant now = clock.instant();
		if (!req.to().isAfter(req.from()) || req.to().isBefore(now) || req.from().isAfter(now.plus(Duration.ofDays(366)))) {
			throw ApiException.badRequest("bad_period", "Choose a period in the coming year, with the end after the start.");
		}
		jdbc.update("INSERT INTO driver_blocks (id, driver_id, starts_at, ends_at, note, created_at) VALUES (?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), me, Timestamp.from(req.from()), Timestamp.from(req.to()), blankToNull(req.note()), Timestamp.from(now));
		return mine(p);
	}

	@Transactional
	public MyDriverProfile removeBlock(UUID me, UUID blockId) {
		DriverProfile p = own(me);
		jdbc.update("DELETE FROM driver_blocks WHERE id = ? AND driver_id = ?", blockId, me);
		return mine(p);
	}

	/** Stops being a driver partner (after any trips they're committed to). */
	@Transactional
	public void leave(UUID me) {
		DriverProfile p = own(me);
		if (trips.countByDriverIdAndStatusIn(me, DriverTrip.HOLDING) > 0) {
			throw ApiException.conflict("trips_active", "You have trips coming up. Finish or cancel them first.");
		}
		jdbc.update("UPDATE driver_trips SET status = 'DECLINED', decided_at = ? WHERE driver_id = ? AND status = 'REQUESTED'",
				Timestamp.from(clock.instant()), me);
		p.remove(clock.instant());
	}

	// ----- Members finding a driver -----

	/** Drivers who work at this place, drive this kind of car and are free for the whole trip. */
	@Transactional(readOnly = true)
	public List<DriverCard> search(UUID me, long placeId, TripKind kind, Instant from, Integer hours, Integer days, boolean automatic,
			boolean womenOnly, Sort sort) {
		Place place = places.find(placeId).orElseThrow(() -> ApiException.badRequest("place_not_found", "Choose a town from the list."));
		String problem = DriverRules.periodProblem(kind, from, hours, days, clock.instant());
		if (problem != null) {
			throw ApiException.badRequest("bad_period", problem);
		}
		Instant to = DriverRules.end(kind, from, hours, days);
		Map<UUID, Double> km = new LinkedHashMap<>();
		jdbc.query(SEARCH, rs -> {
			km.put(rs.getObject("user_id", UUID.class), rs.getDouble("km"));
		}, place.lng(), place.lat(), place.lng(), place.lat(), automatic, automatic, kind == TripKind.OUTSTATION,
				java.sql.Date.valueOf(to.atZone(DriverRules.INDIA).toLocalDate()), Timestamp.from(from.minus(DriverRules.GAP)),
				Timestamp.from(to.plus(DriverRules.GAP)), Timestamp.from(from), Timestamp.from(to));
		if (me != null) {
			km.remove(me);
			blocks.blockedEitherWay(me).forEach(km::remove);
			if (womenOnly && members.traveller(me).isWoman()) {
				Map<UUID, Traveller> who = members.travellers(km.keySet());
				km.keySet().removeIf(id -> who.get(id) == null || !who.get(id).isWoman());
			}
		}
		List<DriverCard> found = new ArrayList<>(cards(profiles.findAllById(km.keySet()), km,
				d -> DriverRules.quote(d, kind, from, hours, days).fare()));
		Comparator<DriverCard> nearest = Comparator.comparing(DriverCard::distanceKm);
		Comparator<DriverCard> order = switch (sort == null ? Sort.BEST : sort) {
			case PRICE -> Comparator.comparing(DriverCard::fare).thenComparing(nearest);
			case NEAREST -> nearest;
			case BEST -> Comparator.comparing((DriverCard c) -> c.rating() == null ? 0 : c.rating(), Comparator.reverseOrder())
				.thenComparing(DriverCard::tripsDone, Comparator.reverseOrder())
				.thenComparing(nearest);
		};
		found.sort(order);
		return found.size() > 40 ? found.subList(0, 40) : found;
	}

	@Transactional(readOnly = true)
	public DriverDetail detail(UUID me, UUID driverId) {
		DriverProfile d = profiles.findById(driverId)
			.filter(p -> DriverProfile.LIVE.equals(p.getStatus()) || driverId.equals(me))
			.orElseThrow(DriverProfileService::notFound);
		if (me != null && !driverId.equals(me) && blocks.blockedEitherWay(me, driverId)) {
			throw notFound();
		}
		List<Object[]> raw = new ArrayList<>();
		jdbc.query("""
				SELECT author_id, stars, comment, created_at FROM driver_reviews
				WHERE subject_id = ? AND by_role = 'CUSTOMER' ORDER BY created_at DESC LIMIT 10""", rs -> {
			raw.add(new Object[] { rs.getObject("author_id", UUID.class), rs.getInt("stars"), rs.getString("comment"),
					rs.getTimestamp("created_at").toInstant() });
		}, driverId);
		Map<UUID, MemberCard> authors = members.cards(raw.stream().map(r -> (UUID) r[0]).toList());
		List<ReviewView> reviews = raw.stream().map(r -> new ReviewView(
				authors.getOrDefault((UUID) r[0], MemberProfiles.deleted()).displayName(), (int) r[1], (String) r[2], (Instant) r[3]))
			.toList();
		return new DriverDetail(cards(List.of(d), null, null).getFirst(), d.getAbout(), LicenceClass.valueOf(d.getLicenceClass()),
				areas(driverId), reviews, driverId.equals(me));
	}

	@Transactional(readOnly = true)
	public Quote quote(UUID me, UUID driverId, long placeId, TripKind kind, Instant from, Integer hours, Integer days,
			boolean automatic) {
		DriverProfile d = profiles.findById(driverId).orElseThrow(DriverProfileService::notFound);
		String problem = DriverRules.periodProblem(kind, from, hours, days, clock.instant());
		if (problem != null) {
			return new Quote(false, problem, kind, from, null, 0, 0, false, 0, 0, 0, d.getHourPrice());
		}
		Quote q = DriverRules.quote(d, kind, from, hours, days);
		problem = availabilityProblem(d, placeId, kind, from, q.to(), automatic, me);
		return problem == null ? q : new Quote(false, problem, kind, from, q.to(), q.units(), q.base(), q.dayPriceApplied(), q.night(),
				q.allowance(), q.fare(), q.extraPrice());
	}

	/** Why this driver can't take this trip, in plain words, or null when they can. */
	String availabilityProblem(DriverProfile d, long placeId, TripKind kind, Instant from, Instant to, boolean automatic, UUID me) {
		if (!DriverProfile.LIVE.equals(d.getStatus()) || !d.isTakingBookings()) {
			return "This driver isn't taking bookings right now.";
		}
		if (d.getUserId().equals(me)) {
			return "That's you.";
		}
		if (automatic ? !d.isAutomatic() : !d.isManual()) {
			return automatic ? "This driver doesn't drive automatic cars." : "This driver drives only automatic cars.";
		}
		if (kind == TripKind.OUTSTATION && !d.isOutstation()) {
			return "This driver doesn't take outstation trips.";
		}
		if (d.getLicenceValidUntil().isBefore(to.atZone(DriverRules.INDIA).toLocalDate())) {
			return "This driver's licence runs out before the trip ends.";
		}
		Place place = places.find(placeId).orElse(null);
		if (place == null) {
			return "Choose a town from the list.";
		}
		if (!serves(d.getUserId(), place)) {
			return "This driver doesn't work in " + place.name() + ".";
		}
		if (!isFree(d.getUserId(), from, to)) {
			return "This driver is busy around then. Try another time or another driver.";
		}
		return null;
	}

	boolean serves(UUID driverId, Place place) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM driver_areas WHERE driver_id = ?
				    AND ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, radius_km * 1000))""",
				Boolean.class, driverId, place.lng(), place.lat()));
	}

	/** Free for the whole time, with an hour either side for getting there and back, and no time off. */
	boolean isFree(UUID driverId, Instant from, Instant to) {
		return !Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM driver_trips WHERE driver_id = ?
				           AND status IN ('ACCEPTED', 'PAID', 'ON_THE_WAY', 'STARTED') AND period && tstzrange(?, ?, '[)'))
				    OR EXISTS (SELECT 1 FROM driver_blocks WHERE driver_id = ? AND period && tstzrange(?, ?, '[)'))""",
				Boolean.class, driverId, Timestamp.from(from.minus(DriverRules.GAP)), Timestamp.from(to.plus(DriverRules.GAP)),
				driverId, Timestamp.from(from), Timestamp.from(to)));
	}

	// ----- Staff -----

	/** {@code status}: IN_REVIEW (waiting, oldest first) or ALL. */
	@Transactional(readOnly = true)
	public List<AdminDriverRow> forStaff(String status) {
		List<DriverProfile> list = DriverProfile.IN_REVIEW.equals(status)
				? profiles.findByStatusOrderByUpdatedAtAsc(DriverProfile.IN_REVIEW, PageRequest.of(0, 100))
				: profiles.findByStatusInOrderByUpdatedAtDesc(
						List.of(DriverProfile.LIVE, DriverProfile.IN_REVIEW, DriverProfile.REJECTED, DriverProfile.DRAFT), PageRequest.of(0, 200));
		return rows(list);
	}

	@Transactional(readOnly = true)
	public long waiting() {
		return profiles.countByStatus(DriverProfile.IN_REVIEW);
	}

	@Transactional
	public LicenceNumber revealLicence(UUID driverId, Actor actor) {
		DriverProfile p = profiles.findById(driverId).orElseThrow(DriverProfileService::notFound);
		audit.record(actor, "driver.licence_viewed", "driver", driverId.toString(), "Viewed a driver's licence number", null);
		return new LicenceNumber(decrypt(p.getLicenceNumber()));
	}

	@Transactional
	public byte[] policeCertificate(UUID driverId, Actor actor) {
		DriverProfile p = profiles.findById(driverId).orElseThrow(DriverProfileService::notFound);
		if (p.getPoliceCertificate() == null) {
			throw ApiException.notFound("certificate_not_found", "This driver hasn't added a police certificate.");
		}
		byte[] jpeg = vault.read(p.getPoliceCertificate())
			.orElseThrow(() -> ApiException.notFound("certificate_not_found", "We couldn't find that certificate."));
		audit.record(actor, "driver.certificate_viewed", "driver", driverId.toString(), "Viewed a driver's police certificate", null);
		return jpeg;
	}

	@Transactional
	public AdminDriverRow approve(UUID driverId, boolean policeChecked, Actor actor) {
		DriverProfile p = profiles.lockById(driverId).orElseThrow(DriverProfileService::notFound);
		if (!DriverProfile.IN_REVIEW.equals(p.getStatus())) {
			throw ApiException.conflict("not_waiting", "This driver isn't waiting for a check.");
		}
		if (!idChecks.licenceVerified(driverId)) {
			throw ApiException.conflict("licence_not_checked", "Their driving licence isn't approved yet. Check it under ID checks first.");
		}
		if (!p.getLicenceValidUntil().isAfter(LocalDate.now(DriverRules.INDIA))) {
			throw ApiException.conflict("licence_expired", "Their licence has expired.");
		}
		boolean police = policeChecked && p.getPoliceCertificate() != null;
		p.approve(actor.id(), police, clock.instant());
		audit.record(actor, "driver.approved", "driver", driverId.toString(),
				"Approved a driver partner" + (police ? " (police certificate checked)" : ""), null);
		return rows(List.of(p)).getFirst();
	}

	/**
	 * Sends a driver back with what to fix, or takes a live driver off the road. Their upcoming trips are cancelled
	 * (members get everything back).
	 */
	@Transactional
	public AdminDriverRow reject(UUID driverId, String note, Actor actor) {
		DriverProfile p = profiles.lockById(driverId).orElseThrow(DriverProfileService::notFound);
		if (!DriverProfile.IN_REVIEW.equals(p.getStatus()) && !DriverProfile.LIVE.equals(p.getStatus())) {
			throw ApiException.conflict("not_waiting", "Only drivers waiting for a check or live drivers can be sent back.");
		}
		boolean wasLive = DriverProfile.LIVE.equals(p.getStatus());
		Instant now = clock.instant();
		p.reject(actor.id(), note.strip(), now);
		int cancelled = jdbc.update("""
				UPDATE driver_trips SET status = 'CANCELLED', cancelled_by = 'STAFF', cancelled_at = ?,
				    cancel_reason = 'The driver is no longer available'
				WHERE driver_id = ? AND status IN ('REQUESTED', 'ACCEPTED', 'PAID', 'ON_THE_WAY')""", Timestamp.from(now), driverId);
		audit.record(actor, wasLive ? "driver.suspended" : "driver.rejected", "driver", driverId.toString(),
				(wasLive ? "Took a driver partner off the road" : "Sent a driver partner back") + (cancelled > 0 ? "; " + cancelled
						+ " upcoming trips cancelled" : ""), Map.of("note", note.strip()));
		return rows(List.of(p)).getFirst();
	}

	@Transactional
	public AdminDriverRow markPoliceChecked(UUID driverId, Actor actor) {
		DriverProfile p = profiles.lockById(driverId).orElseThrow(DriverProfileService::notFound);
		if (p.getPoliceCertificate() == null) {
			throw ApiException.conflict("no_certificate", "This driver hasn't added a police certificate.");
		}
		p.policeChecked(actor.id(), clock.instant());
		audit.record(actor, "driver.police_checked", "driver", driverId.toString(), "Checked a driver's police certificate", null);
		return rows(List.of(p)).getFirst();
	}

	// ----- Inside -----

	record Stats(Double rating, int reviews, int trips) {

		static final Stats NONE = new Stats(null, 0, 0);
	}

	/** Ratings from members they drove for, and trips done, per driver. */
	Map<UUID, Stats> stats(Collection<UUID> driverIds) {
		Map<UUID, Stats> out = new HashMap<>();
		if (driverIds.isEmpty()) {
			return out;
		}
		Object ids = driverIds.toArray(new UUID[0]);
		Map<UUID, double[]> rated = ratings(driverIds, "CUSTOMER");
		Map<UUID, Integer> done = new HashMap<>();
		jdbc.query("SELECT driver_id, count(*) AS n FROM driver_trips WHERE status = 'COMPLETED' AND driver_id = ANY (?) GROUP BY driver_id",
				rs -> {
					done.put(rs.getObject("driver_id", UUID.class), rs.getInt("n"));
				}, ids);
		driverIds.forEach(id -> {
			double[] r = rated.get(id);
			out.put(id, new Stats(r == null ? null : r[0], r == null ? 0 : (int) r[1], done.getOrDefault(id, 0)));
		});
		return out;
	}

	/** Average stars (rounded to one decimal) and how many, for reviews written by {@code byRole} about these people. */
	Map<UUID, double[]> ratings(Collection<UUID> subjectIds, String byRole) {
		Map<UUID, double[]> out = new HashMap<>();
		if (subjectIds.isEmpty()) {
			return out;
		}
		jdbc.query("""
				SELECT subject_id, avg(stars) AS a, count(*) AS n FROM driver_reviews
				WHERE by_role = ? AND subject_id = ANY (?) GROUP BY subject_id""", rs -> {
			out.put(rs.getObject("subject_id", UUID.class), new double[] { Math.round(rs.getDouble("a") * 10) / 10.0, rs.getInt("n") });
		}, byRole, subjectIds.toArray(new UUID[0]));
		return out;
	}

	List<DriverCard> cards(Collection<DriverProfile> list, Map<UUID, Double> km, Function<DriverProfile, Integer> fare) {
		List<UUID> ids = list.stream().map(DriverProfile::getUserId).toList();
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<UUID, MemberCard> people = members.cards(ids);
		Map<UUID, Stats> stats = stats(ids);
		Map<UUID, List<String>> areaNames = new HashMap<>();
		jdbc.query("SELECT driver_id, place_name FROM driver_areas WHERE driver_id = ANY (?) ORDER BY created_at", rs -> {
			areaNames.computeIfAbsent(rs.getObject("driver_id", UUID.class), k -> new ArrayList<>()).add(rs.getString("place_name"));
		}, (Object) ids.toArray(new UUID[0]));
		return list.stream().map(d -> {
			UUID id = d.getUserId();
			Stats s = stats.getOrDefault(id, Stats.NONE);
			Double distance = km == null || km.get(id) == null ? null : Math.round(km.get(id) * 10) / 10.0;
			return new DriverCard(id, people.getOrDefault(id, MemberProfiles.deleted()), s.rating(), s.reviews(), s.trips(),
					d.getExperienceYears(), languages(d), d.isManual(), d.isAutomatic(), d.isOutstation(), d.isPoliceChecked(),
					areaNames.getOrDefault(id, List.of()), rates(d), distance, fare == null ? null : fare.apply(d));
		}).toList();
	}

	List<AreaView> areas(UUID driverId) {
		return jdbc.query("SELECT id, place_id, place_name, state, radius_km FROM driver_areas WHERE driver_id = ? ORDER BY created_at",
				(rs, n) -> new AreaView(rs.getObject("id", UUID.class), rs.getLong("place_id"), rs.getString("place_name"),
						rs.getString("state"), rs.getInt("radius_km")), driverId);
	}

	private List<BlockView> blocks(UUID driverId) {
		return jdbc.query("""
				SELECT id, starts_at, ends_at, note FROM driver_blocks WHERE driver_id = ? AND ends_at > ? ORDER BY starts_at""",
				(rs, n) -> new BlockView(rs.getObject("id", UUID.class), rs.getTimestamp("starts_at").toInstant(),
						rs.getTimestamp("ends_at").toInstant(), rs.getString("note")), driverId, Timestamp.from(clock.instant()));
	}

	private MyDriverProfile mine(DriverProfile p) {
		UUID id = p.getUserId();
		Stats s = stats(List.of(id)).getOrDefault(id, Stats.NONE);
		return new MyDriverProfile(p.getStatus(), p.getReviewNote(), idChecks.licenceStatus(id), mask(decrypt(p.getLicenceNumber())),
				LicenceClass.valueOf(p.getLicenceClass()), p.getLicenceValidUntil(), p.getExperienceYears(), languages(p), p.isManual(),
				p.isAutomatic(), p.isOutstation(), rates(p), p.getAbout(), p.getPoliceCertificate() != null, p.isPoliceChecked(),
				p.isTakingBookings(), areas(id), blocks(id), s.rating(), s.reviews(), s.trips(),
				trips.countByDriverIdAndStatusIn(id, Set.of(DriverTrip.REQUESTED)), members.card(id).avatarUrl() != null);
	}

	private List<AdminDriverRow> rows(List<DriverProfile> list) {
		List<UUID> ids = list.stream().map(DriverProfile::getUserId).toList();
		Map<UUID, MemberCard> people = members.cards(ids);
		Map<UUID, Stats> stats = stats(ids);
		return list.stream().map(p -> {
			UUID id = p.getUserId();
			Stats s = stats.getOrDefault(id, Stats.NONE);
			return new AdminDriverRow(id, people.getOrDefault(id, MemberProfiles.deleted()), p.getStatus(), idChecks.licenceStatus(id),
					mask(decrypt(p.getLicenceNumber())), LicenceClass.valueOf(p.getLicenceClass()), p.getLicenceValidUntil(),
					p.getExperienceYears(), languages(p), p.isManual(), p.isAutomatic(), p.isOutstation(), rates(p), p.getAbout(),
					areas(id), p.getPoliceCertificate() != null, p.isPoliceChecked(), p.isTakingBookings(), p.getReviewNote(),
					p.getUpdatedAt(), s.trips(), s.rating());
		}).toList();
	}

	private DriverProfile own(UUID me) {
		return profiles.lockById(me).filter(p -> !DriverProfile.REMOVED.equals(p.getStatus()))
			.orElseThrow(() -> ApiException.notFound("not_a_driver", "Sign up as a driver partner first."));
	}

	static Rates rates(DriverProfile d) {
		return new Rates(d.getHourPrice(), d.getDayPrice(), d.getOutstationPrice(), d.getAllowance(), d.getNightCharge());
	}

	static List<String> languages(DriverProfile d) {
		return d.getLanguages().isBlank() ? List.of() : List.of(d.getLanguages().split(","));
	}

	/** "Hindi, english ,Hindi" → [Hindi, English]: tidy, no repeats, at most six. */
	static List<String> cleanLanguages(List<String> raw) {
		if (raw == null) {
			return List.of();
		}
		Set<String> seen = new LinkedHashSet<>();
		for (String s : raw) {
			if (s == null) {
				continue;
			}
			String name = s.replace(",", " ").strip().replaceAll("\\s+", " ");
			if (name.isEmpty() || name.length() > 20) {
				continue;
			}
			name = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1).toLowerCase(Locale.ROOT);
			seen.add(name);
		}
		return seen.stream().limit(6).toList();
	}

	/** "mh-14 2011 0062821" → "MH1420110062821"; empty → null. */
	static String cleanLicence(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return raw.toUpperCase(Locale.ROOT).replaceAll("[\\s\\-/.]", "");
	}

	static String mask(String number) {
		return number == null || number.length() < 4 ? null : "•••• " + number.substring(number.length() - 4);
	}

	private String decrypt(String stored) {
		return stored == null || stored.isBlank() ? null : encryptor.decrypt(stored);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}

	static ApiException notFound() {
		return ApiException.notFound("driver_not_found", "We couldn't find that driver.");
	}
}
