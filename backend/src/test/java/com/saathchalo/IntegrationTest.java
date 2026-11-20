package com.saathchalo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.auth.staff.Totp;
import com.saathchalo.features.FeatureService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Base for tests that run against the real app and the {@code saathchalo_test} PostgreSQL database. MockMvc is set
 * up by Spring Boot, so requests pass through every servlet filter (security, maintenance mode, request IDs).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

	/** Matches app.bootstrap.super-admin-phone in application-test.properties. */
	protected static final String OWNER_PHONE = "+919999900001";

	public record LoggedIn(String accessToken, Cookie refreshCookie, String userId) {

		public String bearer() {
			return "Bearer " + accessToken;
		}

		public UUID id() {
			return UUID.fromString(userId);
		}
	}

	/** A staff member with two-step verification set up and an open console session. */
	public record Staff(LoggedIn member, String secret, String consoleToken) {

		public String console() {
			return "Bearer " + consoleToken;
		}

		public UUID id() {
			return member.id();
		}
	}

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected FeatureService featureService;

	@BeforeEach
	void cleanDatabase() {
		// The audit log is append-only by design, so it is not cleaned between tests.
		jdbc.execute("TRUNCATE users, otp_challenges, refresh_tokens, closed_accounts CASCADE");
		jdbc.update("DELETE FROM feature_flags");
		featureService.syncCatalog();
	}

	/** Requests an OTP and returns it (the test profile exposes it in the response). */
	protected String requestCode(String phone) throws Exception {
		MvcResult result = mvc.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + phone + "\"}"))
			.andExpect(status().isOk())
			.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.devCode");
	}

	protected MvcResult verify(String phone, String code) throws Exception {
		return mvc.perform(post("/api/auth/otp/verify").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"))
			.andReturn();
	}

	protected LoggedIn login(String phone) throws Exception {
		MvcResult result = verify(phone, requestCode(phone));
		String body = result.getResponse().getContentAsString();
		return new LoggedIn(JsonPath.read(body, "$.accessToken"), result.getResponse().getCookie("sc_refresh"),
				JsonPath.read(body, "$.user.id"));
	}

	/** Logs in with a female profile, a city and a name, for audience tests. */
	protected LoggedIn loginWithProfile(String phone, String name, String gender, String city) throws Exception {
		LoggedIn member = login(phone);
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/me")
			.header("Authorization", member.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"firstName":"%s","gender":"%s","city":%s,"prefChat":"SOMETIMES"}"""
				.formatted(name, gender, city == null ? "null" : "\"" + city + "\"")))
			.andExpect(status().isOk());
		return member;
	}

	/** The configured owner number logs in, becomes super admin, and sets up 2FA. */
	protected Staff superAdmin() throws Exception {
		return withTwoFactor(login(OWNER_PHONE));
	}

	protected Staff staff(String phone, String role) throws Exception {
		LoggedIn member = login(phone);
		jdbc.update("INSERT INTO staff_members (user_id, role_code, added_at, updated_at) VALUES (?, ?, now(), now())",
				member.id(), role);
		return withTwoFactor(member);
	}

	protected Staff withTwoFactor(LoggedIn member) throws Exception {
		String setup = mvc.perform(post("/api/auth/staff/totp/setup").header("Authorization", member.bearer()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		String secret = JsonPath.<String>read(setup, "$.secret").replace(" ", "");
		String done = mvc.perform(post("/api/auth/staff/totp/confirm").header("Authorization", member.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"" + Totp.codeAt(secret, Totp.stepAt(Instant.now())) + "\"}"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return new Staff(member, secret, JsonPath.read(done, "$.session.token"));
	}

	/** A code that confirmation will accept (each code works once, so the "last used" marker is reset first). */
	protected String confirmCode(Staff staff) {
		jdbc.update("UPDATE staff_totp SET last_confirm_step = 0 WHERE user_id = ?", staff.id());
		return Totp.codeAt(staff.secret(), Totp.stepAt(Instant.now()));
	}

	protected String currentCode(Staff staff) {
		return Totp.codeAt(staff.secret(), Totp.stepAt(Instant.now()));
	}
}
