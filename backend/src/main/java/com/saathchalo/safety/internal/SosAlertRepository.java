package com.saathchalo.safety.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SosAlertRepository extends JpaRepository<SosAlert, UUID> {

	List<SosAlert> findByStatusNotOrderByCreatedAtDesc(String status);

	List<SosAlert> findByStatusOrderByResolvedAtDesc(String status, Pageable page);

	long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);

	long countByStatusNot(String status);

	Optional<SosAlert> findFirstByUserIdAndRideIdAndStatusNotOrderByCreatedAtDesc(UUID userId, UUID rideId, String status);
}
