package com.saathchalo.features.internal;

import com.saathchalo.features.Audience;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

	@Id
	private String key;

	private boolean enabled;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Audience audience = Audience.EVERYONE;

	/** Comma-separated city names, used when the audience is CITIES. */
	@Column(nullable = false)
	private String cities = "";

	private UUID updatedBy;

	@Column(nullable = false)
	private Instant updatedAt;

	protected FeatureFlag() {
	}

	public FeatureFlag(String key, boolean enabled, Instant now) {
		this.key = key;
		this.enabled = enabled;
		this.updatedAt = now;
	}

	public void update(boolean enabled, Audience audience, String cities, UUID by, Instant now) {
		this.enabled = enabled;
		this.audience = audience;
		this.cities = cities;
		this.updatedBy = by;
		this.updatedAt = now;
	}

	public String getKey() { return key; }
	public boolean isEnabled() { return enabled; }
	public Audience getAudience() { return audience; }
	public String getCities() { return cities; }
	public UUID getUpdatedBy() { return updatedBy; }
	public Instant getUpdatedAt() { return updatedAt; }
}
