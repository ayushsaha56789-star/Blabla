package com.saathchalo.safety.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Someone a member trusts, texted when they press SOS. The number is encrypted: it belongs to someone else. */
@Entity
@Table(name = "emergency_contacts")
public class EmergencyContact {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String phoneEnc;

	@Column(nullable = false)
	private String phoneHint;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected EmergencyContact() {
	}

	public EmergencyContact(UUID userId, String name, String phoneEnc, String phoneHint, Instant createdAt) {
		this.userId = userId;
		this.name = name;
		this.phoneEnc = phoneEnc;
		this.phoneHint = phoneHint;
		this.createdAt = createdAt;
	}

	public UUID getId() { return id; }
	public UUID getUserId() { return userId; }
	public String getName() { return name; }
	public String getPhoneEnc() { return phoneEnc; }
	public String getPhoneHint() { return phoneHint; }
	public Instant getCreatedAt() { return createdAt; }
}
