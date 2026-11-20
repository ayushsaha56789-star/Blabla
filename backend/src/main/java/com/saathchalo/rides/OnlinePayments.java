package com.saathchalo.rides;

import java.util.UUID;

/**
 * Implemented by the payments module, so the booking rules can tell whether a member can pay online right now
 * (without the rides module depending on payments).
 */
public interface OnlinePayments {

	boolean availableFor(UUID memberId);
}
