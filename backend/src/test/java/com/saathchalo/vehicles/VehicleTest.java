package com.saathchalo.vehicles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.IntegrationTest;
import com.saathchalo.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class VehicleTest extends IntegrationTest {

	private static final String SWIFT = """
			{"make":"Maruti Suzuki","model":"Swift","color":"Pearl White","seats":4,"plate":"dl 3c ab 1234"}""";

	@Test
	void membersKeepAListOfTheirCars() throws Exception {
		LoggedIn owner = login("9200000001");
		String body = mvc.perform(post("/api/vehicles").header("Authorization", owner.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(SWIFT))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.plate").value("DL 3 CAB 1234"))
			.andExpect(jsonPath("$.seats").value(4))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");

		// The registration number is stored encrypted, with only its last characters in clear.
		String stored = jdbc.queryForObject("SELECT plate_enc FROM vehicles WHERE id = ?::uuid", String.class, id);
		assertThat(stored).startsWith("v1:").doesNotContain("1234");
		assertThat(jdbc.queryForObject("SELECT plate_hint FROM vehicles WHERE id = ?::uuid", String.class, id)).isEqualTo("1234");

		mvc.perform(put("/api/vehicles/" + id).header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"make":"Maruti Suzuki","model":"Swift Dzire","color":"","seats":3,"plate":"22 BH 1234 AA"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.model").value("Swift Dzire"))
			.andExpect(jsonPath("$.color").doesNotExist())
			.andExpect(jsonPath("$.plate").value("22 BH 1234 AA"));
		mvc.perform(get("/api/vehicles").header("Authorization", owner.bearer())).andExpect(jsonPath("$", hasSize(1)));

		LoggedIn other = login("9200000002");
		mvc.perform(put("/api/vehicles/" + id).header("Authorization", other.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content(SWIFT))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/api/vehicles/" + id).header("Authorization", other.bearer())).andExpect(status().isNotFound());
		mvc.perform(get("/api/vehicles").header("Authorization", other.bearer())).andExpect(jsonPath("$", hasSize(0)));

		mvc.perform(delete("/api/vehicles/" + id).header("Authorization", owner.bearer())).andExpect(status().isNoContent());
		mvc.perform(get("/api/vehicles").header("Authorization", owner.bearer())).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/vehicles")).andExpect(status().isUnauthorized());
	}

	@Test
	void checksTheDetails() throws Exception {
		LoggedIn owner = login("9200000003");
		mvc.perform(post("/api/vehicles").header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"make":"Maruti Suzuki","model":"Swift","seats":4,"plate":"not a plate"}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("bad_plate"));
		mvc.perform(post("/api/vehicles").header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"make":"<script>","model":"","seats":12,"plate":""}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.make").value("Please use letters and numbers only"))
			.andExpect(jsonPath("$.errors.model").exists())
			.andExpect(jsonPath("$.errors.seats").value("At most 8 seats"))
			.andExpect(jsonPath("$.errors.plate").exists());
	}

	@Test
	void readsIndianNumberPlates() {
		assertThat(Plates.normalize("mh-12-ab-1234")).isEqualTo("MH12AB1234");
		assertThat(Plates.format("MH12AB1234")).isEqualTo("MH 12 AB 1234");
		assertThat(Plates.format(Plates.normalize("KA 05 1234"))).isEqualTo("KA 05 1234");
		assertThat(Plates.format(Plates.normalize("22BH1234A"))).isEqualTo("22 BH 1234 A");
		assertThat(Plates.hint("MH12AB1234")).isEqualTo("1234");
		assertThatThrownBy(() -> Plates.normalize("12345")).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> Plates.normalize(null)).isInstanceOf(ApiException.class);
	}
}
