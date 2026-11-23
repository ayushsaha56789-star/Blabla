package com.saathchalo.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.rides.RideHousekeeping;
import com.saathchalo.rides.RideTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class NotificationsTest extends RideTestSupport {

	@Autowired
	RideHousekeeping housekeeping;

	@Autowired
	Notifier notifier;

	@Test
	void driversAndPassengersHearAboutEachStep() throws Exception {
		LoggedIn ravi = member("9700000001", "Ravi", "MALE");
		LoggedIn priya = member("9700000002", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(ravi, 3, false);

		// A request reaches the driver, linking to the ride.
		String bookingId = JsonPath.read(book(priya, rideId, 0, 2, 1).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id");
		inbox(ravi).andExpect(jsonPath("$.unread").value(1))
			.andExpect(jsonPath("$.items[0].title").value("Priya asked for 1 seat"))
			.andExpect(jsonPath("$.items[0].body").value(startsWith("Delhi → Jaipur, ")))
			.andExpect(jsonPath("$.items[0].link").value("/rides/" + rideId))
			.andExpect(jsonPath("$.items[0].topic").value("TRIPS"))
			.andExpect(jsonPath("$.items[0].read").value(false));
		unread(ravi, 1);

		// The answer reaches the passenger; her cancellation reaches the driver.
		mvc.perform(post("/api/bookings/" + bookingId + "/accept").header("Authorization", ravi.bearer())).andExpect(status().isOk());
		inbox(priya).andExpect(jsonPath("$.items[0].title").value("Ravi accepted your request"));
		mvc.perform(post("/api/bookings/" + bookingId + "/cancel").header("Authorization", priya.bearer())).andExpect(status().isOk());
		inbox(ravi).andExpect(jsonPath("$.items", hasSize(2)))
			.andExpect(jsonPath("$.items[0].title").value("Priya cancelled their booking"))
			.andExpect(jsonPath("$.items[0].body").value(startsWith("1 seat free again on Delhi → Jaipur")));

		// Reading one, then everything.
		Number first = JsonPath.read(inbox(ravi).andReturn().getResponse().getContentAsString(), "$.items[1].id");
		markRead(ravi, "{\"ids\":[" + first + "]}");
		unread(ravi, 1);
		markRead(ravi, "{\"all\":true}");
		unread(ravi, 0);
		inbox(ravi).andExpect(jsonPath("$.items[0].read").value(true));

		// Someone else's notices can't be marked read: the count stays.
		Number priyas = JsonPath.read(inbox(priya).andReturn().getResponse().getContentAsString(), "$.items[0].id");
		markRead(ravi, "{\"ids\":[" + priyas + "]}");
		unread(priya, 1);
	}

	@Test
	void finishedTripsAskForARatingUnlessMuted() throws Exception {
		LoggedIn ravi = member("9700000011", "Ravi", "MALE");
		LoggedIn priya = member("9700000012", "Priya", "FEMALE");
		LoggedIn asha = member("9700000013", "Asha", "FEMALE");
		String rideId = publishDelhiJaipur(ravi, 3, true);
		book(priya, rideId, 0, 2, 1).andExpect(status().isCreated());
		book(asha, rideId, 0, 1, 1).andExpect(status().isCreated());
		inbox(ravi).andExpect(jsonPath("$.items[0].title").value("Asha booked 1 seat"));

		// Asha doesn't want reminders. Trip notices can't be muted.
		mvc.perform(put("/api/notifications/settings").header("Authorization", asha.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"muted\":[\"REMINDERS\",\"TRIPS\"]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.topics[?(@.topic == 'REMINDERS')].muted").value(true))
			.andExpect(jsonPath("$.topics[?(@.topic == 'TRIPS')].muted").value(false))
			.andExpect(jsonPath("$.topics[?(@.topic == 'TRIPS')].canMute").value(false));

		jdbc.update("UPDATE rides SET departure_at = now() - interval '2 days' WHERE id = ?::uuid", rideId);
		housekeeping.run();
		inbox(priya).andExpect(jsonPath("$.items[0].title").value("How was your trip with Ravi?"))
			.andExpect(jsonPath("$.items[0].topic").value("REMINDERS"));
		inbox(ravi).andExpect(jsonPath("$.items[0].title").value(matchesPattern("How was your trip with (Priya|Asha) and 1 other\\?")));
		inbox(asha).andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void switchedOffMeansNothingIsStoredOrShown() throws Exception {
		LoggedIn ravi = member("9700000021", "Ravi", "MALE");
		LoggedIn priya = member("9700000022", "Priya", "FEMALE");
		jdbc.update("UPDATE feature_flags SET enabled = FALSE WHERE key = 'notifications'");
		featureService.reload();
		try {
			String rideId = publishDelhiJaipur(ravi, 3, false);
			book(priya, rideId, 0, 2, 1).andExpect(status().isCreated());
			mvc.perform(get("/api/notifications").header("Authorization", ravi.bearer())).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("feature_unavailable"));
		}
		finally {
			jdbc.update("UPDATE feature_flags SET enabled = TRUE WHERE key = 'notifications'");
			featureService.reload();
		}
		inbox(ravi).andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void olderNoticesComePageByPageAndLeaveWithTheAccount() throws Exception {
		LoggedIn priya = member("9700000031", "Priya", "FEMALE");
		UUID id = UUID.fromString(priya.userId());
		for (int i = 1; i <= 35; i++) {
			notifier.send(id, Notice.account("info", "Notice " + i, null, "/help"));
		}
		String page = inbox(priya).andExpect(jsonPath("$.items", hasSize(30)))
			.andExpect(jsonPath("$.more").value(true))
			.andExpect(jsonPath("$.unread").value(35))
			.andExpect(jsonPath("$.items[0].title").value("Notice 35"))
			.andReturn().getResponse().getContentAsString();
		Number last = JsonPath.read(page, "$.items[29].id");
		mvc.perform(get("/api/notifications?before=" + last).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.items", hasSize(5)))
			.andExpect(jsonPath("$.more").value(false))
			.andExpect(jsonPath("$.items[4].title").value("Notice 1"));

		String data = mvc.perform(get("/api/privacy/export").header("Authorization", priya.bearer()))
			.andReturn().getResponse().getContentAsString();
		assertThat(data).contains("Notice 35");
		Integer before = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?::uuid", Integer.class, priya.userId());
		assertThat(before).isEqualTo(35);
	}

	@Test
	void noticesStayInsideTheApp() {
		assertThatThrownBy(() -> Notice.trips("car", "Hi", null, "https://example.com")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Notice.trips("car", "Hi", null, "//example.com")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Notice.trips("car", " ", null, "/rides")).isInstanceOf(IllegalArgumentException.class);
		assertThat(Notice.trips("car", "x".repeat(200), "y".repeat(400), null).title()).hasSize(120).endsWith("…");
	}

	private ResultActions inbox(LoggedIn who) throws Exception {
		return mvc.perform(get("/api/notifications").header("Authorization", who.bearer())).andExpect(status().isOk());
	}

	private void unread(LoggedIn who, int count) throws Exception {
		mvc.perform(get("/api/notifications/unread").header("Authorization", who.bearer())).andExpect(jsonPath("$.count").value(count));
	}

	private void markRead(LoggedIn who, String json) throws Exception {
		mvc.perform(post("/api/notifications/read").header("Authorization", who.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isNoContent());
	}
}
