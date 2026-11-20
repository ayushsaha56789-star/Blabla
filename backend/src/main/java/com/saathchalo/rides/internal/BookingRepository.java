package com.saathchalo.rides.internal;

import com.saathchalo.rides.BookingStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

	List<Booking> findByRideIdInAndStatusIn(Collection<UUID> rideIds, Collection<BookingStatus> statuses);

	List<Booking> findByRideIdOrderByCreatedAtAsc(UUID rideId);

	Optional<Booking> findFirstByRideIdAndPassengerIdOrderByCreatedAtDesc(UUID rideId, UUID passengerId);

	boolean existsByRideIdAndPassengerIdAndStatusIn(UUID rideId, UUID passengerId, Collection<BookingStatus> statuses);

	List<Booking> findByPassengerIdOrderByCreatedAtDesc(UUID passengerId);

	List<Booking> findByPassengerIdAndRideIdIn(UUID passengerId, Collection<UUID> rideIds);

	List<Booking> findByPassengerIdAndStatusAndCreatedAtAfter(UUID passengerId, BookingStatus status, Instant after);

	/** Only the ride id, without loading the booking (so it can be loaded fresh after the ride is locked). */
	@Query("select b.rideId from Booking b where b.id = :id")
	Optional<UUID> findRideIdOf(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Booking b where b.id = :id")
	Optional<Booking> lockById(@Param("id") UUID id);
}
