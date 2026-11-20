package com.saathchalo.rides;

import com.saathchalo.rides.internal.Booking;
import com.saathchalo.rides.internal.BookingRepository;
import com.saathchalo.rides.internal.Ride;
import com.saathchalo.rides.internal.RideRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only facts about rides for other parts of SaathChalo (chat, reviews, safety). */
@Service
public class RideLookup {

	/** A ride in one line: "Delhi → Jaipur, Fri 7:30 AM". */
	public record RideBrief(UUID id, UUID driverId, String fromName, String toName, Instant departureAt,
			Instant arrivalAt, RideStatus status, boolean ladiesOnly) {
	}

	/** A passenger's latest booking on a ride. */
	public record Seat(UUID passengerId, BookingStatus status, int fromPosition, int toPosition, String fromName,
			String toName) {
	}

	/** Who is on a ride: the driver, and each passenger's latest booking. */
	public record RideParticipants(RideBrief ride, Map<UUID, Seat> passengers) {

		public boolean isDriver(UUID memberId) {
			return ride.driverId().equals(memberId);
		}

		/** Has (or had) a booking of any kind on this ride. */
		public boolean hasBooking(UUID memberId) {
			return passengers.containsKey(memberId);
		}

		/** Travelled, or is going to travel, on this ride. */
		public boolean isConfirmedPassenger(UUID memberId) {
			Seat seat = passengers.get(memberId);
			return seat != null && (seat.status() == BookingStatus.CONFIRMED || seat.status() == BookingStatus.COMPLETED);
		}
	}

	private final RideRepository rides;

	private final BookingRepository bookings;

	public RideLookup(RideRepository rides, BookingRepository bookings) {
		this.rides = rides;
		this.bookings = bookings;
	}

	@Transactional(readOnly = true)
	public Optional<RideParticipants> participants(UUID rideId) {
		return rides.findWithStops(List.of(rideId)).stream().findFirst().map(ride -> {
			Map<UUID, Seat> seats = new LinkedHashMap<>();
			bookings.findByRideIdOrderByCreatedAtAsc(rideId)
				.stream()
				.sorted(Comparator.comparing(Booking::getCreatedAt))
				.forEach(b -> seats.put(b.getPassengerId(), new Seat(b.getPassengerId(), b.getStatus(), b.getFromPosition(),
						b.getToPosition(), ride.getStops().get(b.getFromPosition()).getName(),
						ride.getStops().get(b.getToPosition()).getName())));
			return new RideParticipants(brief(ride), seats);
		});
	}

	/**
	 * What the payments module needs about a booking: who, how much, when, and how it ended. {@code decidedAt} is when
	 * the seat was confirmed (or declined); null for requests nobody answered.
	 */
	public record BookingFacts(UUID bookingId, UUID rideId, UUID driverId, UUID passengerId, BookingStatus status,
			int seats, int pricePerSeat, String fromName, String toName, Instant pickupAt, Instant decidedAt,
			Instant cancelledAt, String cancelledBy, RideStatus rideStatus) {

		public int total() {
			return seats * pricePerSeat;
		}
	}

	@Transactional(readOnly = true)
	public Map<UUID, BookingFacts> bookingFacts(Collection<UUID> bookingIds) {
		if (bookingIds.isEmpty()) {
			return Map.of();
		}
		List<Booking> list = bookings.findAllById(bookingIds);
		Map<UUID, Ride> byId = new LinkedHashMap<>();
		rides.findWithStops(list.stream().map(Booking::getRideId).distinct().toList()).forEach(r -> byId.put(r.getId(), r));
		Map<UUID, BookingFacts> out = new LinkedHashMap<>();
		for (Booking b : list) {
			Ride r = byId.get(b.getRideId());
			if (r == null) {
				continue;
			}
			out.put(b.getId(), new BookingFacts(b.getId(), r.getId(), r.getDriverId(), b.getPassengerId(), b.getStatus(),
					b.getSeats(), b.getPricePerSeat(), r.getStops().get(b.getFromPosition()).getName(),
					r.getStops().get(b.getToPosition()).getName(), r.timeAt(b.getFromPosition()), b.getDecidedAt(),
					b.getCancelledAt(), b.getCancelledBy(), r.getStatus()));
		}
		return out;
	}

