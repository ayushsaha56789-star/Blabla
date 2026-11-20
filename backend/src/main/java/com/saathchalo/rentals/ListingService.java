package com.saathchalo.rentals;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.features.FeatureService;
import com.saathchalo.files.CarPhotoStorage;
import com.saathchalo.files.DocumentVault;
import com.saathchalo.geo.Place;
import com.saathchalo.geo.PlaceDirectory;
import com.saathchalo.rentals.RentalDtos.AdminListingRow;
import com.saathchalo.rentals.RentalDtos.BlockRequest;
import com.saathchalo.rentals.RentalDtos.BlockView;
import com.saathchalo.rentals.RentalDtos.CarSummary;
import com.saathchalo.rentals.RentalDtos.DocumentKind;
import com.saathchalo.rentals.RentalDtos.DocumentView;
import com.saathchalo.rentals.RentalDtos.FuelPolicy;
import com.saathchalo.rentals.RentalDtos.ListingCard;
import com.saathchalo.rentals.RentalDtos.ListingDetail;
import com.saathchalo.rentals.RentalDtos.ListingRequest;
import com.saathchalo.rentals.RentalDtos.Mode;
import com.saathchalo.rentals.RentalDtos.MyListing;
import com.saathchalo.rentals.RentalDtos.Period;
import com.saathchalo.rentals.RentalDtos.PhotoView;
import com.saathchalo.rentals.RentalDtos.Quote;
import com.saathchalo.rentals.internal.Listing;
import com.saathchalo.rentals.internal.ListingRepository;
import com.saathchalo.rentals.internal.RentalBooking;
import com.saathchalo.rentals.internal.RentalBookingRepository;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import com.saathchalo.vehicles.VehicleInfo;
import com.saathchalo.vehicles.VehicleService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Rental listings: owners offer their cars; staff check the documents; renters search and get quotes. */
@Service
public class ListingService {

	static final int MAX_PHOTOS = 8;

	static final double SEARCH_RADIUS_KM = 30;

	private final ListingRepository listings;

	private final RentalBookingRepository bookings;

	private final VehicleService vehicles;

	private final PlaceDirectory places;

	private final MemberProfiles members;

	private final CarPhotoStorage photos;

	private final DocumentVault vault;

	private final FeatureService features;

