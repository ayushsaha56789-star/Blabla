package com.saathchalo.features;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saathchalo.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class FeatureSwitchTest extends IntegrationTest {

	@Test
	void visitorsSeeOnlyTheDefaultFeatures() throws Exception {
		mvc.perform(get("/api/features"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.enabled", hasItem("rides.booking")))
			.andExpect(jsonPath("$.enabled", not(hasItem("games"))))
			.andExpect(jsonPath("$.maintenance").value(false));
		LoggedIn member = login("9000000020");
		mvc.perform(get("/api/test/games").header("Authorization", member.bearer()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("feature_unavailable"));
	}

	@Test
	void theOwnerDecidesWhoSeesGames() throws Exception {
		Staff owner = superAdmin();
		LoggedIn woman = loginWithProfile("9000000021", "Asha", "FEMALE", "Pune");
		LoggedIn man = loginWithProfile("9000000022", "Ravi", "MALE", "Delhi");

		set(owner, "games", "{\"enabled\":true,\"audience\":\"EVERYONE\"}").andExpect(status().isOk());
		mvc.perform(get("/api/features")).andExpect(jsonPath("$.enabled", hasItem("games")));
		seesGames(man, true);

		set(owner, "games", "{\"enabled\":true,\"audience\":\"WOMEN\"}").andExpect(status().isOk());
		seesGames(woman, true);
		seesGames(man, false);
		mvc.perform(get("/api/features")).andExpect(jsonPath("$.enabled", not(hasItem("games"))));

		set(owner, "games", "{\"enabled\":true,\"audience\":\"CITIES\",\"cities\":[\"Delhi\",\" mumbai \"]}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.cities[1]").value("mumbai"));
		seesGames(man, true);
		seesGames(woman, false);
		set(owner, "games", "{\"enabled\":true,\"audience\":\"CITIES\",\"cities\":[]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("cities_required"));
	}

	@Test
	void aChildSwitchNeedsItsParent() throws Exception {
		Staff owner = superAdmin();
		set(owner, "games.chess", "{\"enabled\":true,\"audience\":\"EVERYONE\"}").andExpect(status().isOk());
		mvc.perform(get("/api/features")).andExpect(jsonPath("$.enabled", not(hasItem("games.chess"))));
		set(owner, "games", "{\"enabled\":true,\"audience\":\"EVERYONE\"}").andExpect(status().isOk());
		mvc.perform(get("/api/features")).andExpect(jsonPath("$.enabled", hasItem("games.chess")));
	}

	@Test
	void criticalSwitchesNeedAFreshCodeAndMaintenanceClosesTheMemberApi() throws Exception {
		Staff owner = superAdmin();
		LoggedIn member = login("9000000023");
		set(owner, "maintenance_mode", "{\"enabled\":true}")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("confirmation_required"));
		mvc.perform(put("/api/admin/features/maintenance_mode").header("Authorization", owner.console())
				.header("X-Confirm-Code", "123456").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("confirmation_invalid"));
		mvc.perform(put("/api/admin/features/maintenance_mode").header("Authorization", owner.console())
				.header("X-Confirm-Code", confirmCode(owner)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\":true}"))
			.andExpect(status().isOk());

		mvc.perform(get("/api/features")).andExpect(jsonPath("$.maintenance").value(true));
		mvc.perform(get("/api/users/me").header("Authorization", member.bearer()))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("maintenance"));
		mvc.perform(post("/api/auth/refresh").cookie(member.refreshCookie())).andExpect(status().isOk());
		mvc.perform(get("/api/admin/me").header("Authorization", owner.console())).andExpect(status().isOk());
	}

	@Test
	void pausedSignUpsStillLetMembersLogIn() throws Exception {
		Staff owner = superAdmin();
		login("9000000024");
		mvc.perform(put("/api/admin/features/signups").header("Authorization", owner.console())
				.header("X-Confirm-Code", confirmCode(owner)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\":false}"))
			.andExpect(status().isOk());
		mvc.perform(post("/api/auth/otp/request").contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"9000000025\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("signups_paused"));
		jdbc.update("UPDATE otp_challenges SET created_at = created_at - interval '1 minute'");
		requestCode("9000000024");
	}

	@Test
	void switchChangesAreAudited() throws Exception {
		Staff owner = superAdmin();
		set(owner, "rentals", "{\"enabled\":true,\"audience\":\"BETA\"}").andExpect(status().isOk());
		mvc.perform(get("/api/admin/audit?action=feature.updated&targetId=rentals").header("Authorization", owner.console()))
			.andExpect(jsonPath("$.items[0].summary").value("Turned on Car rentals for beta testers"))
			.andExpect(jsonPath("$.items[0].actorLabel").value(endsWith("· Super admin")));
	}

	private ResultActions set(Staff owner, String key, String body) throws Exception {
		return mvc.perform(put("/api/admin/features/" + key).header("Authorization", owner.console())
			.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private void seesGames(LoggedIn member, boolean expected) throws Exception {
		mvc.perform(get("/api/features").header("Authorization", member.bearer()))
			.andExpect(jsonPath("$.enabled", expected ? hasItem("games") : not(hasItem("games"))));
		mvc.perform(get("/api/test/games").header("Authorization", member.bearer()))
			.andExpect(expected ? status().isOk() : status().isForbidden());
	}
}
