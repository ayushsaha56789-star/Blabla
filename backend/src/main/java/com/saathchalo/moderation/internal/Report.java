package com.saathchalo.moderation.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A member's report about another member, a ride, a chat message or a review. */
@Entity
@Table(name = "reports")
public class Report {

	public static final String OPEN = "OPEN";

	public static final String ACTIONED = "ACTIONED";

	public static final String DISMISSED = "DISMISSED";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private UUID reporterId;

	@Column(nullable = false, updatable = false)
	private String targetType;

	@Column(nullable = false, updatable = false)
	private UUID targetId;

	private UUID reportedUserId;

	@Column(nullable = false, updatable = false)
	private String reason;

	private String details;

	/** A copy of the reported text, so the report stands even if the original is deleted later. */
	private String snapshot;

	@Column(nullable = false)
	private String status = OPEN;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant acknowledgedAt;

	private Instant resolvedAt;

	private UUID handledBy;

	private String resolution;

	protected Report() {
	}

	public Report(UUID reporterId, String targetType, UUID targetId, UUID reportedUserId, String reason, String details,
			String snapshot, Instant createdAt) {
		this.reporterId = reporterId;
		this.targetType = targetType;
		this.targetId = targetId;
		this.reportedUserId = reportedUserId;
		this.reason = reason;
		this.details = details;
		this.snapshot = snapshot;
		this.createdAt = createdAt;
	}

	public void acknowledge(UUID staffId, Instant when) {
		if (acknowledgedAt == null) {
			acknowledgedAt = when;
			handledBy = staffId;
		}
	}

	public void close(String status, String resolution, UUID staffId, Instant when) {
		acknowledge(staffId, when);
		this.status = status;
		this.resolution = resolution;
		this.handledBy = staffId;
		this.resolvedAt = when;
	}

	public UUID getId() { return id; }
	public UUID getReporterId() { return reporterId; }
	public String getTargetType() { return targetType; }
	public UUID getTargetId() { return targetId; }
	public UUID getReportedUserId() { return reportedUserId; }
	public String getReason() { return reason; }
	public String getDetails() { return details; }
	public String getSnapshot() { return snapshot; }
	public String getStatus() { return status; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getAcknowledgedAt() { return acknowledgedAt; }
	public Instant getResolvedAt() { return resolvedAt; }
	public UUID getHandledBy() { return handledBy; }
	public String getResolution() { return resolution; }
}
