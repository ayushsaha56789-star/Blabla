package com.saathchalo.access;

import java.util.UUID;

/** Published when someone stops being staff, so their console sessions and 2FA setup are removed. */
public record StaffAccessRevoked(UUID userId) {
}
