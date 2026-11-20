package com.saathchalo.chat.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

	List<ChatMessage> findTop200ByConversationIdOrderByCreatedAtDesc(UUID conversationId);

	List<ChatMessage> findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID conversationId, Instant after);
}
