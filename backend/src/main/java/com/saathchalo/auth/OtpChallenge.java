package com.saathchalo.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One "we sent you a code" event. Only a keyed hash of the code is stored. */
@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 16)
	private String phone;

	@Column(nullable = false, length = 64)
	private String codeHash;

	private int attempts;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant consumedAt;

	@Column(nullable = false)
	private Instant createdAt;

	protected OtpChallenge() {
	}

	public OtpChallenge(String phone, String codeHash, Instant createdAt, Instant expiresAt) {
		this.phone = phone;
		this.codeHash = codeHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public boolean isUsable(Instant now) {
		return consumedAt == null && now.isBefore(expiresAt);
	}

	public void recordAttempt() {
		attempts++;
	}

	public void consume(Instant now) {
		consumedAt = now;
	}

	public UUID getId() { return id; }
	public String getPhone() { return phone; }
	public String getCodeHash() { return codeHash; }
	public int getAttempts() { return attempts; }
	public Instant getExpiresAt() { return expiresAt; }
	public Instant getConsumedAt() { return consumedAt; }
	public Instant getCreatedAt() { return createdAt; }
}
