package com.saathchalo.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.rides.RideHousekeeping;
import com.saathchalo.rides.RideTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class PrivacyTest extends RideTestSupport {

	@Autowired
	AccountErasure erasure;

	@Autowired
	RideHousekeeping rideHousekeeping;

	@Test
	void membersDownloadEverythingWeHoldAboutThem() throws Exception {
		LoggedIn priya = member("9800000001", "Priya", "FEMALE");
		addCar(priya, 3);
		mvc.perform(post("/api/safety/contacts").header("Authorization", priya.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Amma\",\"phone\":\"98765 43210\"}"))
			.andExpect(status().isCreated());

		String file = mvc.perform(get("/api/privacy/export").header("Authorization", priya.bearer()))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", containsString("saathchalo-my-data-")))
			.andExpect(jsonPath("$.about.service").value("SaathChalo"))
			.andExpect(jsonPath("$.profile.phone").value("+919800000001"))
			.andExpect(jsonPath("$.profile.firstName").value("Priya"))
			.andExpect(jsonPath("$.logins", hasSize(1)))
			.andExpect(jsonPath("$.cars[0].numberPlate").value("MH 12 AB 1234"))
			.andExpect(jsonPath("$.safety.emergencyContacts[0].phone").value("+919876543210"))
			.andExpect(jsonPath("$.rides.offered").isArray())
			.andExpect(jsonPath("$.reviews.received").isArray())
			.andExpect(jsonPath("$.helpRequests").isArray())
			.andReturn().getResponse().getContentAsString();
		// Only readable values: no token hashes and no encrypted columns.
		assertThat(file).doesNotContain("token_hash", "plate_enc", "phone_enc", "_enc\"");

		mvc.perform(get("/api/privacy/export")).andExpect(status().isUnauthorized());
	}

	@Test
	void nobodyIsLeftStrandedAndLoggingInAgainCancelsTheDeletion() throws Exception {
		LoggedIn driver = member("9800000001", "Ravi", "MALE");
		LoggedIn priya = member("9800000002", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		String booking = JsonPath.read(book(priya, rideId, 0, 2, 1).andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id");

		deleteAccount(driver, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("upcoming_trips"))
			.andExpect(jsonPath("$.ridesWithPassengers").value(1))
			.andExpect(jsonPath("$.detail", containsString("1 ride you're driving has confirmed passengers")));
		deleteAccount(priya, null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.confirmedSeats").value(1));

		// Once Priya cancels her seat she can leave; the driver can too, and his empty ride is cancelled.
		mvc.perform(post("/api/bookings/" + booking + "/cancel").header("Authorization", priya.bearer())).andExpect(status().isOk());
		deleteAccount(priya, "NOT_USING").andExpect(status().isOk()).andExpect(jsonPath("$.deleteOn").exists());
		deleteAccount(driver, null).andExpect(status().isOk());
		ride(null, rideId).andExpect(jsonPath("$.status").value("CANCELLED"))
			.andExpect(jsonPath("$.cancelReason").value("The driver has left SaathChalo."));

		// Logged out everywhere at once, and hidden from other members.
		mvc.perform(get("/api/users/me").header("Authorization", priya.bearer()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("session_ended"));
		mvc.perform(post("/api/auth/refresh").cookie(priya.refreshCookie())).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/users/" + priya.userId())).andExpect(status().isNotFound());
		mvc.perform(get("/api/admin/audit").header("Authorization", superAdmin().console()))
			.andExpect(jsonPath("$.items[?(@.action == 'member.deletion_requested' && @.targetId == '" + priya.userId() + "')]",
					hasSize(1)))
			.andExpect(jsonPath("$.items[?(@.targetId == '" + priya.userId() + "')].details", hasSize(1)));

		// Logging in again within the 30 days cancels the deletion.
		jdbc.update("UPDATE otp_challenges SET created_at = created_at - interval '1 minute'");
		MvcResult again = verify("9800000002", requestCode("9800000002"));
		String body = again.getResponse().getContentAsString();
		assertThat((String) JsonPath.read(body, "$.user.id")).isEqualTo(priya.userId());
		assertThat((Boolean) JsonPath.read(body, "$.accountRestored")).isTrue();
		String token = JsonPath.read(body, "$.accessToken");
		mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
		mvc.perform(get("/api/users/" + priya.userId())).andExpect(status().isOk());
		assertThat(erasure.eraseDueAccounts()).isZero();
	}

	@Test
	void afterThirtyDaysTheAccountIsErased() throws Exception {
		LoggedIn driver = member("9800000001", "Ravi", "MALE");
		LoggedIn priya = member("9800000002", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		book(priya, rideId, 0, 2, 1).andExpect(status().isCreated());
		String chat = JsonPath.read(mvc.perform(post("/api/chats").header("Authorization", priya.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"rideId\":\"" + rideId + "\"}"))
			.andReturn().getResponse().getContentAsString(), "$.id");
		send(priya, chat, "Hi Ravi, I'm near the metro.");
		send(driver, chat, "See you there!");
		jdbc.update("UPDATE rides SET departure_at = now() - interval '12 hours' WHERE id = ?::uuid", rideId);
		rideHousekeeping.run();
		review(priya, rideId, driver.userId(), "Lovely trip").andExpect(status().isCreated());
		review(driver, rideId, priya.userId(), "Great company").andExpect(status().isCreated());
		mvc.perform(post("/api/safety/contacts").header("Authorization", priya.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Amma\",\"phone\":\"98765 43210\"}"))
			.andExpect(status().isCreated());

		deleteAccount(priya, null).andExpect(status().isOk());
		assertThat(erasure.eraseDueAccounts()).as("not before the 30 days are over").isZero();
		jdbc.update("UPDATE users SET deletion_due_at = now() - interval '1 minute' WHERE id = ?::uuid", priya.userId());
		assertThat(erasure.eraseDueAccounts()).isEqualTo(1);

		Map<String, Object> row = jdbc.queryForMap("SELECT phone, first_name, email, deleted_at FROM users WHERE id = ?::uuid",
				priya.userId());
		assertThat(row.get("phone")).isNull();
		assertThat(row.get("first_name")).isNull();
		assertThat(row.get("deleted_at")).isNotNull();
		// The sign-up details are sealed (encrypted) for 180 days.
		String sealed = jdbc.queryForObject("SELECT details_enc FROM closed_accounts WHERE user_id = ?::uuid", String.class,
				priya.userId());
		assertThat(sealed).doesNotContain("9800000002").doesNotContain("Priya");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM emergency_contacts WHERE user_id = ?::uuid", Long.class,
				priya.userId())).isZero();

		// The driver's trip history still makes sense, without Priya's name or her words.
		ride(driver, rideId).andExpect(jsonPath("$.passengers[0].member.displayName").value("Former member"))
			.andExpect(jsonPath("$.passengers[0].message").doesNotExist());
		mvc.perform(get("/api/chats/" + chat).header("Authorization", driver.bearer()))
			.andExpect(jsonPath("$.messages", hasSize(1)))
			.andExpect(jsonPath("$.messages[0].body").value("See you there!"));
		// Her review of the driver still counts; the review about her is gone.
		mvc.perform(get("/api/users/" + driver.userId() + "/reviews"))
			.andExpect(jsonPath("$.count").value(1))
			.andExpect(jsonPath("$.items[0].author.displayName").value("Former member"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM reviews WHERE subject_id = ?::uuid", Long.class, priya.userId()))
			.isZero();

		// The number is free again: logging in with it starts a brand-new account.
		jdbc.update("UPDATE otp_challenges SET created_at = created_at - interval '1 minute'");
		LoggedIn fresh = login("9800000002");
		assertThat(fresh.userId()).isNotEqualTo(priya.userId());

		// After 180 days the sealed details go too.
		jdbc.update("UPDATE closed_accounts SET purge_after = now() - interval '1 minute'");
		erasure.run();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM closed_accounts", Long.class)).isZero();
	}

	@Test
	void staffAccountsCantBeDeletedHere() throws Exception {
		Staff support = staff("9800000010", "SUPPORT");
		deleteAccount(support.member(), null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("staff_account"));
	}

	@Test
	void theGrievanceContactIsPublic() throws Exception {
		mvc.perform(get("/api/legal/contacts")).andExpect(status().isOk())
			.andExpect(jsonPath("$.grievanceOfficer").doesNotExist());
	}

	private ResultActions deleteAccount(LoggedIn who, String reason) throws Exception {
		return mvc.perform(post("/api/privacy/delete-account").header("Authorization", who.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}"));
	}

	private void send(LoggedIn who, String chatId, String text) throws Exception {
		mvc.perform(post("/api/chats/" + chatId + "/messages").header("Authorization", who.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + text + "\"}"))
			.andExpect(status().isCreated());
	}

	private ResultActions review(LoggedIn author, String rideId, String subjectId, String comment) throws Exception {
		return mvc.perform(post("/api/reviews").header("Authorization", author.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"rideId\":\"%s\",\"subjectId\":\"%s\",\"rating\":5,\"comment\":\"%s\"}".formatted(rideId, subjectId, comment)));
	}
}
