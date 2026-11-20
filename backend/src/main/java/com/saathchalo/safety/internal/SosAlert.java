package com.saathchalo.safety.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A member pressed SOS. Support sees it in the console until someone resolves it. */
@Entity
@Table(name = "sos_alerts")
public class SosAlert {

	public static final String OPEN = "OPEN";

	public static final String ACKNOWLEDGED = "ACKNOWLEDGED";

	public static final String RESOLVED = "RESOLVED";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID userId;

	private UUID rideId;

	private Double lat;

	private Double lng;

	private Integer accuracy;

	@Column(nullable = false)
	private String status = OPEN;

	private int contactsNotified;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant acknowledgedAt;

	private UUID acknowledgedBy;

	private Instant resolvedAt;

	private UUID resolvedBy;

	private String notes;

	protected SosAlert() {
	}

	public SosAlert(UUID userId, UUID rideId, Double lat, Double lng, Integer accuracy, Instant createdAt) {
		this.userId = userId;
		this.rideId = rideId;
		this.lat = lat;
		this.lng = lng;
		this.accuracy = accuracy;
		this.createdAt = createdAt;
	}

	public void contactsNotified(int count) {
		this.contactsNotified = count;
	}

	public void updateLocation(double lat, double lng, Integer accuracy) {
		this.lat = lat;
		this.lng = lng;
		this.accuracy = accuracy;
	}

	public void acknowledge(UUID staffId, Instant when) {
		if (OPEN.equals(status)) {
			status = ACKNOWLEDGED;
			acknowledgedBy = staffId;
			acknowledgedAt = when;
		}
	}

	public void resolve(UUID staffId, String notes, Instant when) {
		if (acknowledgedAt == null) {
			acknowledge(staffId, when);
		}
		status = RESOLVED;
		resolvedBy = staffId;
		resolvedAt = when;
		this.notes = notes;
	}

	public UUID getId() { return id; }
	public UUID getUserId() { return userId; }
	public UUID getRideId() { return rideId; }
	public Double getLat() { return lat; }
	public Double getLng() { return lng; }
	public Integer getAccuracy() { return accuracy; }
	public String getStatus() { return status; }
	public int getContactsNotified() { return contactsNotified; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getAcknowledgedAt() { return acknowledgedAt; }
	public UUID getAcknowledgedBy() { return acknowledgedBy; }
	public Instant getResolvedAt() { return resolvedAt; }
	public UUID getResolvedBy() { return resolvedBy; }
	public String getNotes() { return notes; }
}
