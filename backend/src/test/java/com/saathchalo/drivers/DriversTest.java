package com.saathchalo.drivers;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.payments.Settlement;
import com.saathchalo.rides.RideTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class DriversTest extends RideTestSupport {

	private static final String PROFILE = """
			{"licenceNumber":"mh-14 2011 0062821","licenceClass":"LMV","licenceValidUntil":"2035-01-01","experienceYears":8,
			 "languages":["hindi","English"],"manual":true,"automatic":%b,"outstation":true,"hourPrice":150,"dayPrice":1000,
			 "outstationPrice":1500,"allowance":300,"nightCharge":200,"about":"Calm, careful driver. Knows Gurugram well."}""";

	@Autowired
	Settlement settlement;

	@Autowired
	DriverHousekeeping housekeeping;

	@BeforeEach
	void driversOn() {
		switchOn("drivers", true);
	}

	@Test
	void driversSignUpAndStaffCheckThem() throws Exception {
		LoggedIn ravi = member("9300000001", "Ravi", "MALE");
		LoggedIn priya = member("9300000002", "Priya", "FEMALE");
		Staff admin = staff("9300000010", "ADMIN");

		mvc.perform(get("/api/drivers/me").header("Authorization", ravi.bearer())).andExpect(status().isNoContent());
		saveProfile(ravi, true).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.licenceNumberMasked").value("•••• 2821"))
			.andExpect(jsonPath("$.languages[1]").value("English"))
			.andExpect(jsonPath("$.licenceStatus").value("NONE"));

		// Each missing step is explained.
		send(ravi, "/api/drivers/me/submit").andExpect(jsonPath("$.code").value("licence_not_checked"));
		licence(ravi, "PENDING");
		send(ravi, "/api/drivers/me/submit").andExpect(jsonPath("$.code").value("areas_missing"));
		area(ravi, GURUGRAM, 25).andExpect(jsonPath("$.areas", hasSize(1)));
		send(ravi, "/api/drivers/me/submit").andExpect(jsonPath("$.code").value("photo_missing"));
		jdbc.update("UPDATE users SET avatar_path = 'ravi.jpg' WHERE id = ?::uuid", ravi.userId());
		send(ravi, "/api/drivers/me/submit").andExpect(jsonPath("$.status").value("IN_REVIEW"));

		// Staff can't approve before the licence itself is checked; then they can.
		mvc.perform(get("/api/admin/drivers").header("Authorization", admin.console()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].licenceStatus").value("PENDING"));
		mvc.perform(post("/api/admin/drivers/" + ravi.userId() + "/approve").header("Authorization", admin.console()))
			.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("licence_not_checked"));
		jdbc.update("UPDATE id_checks SET status = 'APPROVED', decided_at = now() WHERE user_id = ?::uuid", ravi.userId());
		mvc.perform(post("/api/admin/drivers/" + ravi.userId() + "/licence-number").header("Authorization", admin.console()))
			.andExpect(jsonPath("$.number").value("MH1420110062821"));
		mvc.perform(post("/api/admin/drivers/" + ravi.userId() + "/approve").header("Authorization", admin.console()))
			.andExpect(jsonPath("$.status").value("LIVE"));
		mvc.perform(get("/api/admin/drivers").header("Authorization", priya.bearer())).andExpect(status().isForbidden());

		// Members near Gurugram find him, priced for their trip; not for automatic cars he doesn't drive.
		Instant from = tomorrowAt(9);
		search(priya, from, false).andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].member.displayName").value("Ravi"))
			.andExpect(jsonPath("$[0].fare").value(600))
			.andExpect(jsonPath("$[0].distanceKm").value(closeTo(0.0, 1.0)));
		saveProfile(ravi, false);
		search(priya, from, true).andExpect(jsonPath("$", hasSize(0)));
		// Time off hides him too.
		mvc.perform(post("/api/drivers/me/blocks").header("Authorization", ravi.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"from\":\"%s\",\"to\":\"%s\",\"note\":\"Wedding\"}".formatted(from.minus(Duration.ofHours(1)), from.plus(Duration.ofHours(10)))))
			.andExpect(jsonPath("$.blocks", hasSize(1)));
		search(priya, from, false).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void hiringADriverFromRequestToRatings() throws Exception {
		LoggedIn ravi = liveDriver("9300000001", "Ravi", staff("9300000010", "ADMIN"));
		LoggedIn priya = member("9300000002", "Priya", "FEMALE");
		LoggedIn asha = member("9300000003", "Asha", "FEMALE");
		Instant from = tomorrowAt(9);

		mvc.perform(get("/api/drivers/profiles/" + ravi.userId() + "/quote").param("placeId", String.valueOf(GURUGRAM))
				.param("kind", "LOCAL").param("from", from.toString()).param("hours", "6"))
			.andExpect(jsonPath("$.available").value(true))
			.andExpect(jsonPath("$.fare").value(900));
		mvc.perform(get("/api/drivers/profiles/" + ravi.userId() + "/quote").param("placeId", String.valueOf(MUMBAI))
				.param("kind", "LOCAL").param("from", from.toString()).param("hours", "6"))
			.andExpect(jsonPath("$.available").value(false))
			.andExpect(jsonPath("$.problem").value("This driver doesn't work in Mumbai."));

		String trip = JsonPath.read(hire(priya, ravi, from, 6).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REQUESTED"))
			.andExpect(jsonPath("$.fare").value(900))
			.andExpect(jsonPath("$.startCode").doesNotExist())
			.andReturn().getResponse().getContentAsString(), "$.id");
		String other = JsonPath.read(hire(asha, ravi, from.plus(Duration.ofHours(2)), 4).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(get("/api/drivers/trips/" + trip).header("Authorization", ravi.bearer()))
			.andExpect(jsonPath("$.pickupAddress").doesNotExist());

		// Accepting one makes the overlapping one impossible.
		send(ravi, "/api/drivers/trips/" + trip + "/accept").andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.otherPhone").value("+919300000002"))
			.andExpect(jsonPath("$.pickupAddress").value("Tower 4, DLF Phase 2"));
		send(ravi, "/api/drivers/trips/" + other + "/accept").andExpect(status().isConflict());
		String code = JsonPath.read(mvc.perform(get("/api/drivers/trips/" + trip).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.startCode").value(matchesPattern("\\d{4}")))
			.andReturn().getResponse().getContentAsString(), "$.startCode");

		// The driver sets off, and starts with the code the member tells him (wrong tries are counted).
		send(ravi, "/api/drivers/trips/" + trip + "/on-the-way").andExpect(jsonPath("$.code").value("too_early"));
		moveTrip(trip, 120, 6);
		send(ravi, "/api/drivers/trips/" + trip + "/on-the-way").andExpect(jsonPath("$.status").value("ON_THE_WAY"));
		start(ravi, trip, code).andExpect(jsonPath("$.code").value("too_early"));
		moveTrip(trip, 20, 6);
		start(ravi, trip, "0000".equals(code) ? "1111" : "0000").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("wrong_code"));
		mvc.perform(get("/api/drivers/trips/" + trip).header("Authorization", ravi.bearer())).andExpect(jsonPath("$.wrongCodes").value(1));
		start(ravi, trip, code).andExpect(jsonPath("$.status").value("STARTED"))
			.andExpect(jsonPath("$.startCode").doesNotExist());
		send(priya, "/api/drivers/trips/" + trip + "/end").andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.extraUnits").value(0))
			.andExpect(jsonPath("$.canReview").value(true));

		// Both sides rate each other, once.
		rate(priya, trip, 5).andExpect(jsonPath("$.myStars").value(5)).andExpect(jsonPath("$.canReview").value(false));
		rate(priya, trip, 4).andExpect(jsonPath("$.code").value("already_rated"));
		rate(ravi, trip, 4).andExpect(jsonPath("$.customerRating").value(4.0));
		mvc.perform(get("/api/drivers/profiles/" + ravi.userId()))
			.andExpect(jsonPath("$.driver.rating").value(5.0))
			.andExpect(jsonPath("$.driver.tripsDone").value(1))
			.andExpect(jsonPath("$.reviews[0].authorName").value("Priya"));
		mvc.perform(get("/api/drivers/trips").header("Authorization", ravi.bearer()))
			.andExpect(jsonPath("$.driving", hasSize(2)))
			.andExpect(jsonPath("$.hired", hasSize(0)));
	}

	@Test
	void payingOnlineAndCancelling() throws Exception {
		switchOn("payments.online", true);
		Staff boss = superAdmin();
		LoggedIn ravi = liveDriver("9300000001", "Ravi", boss);
		LoggedIn priya = member("9300000002", "Priya", "FEMALE");

		// Paid online once the driver accepts; a late cancellation after he set off pays him half.
		String trip = JsonPath.read(hire(priya, ravi, tomorrowAt(9), 4).andExpect(jsonPath("$.payOnline").value(true))
			.andReturn().getResponse().getContentAsString(), "$.id");
		send(ravi, "/api/drivers/trips/" + trip + "/accept").andExpect(jsonPath("$.status").value("ACCEPTED"));
		send(ravi, "/api/drivers/trips/" + trip + "/on-the-way").andExpect(jsonPath("$.code").value("not_ready"));
		payFor(priya, trip, 60000);
		moveTrip(trip, 120, 4);
		send(ravi, "/api/drivers/trips/" + trip + "/on-the-way").andExpect(jsonPath("$.status").value("ON_THE_WAY"));
		send(priya, "/api/drivers/trips/" + trip + "/cancel").andExpect(jsonPath("$.status").value("CANCELLED"));
		settlement.run();
		mvc.perform(get("/api/payments/bookings").param("ids", trip).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$[0].outcome").value("SPLIT"))
			.andExpect(jsonPath("$[0].refunds[0].onlinePaise").value(30000));
		mvc.perform(get("/api/wallet").header("Authorization", ravi.bearer())).andExpect(jsonPath("$.earningsPaise").value(30000));

		// A driver who cancels must say why, and the member gets everything back.
		String second = JsonPath.read(hire(priya, ravi, tomorrowAt(9).plus(Duration.ofDays(1)), 4)
			.andReturn().getResponse().getContentAsString(), "$.id");
		send(ravi, "/api/drivers/trips/" + second + "/accept");
		payFor(priya, second, 60000);
		send(ravi, "/api/drivers/trips/" + second + "/cancel").andExpect(jsonPath("$.code").value("reason_missing"));
		mvc.perform(post("/api/drivers/trips/" + second + "/cancel").header("Authorization", ravi.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"My father is unwell\"}"))
			.andExpect(jsonPath("$.cancelledBy").value("DRIVER"));
		settlement.run();
		mvc.perform(get("/api/payments/bookings").param("ids", second).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$[0].outcome").value("REFUNDED"));
		mvc.perform(get("/api/admin/payments/summary").header("Authorization", boss.console()))
			.andExpect(jsonPath("$.ledgerBalanced").value(true))
			.andExpect(jsonPath("$.escrowMatches").value(true));

		// A request the driver doesn't answer in time lapses.
		String unanswered = JsonPath.read(hire(priya, ravi, tomorrowAt(9).plus(Duration.ofDays(2)), 4)
			.andReturn().getResponse().getContentAsString(), "$.id");
		jdbc.update("UPDATE driver_trips SET answer_by = now() - interval '1 minute' WHERE id = ?::uuid", unanswered);
		housekeeping.run();
		mvc.perform(get("/api/drivers/trips/" + unanswered).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.status").value("EXPIRED"));
	}

	// ----- Helpers -----

	private void switchOn(String key, boolean on) {
		jdbc.update("UPDATE feature_flags SET enabled = ? WHERE key = ?", on, key);
		featureService.reload();
	}

	/** A daytime start (no night charge), whatever time the tests run. */
	private static Instant tomorrowAt(int hour) {
		return LocalDate.now(DriverRules.INDIA).plusDays(1).atTime(hour, 0).atZone(DriverRules.INDIA).toInstant();
	}

	/** Brings a booked trip close, to try the steps around its start. */
	private void moveTrip(String trip, int minutesFromNow, int hours) {
		jdbc.update("""
				UPDATE driver_trips SET starts_at = now() + make_interval(mins => ?), ends_at = now() + make_interval(mins => ?)
				WHERE id = ?::uuid""", minutesFromNow, minutesFromNow + hours * 60, trip);
	}

	private ResultActions saveProfile(LoggedIn who, boolean automatic) throws Exception {
		return mvc.perform(put("/api/drivers/me").header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content(PROFILE.formatted(automatic)));
	}

	private ResultActions area(LoggedIn who, long placeId, int km) throws Exception {
		return mvc.perform(post("/api/drivers/me/areas").header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"placeId\":%d,\"radiusKm\":%d}".formatted(placeId, km)));
	}

	private LoggedIn liveDriver(String phone, String name, Staff checker) throws Exception {
		LoggedIn who = member(phone, name, "MALE");
		saveProfile(who, true).andExpect(status().isOk());
		licence(who, "APPROVED");
		area(who, GURUGRAM, 25);
		jdbc.update("UPDATE users SET avatar_path = 'driver.jpg' WHERE id = ?::uuid", who.userId());
		send(who, "/api/drivers/me/submit").andExpect(jsonPath("$.status").value("IN_REVIEW"));
		mvc.perform(post("/api/admin/drivers/" + who.userId() + "/approve").header("Authorization", checker.console()))
			.andExpect(jsonPath("$.status").value("LIVE"));
		return who;
	}

	private ResultActions search(LoggedIn who, Instant from, boolean automatic) throws Exception {
		return mvc.perform(get("/api/drivers/search").param("placeId", String.valueOf(GURUGRAM)).param("kind", "LOCAL")
			.param("from", from.toString()).param("hours", "4").param("automatic", String.valueOf(automatic))
			.header("Authorization", who.bearer()));
	}

	private ResultActions hire(LoggedIn who, LoggedIn driver, Instant from, int hours) throws Exception {
		return mvc.perform(post("/api/drivers/trips").header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"driverId":"%s","kind":"LOCAL","from":"%s","hours":%d,"placeId":%d,"pickupAddress":"Tower 4, DLF Phase 2",
					 "car":"White Maruti Swift","automatic":false,"message":"Airport drop and back"}"""
				.formatted(driver.userId(), from, hours, GURUGRAM)));
	}

	private ResultActions start(LoggedIn who, String trip, String code) throws Exception {
		return mvc.perform(post("/api/drivers/trips/" + trip + "/start").header("Authorization", who.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
	}

	private ResultActions rate(LoggedIn who, String trip, int stars) throws Exception {
		return mvc.perform(post("/api/drivers/trips/" + trip + "/review").header("Authorization", who.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"stars\":%d,\"comment\":\"On time and polite\"}".formatted(stars)));
	}

	private void payFor(LoggedIn who, String trip, long expectedPaise) throws Exception {
		String checkout = send(who, "/api/drivers/trips/" + trip + "/pay")
			.andExpect(jsonPath("$.kind").value("DRIVER"))
			.andExpect(jsonPath("$.onlinePaise").value(expectedPaise))
			.andReturn().getResponse().getContentAsString();
		String paymentId = JsonPath.read(checkout, "$.paymentId");
		String answer = send(who, "/api/payments/" + paymentId + "/pretend-pay").andReturn().getResponse().getContentAsString();
		mvc.perform(post("/api/payments/" + paymentId + "/confirm").header("Authorization", who.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(answer))
			.andExpect(jsonPath("$.status").value("PAID"));
		mvc.perform(get("/api/drivers/trips/" + trip).header("Authorization", who.bearer())).andExpect(jsonPath("$.status").value("PAID"));
	}

	private ResultActions send(LoggedIn who, String path) throws Exception {
		return mvc.perform(post(path).header("Authorization", who.bearer()));
	}

	private void licence(LoggedIn who, String status) {
		jdbc.update("""
				INSERT INTO id_checks (id, user_id, document_type, status, submitted_at, decided_at)
				VALUES (gen_random_uuid(), ?::uuid, 'DRIVING_LICENCE', ?, now(), CASE WHEN ? = 'APPROVED' THEN now() END)""",
				who.userId(), status, status);
	}
}
