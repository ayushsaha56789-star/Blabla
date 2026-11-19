package com.saathchalo.rides.internal;

import com.saathchalo.rides.RideStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RideRepository extends JpaRepository<Ride, UUID> {

	/** Loads a ride and locks it until the transaction ends, so seat counts can't change underneath a booking. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Ride r where r.id = :id")
	Optional<Ride> lockById(@Param("id") UUID id);

	@EntityGraph(attributePaths = "stops")
	@Query("select r from Ride r where r.id in :ids")
	List<Ride> findWithStops(@Param("ids") Collection<UUID> ids);

	@EntityGraph(attributePaths = "stops")
	@Query("select r from Ride r where r.driverId = :driverId and r.departureAt >= :from and r.departureAt < :to "
			+ "order by r.departureAt")
	List<Ride> findByDriverBetween(@Param("driverId") UUID driverId, @Param("from") Instant from, @Param("to") Instant to);

	long countByDriverIdAndStatusAndDepartureAtAfter(UUID driverId, RideStatus status, Instant after);

	long countByDriverIdAndStatus(UUID driverId, RideStatus status);
}
