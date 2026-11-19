package com.saathchalo.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The staff member must send a fresh code from their authenticator app in the {@code X-Confirm-Code} header.
 * Without it the API answers 403 {@code confirmation_required}; with a wrong code, 403 {@code confirmation_invalid}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresConfirmation {
}
