package com.saathchalo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.IntegrationTest;
import com.saathchalo.auth.staff.Totp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Super admin bootstrap, two-step verification and console sessions. */
class StaffConsoleTest extends IntegrationTest {

	@Test
	void ownerNumberBecomesSuperAdminOnFirstLogin() throws Exception {
		LoggedIn owner = login(OWNER_PHONE);
		mvc.perform(get("/api/auth/staff/status").header("Authorization", owner.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.staff").value(true))
			.andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
			.andExpect(jsonPath("$.totpEnabled").value(false));

		LoggedIn someoneElse = login("9876543210");
		mvc.perform(get("/api/auth/staff/status").header("Authorization", someoneElse.bearer()))
			.andExpect(jsonPath("$.staff").value(false));
		Integer bootstraps = jdbc.queryForObject(
				"SELECT count(*) FROM audit_log WHERE action = 'staff.bootstrap' AND target_id = ?", Integer.class,
				owner.userId());
		assertThat(bootstraps).isEqualTo(1);
	}

	@Test
	void membersCannotSetUpTwoFactorOrOpenTheConsole() throws Exception {
		LoggedIn member = login("9876543210");
		mvc.perform(post("/api/auth/staff/totp/setup").header("Authorization", member.bearer()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_staff"));
		mvc.perform(get("/api/admin/me").header("Authorization", member.bearer())).andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void setupGivesRecoveryCodesAndAWorkingConsoleSession() throws Exception {
		LoggedIn owner = login(OWNER_PHONE);
		String setup = mvc.perform(post("/api/auth/staff/totp/setup").header("Authorization", owner.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.qrCode").value(org.hamcrest.Matchers.startsWith("data:image/png;base64,")))
			.andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/SaathChalo")))
			.andReturn().getResponse().getContentAsString();
		String secret = JsonPath.<String>read(setup, "$.secret").replace(" ", "");
		String stored = jdbc.queryForObject("SELECT secret_enc FROM staff_totp WHERE user_id = ?", String.class, owner.id());
		assertThat(stored).startsWith("v1:").doesNotContain(secret);

		mvc.perform(post("/api/auth/staff/totp/confirm").header("Authorization", owner.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_code"))
			.andExpect(jsonPath("$.attemptsLeft").value(4));

		String done = mvc.perform(post("/api/auth/staff/totp/confirm").header("Authorization", owner.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"" + Totp.codeAt(secret, Totp.stepAt(Instant.now())) + "\"}"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		List<String> codes = JsonPath.read(done, "$.recoveryCodes");
		assertThat(codes).hasSize(10).allMatch(c -> c.matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
		String console = "Bearer " + JsonPath.read(done, "$.session.token");

		mvc.perform(get("/api/admin/me").header("Authorization", console))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
			.andExpect(jsonPath("$.permissions", org.hamcrest.Matchers.hasItem("features.manage")));
		// The plain member token of a staff member is not enough for the console.
		mvc.perform(get("/api/admin/me").header("Authorization", owner.bearer())).andExpect(status().isForbidden());
	}

	@Test
	void eachCodeOpensOneSessionAndRecoveryCodesWorkOnce() throws Exception {
		Staff owner = superAdmin();
		String code = currentCode(owner);
		jdbc.update("UPDATE staff_totp SET last_login_step = 0 WHERE user_id = ?", owner.id());
		signIn(owner.member(), "{\"code\":\"" + code + "\"}").andExpect(status().isOk());
		signIn(owner.member(), "{\"code\":\"" + code + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_code"));

		String recovery = JsonPath.<List<String>>read(regenerateCodes(owner), "$.recoveryCodes").get(0);
		signIn(owner.member(), "{\"recoveryCode\":\"" + recovery.toLowerCase() + "\"}").andExpect(status().isOk());
		signIn(owner.member(), "{\"recoveryCode\":\"" + recovery + "\"}").andExpect(status().isBadRequest());
	}

	@Test
	void fiveWrongCodesLockSignInForAWhile() throws Exception {
		Staff owner = superAdmin();
		for (int i = 0; i < 5; i++) {
			signIn(owner.member(), "{\"code\":\"000001\"}").andExpect(status().isBadRequest());
		}
		jdbc.update("UPDATE staff_totp SET last_login_step = 0 WHERE user_id = ?", owner.id());
		signIn(owner.member(), "{\"code\":\"" + currentCode(owner) + "\"}")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("2fa_locked"));
	}

	@Test
	void consoleSessionsEndWhenIdleSignedOutOrRemovedFromStaff() throws Exception {
		Staff owner = superAdmin();
		mvc.perform(get("/api/admin/me").header("Authorization", owner.console())).andExpect(status().isOk());

		jdbc.update("UPDATE staff_sessions SET last_seen_at = now() - interval '16 minutes' WHERE user_id = ?", owner.id());
		mvc.perform(get("/api/admin/me").header("Authorization", owner.console())).andExpect(status().isUnauthorized());

		Staff again = new Staff(owner.member(), owner.secret(), openConsole(owner));
		mvc.perform(post("/api/auth/staff/logout").header("Authorization", again.console())).andExpect(status().isNoContent());
		mvc.perform(get("/api/admin/me").header("Authorization", again.console())).andExpect(status().isUnauthorized());

		Staff moderator = staff("9000000003", "MODERATOR");
		mvc.perform(get("/api/admin/me").header("Authorization", moderator.console())).andExpect(status().isOk());
		Staff fresh = new Staff(owner.member(), owner.secret(), openConsole(owner));
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/admin/staff/" + moderator.id())
				.header("Authorization", fresh.console()).header("X-Confirm-Code", confirmCode(owner)))
			.andExpect(status().isNoContent());
		mvc.perform(get("/api/admin/me").header("Authorization", moderator.console())).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/staff/status").header("Authorization", moderator.member().bearer()))
			.andExpect(jsonPath("$.staff").value(false))
			.andExpect(jsonPath("$.totpEnabled").value(false));
	}

	private org.springframework.test.web.servlet.ResultActions signIn(LoggedIn member, String body) throws Exception {
		return mvc.perform(post("/api/auth/staff/session").header("Authorization", member.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String openConsole(Staff staff) throws Exception {
		jdbc.update("UPDATE staff_totp SET last_login_step = 0 WHERE user_id = ?", staff.id());
		String body = signIn(staff.member(), "{\"code\":\"" + currentCode(staff) + "\"}")
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private String regenerateCodes(Staff staff) throws Exception {
		return mvc.perform(post("/api/auth/staff/recovery-codes").header("Authorization", staff.console())
				.header("X-Confirm-Code", confirmCode(staff)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
	}
}
