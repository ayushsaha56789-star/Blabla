package com.saathchalo.payments.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Paying a driver what they earned: a Razorpay payout link, or money sent outside the app and recorded here. */
@Entity
@Table(name = "payouts")
public class Payout {

	public static final String LINK = "LINK";

	public static final String MANUAL = "MANUAL";

	public static final String SENT = "SENT";

	public static final String PAID = "PAID";

	public static final String CANCELLED = "CANCELLED";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID driverId;

	private long amountPaise;

	@Column(nullable = false)
	private String method;

	@Column(nullable = false)
	private String status;

	private String gatewayPayoutId;

	private String linkUrl;

	private String reference;

	@Column(nullable = false, updatable = false)
	private UUID createdBy;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant paidAt;

	protected Payout() {
	}

	public Payout(UUID driverId, long amountPaise, String method, String reference, UUID createdBy, Instant now) {
		this.driverId = driverId;
		this.amountPaise = amountPaise;
		this.method = method;
		this.reference = reference;
		this.createdBy = createdBy;
		this.createdAt = now;
		this.status = MANUAL.equals(method) ? PAID : SENT;
		this.paidAt = MANUAL.equals(method) ? now : null;
	}

	public void linkSent(String gatewayPayoutId, String url) {
		this.gatewayPayoutId = gatewayPayoutId;
		this.linkUrl = url;
	}

	public void paid(Instant now) {
		this.status = PAID;
		this.paidAt = now;
	}

	public void cancelled() {
		this.status = CANCELLED;
	}

	public UUID getId() { return id; }
	public UUID getDriverId() { return driverId; }
	public long getAmountPaise() { return amountPaise; }
	public String getMethod() { return method; }
	public String getStatus() { return status; }
	public String getGatewayPayoutId() { return gatewayPayoutId; }
	public String getLinkUrl() { return linkUrl; }
	public String getReference() { return reference; }
	public UUID getCreatedBy() { return createdBy; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getPaidAt() { return paidAt; }
}
