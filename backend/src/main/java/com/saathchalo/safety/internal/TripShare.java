package com.saathchalo.safety.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A secret, time-limited link to follow one member's trip. Holds only the latest location point, if any. */
@Entity
@Table(name = "trip_shares")
public class TripShare {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String tokenHash;

	@Column(nullable = false, updatable = false)
	private String tokenEnc;

	@Column(nullable = false, updatable = false)
	private UUID rideId;

	@Column(nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant revokedAt;

	private Double liveLat;

	private Double liveLng;

	private Integer liveAccuracy;

	private Instant liveAt;

	protected TripShare() {
	}

	public TripShare(String tokenHash, String tokenEnc, UUID rideId, UUID userId, Instant createdAt, Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.tokenEnc = tokenEnc;
		this.rideId = rideId;
		this.userId = userId;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public boolean isActive(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	public void revoke(Instant when) {
		revokedAt = when;
		clearLocation();
	}

	public void updateLocation(double lat, double lng, Integer accuracy, Instant when) {
		liveLat = lat;
		liveLng = lng;
		liveAccuracy = accuracy;
		liveAt = when;
	}

	public void clearLocation() {
		liveLat = null;
		liveLng = null;
		liveAccuracy = null;
		liveAt = null;
	}

	public UUID getId() { return id; }
	public String getTokenEnc() { return tokenEnc; }
	public UUID getRideId() { return rideId; }
	public UUID getUserId() { return userId; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getExpiresAt() { return expiresAt; }
	public Instant getRevokedAt() { return revokedAt; }
	public Double getLiveLat() { return liveLat; }
	public Double getLiveLng() { return liveLng; }
	public Integer getLiveAccuracy() { return liveAccuracy; }
	public Instant getLiveAt() { return liveAt; }
}
