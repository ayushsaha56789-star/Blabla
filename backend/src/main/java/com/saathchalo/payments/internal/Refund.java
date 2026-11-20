package com.saathchalo.payments.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Money going back to a passenger: the online part through the gateway, the credits part straight away. */
@Entity
@Table(name = "refunds")
public class Refund {

	public static final String PENDING = "PENDING";

	public static final String PROCESSED = "PROCESSED";

	public static final String FAILED = "FAILED";

	static final int MAX_ATTEMPTS = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID paymentId;

	private long onlinePaise;

	private long creditsPaise;

	@Column(nullable = false)
	private String reason;

	@Column(nullable = false)
	private String status;

	private String gatewayRefundId;

	private int attempts;

	private String lastError;

	private UUID createdBy;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant processedAt;

	protected Refund() {
	}

	public Refund(UUID paymentId, long onlinePaise, long creditsPaise, String reason, UUID createdBy, Instant now) {
		this.paymentId = paymentId;
		this.onlinePaise = onlinePaise;
		this.creditsPaise = creditsPaise;
		this.reason = reason;
		this.createdBy = createdBy;
		this.createdAt = now;
		// Credits go back at once; only an online part has to wait for the gateway.
		this.status = onlinePaise > 0 ? PENDING : PROCESSED;
		this.processedAt = onlinePaise > 0 ? null : now;
	}

	/** Whether the gateway still has to be asked. */
	public boolean needsSending() {
		return PENDING.equals(status) && gatewayRefundId == null;
	}

	public void sent(String gatewayRefundId, boolean processed, Instant now) {
		this.gatewayRefundId = gatewayRefundId;
		this.attempts++;
		if (processed) {
			processed(now);
		}
	}

	public void processed(Instant now) {
		this.status = PROCESSED;
		this.processedAt = now;
		this.lastError = null;
	}

	/** After {@link #MAX_ATTEMPTS} failed tries the refund waits for staff. */
	public void failedAttempt(String error) {
		this.attempts++;
		this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 300));
		if (attempts >= MAX_ATTEMPTS) {
			this.status = FAILED;
		}
	}

	/** The gateway reported that the refund didn't go through. */
	public void failed(String error) {
		this.status = FAILED;
		this.lastError = error;
	}

	public void retry() {
		this.status = PENDING;
		this.gatewayRefundId = null;
		this.attempts = 0;
	}

	public UUID getId() { return id; }
	public UUID getPaymentId() { return paymentId; }
	public long getOnlinePaise() { return onlinePaise; }
	public long getCreditsPaise() { return creditsPaise; }
	public String getReason() { return reason; }
	public String getStatus() { return status; }
	public String getGatewayRefundId() { return gatewayRefundId; }
	public int getAttempts() { return attempts; }
	public String getLastError() { return lastError; }
	public UUID getCreatedBy() { return createdBy; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getProcessedAt() { return processedAt; }
}
