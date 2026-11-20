package com.saathchalo.support.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A member's help request. OPEN waits for staff, ANSWERED waits for the member, CLOSED is done. */
@Entity
@Table(name = "support_tickets")
public class Ticket {

	public static final String OPEN = "OPEN";

	public static final String ANSWERED = "ANSWERED";

	public static final String CLOSED = "CLOSED";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false)
	private String category;

	@Column(nullable = false, updatable = false)
	private String subject;

	private UUID rideId;

	@Column(nullable = false)
	private String status = OPEN;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	private Instant firstResponseAt;

	private Instant closedAt;

	private UUID assignedTo;

	/** When the member last opened the request; a staff reply after this is "new" for them. */
	private Instant memberReadAt;

	protected Ticket() {
	}

	public Ticket(UUID userId, String category, String subject, UUID rideId, Instant now) {
		this.userId = userId;
		this.category = category;
		this.subject = subject;
		this.rideId = rideId;
		this.createdAt = now;
		this.updatedAt = now;
		this.memberReadAt = now;
	}

	public void memberReplied(Instant now) {
		status = OPEN;
		closedAt = null;
		updatedAt = now;
		memberReadAt = now;
	}

	public void readByMember(Instant now) {
		memberReadAt = now;
	}

	/** Support replied and the member hasn't opened the request since. */
	public boolean hasNewReply() {
		return ANSWERED.equals(status) && (memberReadAt == null || memberReadAt.isBefore(updatedAt));
	}

	public void staffReplied(UUID staffId, Instant now) {
		status = ANSWERED;
		if (firstResponseAt == null) {
			firstResponseAt = now;
		}
		if (assignedTo == null) {
			assignedTo = staffId;
		}
		updatedAt = now;
	}

	public void close(Instant now) {
		status = CLOSED;
		closedAt = now;
		updatedAt = now;
	}

	public UUID getId() { return id; }
	public UUID getUserId() { return userId; }
	public String getCategory() { return category; }
	public String getSubject() { return subject; }
	public UUID getRideId() { return rideId; }
	public String getStatus() { return status; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public Instant getFirstResponseAt() { return firstResponseAt; }
	public Instant getClosedAt() { return closedAt; }
	public UUID getAssignedTo() { return assignedTo; }
	public Instant getMemberReadAt() { return memberReadAt; }
}
