package com.saathchalo.features;

import java.util.UUID;

/** Implemented by the user module, which knows members' gender, city, verification and restrictions. */
public interface AudienceProfileProvider {

	AudienceProfile profileOf(UUID userId);
}
