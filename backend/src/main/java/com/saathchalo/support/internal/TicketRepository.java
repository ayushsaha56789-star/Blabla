package com.saathchalo.support.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

	List<Ticket> findByUserIdOrderByUpdatedAtDesc(UUID userId);

	List<Ticket> findByStatusOrderByUpdatedAtAsc(String status, Pageable page);

	List<Ticket> findAllByOrderByUpdatedAtDesc(Pageable page);

	long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);

	long countByStatus(String status);

	@Query("SELECT COUNT(t) FROM Ticket t WHERE t.userId = :userId AND t.status = 'ANSWERED'"
			+ " AND (t.memberReadAt IS NULL OR t.memberReadAt < t.updatedAt)")
	long countNewReplies(UUID userId);
}
