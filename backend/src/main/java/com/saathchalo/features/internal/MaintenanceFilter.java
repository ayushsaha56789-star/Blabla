package com.saathchalo.features.internal;

import com.saathchalo.common.ProblemResponses;
import com.saathchalo.features.FeatureService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * While maintenance mode is on, member APIs answer 503. Login, feature switches, files and the staff console keep
 * working, so staff can still get in and switch it off.
 */
@Component
class MaintenanceFilter extends OncePerRequestFilter {

	private final FeatureService features;

	private final ObjectMapper json;

	MaintenanceFilter(FeatureService features, ObjectMapper json) {
		this.features = features;
		this.json = json;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !path.startsWith("/api/") || path.startsWith("/api/auth/") || path.startsWith("/api/admin/")
				|| path.startsWith("/api/files/") || path.equals("/api/features");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (features.isMaintenance()) {
			ProblemResponses.write(response, json, HttpStatus.SERVICE_UNAVAILABLE, "maintenance",
					"SaathChalo is having a short break for maintenance. Please try again soon.", null);
			return;
		}
		chain.doFilter(request, response);
	}
}
