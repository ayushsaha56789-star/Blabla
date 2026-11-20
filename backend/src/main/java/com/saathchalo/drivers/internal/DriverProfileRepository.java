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

public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from DriverProfile p where p.userId = :id")
	Optional<DriverProfile> lockById(@Param("id") UUID id);

	List<DriverProfile> findByStatusOrderByUpdatedAtAsc(String status, Pageable page);

	List<DriverProfile> findByStatusInOrderByUpdatedAtDesc(Collection<String> statuses, Pageable page);

	long countByStatus(String status);
}
