package com.saathchalo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saathchalo.common.RateLimiter;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

/** The production start-up checks and the request ceiling. */
class LaunchHardeningTest {

	@AfterEach
	void clearLogin() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void unsafeProductionSettingsAreAllListed() {
		MockEnvironment env = new MockEnvironment()
			.withProperty("app.otp.expose-code", "true")
			.withProperty("app.jwt.secret", "short")
			.withProperty("app.security.data-key", "")
			.withProperty("app.auth.refresh-cookie-secure", "false")
			.withProperty("app.payments.provider", "fake")
			.withProperty("app.public-url", "http://localhost:4200")
			.withProperty("app.sms.provider", "console")
			.withProperty("app.email.provider", "console");
		List<String> problems = ProductionChecks.problems(env);
		assertThat(problems).hasSize(8);
		assertThat(String.join(" ", problems)).contains("expose-code", "JWT_SECRET", "DATA_ENCRYPTION_KEY", "refresh-cookie-secure",
				"PAYMENTS_PROVIDER=fake", "PUBLIC_URL", "SMS_PROVIDER", "EMAIL_PROVIDER");
		assertThatThrownBy(() -> new ProductionChecks(env).afterPropertiesSet()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("can't start");
	}

	@Test
	void safeProductionSettingsStart() {
		MockEnvironment env = new MockEnvironment()
			.withProperty("app.jwt.secret", "a-long-random-secret-of-more-than-32-characters")
			.withProperty("app.security.data-key", "XWOPv4fOlpJGT4THas2g/t+l97/1g6ToVFkq1Kfd94s=")
			.withProperty("app.public-url", "https://saathchalo.in")
			.withProperty("app.payments.provider", "razorpay")
			.withProperty("app.sms.provider", "twilio")
			.withProperty("app.email.provider", "smtp");
		assertThat(ProductionChecks.problems(env)).isEmpty();
		new ProductionChecks(env).afterPropertiesSet();

		// A private test server may write texts and emails to the log, but only when asked to explicitly.
		env.withProperty("app.sms.provider", "console").withProperty("app.launch.allow-console-senders", "true");
		assertThat(ProductionChecks.problems(env)).isEmpty();
	}

	@Test
	void developmentAndTestsAreExempt() {
		MockEnvironment env = new MockEnvironment().withProperty("app.otp.expose-code", "true");
		env.setActiveProfiles("dev");
		new ProductionChecks(env).afterPropertiesSet();
		env.setActiveProfiles("test");
		new ProductionChecks(env).afterPropertiesSet();
	}

	@Test
	void visitorsAreLimitedByAddressAndMembersOnlyForChanges() throws Exception {
		Clock clock = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC);
		RequestLimitFilter filter = new RequestLimitFilter(new RateLimiter(clock), JsonMapper.builder().build(), 2, 2);
		AtomicInteger passed = new AtomicInteger();
		FilterChain chain = (req, res) -> passed.incrementAndGet();

		// Two visitor requests from one address pass; the third is turned away. Another address is unaffected.
		assertThat(run(filter, chain, "GET", "/api/rides/search", "10.0.0.1").getStatus()).isEqualTo(200);
		assertThat(run(filter, chain, "GET", "/api/places", "10.0.0.1").getStatus()).isEqualTo(200);
		MockHttpServletResponse limited = run(filter, chain, "GET", "/api/places", "10.0.0.1");
		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
		assertThat(limited.getContentAsString()).contains("\"code\":\"slow_down\"");
		assertThat(run(filter, chain, "GET", "/api/places", "10.0.0.2").getStatus()).isEqualTo(200);

		// The payment gateway's notifications and non-API paths are never limited.
		assertThat(run(filter, chain, "POST", "/api/payments/webhook", "10.0.0.1").getStatus()).isEqualTo(200);
		assertThat(run(filter, chain, "GET", "/index.html", "10.0.0.1").getStatus()).isEqualTo(200);

		// A logged-in member reads freely; changes are limited per account.
		String member = UUID.randomUUID().toString();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
				Jwt.withTokenValue("t").header("alg", "none").subject(member).build()));
		for (int i = 0; i < 5; i++) {
			assertThat(run(filter, chain, "GET", "/api/notifications", "10.0.0.1").getStatus()).isEqualTo(200);
		}
		assertThat(run(filter, chain, "POST", "/api/chats/x/messages", "10.0.0.1").getStatus()).isEqualTo(200);
		assertThat(run(filter, chain, "PUT", "/api/users/me", "10.0.0.1").getStatus()).isEqualTo(200);
		assertThat(run(filter, chain, "POST", "/api/chats/x/messages", "10.0.0.1").getStatus()).isEqualTo(429);
		assertThat(passed.get()).isEqualTo(12);
	}

	private static MockHttpServletResponse run(RequestLimitFilter filter, FilterChain chain, String method, String path, String ip)
			throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr(ip);
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, chain);
		return response;
	}
}
