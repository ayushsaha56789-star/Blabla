package com.saathchalo.safety.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripShareRepository extends JpaRepository<TripShare, UUID> {

	Optional<TripShare> findByTokenHash(String tokenHash);

	Optional<TripShare> findByRideIdAndUserIdAndRevokedAtIsNull(UUID rideId, UUID userId);
}
