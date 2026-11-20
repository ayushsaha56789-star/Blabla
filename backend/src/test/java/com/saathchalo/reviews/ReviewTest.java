package com.saathchalo.reviews;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saathchalo.rides.RideHousekeeping;
import com.saathchalo.rides.RideTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ReviewTest extends RideTestSupport {

	@Autowired
	RideHousekeeping housekeeping;

	@Test
	void coTravellersReviewEachOtherOnceAfterTheTrip() throws Exception {
		LoggedIn driver = member("9500000001", "Rahul", "MALE");
		LoggedIn priya = member("9500000002", "Priya", "FEMALE");
		LoggedIn asha = member("9500000003", "Asha", "FEMALE");
		LoggedIn stranger = member("9500000004", "Vikram", "MALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		book(priya, rideId, 0, 2, 1).andExpect(status().isCreated());
		book(asha, rideId, 1, 2, 1).andExpect(status().isCreated());

		review(priya, rideId, driver.userId(), 5, "Great driver").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("trip_not_finished"));

		finish(rideId);
		mvc.perform(get("/api/reviews/pending").header("Authorization", driver.bearer()))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[*].subject.displayName", containsInAnyOrder("Priya", "Asha")))
			.andExpect(jsonPath("$[0].subjectRole").value("PASSENGER"));
		mvc.perform(get("/api/reviews/pending").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].subject.displayName").value("Rahul"))
			.andExpect(jsonPath("$[0].subjectRole").value("DRIVER"))
			.andExpect(jsonPath("$[0].ride.fromName").value("Delhi"));

		review(priya, rideId, driver.userId(), 5, "  Great driver, smooth ride!  ").andExpect(status().isCreated())
			.andExpect(jsonPath("$.comment").value("Great driver, smooth ride!"))
			.andExpect(jsonPath("$.subjectRole").value("DRIVER"));
		review(priya, rideId, driver.userId(), 4, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("already_reviewed"));
		review(priya, rideId, asha.userId(), 4, null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_your_trip"));
		review(stranger, rideId, driver.userId(), 1, "Never met him").andExpect(status().isForbidden());
		review(driver, rideId, driver.userId(), 5, null).andExpect(jsonPath("$.code").value("self_review"));
		review(driver, rideId, priya.userId(), 6, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.rating").exists());
		review(driver, rideId, priya.userId(), 4, "On time and friendly").andExpect(status().isCreated());
		mvc.perform(get("/api/reviews/pending").header("Authorization", driver.bearer()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].subject.displayName").value("Asha"));

		// Reviews are public, like profiles, and the rating shows wherever the driver appears.
		mvc.perform(get("/api/users/" + driver.userId() + "/reviews"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.average").value(5.0))
			.andExpect(jsonPath("$.count").value(1))
			.andExpect(jsonPath("$.items[0].author.displayName").value("Priya"))
			.andExpect(jsonPath("$.items[0].fromName").value("Delhi"))
			.andExpect(jsonPath("$.items[0].comment").value("Great driver, smooth ride!"));
		mvc.perform(get("/api/users/" + driver.userId())).andExpect(jsonPath("$.rating").value(5.0))
			.andExpect(jsonPath("$.reviewCount").value(1));
		ride(null, rideId).andExpect(jsonPath("$.driver.profile.rating").value(5.0));
		mvc.perform(get("/api/trips?when=past").header("Authorization", asha.bearer()))
			.andExpect(jsonPath("$.riding[0].driver.rating").value(5.0))
			.andExpect(jsonPath("$.riding[0].driver.reviewCount").value(1));
		mvc.perform(get("/api/users/" + priya.userId())).andExpect(jsonPath("$.rating").value(4.0));
		mvc.perform(get("/api/users/" + stranger.userId())).andExpect(jsonPath("$.rating").doesNotExist())
			.andExpect(jsonPath("$.reviewCount").value(0));
	}

	@Test
	void reviewsCloseTwoWeeksAfterTheTrip() throws Exception {
		LoggedIn driver = member("9500000005", "Rahul", "MALE");
		LoggedIn priya = member("9500000006", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		book(priya, rideId, 0, 2, 1).andExpect(status().isCreated());
		finish(rideId);
		jdbc.update("UPDATE rides SET departure_at = now() - interval '20 days' WHERE id = ?::uuid", rideId);
		review(priya, rideId, driver.userId(), 5, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("review_window_closed"));
		mvc.perform(get("/api/reviews/pending").header("Authorization", priya.bearer())).andExpect(jsonPath("$", hasSize(0)));
	}

	/** Moves the ride into the past and lets the housekeeping job mark it (and its bookings) completed. */
	private void finish(String rideId) {
		jdbc.update("UPDATE rides SET departure_at = now() - interval '12 hours' WHERE id = ?::uuid", rideId);
		housekeeping.run();
	}

	private ResultActions review(LoggedIn author, String rideId, String subjectId, int rating, String comment) throws Exception {
		String json = """
				{"rideId":"%s","subjectId":"%s","rating":%d,"comment":%s}""".formatted(rideId, subjectId, rating,
				comment == null ? "null" : "\"" + comment + "\"");
		return mvc.perform(post("/api/reviews").header("Authorization", author.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}
}
