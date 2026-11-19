package com.saathchalo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class RestrictionTest extends IntegrationTest {

	@Test
	void aSuspendedMemberIsLockedOutUntilTheSuspensionIsLifted() throws Exception {
		Staff moderator = staff("9000000031", "MODERATOR");
		LoggedIn member = login("9000000032");

		String body = mvc.perform(post("/api/admin/members/" + member.userId() + "/restrictions")
				.header("Authorization", moderator.console()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"area\":\"ALL\",\"reason\":\"Repeated no-shows\",\"until\":\"2099-01-31\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(true))
			.andReturn().getResponse().getContentAsString();
		String restrictionId = JsonPath.read(body, "$.id");

		mvc.perform(get("/api/users/me").header("Authorization", member.bearer()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("account_suspended"))
			.andExpect(jsonPath("$.reason").value("Repeated no-shows"))
			.andExpect(jsonPath("$.until").value("2099-01-31T18:30:00Z"));
		// Sessions ended when the suspension started, and asking for a new login code is refused.
		mvc.perform(post("/api/auth/refresh").cookie(member.refreshCookie())).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"9000000032\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("account_suspended"));

		mvc.perform(delete("/api/admin/members/" + member.userId() + "/restrictions/" + restrictionId)
				.header("Authorization", moderator.console()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.active").value(false));
		mvc.perform(get("/api/users/me").header("Authorization", member.bearer())).andExpect(status().isOk());
	}

	@Test
	void anAreaRestrictionHidesJustThatFeature() throws Exception {
		Staff owner = superAdmin();
		LoggedIn member = login("9000000033");
		mvc.perform(put("/api/admin/features/games").header("Authorization", owner.console())
			.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"audience\":\"EVERYONE\"}"));
		mvc.perform(get("/api/features").header("Authorization", member.bearer())).andExpect(jsonPath("$.enabled", hasItem("games")));

		mvc.perform(post("/api/admin/members/" + member.userId() + "/restrictions").header("Authorization", owner.console())
				.contentType(MediaType.APPLICATION_JSON).content("{\"area\":\"games\",\"reason\":\"Abusive in game chat\"}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/features").header("Authorization", member.bearer()))
			.andExpect(jsonPath("$.enabled", not(hasItem("games"))))
			.andExpect(jsonPath("$.enabled", hasItem("rides.booking")));
		mvc.perform(get("/api/users/me").header("Authorization", member.bearer())).andExpect(status().isOk());
		mvc.perform(post("/api/admin/members/" + member.userId() + "/restrictions").header("Authorization", owner.console())
				.contentType(MediaType.APPLICATION_JSON).content("{\"area\":\"games\",\"reason\":\"Again\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void staffAreProtectedFromEachOther() throws Exception {
		Staff owner = superAdmin();
		Staff moderator = staff("9000000034", "MODERATOR");
		Staff support = staff("9000000035", "SUPPORT");
		String suspend = "{\"area\":\"ALL\",\"reason\":\"Testing protection\"}";

		mvc.perform(post("/api/admin/members/" + owner.id() + "/restrictions").header("Authorization", moderator.console())
				.contentType(MediaType.APPLICATION_JSON).content(suspend))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/admin/members/" + support.id() + "/restrictions").header("Authorization", moderator.console())
				.contentType(MediaType.APPLICATION_JSON).content(suspend))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("Only the super admin can restrict staff members."));
		mvc.perform(post("/api/admin/members/" + moderator.id() + "/restrictions").header("Authorization", moderator.console())
				.contentType(MediaType.APPLICATION_JSON).content(suspend))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/admin/members/" + moderator.id() + "/restrictions").header("Authorization", owner.console())
				.contentType(MediaType.APPLICATION_JSON).content(suspend))
			.andExpect(status().isOk());
		// The suspension also ended the moderator's console session.
		mvc.perform(get("/api/admin/me").header("Authorization", moderator.console())).andExpect(status().isUnauthorized());
	}

	@Test
	void revealingAPhoneNumberIsAudited() throws Exception {
		Staff support = staff("9000000036", "SUPPORT");
		LoggedIn member = login("9000000037");
		mvc.perform(get("/api/admin/members?q=00037").header("Authorization", support.console()))
			.andExpect(jsonPath("$.items[0].maskedPhone").value("+91 90••• ••037"));
		mvc.perform(post("/api/admin/members/" + member.userId() + "/contact").header("Authorization", support.console()))
			.andExpect(jsonPath("$.phone").value("+919000000037"));
		Integer audited = jdbc.queryForObject(
				"SELECT count(*) FROM audit_log WHERE action = 'member.phone_revealed' AND target_id = ?", Integer.class,
				member.userId());
		assertThat(audited).isEqualTo(1);
	}
}
