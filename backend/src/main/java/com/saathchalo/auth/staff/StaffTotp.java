package com.saathchalo.auth.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A staff member's authenticator-app secret (encrypted). Not enabled until the first code is confirmed. */
@Entity
@Table(name = "staff_totp")
public class StaffTotp {

	@Id
	private UUID userId;

	@Column(nullable = false)
	private String secretEnc;

	private Instant enabledAt;

	private long lastLoginStep;

	private long lastConfirmStep;

	@Column(nullable = false)
	private Instant createdAt;

	protected StaffTotp() {
	}

	public StaffTotp(UUID userId, String secretEnc, Instant now) {
		this.userId = userId;
		this.secretEnc = secretEnc;
		this.createdAt = now;
	}

	public boolean isEnabled() {
		return enabledAt != null;
	}

	public void enable(Instant now, long step) {
		this.enabledAt = now;
		this.lastLoginStep = step;
		this.lastConfirmStep = step;
	}

	public void replaceSecret(String secretEnc, Instant now) {
		this.secretEnc = secretEnc;
		this.createdAt = now;
		this.enabledAt = null;
	}

	public UUID getUserId() { return userId; }
	public String getSecretEnc() { return secretEnc; }
	public long getLastLoginStep() { return lastLoginStep; }
	public void setLastLoginStep(long step) { this.lastLoginStep = step; }
	public long getLastConfirmStep() { return lastConfirmStep; }
	public void setLastConfirmStep(long step) { this.lastConfirmStep = step; }
}
