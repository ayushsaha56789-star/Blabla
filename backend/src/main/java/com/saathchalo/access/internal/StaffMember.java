package com.saathchalo.access.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_members")
public class StaffMember {

	@Id
	private UUID userId;

	@Column(nullable = false)
	private String roleCode;

	private UUID addedBy;

	@Column(nullable = false)
	private Instant addedAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected StaffMember() {
	}

	public StaffMember(UUID userId, String roleCode, UUID addedBy, Instant now) {
		this.userId = userId;
		this.roleCode = roleCode;
		this.addedBy = addedBy;
		this.addedAt = now;
		this.updatedAt = now;
	}

	public void changeRole(String roleCode, Instant now) {
		this.roleCode = roleCode;
		this.updatedAt = now;
	}

	public UUID getUserId() { return userId; }
	public String getRoleCode() { return roleCode; }
	public UUID getAddedBy() { return addedBy; }
	public Instant getAddedAt() { return addedAt; }
}
