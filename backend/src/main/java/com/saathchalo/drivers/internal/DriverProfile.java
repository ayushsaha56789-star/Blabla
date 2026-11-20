package com.saathchalo.drivers.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A member who drives other members' cars. Money in rupees. The licence number is stored encrypted. */
@Entity
@Table(name = "driver_profiles")
public class DriverProfile {

	public static final String DRAFT = "DRAFT";

	public static final String IN_REVIEW = "IN_REVIEW";

	public static final String LIVE = "LIVE";

	public static final String REJECTED = "REJECTED";

	public static final String REMOVED = "REMOVED";

	@Id
	private UUID userId;

	@Column(nullable = false)
	private String status;

	@Column(nullable = false)
	private String licenceNumber;

	@Column(nullable = false)
	private String licenceClass;

	@Column(nullable = false)
	private LocalDate licenceValidUntil;

	private int experienceYears;

	@Column(nullable = false)
	private String languages;

	private boolean manual;

	private boolean automatic;

	private boolean outstation;

	private int hourPrice;

	private int dayPrice;

	private int outstationPrice;

	private int allowance;

	private int nightCharge;

	private String about;

	private String policeCertificate;

	private boolean policeChecked;

	private boolean takingBookings;

	private String reviewNote;

	private UUID reviewedBy;

	private Instant reviewedAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected DriverProfile() {
	}

	public DriverProfile(UUID userId, Instant now) {
		this.userId = userId;
		this.status = DRAFT;
		this.languages = "";
		this.takingBookings = true;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void licence(String encryptedNumber, String licenceClass, LocalDate validUntil, Instant now) {
		this.licenceNumber = encryptedNumber;
		this.licenceClass = licenceClass;
		this.licenceValidUntil = validUntil;
		this.updatedAt = now;
	}

	public void details(int experienceYears, String languages, boolean manual, boolean automatic, boolean outstation,
			int hourPrice, int dayPrice, int outstationPrice, int allowance, int nightCharge, String about, Instant now) {
		this.experienceYears = experienceYears;
		this.languages = languages;
		this.manual = manual;
		this.automatic = automatic;
		this.outstation = outstation;
		this.hourPrice = hourPrice;
		this.dayPrice = dayPrice;
		this.outstationPrice = outstationPrice;
		this.allowance = allowance;
		this.nightCharge = nightCharge;
		this.about = about;
		this.updatedAt = now;
	}

	/** Sent (again) for checking: after signing up, after being sent back, or when a live driver changes their licence. */
	public void submit(Instant now) {
		this.status = IN_REVIEW;
		this.reviewNote = null;
		this.updatedAt = now;
	}

	/** A removed driver who signs up again starts over. */
	public void restart(Instant now) {
		this.status = DRAFT;
		this.reviewNote = null;
		this.policeChecked = false;
		this.takingBookings = true;
		this.updatedAt = now;
	}

	public void approve(UUID staffId, boolean policeChecked, Instant now) {
		this.status = LIVE;
		this.policeChecked = policeChecked;
		this.reviewNote = null;
		this.reviewedBy = staffId;
		this.reviewedAt = now;
		this.updatedAt = now;
	}

	public void reject(UUID staffId, String note, Instant now) {
		this.status = REJECTED;
		this.reviewNote = note;
		this.reviewedBy = staffId;
		this.reviewedAt = now;
		this.updatedAt = now;
	}

	public void remove(Instant now) {
		this.status = REMOVED;
		this.takingBookings = false;
		this.updatedAt = now;
	}

	public void takingBookings(boolean on, Instant now) {
		this.takingBookings = on;
		this.updatedAt = now;
	}

	public void policeChecked(UUID staffId, Instant now) {
		this.policeChecked = true;
		this.reviewedBy = staffId;
		this.reviewedAt = now;
		this.updatedAt = now;
	}

	public void policeCertificate(String fileName, Instant now) {
		this.policeCertificate = fileName;
		this.policeChecked = false;
		this.updatedAt = now;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getStatus() {
		return status;
	}

	public String getLicenceNumber() {
		return licenceNumber;
	}

	public String getLicenceClass() {
		return licenceClass;
	}

	public LocalDate getLicenceValidUntil() {
		return licenceValidUntil;
	}

	public int getExperienceYears() {
		return experienceYears;
	}

	public String getLanguages() {
		return languages;
	}

	public boolean isManual() {
		return manual;
	}

	public boolean isAutomatic() {
		return automatic;
	}

	public boolean isOutstation() {
		return outstation;
	}

	public int getHourPrice() {
		return hourPrice;
	}

	public int getDayPrice() {
		return dayPrice;
	}

	public int getOutstationPrice() {
		return outstationPrice;
	}

	public int getAllowance() {
		return allowance;
	}

	public int getNightCharge() {
		return nightCharge;
	}

	public String getAbout() {
		return about;
	}

	public String getPoliceCertificate() {
		return policeCertificate;
	}

	public boolean isPoliceChecked() {
		return policeChecked;
	}

	public boolean isTakingBookings() {
		return takingBookings;
	}

	public String getReviewNote() {
		return reviewNote;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
