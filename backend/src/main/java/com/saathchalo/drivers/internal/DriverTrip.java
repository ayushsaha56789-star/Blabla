package com.saathchalo.drivers.internal;

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
 * A member hires a driver for their own car. Money in rupees. The database stops two active trips of one driver
 * overlapping (an exclusion constraint on the period computed from {@code startsAt} and {@code endsAt}).
 */
@Entity
@Table(name = "driver_trips")
public class DriverTrip {

	public static final String REQUESTED = "REQUESTED";

	public static final String ACCEPTED = "ACCEPTED";

	public static final String PAID = "PAID";

	public static final String ON_THE_WAY = "ON_THE_WAY";

	public static final String STARTED = "STARTED";

	public static final String COMPLETED = "COMPLETED";

	public static final String DECLINED = "DECLINED";

	public static final String EXPIRED = "EXPIRED";

	public static final String CANCELLED = "CANCELLED";

	/** Trips that hold the driver's time (the same list as the database's exclusion constraint). */
	public static final Set<String> HOLDING = Set.of(ACCEPTED, PAID, ON_THE_WAY, STARTED);

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID driverId;

	@Column(nullable = false, updatable = false)
	private UUID customerId;

	@Column(nullable = false, updatable = false)
	private String kind;

	@Column(nullable = false, updatable = false)
	private Instant startsAt;

	@Column(nullable = false, updatable = false)
	private Instant endsAt;

	private long placeId;

	@Column(nullable = false)
	private String placeName;

	private double lat;

	private double lng;

	@Column(nullable = false)
	private String pickupAddress;

	private Long destinationId;

	private String destinationName;

	@Column(nullable = false)
	private String car;

	private boolean automatic;

	private int fare;

	private int extraPrice;

	private boolean payOnline;

	@Column(nullable = false)
	private String status;

	@Column(nullable = false)
	private String startCode;

	private int wrongCodes;

	private String message;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant answerBy;

	private Instant decidedAt;

	private Instant payBy;

	private Instant paidAt;

	private Instant onTheWayAt;

	private Instant startedAt;

	private Instant endedAt;

	private Instant cancelledAt;

	private String cancelledBy;

	private String cancelReason;

	private int extraUnits;

	protected DriverTrip() {
	}

	public DriverTrip(UUID driverId, UUID customerId, String kind, Instant startsAt, Instant endsAt, long placeId, String placeName,
			double lat, double lng, String pickupAddress, Long destinationId, String destinationName, String car, boolean automatic,
			int fare, int extraPrice, boolean payOnline, String startCode, String message, Instant now, Instant answerBy) {
		this.driverId = driverId;
		this.customerId = customerId;
		this.kind = kind;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.placeId = placeId;
		this.placeName = placeName;
		this.lat = lat;
		this.lng = lng;
		this.pickupAddress = pickupAddress;
		this.destinationId = destinationId;
		this.destinationName = destinationName;
		this.car = car;
		this.automatic = automatic;
		this.fare = fare;
		this.extraPrice = extraPrice;
		this.payOnline = payOnline;
		this.startCode = startCode;
		this.message = message;
		this.status = REQUESTED;
		this.createdAt = now;
		this.answerBy = answerBy;
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

	public void paid(Instant now) {
		this.status = PAID;
		this.paidAt = now;
	}

	public void onTheWay(Instant now) {
		this.status = ON_THE_WAY;
		this.onTheWayAt = now;
	}

	public void wrongCode() {
		this.wrongCodes++;
	}

	public void start(Instant now) {
		this.status = STARTED;
		this.startedAt = now;
	}

	public void end(Instant now, int extraUnits) {
		this.status = COMPLETED;
		this.endedAt = now;
		this.extraUnits = extraUnits;
	}

	public void cancel(String by, String reason, Instant now) {
		this.status = CANCELLED;
		this.cancelledBy = by;
		this.cancelReason = reason;
		this.cancelledAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getDriverId() {
		return driverId;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public String getKind() {
		return kind;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public long getPlaceId() {
		return placeId;
	}

	public String getPlaceName() {
		return placeName;
	}

	public String getPickupAddress() {
		return pickupAddress;
	}

	public String getDestinationName() {
		return destinationName;
	}

	public String getCar() {
		return car;
	}

	public boolean isAutomatic() {
		return automatic;
	}

	public int getFare() {
		return fare;
	}

	public int getExtraPrice() {
		return extraPrice;
	}

	public boolean isPayOnline() {
		return payOnline;
	}

	public String getStatus() {
		return status;
	}

	public String getStartCode() {
		return startCode;
	}

	public int getWrongCodes() {
		return wrongCodes;
	}

	public String getMessage() {
		return message;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getAnswerBy() {
		return answerBy;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}

	public Instant getPayBy() {
		return payBy;
	}

	public Instant getOnTheWayAt() {
		return onTheWayAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public String getCancelledBy() {
		return cancelledBy;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public int getExtraUnits() {
		return extraUnits;
	}
}
