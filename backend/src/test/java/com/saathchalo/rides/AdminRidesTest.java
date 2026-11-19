package com.saathchalo.rides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AdminRidesTest extends RideTestSupport {

	@Test
	void staffSeeRidesAndCanCancelOneWithAFreshCode() throws Exception {
		Staff owner = superAdmin();
		Staff support = staff("9300000001", "SUPPORT");
		LoggedIn driver = member("9300000002", "Rahul", "MALE");
		LoggedIn passenger = member("9300000003", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		book(passenger, rideId, 0, 2, 1).andExpect(status().isCreated());

		mvc.perform(get("/api/admin/rides").header("Authorization", support.console()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].fromName").value("Delhi"))
			.andExpect(jsonPath("$.items[0].toName").value("Jaipur"))
			.andExpect(jsonPath("$.items[0].seatsBooked").value(1))
			.andExpect(jsonPath("$.items[0].driver.displayName").value("Rahul"));
		mvc.perform(get("/api/admin/rides?q=gurug").header("Authorization", support.console()))
			.andExpect(jsonPath("$.total").value(1));
		mvc.perform(get("/api/admin/rides?q=pune").header("Authorization", support.console()))
			.andExpect(jsonPath("$.total").value(0));
		mvc.perform(get("/api/admin/rides?show=cancelled").header("Authorization", support.console()))
			.andExpect(jsonPath("$.total").value(0));
		mvc.perform(get("/api/admin/rides/" + rideId).header("Authorization", support.console()))
			.andExpect(jsonPath("$.ride.viewer").value("STAFF"))
			.andExpect(jsonPath("$.ride.driver.phone").doesNotExist())
			.andExpect(jsonPath("$.bookings", hasSize(1)))
			.andExpect(jsonPath("$.bookings[0].message").value("Hi! I'll be at the metro station."));
		// Members' tokens can't use the console.
		mvc.perform(get("/api/admin/rides").header("Authorization", driver.bearer())).andExpect(status().isForbidden());

		String cancel = "{\"reason\":\"Safety report from a passenger\"}";
		// Support can look but not cancel.
		mvc.perform(post("/api/admin/rides/" + rideId + "/cancel").header("Authorization", support.console())
				.header("X-Confirm-Code", confirmCode(support)).contentType(MediaType.APPLICATION_JSON).content(cancel))
			.andExpect(status().isForbidden());
		// Cancelling needs a fresh two-step code.
		mvc.perform(post("/api/admin/rides/" + rideId + "/cancel").header("Authorization", owner.console())
				.contentType(MediaType.APPLICATION_JSON).content(cancel))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("confirmation_required"));
		mvc.perform(post("/api/admin/rides/" + rideId + "/cancel").header("Authorization", owner.console())
				.header("X-Confirm-Code", confirmCode(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/admin/rides/" + rideId + "/cancel").header("Authorization", owner.console())
				.header("X-Confirm-Code", confirmCode(owner)).contentType(MediaType.APPLICATION_JSON).content(cancel))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ride.status").value("CANCELLED"))
			.andExpect(jsonPath("$.ride.cancelledBy").value("STAFF"))
			.andExpect(jsonPath("$.bookings[0].status").value("CANCELLED"));

		ride(passenger, rideId).andExpect(jsonPath("$.cancelReason").value("Safety report from a passenger"));
		Integer logged = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'ride.cancelled' AND target_id = ?",
				Integer.class, rideId);
		assertThat(logged).isEqualTo(1);

		mvc.perform(get("/api/admin/dashboard").header("Authorization", owner.console()))
			.andExpect(jsonPath("$.rides.publishedLast7Days").value(1))
			.andExpect(jsonPath("$.rides.cancelledLast7Days").value(1))
			.andExpect(jsonPath("$.rides.upcoming").value(0));
	}
}
