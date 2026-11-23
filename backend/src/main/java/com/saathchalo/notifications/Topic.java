package com.saathchalo.notifications;

/** What a notice is about. Members can mute reminders and rewards; notices about trips and their account always arrive. */
public enum Topic {

	/** Bookings, rides, rentals and hired drivers: something the member usually has to act on. */
	TRIPS("Bookings and trips", false),

	/** Nudges such as "rate your trip". */
	REMINDERS("Reminders to rate a trip", true),

	/** Help replies, ID checks, payments and promotion decisions. */
	ACCOUNT("Help, checks and payments", false),

	/** Invite rewards and other good news. */
	REWARDS("Rewards and invites", true);

	private final String label;

	private final boolean mutable;

	Topic(String label, boolean mutable) {
		this.label = label;
		this.mutable = mutable;
	}

	public String label() {
		return label;
	}

	public boolean mutable() {
		return mutable;
	}
}
