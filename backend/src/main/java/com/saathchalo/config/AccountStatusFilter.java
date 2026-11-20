package com.saathchalo.config;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.common.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** Stops suspended members from using the API, with a message saying why and until when. */
class AccountStatusFilter extends OncePerRequestFilter {

	private final AccountStatusPort accounts;

	private final ObjectMapper json;

	AccountStatusFilter(AccountStatusPort accounts, ObjectMapper json) {
		this.accounts = accounts;
		this.json = json;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		// Logging out and refreshing (which checks suspension itself) stay possible. Feature switches are checked
		// on purpose: the website polls them, so an open tab learns about a suspension within a minute.
		return !path.startsWith("/api/") || path.startsWith("/api/auth/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Optional<UUID> userId = CurrentUser.id();
		if (userId.isPresent()) {
			AccountStatus status = accounts.statusOf(userId.get());
			if (status.closing()) {
				// Tokens issued before the member asked to delete their account stop working straight away.
				ProblemResponses.write(response, json, HttpStatus.UNAUTHORIZED, "session_ended",
						"You've been logged out because this account is being deleted. Log in again if you change your mind.",
						Map.of());
				return;
			}
			if (status.suspended()) {
				Map<String, Object> extra = new HashMap<>();
				extra.put("reason", status.reason());
				extra.put("until", status.until() == null ? null : status.until().toString());
				ProblemResponses.write(response, json, HttpStatus.FORBIDDEN, "account_suspended",
						"Your account is suspended.", extra);
				return;
			}
		}
		chain.doFilter(request, response);
	}
}
