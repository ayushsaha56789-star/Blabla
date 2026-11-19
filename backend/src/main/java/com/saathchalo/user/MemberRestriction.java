package com.saathchalo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "member_restrictions")
public class MemberRestriction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID userId;

	@Column(nullable = false)
	private String area;

	@Column(nullable = false)
	private String reason;

	@Column(nullable = false)
	private Instant startsAt;

	private Instant endsAt;

	private UUID createdBy;

	private Instant liftedAt;

	private UUID liftedBy;

	protected MemberRestriction() {
	}

	public MemberRestriction(UUID userId, String area, String reason, Instant startsAt, Instant endsAt, UUID createdBy) {
		this.userId = userId;
		this.area = area;
		this.reason = reason;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.createdBy = createdBy;
	}

	public boolean isActive(Instant now) {
		return liftedAt == null && !startsAt.isAfter(now) && (endsAt == null || endsAt.isAfter(now));
	}

	public void lift(UUID by, Instant now) {
		this.liftedAt = now;
		this.liftedBy = by;
	}

	public UUID getId() { return id; }
	public UUID getUserId() { return userId; }
	public String getArea() { return area; }
	public String getReason() { return reason; }
	public Instant getStartsAt() { return startsAt; }
	public Instant getEndsAt() { return endsAt; }
	public UUID getCreatedBy() { return createdBy; }
	public Instant getLiftedAt() { return liftedAt; }
	public UUID getLiftedBy() { return liftedBy; }
}
