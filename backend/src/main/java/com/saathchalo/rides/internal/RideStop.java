package com.saathchalo.rides.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A place on a ride: the origin, a stop on the way, or the destination. Its position is the list index. */
@Embeddable
public class RideStop {

	@Column(nullable = false)
	private long placeId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String state;

	private String meetingPoint;

	private double lat;

	private double lng;

	private int kmFromStart;

	private int minutesFromStart;

	protected RideStop() {
	}

	public RideStop(long placeId, String name, String state, String meetingPoint, double lat, double lng, int kmFromStart,
			int minutesFromStart) {
		this.placeId = placeId;
		this.name = name;
		this.state = state;
		this.meetingPoint = meetingPoint;
		this.lat = lat;
		this.lng = lng;
		this.kmFromStart = kmFromStart;
		this.minutesFromStart = minutesFromStart;
	}

	public long getPlaceId() { return placeId; }
	public String getName() { return name; }
	public String getState() { return state; }
	public String getMeetingPoint() { return meetingPoint; }
	public double getLat() { return lat; }
	public double getLng() { return lng; }
	public int getKmFromStart() { return kmFromStart; }
	public int getMinutesFromStart() { return minutesFromStart; }
}
