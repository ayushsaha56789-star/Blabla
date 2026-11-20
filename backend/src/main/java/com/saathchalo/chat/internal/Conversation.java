package com.saathchalo.chat.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A chat about one ride between its driver and one passenger (or someone asking before they book). */
@Entity
@Table(name = "conversations")
public class Conversation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID rideId;

	@Column(nullable = false, updatable = false)
	private UUID driverId;

	@Column(nullable = false, updatable = false)
	private UUID passengerId;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant lastMessageAt;

	private Instant driverReadAt;

	private Instant passengerReadAt;

	protected Conversation() {
	}

	public Conversation(UUID rideId, UUID driverId, UUID passengerId, Instant createdAt) {
		this.rideId = rideId;
		this.driverId = driverId;
		this.passengerId = passengerId;
		this.createdAt = createdAt;
	}

	public boolean isParticipant(UUID memberId) {
		return driverId.equals(memberId) || passengerId.equals(memberId);
	}

	public boolean isDriver(UUID memberId) {
		return driverId.equals(memberId);
	}

	public UUID otherThan(UUID memberId) {
		return isDriver(memberId) ? passengerId : driverId;
	}

	public void markRead(UUID memberId, Instant when) {
		if (isDriver(memberId)) {
			driverReadAt = when;
		}
		else {
			passengerReadAt = when;
		}
	}

	/** When the other person last opened the chat (for "Seen"). */
	public Instant readByOtherAt(UUID memberId) {
		return isDriver(memberId) ? passengerReadAt : driverReadAt;
	}

	public void messageSent(Instant when) {
		lastMessageAt = when;
	}

	public UUID getId() { return id; }
	public UUID getRideId() { return rideId; }
	public UUID getDriverId() { return driverId; }
	public UUID getPassengerId() { return passengerId; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getLastMessageAt() { return lastMessageAt; }
}
