package com.saathchalo.payments.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Paying for one booking, partly or wholly online and/or with SaathChalo credits. Amounts in paise. */
@Entity
@Table(name = "payments")
public class Payment {

	public static final String CREATED = "CREATED";

	public static final String PAID = "PAID";

	public static final String EXPIRED = "EXPIRED";

	/** The driver got the seat money. */
	public static final String SETTLED = "SETTLED";

	/** The passenger got everything back. */
	public static final String REFUNDED = "REFUNDED";

	/** Late cancellation: part back to the passenger, part to the driver. */
	public static final String SPLIT = "SPLIT";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID bookingId;

	@Column(nullable = false, updatable = false)
	private UUID payerId;

	@Column(nullable = false, updatable = false)
	private UUID driverId;

	private long seatPaise;

	private long feePaise;

	private long creditsPaise;

	private long onlinePaise;

	@Column(nullable = false)
	private String gateway;

	private String gatewayOrderId;

	private String gatewayPaymentId;

	@Column(nullable = false)
	private String status = CREATED;

	private String outcome;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant holdUntil;

	private Instant paidAt;

	private Instant closedAt;

	protected Payment() {
	}

	public Payment(UUID bookingId, UUID payerId, UUID driverId, long seatPaise, long feePaise, long creditsPaise,
			String gateway, Instant now, Instant holdUntil) {
		this.bookingId = bookingId;
		this.payerId = payerId;
		this.driverId = driverId;
		this.seatPaise = seatPaise;
		this.feePaise = feePaise;
		this.creditsPaise = creditsPaise;
		this.onlinePaise = seatPaise + feePaise - creditsPaise;
		this.gateway = gateway;
		this.createdAt = now;
		this.holdUntil = holdUntil;
	}

	public void orderCreated(String orderId) {
		this.gatewayOrderId = orderId;
	}

	public void paid(String gatewayPaymentId, Instant now) {
		this.status = PAID;
		this.gatewayPaymentId = gatewayPaymentId;
		this.paidAt = now;
	}

	public void expired(Instant now) {
		this.status = EXPIRED;
		this.closedAt = now;
	}

	public void close(String outcome, Instant now) {
		this.outcome = outcome;
		this.closedAt = now;
	}

	public long total() {
		return seatPaise + feePaise;
	}

	public boolean isOpen() {
		return outcome == null && !EXPIRED.equals(status);
	}

	public UUID getId() { return id; }
	public UUID getBookingId() { return bookingId; }
	public UUID getPayerId() { return payerId; }
	public UUID getDriverId() { return driverId; }
	public long getSeatPaise() { return seatPaise; }
	public long getFeePaise() { return feePaise; }
	public long getCreditsPaise() { return creditsPaise; }
	public long getOnlinePaise() { return onlinePaise; }
	public String getGateway() { return gateway; }
	public String getGatewayOrderId() { return gatewayOrderId; }
	public String getGatewayPaymentId() { return gatewayPaymentId; }
	public String getStatus() { return status; }
	public String getOutcome() { return outcome; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getHoldUntil() { return holdUntil; }
	public Instant getPaidAt() { return paidAt; }
	public Instant getClosedAt() { return closedAt; }
}
