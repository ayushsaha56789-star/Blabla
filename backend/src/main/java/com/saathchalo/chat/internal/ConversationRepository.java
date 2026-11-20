package com.saathchalo.chat.internal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

	Optional<Conversation> findByRideIdAndPassengerId(UUID rideId, UUID passengerId);

	long countByPassengerIdAndCreatedAtAfter(UUID passengerId, Instant after);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Conversation c where c.id = :id")
	Optional<Conversation> lockById(@Param("id") UUID id);
}
