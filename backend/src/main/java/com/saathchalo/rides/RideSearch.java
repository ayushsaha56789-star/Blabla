package com.saathchalo.rides;

import com.saathchalo.common.ApiException;
import com.saathchalo.config.AppConfig;
import com.saathchalo.config.AppProperties;
import com.saathchalo.geo.Place;
import com.saathchalo.geo.PlaceDirectory;
import com.saathchalo.rides.RideDtos.DateCount;
import com.saathchalo.rides.RideDtos.SearchResponse;
import com.saathchalo.rides.RideDtos.SearchResult;
import com.saathchalo.rides.internal.Booking;
import com.saathchalo.rides.internal.BookingRepository;
import com.saathchalo.rides.internal.Ride;
import com.saathchalo.rides.internal.RideRepository;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import com.saathchalo.user.MemberProfiles.Traveller;
import com.saathchalo.vehicles.VehicleInfo;
import com.saathchalo.vehicles.VehicleService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds rides for a passenger: a stop near where they start, and a later stop near where they are going, on the day
 * they chose. Rides that only pass close by count too (within the search radius), with the distance shown.
 */
@Service
public class RideSearch {

	static final int MAX_RESULTS = 100;

	/** For each ride, the pair of stops closest to the searched places (pickup before drop-off). */
	private static final String CANDIDATES = """
			SELECT DISTINCT ON (ride_id) ride_id, from_pos, to_pos, from_m, to_m FROM (
			    SELECT r.id AS ride_id, a.position AS from_pos, b.position AS to_pos,
			           ST_Distance(a.location, ST_SetSRID(ST_MakePoint(:fromLng, :fromLat), 4326)::geography) AS from_m,
			           ST_Distance(b.location, ST_SetSRID(ST_MakePoint(:toLng, :toLat), 4326)::geography) AS to_m
			    FROM rides r
			    JOIN ride_stops a ON a.ride_id = r.id
			    JOIN ride_stops b ON b.ride_id = r.id AND b.position > a.position
			    WHERE r.status = 'SCHEDULED'
			      AND (:women OR NOT r.ladies_only)
			      AND ST_DWithin(a.location, ST_SetSRID(ST_MakePoint(:fromLng, :fromLat), 4326)::geography, :radius)
			      AND ST_DWithin(b.location, ST_SetSRID(ST_MakePoint(:toLng, :toLat), 4326)::geography, :radius)
			      AND r.departure_at + make_interval(mins => a.minutes_from_start) >= :start
			      AND r.departure_at + make_interval(mins => a.minutes_from_start) < :end
			) c
			ORDER BY ride_id, from_m + to_m, from_pos DESC
			LIMIT 1000
			""";

	private record Candidate(UUID rideId, int from, int to, double fromMeters, double toMeters) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final RideRepository rides;

	private final BookingRepository bookings;

	private final PlaceDirectory places;

	private final VehicleService vehicles;

	private final MemberProfiles members;

	private final RideViews views;

	private final AppProperties.Rides config;

	private final Clock clock;

	public RideSearch(NamedParameterJdbcTemplate jdbc, RideRepository rides, BookingRepository bookings,
			PlaceDirectory places, VehicleService vehicles, MemberProfiles members, RideViews views, AppProperties props,
			Clock clock) {
		this.jdbc = jdbc;
		this.rides = rides;
		this.bookings = bookings;
		this.places = places;
		this.vehicles = vehicles;
		this.members = members;
		this.views = views;
		this.config = props.rides();
		this.clock = clock;
	}