	/** A completed trip and the people this member travelled with: the passengers (as driver) or the driver. */
	public record FinishedTrip(RideBrief ride, boolean asDriver, List<UUID> mates) {
	}

	/** Completed trips (driven or ridden) that arrived after {@code arrivedAfter}, newest first. */
	@Transactional(readOnly = true)
	public List<FinishedTrip> finishedTrips(UUID memberId, Instant arrivedAfter) {
		List<FinishedTrip> trips = new java.util.ArrayList<>();
		List<Ride> driven = rides.findByDriverIdAndStatusAndDepartureAtAfter(memberId, RideStatus.COMPLETED,
				arrivedAfter.minus(java.time.Duration.ofDays(5)));
		Map<UUID, List<Booking>> riders = driven.isEmpty() ? Map.of()
				: bookings.findByRideIdInAndStatusIn(driven.stream().map(Ride::getId).toList(), java.util.Set.of(BookingStatus.COMPLETED))
					.stream()
					.collect(java.util.stream.Collectors.groupingBy(Booking::getRideId));
		for (Ride r : driven) {
			List<UUID> mates = riders.getOrDefault(r.getId(), List.of()).stream().map(Booking::getPassengerId).distinct().toList();
			if (r.arrivalAt().isAfter(arrivedAfter) && !mates.isEmpty()) {
				trips.add(new FinishedTrip(brief(r), true, mates));
			}
		}
		List<Booking> ridden = bookings.findByPassengerIdAndStatusAndCreatedAtAfter(memberId, BookingStatus.COMPLETED,
				arrivedAfter.minus(java.time.Duration.ofDays(100)));
		if (!ridden.isEmpty()) {
			for (Ride r : rides.findWithStops(ridden.stream().map(Booking::getRideId).distinct().toList())) {
				if (r.getStatus() == RideStatus.COMPLETED && r.arrivalAt().isAfter(arrivedAfter)) {
					trips.add(new FinishedTrip(brief(r), false, List.of(r.getDriverId())));
				}
			}
		}
		trips.sort(Comparator.comparing((FinishedTrip t) -> t.ride().departureAt()).reversed());
		return trips;
	}

	/**
	 * A completed trip as rewards count it. {@code km}: the part this member travelled (a driver: the whole ride);
	 * {@code passengerKm}: km × seats of the passengers on it, the car-km saved; {@code rupees}: costs shared.
	 */
	public record SharedTrip(UUID rideId, Instant departureAt, boolean asDriver, int km, int passengerKm, List<UUID> mates,
			int rupees) {
	}

