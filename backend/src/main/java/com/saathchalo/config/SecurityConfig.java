package com.saathchalo.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, StaffSessionPort staffSessions,
			AccountStatusPort accounts, ObjectMapper json) throws Exception {
		http
			// Stateless API: the access token travels in the Authorization header, and the
			// refresh-token cookie is SameSite=Strict, so classic CSRF protection is not needed.
			.csrf(AbstractHttpConfigurer::disable)
			.cors(Customizer.withDefaults())
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.headers(h -> h
				.referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
				.addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=(self)"))
				// API responses are data, never pages: forbid everything a browser might do with them.
				.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
						PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
						new StaticHeadersWriter("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/admin/**").hasAuthority(SaathChaloJwtConverter.STAFF_SESSION)
				.requestMatchers("/api/auth/staff/**").authenticated()
				.requestMatchers("/api/users/me", "/api/users/me/**").authenticated()
				.requestMatchers(HttpMethod.POST, "/api/auth/otp/request", "/api/auth/otp/verify",
						"/api/auth/refresh", "/api/auth/logout").permitAll()
				// The payment gateway's notifications: no login, but every event must carry a valid signature.
				.requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/users/*", "/api/files/**", "/api/features").permitAll()
				// Visitors can look for rides before logging in: place names, search results and ride pages.
				// Booking, offering and everything personal still needs a login.
				.requestMatchers(HttpMethod.GET, "/api/places", "/api/places/**", "/api/rides/search", "/api/rides/*",
						"/api/users/*/reviews", "/api/shared-trips/*", "/api/legal/contacts",
						"/api/rentals/search", "/api/rentals/listings/*", "/api/rentals/listings/*/quote")
				.permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(new SaathChaloJwtConverter(staffSessions))))
			.addFilterAfter(new AccountStatusFilter(accounts, json), BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	/** Only needed when the web app is served from a different origin than the API. */
	@Bean
	CorsConfigurationSource corsConfigurationSource(AppProperties props) {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		List<String> origins = props.cors() == null ? List.of() : props.cors().origins();
		if (!origins.isEmpty()) {
			CorsConfiguration config = new CorsConfiguration();
			config.setAllowedOrigins(origins);
			config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
			config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Confirm-Code", "X-Request-Id"));
			config.setAllowCredentials(true);
			source.registerCorsConfiguration("/api/**", config);
		}
		return source;
	}
}
