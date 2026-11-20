package com.saathchalo.drivers.internal;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverTripRepository extends JpaRepository<DriverTrip, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from DriverTrip t where t.id = :id")
	Optional<DriverTrip> lockById(@Param("id") UUID id);

	List<DriverTrip> findByCustomerIdOrderByStartsAtDesc(UUID customerId, Pageable page);

	List<DriverTrip> findByDriverIdOrderByStartsAtDesc(UUID driverId, Pageable page);

	long countByCustomerIdAndStatusIn(UUID customerId, Collection<String> statuses);

	long countByDriverIdAndStatusIn(UUID driverId, Collection<String> statuses);

	List<DriverTrip> findAllByOrderByCreatedAtDesc(Pageable page);

	List<DriverTrip> findByStatusInOrderByStartsAtAsc(Collection<String> statuses, Pageable page);
}
