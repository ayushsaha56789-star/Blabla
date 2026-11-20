package com.saathchalo.chat.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID conversationId;

	@Column(nullable = false, updatable = false)
	private UUID senderId;

	@Column(nullable = false, updatable = false)
	private String body;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	/** Set when staff remove a reported message; the text is then no longer shown. */
	private Instant hiddenAt;

	protected ChatMessage() {
	}

	public ChatMessage(UUID conversationId, UUID senderId, String body, Instant createdAt) {
		this.conversationId = conversationId;
		this.senderId = senderId;
		this.body = body;
		this.createdAt = createdAt;
	}

	public void hide(Instant when) {
		this.hiddenAt = when;
	}

	public UUID getId() { return id; }
	public UUID getConversationId() { return conversationId; }
	public UUID getSenderId() { return senderId; }
	public String getBody() { return body; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getHiddenAt() { return hiddenAt; }
}
