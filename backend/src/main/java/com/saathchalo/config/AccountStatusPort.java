package com.saathchalo.config;

import java.util.UUID;

/** Implemented by the user module; lets the security layer block suspended accounts on every request. */
public interface AccountStatusPort {

	AccountStatus statusOf(UUID userId);
}
