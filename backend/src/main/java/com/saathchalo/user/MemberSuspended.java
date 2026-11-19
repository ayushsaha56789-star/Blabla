package com.saathchalo.user;

import java.util.UUID;

/** Published when a member's whole account is suspended, so their sessions can be ended. */
public record MemberSuspended(UUID userId) {
}
