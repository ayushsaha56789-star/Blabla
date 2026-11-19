package com.saathchalo.vehicles.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID ownerId;

	@Column(nullable = false)
	private String make;

	@Column(nullable = false)
	private String model;

	private String color;

	private int seats;

	/** Encrypted registration number (AES-GCM, see FieldEncryptor). */
	@Column(nullable = false)
	private String plateEnc;

	@Column(nullable = false)
	private String plateHint;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant deletedAt;

	protected Vehicle() {
	}

	public Vehicle(UUID ownerId, Instant createdAt) {
		this.ownerId = ownerId;
		this.createdAt = createdAt;
	}

	public void update(String make, String model, String color, int seats, String plateEnc, String plateHint) {
		this.make = make;
		this.model = model;
		this.color = color;
		this.seats = seats;
		this.plateEnc = plateEnc;
		this.plateHint = plateHint;
	}

	public void delete(Instant when) {
		this.deletedAt = when;
	}

	public boolean isActive() {
		return deletedAt == null;
	}

	public UUID getId() { return id; }
	public UUID getOwnerId() { return ownerId; }
	public String getMake() { return make; }
	public String getModel() { return model; }
	public String getColor() { return color; }
	public int getSeats() { return seats; }
	public String getPlateEnc() { return plateEnc; }
	public String getPlateHint() { return plateHint; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getDeletedAt() { return deletedAt; }
}
