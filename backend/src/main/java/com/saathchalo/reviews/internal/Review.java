package com.saathchalo.reviews.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One member's review of another after a trip they shared. */
@Entity
@Table(name = "reviews")
public class Review {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false)
	private UUID rideId;

	@Column(nullable = false, updatable = false)
	private UUID authorId;

	@Column(nullable = false, updatable = false)
	private UUID subjectId;

	/** DRIVER or PASSENGER: what the reviewed member was on this trip. */
	@Column(nullable = false, updatable = false)
	private String subjectRole;

	private int rating;

	private String comment;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant hiddenAt;

	protected Review() {
	}

	public Review(UUID rideId, UUID authorId, UUID subjectId, String subjectRole, int rating, String comment,
			Instant createdAt) {
		this.rideId = rideId;
		this.authorId = authorId;
		this.subjectId = subjectId;
		this.subjectRole = subjectRole;
		this.rating = rating;
		this.comment = comment;
		this.createdAt = createdAt;
	}

	public void hide(Instant when) {
		this.hiddenAt = when;
	}

	public UUID getId() { return id; }
	public UUID getRideId() { return rideId; }
	public UUID getAuthorId() { return authorId; }
	public UUID getSubjectId() { return subjectId; }
	public String getSubjectRole() { return subjectRole; }
	public int getRating() { return rating; }
	public String getComment() { return comment; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getHiddenAt() { return hiddenAt; }
}
