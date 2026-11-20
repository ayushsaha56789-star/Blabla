package com.saathchalo.support.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_messages")
public class TicketMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID ticketId;

	private UUID authorId;

	private boolean fromStaff;

	@Column(nullable = false, updatable = false)
	private String body;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected TicketMessage() {
	}

	public TicketMessage(UUID ticketId, UUID authorId, boolean fromStaff, String body, Instant createdAt) {
		this.ticketId = ticketId;
		this.authorId = authorId;
		this.fromStaff = fromStaff;
		this.body = body;
		this.createdAt = createdAt;
	}

	public UUID getId() { return id; }
	public UUID getTicketId() { return ticketId; }
	public UUID getAuthorId() { return authorId; }
	public boolean isFromStaff() { return fromStaff; }
	public String getBody() { return body; }
	public Instant getCreatedAt() { return createdAt; }
}