	private final AuditService audit;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public ListingService(ListingRepository listings, RentalBookingRepository bookings, VehicleService vehicles,
			PlaceDirectory places, MemberProfiles members, CarPhotoStorage photos, DocumentVault vault, FeatureService features,
			AuditService audit, JdbcTemplate jdbc, Clock clock) {
		this.listings = listings;
		this.bookings = bookings;
		this.vehicles = vehicles;
		this.places = places;
		this.members = members;
		this.photos = photos;
		this.vault = vault;
		this.features = features;
		this.audit = audit;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// ----- Owners -----

	@Transactional
	public MyListing create(UUID owner, ListingRequest req) {
		if (!members.traveller(owner).profileComplete()) {
			throw ApiException.forbidden("profile_incomplete", "Add your first name and gender to your profile first.");
		}
		VehicleInfo car = vehicles.activeOf(owner, req.vehicleId())
			.orElseThrow(() -> ApiException.badRequest("vehicle_not_found", "Choose one of your cars (add it under My cars first)."));
		if (listings.findByVehicleIdAndStatusNot(car.id(), Listing.REMOVED).isPresent()) {
			throw ApiException.conflict("already_listed", "This car is already listed for rent.");
		}
		Listing l = new Listing(owner, car.id(), clock.instant());
		apply(l, req, owner);
		return mine(listings.save(l));
	}

	@Transactional
	public MyListing update(UUID owner, UUID id, ListingRequest req) {
		Listing l = own(owner, id);
		if (!l.getVehicleId().equals(req.vehicleId())) {
			throw ApiException.badRequest("vehicle_fixed", "A listing stays with its car. Remove it and list the other car instead.");
		}
		apply(l, req, owner);
		return mine(l);
	}

	@Transactional
	public MyListing addPhoto(UUID owner, UUID id, MultipartFile file) {
		Listing l = own(owner, id);
		Integer count = jdbc.queryForObject("SELECT count(*) FROM rental_photos WHERE listing_id = ?", Integer.class, id);
		if (count != null && count >= MAX_PHOTOS) {
			throw ApiException.conflict("too_many_photos", "Up to " + MAX_PHOTOS + " photos. Remove one first.");
		}
		String path = photos.store(file);
		jdbc.update("INSERT INTO rental_photos (id, listing_id, path, position, created_at) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), id, path, count == null ? 0 : count, Timestamp.from(clock.instant()));
		return mine(l);
	}

	@Transactional
	public MyListing removePhoto(UUID owner, UUID id, UUID photoId) {
		Listing l = own(owner, id);
		List<String> paths = jdbc.queryForList("SELECT path FROM rental_photos WHERE id = ? AND listing_id = ?", String.class, photoId, id);
		jdbc.update("DELETE FROM rental_photos WHERE id = ? AND listing_id = ?", photoId, id);
		paths.forEach(photos::deleteQuietly);
		return mine(l);
	}

	/** Uploading a document again replaces it, and the listing is checked again. */
	@Transactional
	public MyListing addDocument(UUID owner, UUID id, DocumentKind kind, MultipartFile file) {
		Listing l = own(owner, id);
		if (kind == null) {
			throw ApiException.badRequest("kind_missing", "Choose which document this is.");
		}
		String name = vault.store(file);
		jdbc.queryForList("SELECT file_name FROM rental_documents WHERE listing_id = ? AND kind = ?", String.class, id, kind.name())
			.forEach(vault::deleteQuietly);
		jdbc.update("DELETE FROM rental_documents WHERE listing_id = ? AND kind = ?", id, kind.name());
		jdbc.update("INSERT INTO rental_documents (id, listing_id, kind, file_name, created_at) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), id, kind.name(), name, Timestamp.from(clock.instant()));
		l.documentsChanged(clock.instant());
		return mine(l);
	}

	/** Sends the listing for checking (it needs photos, the registration and the insurance). */
	@Transactional
	public MyListing submit(UUID owner, UUID id) {
		Listing l = own(owner, id);
		if (!Set.of(Listing.DRAFT, Listing.REJECTED, Listing.PAUSED).contains(l.getStatus())) {
			throw ApiException.conflict("not_submittable", "This listing is already live or being checked.");
		}
		Integer photoCount = jdbc.queryForObject("SELECT count(*) FROM rental_photos WHERE listing_id = ?", Integer.class, id);
		if (photoCount == null || photoCount == 0) {
			throw ApiException.badRequest("photos_missing", "Add at least one photo of the car.");
		}
		Set<String> docs = new HashSet<>(jdbc.queryForList("SELECT kind FROM rental_documents WHERE listing_id = ?", String.class, id));
		if (!docs.contains(DocumentKind.RC.name()) || !docs.contains(DocumentKind.INSURANCE.name())) {
			throw ApiException.badRequest("documents_missing", "Upload the car's registration (RC) and insurance first.");
		}
		boolean liveAtOnce = l.isVerified() || features.isOnFor("rentals.unverified", null);
		l.submit(liveAtOnce, clock.instant());
		return mine(l);
	}

	@Transactional
	public MyListing pause(UUID owner, UUID id) {
		Listing l = own(owner, id);
		if (!Listing.LIVE.equals(l.getStatus())) {
			throw ApiException.conflict("not_live", "Only a live listing can be paused.");
		}
		l.setStatus(Listing.PAUSED, clock.instant());
		return mine(l);
	}

	/** Back to live (or to checking, if it was never checked and unchecked listings can't go live). */
	@Transactional
	public MyListing resume(UUID owner, UUID id) {
		Listing l = own(owner, id);
		if (!Listing.PAUSED.equals(l.getStatus())) {
			throw ApiException.conflict("not_paused", "Only a paused listing can be resumed.");
		}
		return submit(owner, id);
	}

	@Transactional
	public void remove(UUID owner, UUID id) {
		Listing l = own(owner, id);
		if (bookings.countByListingIdAndStatusIn(id, RentalBooking.HOLDING) > 0) {
			throw ApiException.conflict("bookings_active", "This car has bookings that aren't finished. Finish or cancel them first.");
		}
		removeListing(l);
	}

	@Transactional
	public MyListing addBlock(UUID owner, UUID id, BlockRequest req) {
		Listing l = own(owner, id);
		Instant now = clock.instant();
		if (!req.to().isAfter(req.from()) || req.to().isBefore(now) || req.from().isAfter(now.plus(Duration.ofDays(366)))) {
			throw ApiException.badRequest("bad_period", "Choose a period in the coming year, with the end after the start.");
		}
		jdbc.update("INSERT INTO rental_blocks (id, listing_id, starts_at, ends_at, note, created_at) VALUES (?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), id, Timestamp.from(req.from()), Timestamp.from(req.to()),
				req.note() == null || req.note().isBlank() ? null : req.note().strip(), Timestamp.from(now));
		return mine(l);
	}

	@Transactional
	public MyListing removeBlock(UUID owner, UUID id, UUID blockId) {
		Listing l = own(owner, id);
		jdbc.update("DELETE FROM rental_blocks WHERE id = ? AND listing_id = ?", blockId, id);
		return mine(l);
	}

	@Transactional(readOnly = true)
	public List<MyListing> mine(UUID owner) {
		return listings.findByOwnerIdAndStatusNotOrderByCreatedAtDesc(owner, Listing.REMOVED).stream().map(this::mine).toList();
	}

	@Transactional(readOnly = true)
	public MyListing myListing(UUID owner, UUID id) {
		return mine(own(owner, id));
	}

	// ----- Renters -----

	/** Live listings near a place; with dates, only free ones, priced for the period. */
	@Transactional(readOnly = true)
	public List<ListingCard> search(UUID viewer, long placeId, Instant from, Instant to, Mode mode) {
		Place place = places.find(placeId).orElseThrow(() -> ApiException.badRequest("place_not_found", "Choose a town from the list."));
		boolean dated = from != null && to != null && to.isAfter(from);
		boolean selfDriveOn = features.isOnFor("rentals.self_drive", viewer);
		boolean withDriverOn = features.isOnFor("rentals.with_driver", viewer);
		StringBuilder sql = new StringBuilder("""
				SELECT l.id, ST_Distance(l.location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000.0 AS km
				FROM rental_listings l
				WHERE l.status = 'LIVE' AND ST_DWithin(l.location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)""");
		List<Object> args = new ArrayList<>(List.of(place.lng(), place.lat(), place.lng(), place.lat(), SEARCH_RADIUS_KM * 1000));
		boolean wantSelf = (mode == null || mode == Mode.SELF_DRIVE) && selfDriveOn;
		boolean wantDriver = (mode == null || mode == Mode.WITH_DRIVER) && withDriverOn;
		if (!wantSelf && !wantDriver) {
			return List.of();
		}
		sql.append(wantSelf && wantDriver ? " AND (l.self_drive OR l.with_driver)" : wantSelf ? " AND l.self_drive" : " AND l.with_driver");
		if (dated) {
			sql.append("""
					 AND NOT EXISTS (SELECT 1 FROM rental_bookings b WHERE b.listing_id = l.id
					     AND b.status IN ('ACCEPTED', 'PAID', 'PICKED_UP', 'RETURNED', 'DISPUTED') AND b.period && tstzrange(?, ?, '[)'))
					 AND NOT EXISTS (SELECT 1 FROM rental_blocks k WHERE k.listing_id = l.id AND k.period && tstzrange(?, ?, '[)'))""");
			args.addAll(List.of(Timestamp.from(from), Timestamp.from(to), Timestamp.from(from), Timestamp.from(to)));
		}
		sql.append(" ORDER BY km LIMIT 60");
		Map<UUID, Double> distance = new java.util.LinkedHashMap<>();
		jdbc.query(sql.toString(), rs -> {
			distance.put(rs.getObject("id", UUID.class), rs.getDouble("km"));
		}, args.toArray());
		List<Listing> found = listings.findAllById(distance.keySet());
		Map<UUID, Listing> byId = found.stream().collect(Collectors.toMap(Listing::getId, x -> x));
		List<Listing> ordered = distance.keySet().stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
		return cards(ordered, distance, dated ? from : null, dated ? to : null);
	}

	@Transactional(readOnly = true)
	public ListingDetail detail(UUID viewer, UUID id) {
		Listing l = listings.findById(id).filter(x -> Listing.LIVE.equals(x.getStatus()) || x.getOwnerId().equals(viewer))
			.orElseThrow(ListingService::notFound);
		return detail(l, viewer);
	}

	/** The price for a period, and whether the car is free then. */
	@Transactional(readOnly = true)
	public Quote quote(UUID viewer, UUID id, Instant from, Instant to, Mode mode) {
		Listing l = listings.findById(id).filter(x -> Listing.LIVE.equals(x.getStatus()) || x.getOwnerId().equals(viewer))
			.orElseThrow(ListingService::notFound);
		String problem = RentalRules.periodProblem(l, from, to, clock.instant());
		if (problem == null && mode == Mode.SELF_DRIVE && !l.isSelfDrive()) {
			problem = "This car is only rented with a driver.";
		}
		if (problem == null && mode == Mode.WITH_DRIVER && !l.isWithDriver()) {
			problem = "This car is only rented for self-drive.";
		}
		if (problem == null && !isFree(id, from, to)) {
			problem = "The car isn't free for all of that time. Try other dates.";
		}
		long hours = RentalRules.hours(from, to);
		return new Quote(problem == null, problem, RentalRules.rent(l, hours), mode == Mode.SELF_DRIVE ? l.getDeposit() : 0,
				RentalRules.kmIncluded(l, hours), l.getExtraKmPrice(), hours, RentalRules.hourly(l, hours));
	}

	// ----- Staff -----

	@Transactional(readOnly = true)
	public List<AdminListingRow> queue(String status) {
		List<Listing> list = Listing.IN_REVIEW.equals(status) ? listings.findByStatusOrderByUpdatedAtAsc(status, PageRequest.of(0, 200))
				: listings.findByStatusNotOrderByUpdatedAtDesc(Listing.REMOVED, PageRequest.of(0, 200));
		return list.stream().map(l -> new AdminListingRow(detail(l, null), l.getPickupAddress(), l.getReviewNote(), documents(l.getId()),
				l.getUpdatedAt())).toList();
	}

	@Transactional(readOnly = true)
	public long waitingForReview() {
		return listings.countByStatus(Listing.IN_REVIEW);
	}

	/** A document photo for staff; every view is in the audit log. */
	@Transactional
	public byte[] document(UUID listingId, UUID documentId, Actor actor) {
		List<String> names = jdbc.queryForList("SELECT file_name FROM rental_documents WHERE id = ? AND listing_id = ?", String.class,
				documentId, listingId);
		byte[] jpeg = names.isEmpty() ? null : vault.read(names.getFirst()).orElse(null);
		if (jpeg == null) {
			throw ApiException.notFound("document_not_found", "We couldn't find that document.");
		}
		audit.record(actor, "rental.document_viewed", "rental_listing", listingId.toString(), "Viewed a rental document", null);
		return jpeg;
	}

	@Transactional
	public AdminListingRow approve(UUID id, Actor actor) {
		Listing l = listings.findById(id).orElseThrow(ListingService::notFound);
		if (Listing.REMOVED.equals(l.getStatus())) {
			throw ApiException.conflict("listing_removed", "This listing was removed.");
		}
		l.approve(actor.id(), clock.instant());
		audit.record(actor, "rental.approved", "rental_listing", id.toString(), "Approved a rental listing", null);
		return new AdminListingRow(detail(l, null), l.getPickupAddress(), l.getReviewNote(), documents(id), l.getUpdatedAt());
	}

	@Transactional
	public AdminListingRow reject(UUID id, String note, Actor actor) {
		Listing l = listings.findById(id).orElseThrow(ListingService::notFound);
		if (note == null || note.isBlank()) {
			throw ApiException.badRequest("note_missing", "Tell the owner what to fix.");
		}
		if (bookings.countByListingIdAndStatusIn(id, RentalBooking.HOLDING) > 0) {
			throw ApiException.conflict("bookings_active", "This car has bookings that aren't finished; cancel them first.");
		}
		l.reject(actor.id(), note.strip(), clock.instant());
		audit.record(actor, "rental.rejected", "rental_listing", id.toString(), "Sent a rental listing back: " + note.strip(), null);
		return new AdminListingRow(detail(l, null), l.getPickupAddress(), l.getReviewNote(), documents(id), l.getUpdatedAt());
	}

	// ----- Shared with the booking service -----

	Listing load(UUID id) {
		return listings.findById(id).orElseThrow(ListingService::notFound);
	}

	/** No holding booking or owner's block overlaps the period. */
	boolean isFree(UUID listingId, Instant from, Instant to) {
		Integer busy = jdbc.queryForObject("""
				SELECT (SELECT count(*) FROM rental_bookings b WHERE b.listing_id = ?
				        AND b.status IN ('ACCEPTED', 'PAID', 'PICKED_UP', 'RETURNED', 'DISPUTED') AND b.period && tstzrange(?, ?, '[)'))
				     + (SELECT count(*) FROM rental_blocks k WHERE k.listing_id = ? AND k.period && tstzrange(?, ?, '[)'))""",
				Integer.class, listingId, Timestamp.from(from), Timestamp.from(to), listingId, Timestamp.from(from), Timestamp.from(to));
		return busy != null && busy == 0;
	}

	List<ListingCard> cards(Collection<Listing> list, Map<UUID, Double> distance, Instant from, Instant to) {
		Map<UUID, VehicleInfo> cars = vehicles.infos(list.stream().map(Listing::getVehicleId).toList());
		Map<UUID, MemberCard> owners = members.cards(list.stream().map(Listing::getOwnerId).toList());
		Map<UUID, String> firstPhoto = firstPhotos(list.stream().map(Listing::getId).toList());
		return list.stream().map(l -> {
			Integer price = from == null ? null : RentalRules.rent(l, RentalRules.hours(from, to));
			Double km = distance == null ? null : distance.get(l.getId());
			return new ListingCard(l.getId(), car(cars.get(l.getVehicleId())), CarPhotoStorage.url(firstPhoto.get(l.getId())),
					l.getPlaceName(), l.getArea(), km == null ? null : Math.round(km * 10) / 10.0, l.isSelfDrive(), l.isWithDriver(),
					l.getDayPrice(), l.getHourPrice(), l.getDeposit(), l.getKmPerDay(), l.isVerified(), l.isInstant(),
					owners.getOrDefault(l.getOwnerId(), MemberProfiles.deleted()), price);
		}).toList();
	}

	ListingDetail detail(Listing l, UUID viewer) {
		VehicleInfo car = vehicles.infos(List.of(l.getVehicleId())).get(l.getVehicleId());
		List<String> photoUrls = jdbc.queryForList("SELECT path FROM rental_photos WHERE listing_id = ? ORDER BY position, created_at",
				String.class, l.getId()).stream().map(CarPhotoStorage::url).toList();
		Instant now = clock.instant();
		List<Period> unavailable = new ArrayList<>();
		jdbc.query("""
				SELECT starts_at, ends_at FROM rental_bookings WHERE listing_id = ? AND ends_at > ?
				    AND status IN ('ACCEPTED', 'PAID', 'PICKED_UP', 'RETURNED', 'DISPUTED')
				UNION ALL SELECT starts_at, ends_at FROM rental_blocks WHERE listing_id = ? AND ends_at > ?
				ORDER BY 1""", rs -> {
			unavailable.add(new Period(rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()));
		}, l.getId(), Timestamp.from(now), l.getId(), Timestamp.from(now));
		return new ListingDetail(l.getId(), car(car), photoUrls, l.getPlaceId(), l.getPlaceName(), l.getState(), l.getArea(), l.getLat(),
				l.getLng(),
				l.isSelfDrive(), l.isWithDriver(), l.getDayPrice(), l.getHourPrice(), l.getDeposit(), l.getKmPerDay(),
				l.getExtraKmPrice(), FuelPolicy.valueOf(l.getFuelPolicy()), l.getMinAge(), l.getDescription(), l.getRules(),
				l.isInstant(), l.isVerified(), l.getStatus(), members.card(l.getOwnerId()), unavailable,
				viewer != null && viewer.equals(l.getOwnerId()));
	}

	void removeListing(Listing l) {
		jdbc.queryForList("SELECT file_name FROM rental_documents WHERE listing_id = ?", String.class, l.getId())
			.forEach(vault::deleteQuietly);
		jdbc.update("DELETE FROM rental_documents WHERE listing_id = ?", l.getId());
		l.setStatus(Listing.REMOVED, clock.instant());
	}

	private MyListing mine(Listing l) {
		List<PhotoView> photoViews = new ArrayList<>();
		jdbc.query("SELECT id, path FROM rental_photos WHERE listing_id = ? ORDER BY position, created_at", rs -> {
			photoViews.add(new PhotoView(rs.getObject("id", UUID.class), CarPhotoStorage.url(rs.getString("path"))));
		}, l.getId());
		List<BlockView> blocks = new ArrayList<>();
		jdbc.query("SELECT id, starts_at, ends_at, note FROM rental_blocks WHERE listing_id = ? AND ends_at > ? ORDER BY starts_at", rs -> {
			blocks.add(new BlockView(rs.getObject("id", UUID.class), rs.getTimestamp("starts_at").toInstant(),
					rs.getTimestamp("ends_at").toInstant(), rs.getString("note")));
		}, l.getId(), Timestamp.from(clock.instant()));
		long open = bookings.countByListingIdAndStatusIn(l.getId(), Set.of(RentalBooking.REQUESTED));
		return new MyListing(detail(l, l.getOwnerId()), l.getPickupAddress(), l.getReviewNote(), photoViews, documents(l.getId()),
				blocks, open);
	}

	private List<DocumentView> documents(UUID listingId) {
		List<DocumentView> out = new ArrayList<>();
		jdbc.query("SELECT id, kind, created_at FROM rental_documents WHERE listing_id = ? ORDER BY kind", rs -> {
			out.add(new DocumentView(rs.getObject("id", UUID.class), DocumentKind.valueOf(rs.getString("kind")),
					rs.getTimestamp("created_at").toInstant()));
		}, listingId);
		return out;
	}

	private Map<UUID, String> firstPhotos(List<UUID> listingIds) {
		if (listingIds.isEmpty()) {
			return Map.of();
		}
		Map<UUID, String> out = new java.util.HashMap<>();
		jdbc.query("""
				SELECT DISTINCT ON (listing_id) listing_id, path FROM rental_photos
				WHERE listing_id = ANY (?) ORDER BY listing_id, position, created_at""", rs -> {
			out.put(rs.getObject("listing_id", UUID.class), rs.getString("path"));
		}, (Object) listingIds.toArray(new UUID[0]));
		return out;
	}

	private void apply(Listing l, ListingRequest req, UUID owner) {
		boolean self = Boolean.TRUE.equals(req.selfDrive());
		boolean driver = Boolean.TRUE.equals(req.withDriver());
		if (!self && !driver) {
			throw ApiException.badRequest("mode_missing", "Choose self-drive, with driver, or both.");
		}
		if (self && !features.isOnFor("rentals.self_drive", owner)) {
			throw ApiException.forbidden("self_drive_off", "Self-drive rentals aren't open right now.");
		}
		if (driver && !features.isOnFor("rentals.with_driver", owner)) {
			throw ApiException.forbidden("with_driver_off", "Rentals with a driver aren't open right now.");
		}
		Place place = places.find(req.placeId()).orElseThrow(() -> ApiException.badRequest("place_not_found", "Choose a town from the list."));
		l.update(self, driver, req.dayPrice(), req.hourPrice(), self && req.deposit() != null ? req.deposit() : 0, req.kmPerDay(),
				req.extraKmPrice(), req.fuelPolicy().name(), req.minAge() == null ? 21 : req.minAge(), place.id(), place.name(),
				place.state(), place.lat(), place.lng(), req.area().strip(), blankToNull(req.pickupAddress()),
				blankToNull(req.description()), blankToNull(req.rules()), Boolean.TRUE.equals(req.instant()), clock.instant());
	}

	private Listing own(UUID owner, UUID id) {
		return listings.findById(id).filter(l -> l.getOwnerId().equals(owner) && !Listing.REMOVED.equals(l.getStatus()))
			.orElseThrow(ListingService::notFound);
	}

	private static CarSummary car(VehicleInfo v) {
		return v == null ? new CarSummary(null, "Car", null, 0) : new CarSummary(v.id(), v.name(), v.color(), v.seats());
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}

	static ApiException notFound() {
		return ApiException.notFound("listing_not_found", "We couldn't find that car.");
	}
}
