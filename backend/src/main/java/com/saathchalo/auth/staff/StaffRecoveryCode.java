package com.saathchalo.auth.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A one-time backup code for when the phone with the authenticator app is lost. Only a keyed hash is stored. */
@Entity
@Table(name = "staff_recovery_codes")
public class StaffRecoveryCode {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID userId;

	@Column(nullable = false)
	private String codeHash;

	private Instant usedAt;

	@Column(nullable = false)
	private Instant createdAt;

	protected StaffRecoveryCode() {
	}

	public StaffRecoveryCode(UUID userId, String codeHash, Instant now) {
		this.userId = userId;
		this.codeHash = codeHash;
		this.createdAt = now;
	}

	public void use(Instant now) {
		this.usedAt = now;
	}

	public String getCodeHash() { return codeHash; }
	public Instant getUsedAt() { return usedAt; }
}
