package com.saathchalo.config;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.common.ProblemResponses;
import com.saathchalo.common.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * A generous ceiling on requests per minute, against scraping and scripts: visitors (not logged in) are counted by
 * their address, members by account and only for changes (reading, including polling, is never limited for them).
 * Individual features keep their own, tighter limits (login codes, bookings, ID checks, …).
 */
class RequestLimitFilter extends OncePerRequestFilter {

	private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

	private final RateLimiter limiter;

	private final ObjectMapper json;

	private final int visitorRequestsPerMinute;

	private final int memberChangesPerMinute;

	RequestLimitFilter(RateLimiter limiter, ObjectMapper json, int visitorRequestsPerMinute, int memberChangesPerMinute) {
		this.limiter = limiter;
		this.json = json;
		this.visitorRequestsPerMinute = visitorRequestsPerMinute;
		this.memberChangesPerMinute = memberChangesPerMinute;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		// The payment gateway's notifications are signed and must never be turned away.
		return !path.startsWith("/api/") || path.equals("/api/payments/webhook");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Optional<UUID> member = CurrentUser.id();
		boolean allowed;
		if (member.isPresent()) {
			allowed = READS.contains(request.getMethod())
					|| limiter.tryAcquire("changes:" + member.get(), memberChangesPerMinute, Duration.ofMinutes(1));
		}
		else {
			allowed = limiter.tryAcquire("visitor:" + request.getRemoteAddr(), visitorRequestsPerMinute, Duration.ofMinutes(1));
		}
		if (!allowed) {
			response.setHeader("Retry-After", "60");
			ProblemResponses.write(response, json, HttpStatus.TOO_MANY_REQUESTS, "slow_down",
					"That's a lot of requests in a short time. Please wait a minute and try again.", Map.of());
			return;
		}
		chain.doFilter(request, response);
	}
}
