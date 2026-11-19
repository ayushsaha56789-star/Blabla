package com.saathchalo.admin.internal;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.CurrentUser;
import com.saathchalo.common.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Generous per-staff limit on console requests, to slow down a stolen session or a runaway script. */
@Component
class AdminRateLimitInterceptor implements HandlerInterceptor {

	private static final int PER_MINUTE = 300;

	private final RateLimiter limiter;

	AdminRateLimitInterceptor(RateLimiter limiter) {
		this.limiter = limiter;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String key = "admin:" + CurrentUser.id().map(Object::toString).orElse(String.valueOf(request.getRemoteAddr()));
		if (!limiter.tryAcquire(key, PER_MINUTE, Duration.ofMinutes(1))) {
			throw ApiException.tooManyRequests("slow_down", "Too many requests. Please wait a minute.");
		}
		return true;
	}
}
