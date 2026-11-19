package com.saathchalo.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * An error whose message is safe and friendly enough to show to the user as-is.
 * {@code code} is a stable machine-readable identifier the web app can switch on.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private final Map<String, Object> properties = new LinkedHashMap<>();

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

	/** Extra fields added to the problem response, e.g. when a suspension ends. */
	public Map<String, Object> properties() {
		return properties;
	}

	public ApiException with(String name, Object value) {
		if (value != null) {
			properties.put(name, value);
		}
		return this;
	}

	public static ApiException badRequest(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

	public static ApiException unauthorized(String code, String message) {
		return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
	}

	public static ApiException forbidden(String code, String message) {
		return new ApiException(HttpStatus.FORBIDDEN, code, message);
	}

	public static ApiException notFound(String code, String message) {
		return new ApiException(HttpStatus.NOT_FOUND, code, message);
	}

	public static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	public static ApiException tooManyRequests(String code, String message) {
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
	}
}
