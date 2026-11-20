package com.saathchalo.user;

import java.util.UUID;

/** Published when a member asks to delete their account, so every session they have open can be ended. */
public record MemberLeaving(UUID userId) {
}
