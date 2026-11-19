package com.saathchalo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saathchalo.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthFlowTest extends IntegrationTest {

	@Test
	void newNumberSignsUpAndGetsASession() throws Exception {
		String code = requestCode("98765 43210");
		MvcResult result = verify("+91 98765 43210", code);

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
		LoggedIn session = new LoggedIn(
				com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"),
				result.getResponse().getCookie("sc_refresh"), null);
		assertThat(session.refreshCookie().isHttpOnly()).isTrue();
		assertThat(session.refreshCookie().getPath()).isEqualTo("/api/auth");

		mvc.perform(get("/api/users/me").header("Authorization", session.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value("+919876543210"))
			.andExpect(jsonPath("$.phoneVerified").value(true))
			.andExpect(jsonPath("$.profileComplete").value(false));
	}

	@Test
	void sameNumberLogsIntoTheSameAccount() throws Exception {
		String first = login("9876543210").userId();
		jdbc.update("UPDATE otp_challenges SET created_at = created_at - interval '1 minute'");
		String second = login("09876543210").userId();
		assertThat(second).isEqualTo(first);
	}

	@Test
	void refreshRotatesTheTokenAndLogoutEndsTheSession() throws Exception {
		LoggedIn session = login("9876543210");

		MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(session.refreshCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andReturn();
		Cookie rotated = refreshed.getResponse().getCookie("sc_refresh");
		assertThat(rotated.getValue()).isNotEqualTo(session.refreshCookie().getValue());

		// The old refresh token is single-use.
		mvc.perform(post("/api/auth/refresh").cookie(session.refreshCookie())).andExpect(status().isUnauthorized());

		mvc.perform(post("/api/auth/logout").cookie(rotated))
			.andExpect(status().isNoContent())
			.andExpect(cookie().maxAge("sc_refresh", 0));
		mvc.perform(post("/api/auth/refresh").cookie(rotated))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("session_expired"));
	}

	@Test
	void wrongCodesAreLimited() throws Exception {
		String code = requestCode("9876543210");
		String wrong = code.equals("000000") ? "111111" : "000000";
		for (int left = 4; left >= 0; left--) {
			MvcResult r = verify("9876543210", wrong);
			assertThat(r.getResponse().getStatus()).isEqualTo(400);
		}
		// Even the right code is refused once the attempts are used up.
		assertThat(verify("9876543210", code).getResponse().getStatus()).isEqualTo(429);
	}

	@Test
	void codeCannotBeUsedTwice() throws Exception {
		String code = requestCode("9876543210");
		assertThat(verify("9876543210", code).getResponse().getStatus()).isEqualTo(200);
		MvcResult again = verify("9876543210", code);
		assertThat(again.getResponse().getStatus()).isEqualTo(400);
		assertThat(again.getResponse().getContentAsString()).contains("otp_expired");
	}

	@Test
	void resendingTooSoonIsRefused() throws Exception {
		requestCode("9876543210");
		mvc.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"9876543210\"}"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("otp_cooldown"));
	}

	@Test
	void invalidNumberGetsAFriendlyError() throws Exception {
		mvc.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
				.content("{\"phone\":\"12345\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_phone"))
			.andExpect(jsonPath("$.detail").value("Please enter a valid 10-digit Indian mobile number."));
	}

	@Test
	void forgedTokenIsRejected() throws Exception {
		String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDAiLCJpc3MiOiJzYWF0aGNoYWxvIn0.c2lnbmF0dXJl";
		mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
	}
}
