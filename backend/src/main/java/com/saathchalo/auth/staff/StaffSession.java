package com.saathchalo.auth.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A signed-in staff console session. Ends when idle too long, too old, signed out, or revoked by staff changes. */
@Entity
@Table(name = "staff_sessions")
public class StaffSession {

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID userId;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant lastSeenAt;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant revokedAt;

	private String ip;

	protected StaffSession() {
	}

	public StaffSession(UUID userId, Instant now, Instant expiresAt, String ip) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.createdAt = now;
		this.lastSeenAt = now;
		this.expiresAt = expiresAt;
		this.ip = ip;
	}

	public void touch(Instant now) {
		this.lastSeenAt = now;
	}

	public void revoke(Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

	public UUID getId() { return id; }
	public UUID getUserId() { return userId; }
	public Instant getLastSeenAt() { return lastSeenAt; }
	public Instant getExpiresAt() { return expiresAt; }
	public Instant getRevokedAt() { return revokedAt; }
}
