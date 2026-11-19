package com.saathchalo.rides;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.IntegrationTest;
import com.saathchalo.config.AppConfig;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Helpers for ride tests: members with cars, publishing rides, searching and booking. */
abstract class RideTestSupport extends IntegrationTest {

	static final long DELHI = 1273294L;

	static final long NEW_DELHI = 1261481L;

	static final long GURUGRAM = 1270642L;

	static final long JAIPUR = 1269515L;

	static final long MUMBAI = 1275339L;

	static final long PUNE = 1259229L;

	static LocalDate inDays(int days) {
		return LocalDate.now(AppConfig.ZONE).plusDays(days);
	}

	/** A member with a complete profile (name and gender), ready to offer or book rides. */
	LoggedIn member(String phone, String name, String gender) throws Exception {
		return loginWithProfile(phone, name, gender, "Delhi");
	}

	String addCar(LoggedIn driver, int seats) throws Exception {
		String body = mvc.perform(post("/api/vehicles").header("Authorization", driver.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"make":"Maruti Suzuki","model":"Swift","color":"White","seats":%d,"plate":"mh 12-ab 1234"}"""
					.formatted(seats)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	static String rideJson(String carId, LocalDate date, String time, int seats, int price, boolean instant,
			boolean ladiesOnly, long... places) {
		String stops = Arrays.stream(places).mapToObj(id -> "{\"placeId\":" + id + "}").collect(Collectors.joining(","));
		return """
				{"vehicleId":"%s","stops":[%s],"date":"%s","time":"%s","seats":%d,"pricePerSeat":%d,"instantBooking":%b,"ladiesOnly":%b}"""
			.formatted(carId, stops, date, time, seats, price, instant, ladiesOnly);
	}

	ResultActions publishRaw(LoggedIn driver, String json) throws Exception {
		return mvc.perform(post("/api/rides").header("Authorization", driver.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	/** Delhi → Gurugram → Jaipur tomorrow at 07:30, ₹600 a seat. */
	String publishDelhiJaipur(LoggedIn driver, int seats, boolean instant) throws Exception {
		String body = publishRaw(driver, rideJson(addCar(driver, 4), inDays(1), "07:30", seats, 600, instant, false,
				DELHI, GURUGRAM, JAIPUR))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	ResultActions search(LoggedIn viewer, String query) throws Exception {
		var request = get("/api/rides/search?" + query);
		return mvc.perform(viewer == null ? request : request.header("Authorization", viewer.bearer()));
	}

	ResultActions book(LoggedIn passenger, String rideId, int from, int to, int seats) throws Exception {
		return mvc.perform(post("/api/rides/" + rideId + "/bookings").header("Authorization", passenger.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"fromPosition":%d,"toPosition":%d,"seats":%d,"message":"Hi! I'll be at the metro station."}"""
				.formatted(from, to, seats)));
	}

	ResultActions ride(LoggedIn viewer, String rideId) throws Exception {
		var request = get("/api/rides/" + rideId);
		return mvc.perform(viewer == null ? request : request.header("Authorization", viewer.bearer()));
	}
}
