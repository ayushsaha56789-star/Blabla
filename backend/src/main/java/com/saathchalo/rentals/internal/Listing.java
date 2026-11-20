package com.saathchalo.rentals.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A car offered for rent. Prices in rupees. */
@Entity
@Table(name = "rental_listings")
public class Listing {

	public static final String DRAFT = "DRAFT";

	public static final String IN_REVIEW = "IN_REVIEW";

	public static final String LIVE = "LIVE";

	public static final String PAUSED = "PAUSED";

	public static final String REJECTED = "REJECTED";

	public static final String REMOVED = "REMOVED";

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID ownerId;

	@Column(nullable = false, updatable = false)
	private UUID vehicleId;

	private boolean selfDrive;

	private boolean withDriver;

	private int dayPrice;

	private Integer hourPrice;

	private int deposit;

	private int kmPerDay;

	private int extraKmPrice;

	@Column(nullable = false)
	private String fuelPolicy;

	private int minAge;

	private long placeId;

	@Column(nullable = false)
	private String placeName;

	private String state;

	private double lat;

	private double lng;

	@Column(nullable = false)
	private String area;

	private String pickupAddress;

	private String description;

	private String rules;

	private boolean instant;

	@Column(nullable = false)
	private String status = DRAFT;

	private boolean verified;

	private String reviewNote;

	private UUID reviewedBy;

	private Instant reviewedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected Listing() {
	}

	public Listing(UUID ownerId, UUID vehicleId, Instant now) {
		this.ownerId = ownerId;
		this.vehicleId = vehicleId;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** Everything the owner can change. */
	public void update(boolean selfDrive, boolean withDriver, int dayPrice, Integer hourPrice, int deposit, int kmPerDay,
			int extraKmPrice, String fuelPolicy, int minAge, long placeId, String placeName, String state, double lat,
			double lng, String area, String pickupAddress, String description, String rules, boolean instant, Instant now) {
		this.selfDrive = selfDrive;
		this.withDriver = withDriver;
		this.dayPrice = dayPrice;
		this.hourPrice = hourPrice;
		this.deposit = deposit;
		this.kmPerDay = kmPerDay;
		this.extraKmPrice = extraKmPrice;
		this.fuelPolicy = fuelPolicy;
		this.minAge = minAge;
		this.placeId = placeId;
		this.placeName = placeName;
		this.state = state;
		this.lat = lat;
		this.lng = lng;
		this.area = area;
		this.pickupAddress = pickupAddress;
		this.description = description;
		this.rules = rules;
		this.instant = instant;
		if (REJECTED.equals(status)) {
			status = DRAFT;
		}
		this.updatedAt = now;
	}

	/** Sent for checking; with {@code liveAtOnce} (the owner allows unchecked listings) it's bookable meanwhile. */
	public void submit(boolean liveAtOnce, Instant now) {
		this.status = liveAtOnce ? LIVE : IN_REVIEW;
		this.reviewNote = null;
		this.updatedAt = now;
	}

	public void approve(UUID staffId, Instant now) {
		this.status = LIVE;
		this.verified = true;
		this.reviewNote = null;
		this.reviewedBy = staffId;
		this.reviewedAt = now;
		this.updatedAt = now;
	}

	public void reject(UUID staffId, String note, Instant now) {
		this.status = REJECTED;
		this.verified = false;
		this.reviewNote = note;
		this.reviewedBy = staffId;
		this.reviewedAt = now;
		this.updatedAt = now;
	}

	public void setStatus(String status, Instant now) {
		this.status = status;
		this.updatedAt = now;
	}

	/** Changing documents means checking them again. */
	public void documentsChanged(Instant now) {
		if (verified) {
			verified = false;
			if (LIVE.equals(status)) {
				status = IN_REVIEW;
			}
		}
		this.updatedAt = now;
	}

	public UUID getId() { return id; }
	public UUID getOwnerId() { return ownerId; }
	public UUID getVehicleId() { return vehicleId; }
	public boolean isSelfDrive() { return selfDrive; }
	public boolean isWithDriver() { return withDriver; }
	public int getDayPrice() { return dayPrice; }
	public Integer getHourPrice() { return hourPrice; }
	public int getDeposit() { return deposit; }
	public int getKmPerDay() { return kmPerDay; }
	public int getExtraKmPrice() { return extraKmPrice; }
	public String getFuelPolicy() { return fuelPolicy; }
	public int getMinAge() { return minAge; }
	public long getPlaceId() { return placeId; }
	public String getPlaceName() { return placeName; }
	public String getState() { return state; }
	public double getLat() { return lat; }
	public double getLng() { return lng; }
	public String getArea() { return area; }
	public String getPickupAddress() { return pickupAddress; }
	public String getDescription() { return description; }
	public String getRules() { return rules; }
	public boolean isInstant() { return instant; }
	public String getStatus() { return status; }
	public boolean isVerified() { return verified; }
	public String getReviewNote() { return reviewNote; }
	public UUID getReviewedBy() { return reviewedBy; }
	public Instant getReviewedAt() { return reviewedAt; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
