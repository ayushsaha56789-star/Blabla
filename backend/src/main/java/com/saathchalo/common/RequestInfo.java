package com.saathchalo.common;

import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Details of the HTTP request being handled on this thread, if any. */
public final class RequestInfo {

	private RequestInfo() {
	}

	public static String requestId() {
		return MDC.get(RequestIdFilter.MDC_KEY);
	}

	/** The client's IP address (behind a proxy, set server.forward-headers-strategy so this is the real one). */
	public static String clientIp() {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
			return attributes.getRequest().getRemoteAddr();
		}
		return null;
	}
}
