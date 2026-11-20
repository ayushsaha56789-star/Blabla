package com.saathchalo.payments.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

	List<Payout> findByDriverIdOrderByCreatedAtDesc(UUID driverId, Pageable page);

	List<Payout> findAllByOrderByCreatedAtDesc(Pageable page);

	Optional<Payout> findByGatewayPayoutId(String gatewayPayoutId);
}
