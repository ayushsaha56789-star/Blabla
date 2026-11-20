package com.saathchalo.safety.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, UUID> {

	List<EmergencyContact> findByUserIdOrderByCreatedAtAsc(UUID userId);

	long countByUserId(UUID userId);
}
