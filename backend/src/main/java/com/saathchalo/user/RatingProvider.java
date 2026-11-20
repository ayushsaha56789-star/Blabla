package com.saathchalo.user;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Members' ratings from trip reviews. The reviews module provides it, so profiles and member cards can show ratings
 * without this module depending on reviews.
 */
public interface RatingProvider {

	/** Average stars (1–5) and how many reviews it is based on. */
	record Rating(double average, int count) {
	}

	/** Ratings for members who have at least one visible review; others are simply absent. */
	Map<UUID, Rating> ratings(Collection<UUID> memberIds);
}
