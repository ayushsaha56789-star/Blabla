package com.saathchalo.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A member as listed in the staff console. The phone number is masked. */
public record MemberSummary(UUID id, String displayName, String maskedPhone, String avatarUrl, Gender gender, String city,
		Instant memberSince, boolean betaTester, boolean suspended, List<String> restrictedAreas) {
}
