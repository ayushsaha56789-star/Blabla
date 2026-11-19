package com.saathchalo;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Base for tests that run against the real app and the {@code saathchalo_test} PostgreSQL database. */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {

	public record LoggedIn(String accessToken, Cookie refreshCookie, String userId) {

		public String bearer() {
			return "Bearer " + accessToken;
		}
	}

	@Autowired
	private WebApplicationContext context;

	@Autowired
	protected JdbcTemplate jdbc;

	protected MockMvc mvc;

	@BeforeEach
	void setUpMockMvcAndCleanDatabase() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		jdbc.execute("TRUNCATE users, otp_challenges, refresh_tokens CASCADE");
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
}
