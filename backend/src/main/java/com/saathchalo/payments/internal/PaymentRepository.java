package com.saathchalo.payments.internal;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.id = :id")
	Optional<Payment> lockById(@Param("id") UUID id);

	Optional<Payment> findByBookingId(UUID bookingId);

	Optional<Payment> findByGatewayOrderId(String orderId);

	List<Payment> findByBookingIdIn(Collection<UUID> bookingIds);

	/** Paid payments whose trip isn't settled or refunded yet, oldest first. */
	@Query("select p.id from Payment p where p.status = 'PAID' and p.outcome is null order by p.createdAt")
	List<UUID> findOpenPaid(Pageable page);

	/** Payments that were never completed and whose hold is over. */
	@Query("select p.id from Payment p where p.status = 'CREATED' and p.holdUntil < :before order by p.createdAt")
	List<UUID> findUnpaidBefore(@Param("before") Instant before, Pageable page);

	List<Payment> findByPayerIdOrderByCreatedAtDesc(UUID payerId, Pageable page);

	List<Payment> findAllByOrderByCreatedAtDesc(Pageable page);

	@Query("select coalesce(sum(p.feePaise), 0) from Payment p where p.outcome in ('SETTLED', 'SPLIT')")
	long feesEarned();
}
