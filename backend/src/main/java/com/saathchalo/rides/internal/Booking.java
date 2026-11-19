package com.saathchalo.rides.internal;

import com.saathchalo.rides.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A passenger's seats on one stretch of a ride, from stop {@code fromPosition} to stop {@code toPosition}. */
@Entity
@Table(name = "bookings")
public class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID rideId;

	@Column(nullable = false, updatable = false)
	private UUID passengerId;

	private int fromPosition;

	private int toPosition;

	private int seats;

	private int pricePerSeat;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookingStatus status;

	private String message;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant decidedAt;

	private Instant cancelledAt;

	private String cancelledBy;

	protected Booking() {
	}

	public Booking(UUID rideId, UUID passengerId, int fromPosition, int toPosition, int seats, int pricePerSeat,
			boolean confirmed, String message, Instant createdAt) {
		this.rideId = rideId;
		this.passengerId = passengerId;
		this.fromPosition = fromPosition;
		this.toPosition = toPosition;
		this.seats = seats;
		this.pricePerSeat = pricePerSeat;
		this.status = confirmed ? BookingStatus.CONFIRMED : BookingStatus.REQUESTED;
		this.decidedAt = confirmed ? createdAt : null;
		this.message = message;
		this.createdAt = createdAt;
	}

	public void decide(boolean accepted, Instant when) {
		this.status = accepted ? BookingStatus.CONFIRMED : BookingStatus.DECLINED;
		this.decidedAt = when;
	}

	public void cancel(String by, Instant when) {
		this.status = BookingStatus.CANCELLED;
		this.cancelledBy = by;
		this.cancelledAt = when;
	}

	/** Whether this booking uses a seat on the stretch between stop {@code leg} and the next stop. */
	public boolean covers(int leg) {
		return leg >= fromPosition && leg < toPosition;
	}

	public UUID getId() { return id; }
	public UUID getRideId() { return rideId; }
	public UUID getPassengerId() { return passengerId; }
	public int getFromPosition() { return fromPosition; }
	public int getToPosition() { return toPosition; }
	public int getSeats() { return seats; }
	public int getPricePerSeat() { return pricePerSeat; }
	public BookingStatus getStatus() { return status; }
	public String getMessage() { return message; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getDecidedAt() { return decidedAt; }
	public Instant getCancelledAt() { return cancelledAt; }
	public String getCancelledBy() { return cancelledBy; }
}
