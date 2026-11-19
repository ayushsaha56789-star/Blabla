package com.saathchalo.rides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class RideFlowTest extends RideTestSupport {

	@Autowired
	RideHousekeeping housekeeping;

	@Test
	void offeringARideChecksEveryDetail() throws Exception {
		LoggedIn newcomer = login("9100000001");
		String newcomersCar = addCar(newcomer, 4);
		publishRaw(newcomer, rideJson(newcomersCar, inDays(1), "07:30", 3, 600, true, false, DELHI, JAIPUR))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("profile_incomplete"));

		LoggedIn driver = member("9100000002", "Rahul", "MALE");
		String car = addCar(driver, 4);
		publishRaw(driver, rideJson(car, inDays(0), "00:00", 3, 600, true, false, DELHI, JAIPUR))
			.andExpect(jsonPath("$.code").value("departure_too_soon"));
		publishRaw(driver, rideJson(car, inDays(120), "07:30", 3, 600, true, false, DELHI, JAIPUR))
			.andExpect(jsonPath("$.code").value("departure_too_far"));
		publishRaw(driver, rideJson(car, inDays(1), "07:30", 5, 600, true, false, DELHI, JAIPUR))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("too_many_seats"))
			.andExpect(jsonPath("$.maxSeats").value(4));
		publishRaw(driver, rideJson(car, inDays(1), "07:30", 3, 5000, true, false, DELHI, JAIPUR))
			.andExpect(jsonPath("$.code").value("price_too_high"))
			.andExpect(jsonPath("$.maxPrice").value(lessThan(5000)));
		publishRaw(driver, rideJson(car, inDays(1), "07:30", 3, 600, true, false, DELHI, GURUGRAM, DELHI))
			.andExpect(jsonPath("$.code").value("duplicate_stop"));
		publishRaw(driver, rideJson(car, inDays(1), "07:30", 3, 600, true, false, DELHI, 42L))
			.andExpect(jsonPath("$.code").value("place_not_found"));
		publishRaw(driver, rideJson(car, inDays(1), "07:30", 3, 600, true, false, DELHI, NEW_DELHI))
			.andExpect(jsonPath("$.code").value("route_too_short"));
		publishRaw(driver, rideJson(newcomersCar, inDays(1), "07:30", 3, 600, true, false, DELHI, JAIPUR))
			.andExpect(jsonPath("$.code").value("vehicle_not_found"));
		publishRaw(driver, rideJson(car, inDays(1), "07:30", 3, 600, true, true, DELHI, JAIPUR))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ladies_only_unavailable"));
		publishRaw(driver, "{\"stops\":[]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors.vehicleId").exists());

		publishRaw(driver, rideJson(car, inDays(1), "07:30", 3, 600, true, false, DELHI, GURUGRAM, JAIPUR))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("SCHEDULED"))
			.andExpect(jsonPath("$.viewer").value("DRIVER"))
			.andExpect(jsonPath("$.stops", hasSize(3)))
			.andExpect(jsonPath("$.stops[1].name").value("Gurugram"))
			.andExpect(jsonPath("$.seatsLeftByLeg[0]").value(3))
			.andExpect(jsonPath("$.prices[0][2]").value(600))
			.andExpect(jsonPath("$.car.plate").value("MH 12 AB 1234"))
			.andExpect(jsonPath("$.bookable").value(false));
		// The same driver can't be on two rides at once.
		publishRaw(driver, rideJson(car, inDays(1), "09:00", 3, 100, true, false, DELHI, GURUGRAM))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ride_overlap"));
	}

	@Test
	void quoteSuggestsAFairPrice() throws Exception {
		LoggedIn driver = member("9100000003", "Rahul", "MALE");
		mvc.perform(post("/api/rides/quote").header("Authorization", driver.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"placeIds\":[" + MUMBAI + "," + PUNE + "]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.distanceKm").value(both(greaterThan(120)).and(lessThan(180))))
			.andExpect(jsonPath("$.suggestedPrice").value(greaterThan(200)))
			.andExpect(jsonPath("$.minPrice").value(20));
	}

	@Test
	void searchFindsRidesThatStopNearbyInTheRightOrder() throws Exception {
		LoggedIn driver = member("9100000004", "Rahul", "MALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		String tomorrow = inDays(1).toString();

		// From a stop on the way: priced for that stretch only. Visitors can search without logging in.
		search(null, "fromId=" + GURUGRAM + "&toId=" + JAIPUR + "&date=" + tomorrow)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rides", hasSize(1)))
			.andExpect(jsonPath("$.rides[0].rideId").value(rideId))
			.andExpect(jsonPath("$.rides[0].fromPosition").value(1))
			.andExpect(jsonPath("$.rides[0].toPosition").value(2))
			.andExpect(jsonPath("$.rides[0].price").value(lessThan(600)))
			.andExpect(jsonPath("$.rides[0].seatsLeft").value(3))
			.andExpect(jsonPath("$.rides[0].driver.displayName").value("Rahul"))
			.andExpect(jsonPath("$.rides[0].driver.phone").doesNotExist())
			.andExpect(jsonPath("$.rides[0].car").value("Maruti Suzuki Swift"));
		// Shared links can use names, including old ones.
		search(null, "from=Gurgaon&to=jaipur&date=" + tomorrow)
			.andExpect(jsonPath("$.from.name").value("Gurugram"))
			.andExpect(jsonPath("$.rides", hasSize(1)));
		// Nearby counts too: New Delhi is a few km from the Delhi stop.
		search(null, "fromId=" + NEW_DELHI + "&toId=" + JAIPUR + "&date=" + tomorrow)
			.andExpect(jsonPath("$.rides[0].fromPosition").value(0))
			.andExpect(jsonPath("$.rides[0].pickupDistanceKm").value(lessThan(10)));
		// Wrong direction: nothing.
		search(null, "fromId=" + JAIPUR + "&toId=" + DELHI + "&date=" + tomorrow).andExpect(jsonPath("$.rides", hasSize(0)));
		// Another day: nothing that day, but tomorrow is suggested.
		search(null, "fromId=" + DELHI + "&toId=" + JAIPUR + "&date=" + inDays(2))
			.andExpect(jsonPath("$.rides", hasSize(0)))
			.andExpect(jsonPath("$.otherDates[0].date").value(tomorrow))
			.andExpect(jsonPath("$.otherDates[0].rides").value(1));
		// More seats than the car has free.
		search(null, "fromId=" + DELHI + "&toId=" + JAIPUR + "&date=" + tomorrow + "&seats=4")
			.andExpect(jsonPath("$.rides", hasSize(0)));
		// The driver sees their own ride marked as theirs.
		search(driver, "fromId=" + DELHI + "&toId=" + JAIPUR + "&date=" + tomorrow)
			.andExpect(jsonPath("$.rides[0].yours").value(true));
		search(null, "fromId=" + DELHI + "&toId=" + DELHI).andExpect(jsonPath("$.code").value("same_place"));
		search(null, "from=Atlantis&to=Jaipur").andExpect(status().isNotFound());
	}

	@Test
	void ladiesOnlyRidesAreForWomenOnly() throws Exception {
		LoggedIn priya = member("9100000005", "Priya", "FEMALE");
		LoggedIn anita = member("9100000006", "Anita", "FEMALE");
		LoggedIn rahul = member("9100000007", "Rahul", "MALE");
		String body = publishRaw(priya, rideJson(addCar(priya, 3), inDays(1), "08:00", 3, 500, true, true, DELHI, JAIPUR))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.ladiesOnly").value(true))
			.andReturn().getResponse().getContentAsString();
		String rideId = JsonPath.read(body, "$.id");
		String query = "fromId=" + DELHI + "&toId=" + JAIPUR + "&date=" + inDays(1);

		search(anita, query).andExpect(jsonPath("$.rides", hasSize(1))).andExpect(jsonPath("$.rides[0].ladiesOnly").value(true));
		search(rahul, query).andExpect(jsonPath("$.rides", hasSize(0)));
		search(null, query).andExpect(jsonPath("$.rides", hasSize(0)));
		ride(rahul, rideId).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ladies_only"));
		ride(null, rideId).andExpect(status().isForbidden());
		book(rahul, rideId, 0, 1, 1).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ladies_only"));
		book(anita, rideId, 0, 1, 1).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

	@Test
	void instantBookingConfirmsTheSeatAndSharesContactsOnlyBetweenDriverAndPassenger() throws Exception {
		LoggedIn driver = member("9100000008", "Rahul", "MALE");
		LoggedIn passenger = member("9100000009", "Priya", "FEMALE");
		LoggedIn stranger = member("9100000010", "Vikram", "MALE");
		String rideId = publishDelhiJaipur(driver, 3, true);

		String booking = book(passenger, rideId, 1, 2, 2)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.fromName").value("Gurugram"))
			.andExpect(jsonPath("$.toName").value("Jaipur"))
			.andExpect(jsonPath("$.seats").value(2))
			.andReturn().getResponse().getContentAsString();
		int price = JsonPath.read(booking, "$.pricePerSeat");
		assertThat((int) JsonPath.read(booking, "$.total")).isEqualTo(2 * price);

		ride(passenger, rideId)
			.andExpect(jsonPath("$.viewer").value("PASSENGER"))
			.andExpect(jsonPath("$.myBooking.status").value("CONFIRMED"))
			.andExpect(jsonPath("$.driver.phone").value("+919100000008"))
			.andExpect(jsonPath("$.car.plate").value("MH 12 AB 1234"))
			.andExpect(jsonPath("$.seatsLeftByLeg[0]").value(3))
			.andExpect(jsonPath("$.seatsLeftByLeg[1]").value(1))
			.andExpect(jsonPath("$.bookable").value(false));
		ride(driver, rideId)
			.andExpect(jsonPath("$.passengers[0].phone").value("+919100000009"))
			.andExpect(jsonPath("$.passengers[0].message").value("Hi! I'll be at the metro station."));
		ride(stranger, rideId)
			.andExpect(jsonPath("$.viewer").value("VISITOR"))
			.andExpect(jsonPath("$.driver.phone").value(nullValue()))
			.andExpect(jsonPath("$.car.plate").value(nullValue()))
			.andExpect(jsonPath("$.passengers[0].member.displayName").value("Priya"))
			.andExpect(jsonPath("$.passengers[0].phone").value(nullValue()))
			.andExpect(jsonPath("$.passengers[0].message").value(nullValue()))
			.andExpect(jsonPath("$.bookable").value(true));
		ride(null, rideId).andExpect(status().isOk()).andExpect(jsonPath("$.driver.phone").value(nullValue()));

		book(passenger, rideId, 0, 2, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("already_booked"));
		book(driver, rideId, 0, 2, 1).andExpect(jsonPath("$.code").value("own_ride"));
		book(stranger, rideId, 2, 1, 1).andExpect(jsonPath("$.code").value("bad_stretch"));
		book(stranger, rideId, 1, 2, 2).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not_enough_seats"))
			.andExpect(jsonPath("$.seatsLeft").value(1));
	}

	@Test
	void requestsWaitForTheDriversAnswer() throws Exception {
		LoggedIn driver = member("9100000011", "Rahul", "MALE");
		LoggedIn first = member("9100000012", "Priya", "FEMALE");
		LoggedIn second = member("9100000013", "Vikram", "MALE");
		String rideId = publishDelhiJaipur(driver, 2, false);

		String request = book(first, rideId, 0, 2, 1)
			.andExpect(jsonPath("$.status").value("REQUESTED"))
			.andExpect(jsonPath("$.expiresAt").exists())
			.andReturn().getResponse().getContentAsString();
		String bookingId = JsonPath.read(request, "$.id");
		// A request holds the seat while the driver decides, and no phone numbers are shared yet.
		ride(first, rideId).andExpect(jsonPath("$.seatsLeftByLeg[0]").value(1)).andExpect(jsonPath("$.driver.phone").value(nullValue()));
		mvc.perform(get("/api/trips/summary").header("Authorization", driver.bearer()))
			.andExpect(jsonPath("$.pendingRequests").value(1));

		mvc.perform(post("/api/bookings/" + bookingId + "/accept").header("Authorization", second.bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(post("/api/bookings/" + bookingId + "/accept").header("Authorization", driver.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CONFIRMED"));
		mvc.perform(post("/api/bookings/" + bookingId + "/decline").header("Authorization", driver.bearer()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("booking_not_pending"));
		ride(first, rideId).andExpect(jsonPath("$.driver.phone").value("+919100000011"));

		String other = book(second, rideId, 1, 2, 1).andReturn().getResponse().getContentAsString();
		mvc.perform(post("/api/bookings/" + JsonPath.read(other, "$.id") + "/decline").header("Authorization", driver.bearer()))
			.andExpect(jsonPath("$.status").value("DECLINED"));
		book(second, rideId, 1, 2, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("request_declined"));

		mvc.perform(post("/api/bookings/" + bookingId + "/cancel").header("Authorization", second.bearer()))
			.andExpect(status().isNotFound());
		mvc.perform(post("/api/bookings/" + bookingId + "/cancel").header("Authorization", first.bearer()))
			.andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancelledBy").value("PASSENGER"));
		ride(driver, rideId).andExpect(jsonPath("$.seatsLeftByLeg[0]").value(2)).andExpect(jsonPath("$.passengers", hasSize(0)));
		// Someone who cancelled can book again.
		book(first, rideId, 0, 2, 1).andExpect(status().isCreated());
	}

	@Test
	void seatsAreCountedPerStretch() throws Exception {
		LoggedIn driver = member("9100000014", "Rahul", "MALE");
		String rideId = publishDelhiJaipur(driver, 1, true);
		book(member("9100000015", "Asha", "FEMALE"), rideId, 0, 1, 1).andExpect(status().isCreated());
		book(member("9100000016", "Ben", "MALE"), rideId, 1, 2, 1).andExpect(status().isCreated());
		book(member("9100000017", "Chitra", "FEMALE"), rideId, 0, 2, 1)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("This ride is full for your stretch."));
		search(null, "fromId=" + DELHI + "&toId=" + JAIPUR + "&date=" + inDays(1)).andExpect(jsonPath("$.rides", hasSize(0)));
	}

	@Test
	void twoPassengersCannotBothGetTheLastSeat() throws Exception {
		LoggedIn driver = member("9100000018", "Rahul", "MALE");
		String rideId = publishDelhiJaipur(driver, 1, true);
		List<LoggedIn> passengers = List.of(member("9100000019", "Asha", "FEMALE"), member("9100000020", "Ben", "MALE"),
				member("9100000021", "Chitra", "FEMALE"));
		ExecutorService pool = Executors.newFixedThreadPool(passengers.size());
		CountDownLatch go = new CountDownLatch(1);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (LoggedIn p : passengers) {
				results.add(pool.submit(() -> {
					go.await();
					return book(p, rideId, 0, 2, 1).andReturn().getResponse().getStatus();
				}));
			}
			go.countDown();
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> f : results) {
				statuses.add(f.get());
			}
			assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409);
		}
		finally {
			pool.shutdownNow();
		}
		ride(driver, rideId).andExpect(jsonPath("$.seatsLeftByLeg[0]").value(0)).andExpect(jsonPath("$.passengers", hasSize(1)));
	}

	@Test
	void whenTheDriverCancelsEveryoneOnTheRideIsCancelledToo() throws Exception {
		LoggedIn driver = member("9100000022", "Rahul", "MALE");
		LoggedIn passenger = member("9100000023", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		book(passenger, rideId, 0, 2, 1).andExpect(status().isCreated());

		mvc.perform(post("/api/rides/" + rideId + "/cancel").header("Authorization", passenger.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
			.andExpect(status().isNotFound());
		mvc.perform(post("/api/rides/" + rideId + "/cancel").header("Authorization", driver.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"My car broke down, sorry!\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
		ride(passenger, rideId)
			.andExpect(jsonPath("$.cancelReason").value("My car broke down, sorry!"))
			.andExpect(jsonPath("$.myBooking.status").value("CANCELLED"))
			.andExpect(jsonPath("$.myBooking.cancelledBy").value("DRIVER"))
			.andExpect(jsonPath("$.driver.phone").value(nullValue()));
		search(null, "fromId=" + DELHI + "&toId=" + JAIPUR + "&date=" + inDays(1)).andExpect(jsonPath("$.rides", hasSize(0)));
		mvc.perform(post("/api/rides/" + rideId + "/cancel").header("Authorization", driver.bearer()))
			.andExpect(status().isConflict());
	}

	@Test
	void myRidesShowsBothSides() throws Exception {
		LoggedIn driver = member("9100000024", "Rahul", "MALE");
		LoggedIn passenger = member("9100000025", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, false);
		book(passenger, rideId, 1, 2, 2).andExpect(status().isCreated());

		mvc.perform(get("/api/trips").header("Authorization", driver.bearer()))
			.andExpect(jsonPath("$.driving", hasSize(1)))
			.andExpect(jsonPath("$.driving[0].ride.fromName").value("Delhi"))
			.andExpect(jsonPath("$.driving[0].ride.via[0]").value("Gurugram"))
			.andExpect(jsonPath("$.driving[0].pendingRequests").value(1))
			.andExpect(jsonPath("$.riding", hasSize(0)));
		mvc.perform(get("/api/trips").header("Authorization", passenger.bearer()))
			.andExpect(jsonPath("$.riding", hasSize(1)))
			.andExpect(jsonPath("$.riding[0].booking.status").value("REQUESTED"))
			.andExpect(jsonPath("$.riding[0].booking.fromName").value("Gurugram"))
			.andExpect(jsonPath("$.riding[0].driver.displayName").value("Rahul"));
		mvc.perform(get("/api/trips/summary").header("Authorization", passenger.bearer()))
			.andExpect(jsonPath("$.pendingRequests").value(0))
			.andExpect(jsonPath("$.upcomingTrips").value(1));
		mvc.perform(get("/api/trips?when=past").header("Authorization", passenger.bearer()))
			.andExpect(jsonPath("$.riding", hasSize(0)));
		mvc.perform(get("/api/trips")).andExpect(status().isUnauthorized());
	}

	@Test
	void housekeepingExpiresUnansweredRequestsAndCompletesFinishedRides() throws Exception {
		LoggedIn driver = member("9100000026", "Rahul", "MALE");
		LoggedIn waiting = member("9100000027", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, false);
		String request = book(waiting, rideId, 0, 2, 1).andReturn().getResponse().getContentAsString();

		jdbc.update("UPDATE bookings SET created_at = now() - interval '13 hours' WHERE id = ?::uuid", (String) JsonPath.read(request, "$.id"));
		housekeeping.run();
		ride(waiting, rideId).andExpect(jsonPath("$.myBooking.status").value("EXPIRED")).andExpect(jsonPath("$.seatsLeftByLeg[0]").value(3));

		LoggedIn rider = member("9100000028", "Asha", "FEMALE");
		String instantRide = publishRaw(driver, rideJson(addCar(driver, 4), inDays(3), "07:30", 3, 600, true, false, DELHI, JAIPUR))
			.andReturn().getResponse().getContentAsString();
		String instantId = JsonPath.read(instantRide, "$.id");
		book(rider, instantId, 0, 1, 1).andExpect(status().isCreated());
		jdbc.update("UPDATE rides SET departure_at = now() - interval '12 hours' WHERE id = ?::uuid", instantId);
		housekeeping.run();
		ride(rider, instantId).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.myBooking.status").value("COMPLETED"));
		mvc.perform(get("/api/trips?when=past").header("Authorization", rider.bearer()))
			.andExpect(jsonPath("$.riding[0].ride.id").value(instantId));
	}

	@Test
	void switchesAndRestrictionsApply() throws Exception {
		Staff owner = superAdmin();
		LoggedIn driver = member("9100000029", "Rahul", "MALE");
		LoggedIn passenger = member("9100000030", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, true);

		// Staff keep one member out of booking: they can still see rides, but not search or book.
		mvc.perform(post("/api/admin/members/" + passenger.userId() + "/restrictions").header("Authorization", owner.console())
				.contentType(MediaType.APPLICATION_JSON).content("{\"area\":\"rides.booking\",\"reason\":\"No-shows\"}"))
			.andExpect(status().isOk());
		book(passenger, rideId, 0, 2, 1).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("feature_unavailable"));
		ride(passenger, rideId).andExpect(status().isOk());

		// The owner pauses offering rides for everyone; booking still works for others.
		mvc.perform(put("/api/admin/features/rides.publish").header("Authorization", owner.console())
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
			.andExpect(status().isOk());
		publishRaw(driver, rideJson(addCarExpectingForbidden(driver), inDays(5), "07:30", 3, 600, true, false, DELHI, JAIPUR))
			.andExpect(status().isForbidden());
		book(member("9100000031", "Asha", "FEMALE"), rideId, 0, 2, 1).andExpect(status().isCreated());
		// Drivers can still cancel rides they already offered.
		mvc.perform(post("/api/rides/" + rideId + "/cancel").header("Authorization", driver.bearer()))
			.andExpect(status().isOk());
	}

	private String addCarExpectingForbidden(LoggedIn driver) throws Exception {
		mvc.perform(get("/api/vehicles").header("Authorization", driver.bearer())).andExpect(status().isForbidden());
		return "00000000-0000-0000-0000-000000000000";
	}
}
