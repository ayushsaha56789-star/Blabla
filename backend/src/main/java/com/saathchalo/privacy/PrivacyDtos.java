package com.saathchalo.privacy;

import java.time.Instant;

public final class PrivacyDtos {

	private PrivacyDtos() {
	}

	/** Why a member is leaving. A fixed list, so no free text (and no personal data) ends up in the records. */
	public enum LeaveReason {
		NOT_USING, NOT_ENOUGH_RIDES, FOUND_ANOTHER_WAY, SAFETY, PRIVACY, OTHER
	}

	/** {@code reason} is optional. */
	public record DeleteAccountRequest(LeaveReason reason) {
	}

	public record DeletionScheduled(Instant deleteOn) {
	}

	/** Blank values mean "not appointed yet". */
	public record LegalContacts(String company, String grievanceOfficer, String grievanceEmail, String address) {
	}
}
