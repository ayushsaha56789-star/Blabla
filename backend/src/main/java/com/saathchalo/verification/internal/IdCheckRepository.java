package com.saathchalo.verification.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IdCheckRepository extends JpaRepository<IdCheck, UUID> {

	Optional<IdCheck> findFirstByUserIdOrderBySubmittedAtDesc(UUID userId);

	List<IdCheck> findByUserIdOrderBySubmittedAtDesc(UUID userId);

	boolean existsByUserIdAndStatus(UUID userId, String status);

	long countByUserIdAndSubmittedAtAfter(UUID userId, Instant after);

	long countByStatus(String status);

	List<IdCheck> findByStatusOrderBySubmittedAtAsc(String status, Pageable page);

	List<IdCheck> findByStatusOrderByDecidedAtDesc(String status, Pageable page);

	@Query("select c from IdCheck c where c.fileName is not null and c.decidedAt < :before")
	List<IdCheck> findPhotosToDelete(Instant before);
}
