package com.saathchalo.common;

import org.springframework.http.HttpStatus;

/**
 * An error whose message is safe and friendly enough to show to the user as-is.
 * {@code code} is a stable machine-readable identifier the web app can switch on.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public static ApiException badRequest(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

	public static ApiException unauthorized(String code, String message) {
		return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
	}

	public static ApiException notFound(String code, String message) {
		return new ApiException(HttpStatus.NOT_FOUND, code, message);
	}

	public static ApiException tooManyRequests(String code, String message) {
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
	}
}