	/**
	 * Places can be given by id (from the search box) or by name (shared links such as /search?from=Delhi&to=Jaipur).
	 * A missing date means today; a past date becomes today.
	 */
	@Transactional(readOnly = true)
	public SearchResponse search(Long fromId, String fromName, Long toId, String toName, LocalDate date, Integer seats,
			UUID viewerId) {
		Place from = resolve(fromId, fromName, "Where are you leaving from?");
		Place to = resolve(toId, toName, "Where are you going?");
		if (from.id() == to.id()) {
			throw ApiException.badRequest("same_place", "Your start and destination are the same. Pick a different destination.");
		}
		Instant now = clock.instant();
		LocalDate today = LocalDate.now(clock.withZone(AppConfig.ZONE));
		LocalDate day = date == null || date.isBefore(today) ? today : date;
		if (day.isAfter(today.plusDays(config.maxDaysAhead()))) {
			throw ApiException.badRequest("date_too_far", "Rides can be found up to " + config.maxDaysAhead() + " days ahead.");
		}
		int wanted = seats == null ? 1 : Math.clamp(seats, 1, 8);
		boolean women = viewerId != null && members.findTraveller(viewerId).map(Traveller::isWoman).orElse(false);

		// Look at a few days around the chosen one, so we can suggest other dates when it has no rides.
		Instant start = max(now, day.minusDays(3).atStartOfDay(AppConfig.ZONE).toInstant());
		Instant end = day.plusDays(8).atStartOfDay(AppConfig.ZONE).toInstant();
		List<Candidate> candidates = jdbc.query(CANDIDATES, new MapSqlParameterSource()
				.addValue("fromLat", from.lat()).addValue("fromLng", from.lng())
				.addValue("toLat", to.lat()).addValue("toLng", to.lng())
				.addValue("radius", config.searchRadiusKm() * 1000.0)
				.addValue("women", women)
				.addValue("start", start.atOffset(ZoneOffset.UTC))
				.addValue("end", end.atOffset(ZoneOffset.UTC)),
				(rs, n) -> new Candidate(rs.getObject("ride_id", UUID.class), rs.getInt("from_pos"), rs.getInt("to_pos"),
						rs.getDouble("from_m"), rs.getDouble("to_m")));
		if (candidates.isEmpty()) {
			return new SearchResponse(from, to, day, wanted, List.of(), List.of());
		}

		List<UUID> ids = candidates.stream().map(Candidate::rideId).toList();
		Map<UUID, Ride> rideById = rides.findWithStops(ids).stream().collect(Collectors.toMap(Ride::getId, Function.identity()));
		Map<UUID, List<Booking>> liveByRide = bookings.findByRideIdInAndStatusIn(ids, BookingStatus.LIVE)
			.stream()
			.collect(Collectors.groupingBy(Booking::getRideId));
		Map<UUID, BookingStatus> yourBookings = new HashMap<>();
		if (viewerId != null) {
			bookings.findByPassengerIdAndRideIdIn(viewerId, ids)
				.stream()
				.filter(b -> b.getStatus().isLive())
				.forEach(b -> yourBookings.put(b.getRideId(), b.getStatus()));
		}
		Map<UUID, MemberCard> drivers = members.cards(rideById.values().stream().map(Ride::getDriverId).toList());
		Map<UUID, VehicleInfo> cars = vehicles.infos(rideById.values().stream().map(Ride::getVehicleId).toList());

		List<SearchResult> results = new ArrayList<>();
		Map<LocalDate, Integer> otherDays = new TreeMap<>();
		for (Candidate c : candidates) {
			Ride ride = rideById.get(c.rideId());
			if (ride == null) {
				continue;
			}
			boolean yours = ride.getDriverId().equals(viewerId);
			BookingStatus yourBooking = yourBookings.get(ride.getId());
			int[] left = RideRules.seatsLeftByLeg(ride.getSeatsTotal(), ride.lastPosition(),
					liveByRide.getOrDefault(ride.getId(), List.of()));
			int seatsLeft = RideRules.seatsLeft(left, c.from(), c.to());
			if (seatsLeft < wanted && !yours && yourBooking == null) {
				continue;
			}
			LocalDate pickupDay = ride.timeAt(c.from()).atZone(AppConfig.ZONE).toLocalDate();
			if (!pickupDay.equals(day)) {
				otherDays.merge(pickupDay, 1, Integer::sum);
				continue;
			}
			VehicleInfo car = cars.get(ride.getVehicleId());
			results.add(new SearchResult(ride.getId(), c.from(), c.to(), views.stop(ride, c.from()), views.stop(ride, c.to()),
					(int) Math.round(c.fromMeters() / 1000), (int) Math.round(c.toMeters() / 1000),
					RideRules.segmentPrice(ride.getPricePerSeat(), views.kmFromStart(ride), c.from(), c.to()), seatsLeft,
					ride.isInstantBooking(), ride.isLadiesOnly(),
					drivers.getOrDefault(ride.getDriverId(), MemberProfiles.deleted()), car == null ? null : car.name(),
					ride.getStops().getFirst().getName(), ride.getStops().getLast().getName(), ride.getStops().size(), yours,
					yourBooking));
		}
		results.sort(Comparator.comparing((SearchResult r) -> r.pickup().at()).thenComparingInt(SearchResult::price));
		List<DateCount> others = otherDays.entrySet().stream().map(e -> new DateCount(e.getKey(), e.getValue())).toList();
		return new SearchResponse(from, to, day, wanted, results.size() > MAX_RESULTS ? results.subList(0, MAX_RESULTS) : results,
				others);
	}

	private Place resolve(Long id, String name, String missingMessage) {
		if (id != null) {
			return places.find(id).orElseThrow(() -> ApiException.notFound("place_not_found", "We couldn't find that place."));
		}
		if (name == null || name.isBlank()) {
			throw ApiException.badRequest("place_required", missingMessage);
		}
		return places.search(name, 1)
			.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("place_not_found",
					"We couldn't find \"" + name.strip() + "\". Try the nearest city."));
	}

	private static Instant max(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}
}