	/** Every completed trip this member shared (driving with passengers, or riding), newest first. */
	@Transactional(readOnly = true)
	public List<SharedTrip> sharedTrips(UUID memberId) {
		List<SharedTrip> out = new java.util.ArrayList<>();
		List<UUID> driven = rides.findByDriverIdAndStatusAndDepartureAtAfter(memberId, RideStatus.COMPLETED, Instant.EPOCH).stream()
			.map(Ride::getId)
			.toList();
		if (!driven.isEmpty()) {
			Map<UUID, List<Booking>> riders = bookings.findByRideIdInAndStatusIn(driven, java.util.Set.of(BookingStatus.COMPLETED))
				.stream()
				.collect(java.util.stream.Collectors.groupingBy(Booking::getRideId));
			for (Ride r : rides.findWithStops(driven)) {
				List<Booking> bs = riders.getOrDefault(r.getId(), List.of());
				if (!bs.isEmpty()) {
					out.add(new SharedTrip(r.getId(), r.getDepartureAt(), true, r.getDistanceKm(),
							bs.stream().mapToInt(b -> segmentKm(r, b) * b.getSeats()).sum(),
							bs.stream().map(Booking::getPassengerId).distinct().toList(),
							bs.stream().mapToInt(b -> b.getPricePerSeat() * b.getSeats()).sum()));
				}
			}
		}
		List<Booking> ridden = bookings.findByPassengerIdAndStatusAndCreatedAtAfter(memberId, BookingStatus.COMPLETED, Instant.EPOCH);
		if (!ridden.isEmpty()) {
			Map<UUID, Ride> byId = new LinkedHashMap<>();
			rides.findWithStops(ridden.stream().map(Booking::getRideId).distinct().toList()).forEach(r -> byId.put(r.getId(), r));
			for (Booking b : ridden) {
				Ride r = byId.get(b.getRideId());
				if (r != null && r.getStatus() == RideStatus.COMPLETED) {
					int km = segmentKm(r, b);
					out.add(new SharedTrip(r.getId(), r.getDepartureAt(), false, km, km * b.getSeats(), List.of(r.getDriverId()),
							b.getPricePerSeat() * b.getSeats()));
				}
			}
		}
		out.sort(Comparator.comparing(SharedTrip::departureAt).reversed());
		return out;
	}

	/** Upcoming rides offered by these drivers, soonest first (for squads). */
	@Transactional(readOnly = true)
	public List<RideBrief> upcomingByDrivers(Collection<UUID> driverIds, int limit) {
		if (driverIds.isEmpty()) {
			return List.of();
		}
		return ordered(rides.findUpcomingIds(driverIds, RideStatus.SCHEDULED, Instant.now(),
				org.springframework.data.domain.PageRequest.of(0, limit)));
	}

	/** Scheduled rides stopping within {@code radiusKm} of a place, departing in the window (for event pages). */
	@Transactional(readOnly = true)
	public List<RideBrief> goingNear(double lat, double lng, double radiusKm, Instant from, Instant to) {
		return ordered(rides.findIdsGoingNear(lat, lng, radiusKm * 1000, from, to));
	}

	private List<RideBrief> ordered(List<UUID> ids) {
		Map<UUID, RideBrief> byId = briefs(ids);
		return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
	}

	private static int segmentKm(Ride r, Booking b) {
		return r.getStops().get(b.getToPosition()).getKmFromStart() - r.getStops().get(b.getFromPosition()).getKmFromStart();
	}

	@Transactional(readOnly = true)
	public Map<UUID, RideBrief> briefs(Collection<UUID> rideIds) {
		Map<UUID, RideBrief> out = new LinkedHashMap<>();
		if (!rideIds.isEmpty()) {
			rides.findWithStops(rideIds.stream().distinct().toList()).forEach(r -> out.put(r.getId(), brief(r)));
		}
		return out;
	}

	/** A stop and when the car is expected there. */
	public record StopTime(int position, String name, String state, String meetingPoint, Instant at) {
	}

	/** Everything a shared trip page shows about a ride (the car's details come from the vehicles module). */
	public record RideSnapshot(RideBrief ride, UUID vehicleId, List<StopTime> stops) {
	}

	@Transactional(readOnly = true)
	public Optional<RideSnapshot> snapshot(UUID rideId) {
		return rides.findWithStops(List.of(rideId)).stream().findFirst().map(r -> {
			List<StopTime> stops = new java.util.ArrayList<>();
			for (int i = 0; i <= r.lastPosition(); i++) {
				var s = r.getStops().get(i);
				stops.add(new StopTime(i, s.getName(), s.getState(), s.getMeetingPoint(), r.timeAt(i)));
			}
			return new RideSnapshot(brief(r), r.getVehicleId(), List.copyOf(stops));
		});
	}

	static RideBrief brief(Ride r) {
		return new RideBrief(r.getId(), r.getDriverId(), r.getStops().getFirst().getName(), r.getStops().getLast().getName(),
				r.getDepartureAt(), r.arrivalAt(), r.getStatus(), r.isLadiesOnly());
	}
}
