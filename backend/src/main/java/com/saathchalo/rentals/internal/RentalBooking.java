package com.saathchalo.rentals.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Renting a car for a period. Money in rupees. The database stops two active bookings of one car overlapping (an
 * exclusion constraint on the period, which is computed from {@code startsAt} and {@code endsAt}).
 */
@Entity
@Table(name = "rental_bookings")
public class RentalBooking {

	public static final String REQUESTED = "REQUESTED";

	public static final String ACCEPTED = "ACCEPTED";

	public static final String PAID = "PAID";

	public static final String PICKED_UP = "PICKED_UP";

	public static final String RETURNED = "RETURNED";

	public static final String DISPUTED = "DISPUTED";

	public static final String COMPLETED = "COMPLETED";

	public static final String DECLINED = "DECLINED";

	public static final String EXPIRED = "EXPIRED";

	public static final String CANCELLED = "CANCELLED";

	/** Bookings that hold the car (the same list as the database's exclusion constraint). */
	public static final Set<String> HOLDING = Set.of(ACCEPTED, PAID, PICKED_UP, RETURNED, DISPUTED);

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID listingId;

	@Column(nullable = false, updatable = false)
	private UUID renterId;

	@Column(nullable = false, updatable = false)
	private UUID ownerId;

	@Column(nullable = false, updatable = false)
	private String mode;

	@Column(nullable = false, updatable = false)
	private Instant startsAt;

	@Column(nullable = false, updatable = false)
	private Instant endsAt;

	private int rent;

	private int deposit;

	private int kmIncluded;

	private int extraKmPrice;

	private boolean payOnline;

	@Column(nullable = false)
	private String status;

	private String message;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant decidedAt;

	private Instant payBy;

	private Instant paidAt;

	private Instant pickedUpAt;

	private Instant returnedAt;

	private Instant completedAt;

	private Instant cancelledAt;

	private String cancelledBy;

	private int extraCharges;

	private String extraNote;

	private String disputeNote;

	private String resolution;

	protected RentalBooking() {
	}

	public RentalBooking(UUID listingId, UUID renterId, UUID ownerId, String mode, Instant startsAt, Instant endsAt, int rent,
			int deposit, int kmIncluded, int extraKmPrice, boolean payOnline, String message, Instant now) {
		this.listingId = listingId;
		this.renterId = renterId;
		this.ownerId = ownerId;
		this.mode = mode;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.rent = rent;
		this.deposit = deposit;
		this.kmIncluded = kmIncluded;
		this.extraKmPrice = extraKmPrice;
		this.payOnline = payOnline;
		this.message = message;
		this.createdAt = now;
		this.status = REQUESTED;
	}

	public void accept(Instant now, Instant payBy) {
		this.status = ACCEPTED;
		this.decidedAt = now;
		this.payBy = payOnline ? payBy : null;
	}

	public void decline(Instant now) {
		this.status = DECLINED;
		this.decidedAt = now;
	}

	public void expire(Instant now) {
		this.status = EXPIRED;
		this.cancelledAt = now;
		this.cancelledBy = "SYSTEM";
	}

	public void cancel(String by, Instant now) {
		this.status = CANCELLED;
		this.cancelledBy = by;
		this.cancelledAt = now;
	}

	public void paid(Instant now) {
		this.status = PAID;
		this.paidAt = now;
	}

	public void pickedUp(Instant now) {
		this.status = PICKED_UP;
		this.pickedUpAt = now;
	}

	public void returned(int extraCharges, String extraNote, Instant now) {
		this.status = RETURNED;
		this.returnedAt = now;
		this.extraCharges = extraCharges;
		this.extraNote = extraNote;
	}

	public void dispute(String note) {
		this.status = DISPUTED;
		this.disputeNote = note;
	}

	public void complete(Instant now) {
		this.status = COMPLETED;
		this.completedAt = now;
	}

	/** Staff settle a dispute: the final charges, and why. */
	public void resolve(int extraCharges, String resolution, Instant now) {
		this.extraCharges = extraCharges;
		this.resolution = resolution;
		complete(now);
	}

	public boolean isHolding() {
		return HOLDING.contains(status);
	}

	public UUID getId() { return id; }
	public UUID getListingId() { return listingId; }
	public UUID getRenterId() { return renterId; }
	public UUID getOwnerId() { return ownerId; }
	public String getMode() { return mode; }
	public Instant getStartsAt() { return startsAt; }
	public Instant getEndsAt() { return endsAt; }
	public int getRent() { return rent; }
	public int getDeposit() { return deposit; }
	public int getKmIncluded() { return kmIncluded; }
	public int getExtraKmPrice() { return extraKmPrice; }
	public boolean isPayOnline() { return payOnline; }
	public String getStatus() { return status; }
	public String getMessage() { return message; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getDecidedAt() { return decidedAt; }
	public Instant getPayBy() { return payBy; }
	public Instant getPaidAt() { return paidAt; }
	public Instant getPickedUpAt() { return pickedUpAt; }
	public Instant getReturnedAt() { return returnedAt; }
	public Instant getCompletedAt() { return completedAt; }
	public Instant getCancelledAt() { return cancelledAt; }
	public String getCancelledBy() { return cancelledBy; }
	public int getExtraCharges() { return extraCharges; }
	public String getExtraNote() { return extraNote; }
	public String getDisputeNote() { return disputeNote; }
	public String getResolution() { return resolution; }
}
