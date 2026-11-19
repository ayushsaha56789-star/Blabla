package com.saathchalo.features;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API (method or whole controller) as part of a feature. When the switch is off for the caller, the API
 * answers 403 {@code feature_unavailable} instead of running. All listed switches must be on.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {

	String[] value();
}
