package com.saathchalo.payments.internal;

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

public interface RefundRepository extends JpaRepository<Refund, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Refund r where r.id = :id")
	Optional<Refund> lockById(@Param("id") UUID id);

	@Query("select r.id from Refund r where r.status = 'PENDING' and r.gatewayRefundId is null order by r.createdAt")
	List<UUID> findToSend(Pageable page);

	List<Refund> findByPaymentIdInOrderByCreatedAtAsc(Collection<UUID> paymentIds);

	Optional<Refund> findByGatewayRefundId(String gatewayRefundId);

	long countByStatus(String status);

	List<Refund> findByStatusOrderByCreatedAtAsc(String status, Pageable page);
}
