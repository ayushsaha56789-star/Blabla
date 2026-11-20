package com.saathchalo.support.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {

	List<TicketMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
