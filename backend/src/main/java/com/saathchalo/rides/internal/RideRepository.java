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

	@EntityGraph(attributePaths = "stops")
	List<Ride> findByDriverIdAndStatusAndDepartureAtAfter(UUID driverId, RideStatus status, Instant after);

	long countByDriverIdAndStatus(UUID driverId, RideStatus status);

	@Query("select r.id from Ride r where r.driverId in :drivers and r.status = :status and r.departureAt > :after order by r.departureAt")
	List<UUID> findUpcomingIds(@Param("drivers") Collection<UUID> drivers, @Param("status") RideStatus status,
			@Param("after") Instant after, org.springframework.data.domain.Pageable page);

	/** Scheduled rides with a stop (after the start) within {@code meters} of a point, departing in the window. */
	@Query(value = """
			SELECT r.id FROM rides r
			WHERE r.status = 'SCHEDULED' AND r.departure_at BETWEEN :from AND :to
			  AND EXISTS (SELECT 1 FROM ride_stops s WHERE s.ride_id = r.id AND s.position > 0
			      AND ST_DWithin(s.location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :meters))
			ORDER BY r.departure_at LIMIT 50""", nativeQuery = true)
	List<UUID> findIdsGoingNear(@Param("lat") double lat, @Param("lng") double lng, @Param("meters") double meters,
			@Param("from") Instant from, @Param("to") Instant to);
}
