package com.saathchalo.verification.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "id_checks")
public class IdCheck {

	public static final String PENDING = "PENDING";

	public static final String APPROVED = "APPROVED";

	public static final String REJECTED = "REJECTED";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false)
	private String documentType;

	/** The encrypted photo in the document vault; null once deleted. */
	private String fileName;

	@Column(nullable = false)
	private String status = PENDING;

	@Column(nullable = false, updatable = false)
	private Instant submittedAt;

	private Instant decidedAt;

	private UUID decidedBy;

	private String rejectReason;

	private String rejectNote;

	private Instant fileDeletedAt;

	protected IdCheck() {
	}

	public IdCheck(UUID userId, String documentType, String fileName, Instant now) {
		this.userId = userId;
		this.documentType = documentType;
		this.fileName = fileName;
		this.submittedAt = now;
	}

	public void approve(UUID staffId, Instant now) {
		status = APPROVED;
		decidedBy = staffId;
		decidedAt = now;
	}

	public void reject(UUID staffId, String reason, String note, Instant now) {
		status = REJECTED;
		decidedBy = staffId;
		decidedAt = now;
		rejectReason = reason;
		rejectNote = note;
	}

	public void photoDeleted(Instant now) {
		fileName = null;
		fileDeletedAt = now;
	}

	public boolean isPending() {
		return PENDING.equals(status);
	}

	public UUID getId() { return id; }
	public UUID getUserId() { return userId; }
	public String getDocumentType() { return documentType; }
	public String getFileName() { return fileName; }
	public String getStatus() { return status; }
	public Instant getSubmittedAt() { return submittedAt; }
	public Instant getDecidedAt() { return decidedAt; }
	public UUID getDecidedBy() { return decidedBy; }
	public String getRejectReason() { return rejectReason; }
	public String getRejectNote() { return rejectNote; }
	public Instant getFileDeletedAt() { return fileDeletedAt; }
}
