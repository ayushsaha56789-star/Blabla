package com.saathchalo.rides.internal;

import com.saathchalo.rides.RideStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rides")
public class Ride {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID driverId;

	@Column(nullable = false, updatable = false)
	private UUID vehicleId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RideStatus status = RideStatus.SCHEDULED;

	@Column(nullable = false)
	private Instant departureAt;

	private int seatsTotal;

	private int pricePerSeat;

	private int distanceKm;

	private int durationMin;

	private boolean ladiesOnly;

	private boolean instantBooking;

	private String notes;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant cancelledAt;

	private String cancelledBy;

	private String cancelReason;

	private Instant completedAt;

	@ElementCollection
	@CollectionTable(name = "ride_stops", joinColumns = @JoinColumn(name = "ride_id"))
	@OrderColumn(name = "position")
	private List<RideStop> stops = new ArrayList<>();

	protected Ride() {
	}

	public Ride(UUID driverId, UUID vehicleId, Instant departureAt, int seatsTotal, int pricePerSeat, int distanceKm,
			int durationMin, boolean ladiesOnly, boolean instantBooking, String notes, List<RideStop> stops,
			Instant createdAt) {
		this.driverId = driverId;
		this.vehicleId = vehicleId;
		this.departureAt = departureAt;
		this.seatsTotal = seatsTotal;
		this.pricePerSeat = pricePerSeat;
		this.distanceKm = distanceKm;
		this.durationMin = durationMin;
		this.ladiesOnly = ladiesOnly;
		this.instantBooking = instantBooking;
		this.notes = notes;
		this.stops = new ArrayList<>(stops);
		this.createdAt = createdAt;
	}

	public void cancel(String by, String reason, Instant when) {
		this.status = RideStatus.CANCELLED;
		this.cancelledBy = by;
		this.cancelReason = reason;
		this.cancelledAt = when;
	}

	/** When the car is expected at a stop. */
	public Instant timeAt(int position) {
		return departureAt.plus(Duration.ofMinutes(stops.get(position).getMinutesFromStart()));
	}

	public Instant arrivalAt() {
		return departureAt.plus(Duration.ofMinutes(durationMin));
	}

	public int lastPosition() {
		return stops.size() - 1;
	}

	public UUID getId() { return id; }
	public UUID getDriverId() { return driverId; }
	public UUID getVehicleId() { return vehicleId; }
	public RideStatus getStatus() { return status; }
	public Instant getDepartureAt() { return departureAt; }
	public int getSeatsTotal() { return seatsTotal; }
	public int getPricePerSeat() { return pricePerSeat; }
	public int getDistanceKm() { return distanceKm; }
	public int getDurationMin() { return durationMin; }
	public boolean isLadiesOnly() { return ladiesOnly; }
	public boolean isInstantBooking() { return instantBooking; }
	public String getNotes() { return notes; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getCancelledAt() { return cancelledAt; }
	public String getCancelledBy() { return cancelledBy; }
	public String getCancelReason() { return cancelReason; }
	public Instant getCompletedAt() { return completedAt; }
	public List<RideStop> getStops() { return stops; }
}
