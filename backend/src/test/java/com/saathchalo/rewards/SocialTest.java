package com.saathchalo.rewards;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.rides.RideTestSupport;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class SocialTest extends RideTestSupport {

	@Autowired
	RewardHousekeeping rewardJobs;

	@BeforeEach
	void switchesOn() {
		jdbc.update("UPDATE feature_flags SET enabled = TRUE WHERE key IN ('rewards', 'community', 'vibe_match')");
		featureService.reload();
	}

	@Test
	void ridesEarnRewardsAndInvitesEarnCredits() throws Exception {
		LoggedIn ravi = member("9500000001", "Ravi", "MALE");
		LoggedIn priya = member("9500000002", "Priya", "FEMALE");
		LoggedIn asha = member("9500000003", "Asha", "FEMALE");

		mvc.perform(get("/api/rewards/me").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.xp").value(0))
			.andExpect(jsonPath("$.levelName").value("New rider"))
			.andExpect(jsonPath("$.referral.canClaim").value(true));
		String code = JsonPath.read(mvc.perform(get("/api/rewards/me").header("Authorization", ravi.bearer()))
			.andExpect(jsonPath("$.referral.code").value(org.hamcrest.Matchers.startsWith("RAVI")))
			.andReturn().getResponse().getContentAsString(), "$.referral.code");

		// Priya joins with Ravi's code; codes are used once, and never your own.
		send(priya, "/api/rewards/claim", "{\"code\":\"" + code.toLowerCase() + "\"}")
			.andExpect(jsonPath("$.referral.invitedByFriend").value(true))
			.andExpect(jsonPath("$.referral.status").value("PENDING"));
		send(priya, "/api/rewards/claim", "{\"code\":\"" + code + "\"}").andExpect(jsonPath("$.code").value("already_invited"));
		send(ravi, "/api/rewards/claim", "{\"code\":\"" + code + "\"}").andExpect(jsonPath("$.code").value("own_code"));

		// Nothing is paid until Priya completes a ride with someone other than Ravi: here, Asha drives her.
		rewardJobs.payReferrals();
		mvc.perform(get("/api/wallet").header("Authorization", priya.bearer())).andExpect(jsonPath("$.creditsPaise").value(0));
		completedRide(asha, priya);
		mvc.perform(get("/api/rewards/me").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.impact.trips").value(1))
			.andExpect(jsonPath("$.impact.co2Kg").value(greaterThan(10.0)))
			.andExpect(jsonPath("$.xp").value(55))
			.andExpect(jsonPath("$.challenge.done").value(1))
			.andExpect(jsonPath("$.badges[0].earned").value(true));
		rewardJobs.payReferrals();
		mvc.perform(get("/api/wallet").header("Authorization", priya.bearer())).andExpect(jsonPath("$.creditsPaise").value(5000));
		mvc.perform(get("/api/wallet").header("Authorization", ravi.bearer())).andExpect(jsonPath("$.creditsPaise").value(5000));
		mvc.perform(get("/api/rewards/me").header("Authorization", ravi.bearer()))
			.andExpect(jsonPath("$.referral.rewarded").value(1))
			.andExpect(jsonPath("$.xp").value(100));

		// The eco leaderboard shows nicknames of members who chose it; no phone numbers as nicknames.
		mvc.perform(put("/api/rewards/board").header("Authorization", priya.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"on\":true,\"nickname\":\"call 98110 00002\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(put("/api/rewards/board").header("Authorization", priya.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"on\":true,\"nickname\":\"Eco Priya\"}"))
			.andExpect(jsonPath("$.board.on").value(true));
		mvc.perform(get("/api/rewards/leaderboard").header("Authorization", asha.bearer()))
			.andExpect(jsonPath("$.top", hasSize(1)))
			.andExpect(jsonPath("$.top[0].nickname").value("Eco Priya"))
			.andExpect(jsonPath("$.me").doesNotExist());
	}

	@Test
	void squadsEventsAndVibeMatch() throws Exception {
		LoggedIn ravi = member("9500000001", "Ravi", "MALE");
		LoggedIn priya = member("9500000002", "Priya", "FEMALE");

		// A college squad needs a confirmed email at the college.
		send(ravi, "/api/community/squads", "{\"name\":\"IIT Delhi riders\",\"kind\":\"COLLEGE\",\"emailDomain\":\"@IITD.ac.in\"}")
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("email_domain"));
		send(ravi, "/api/community/squads", "{\"name\":\"Gmail gang\",\"kind\":\"FRIENDS\",\"emailDomain\":\"gmail.com\"}")
			.andExpect(jsonPath("$.code").value("public_domain"));
		verifiedEmail(ravi, "ravi@iitd.ac.in");
		String squad = send(ravi, "/api/community/squads", "{\"name\":\"IIT Delhi riders\",\"kind\":\"COLLEGE\",\"emailDomain\":\"iitd.ac.in\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.squad.emailDomain").value("iitd.ac.in"))
			.andReturn().getResponse().getContentAsString();
		String code = JsonPath.read(squad, "$.squad.code");
		String squadId = JsonPath.read(squad, "$.squad.id");
		send(priya, "/api/community/squads/join", "{\"code\":\"" + code + "\"}").andExpect(status().isForbidden());
		verifiedEmail(priya, "priya@cse.iitd.ac.in");
		send(priya, "/api/community/squads/join", "{\"code\":\"" + code + "\"}").andExpect(jsonPath("$.members", hasSize(2)));

		// Members see each other's upcoming rides.
		String ride = publishDelhiJaipur(ravi, 3, true);
		mvc.perform(get("/api/community/squads/" + squadId).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.rides[0].id").value(ride))
			.andExpect(jsonPath("$.rides[0].driver.displayName").value("Ravi"));

		// An event in Jaipur tomorrow evening lists the rides going there.
		Staff admin = staff("9500000010", "ADMIN");
		String starts = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1).atTime(18, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toString();
		String event = JsonPath.read(mvc.perform(post("/api/admin/events").header("Authorization", admin.console())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Jaipur Lit Fest\",\"venue\":\"Diggi Palace\",\"placeId\":%d,\"startsAt\":\"%s\"}".formatted(JAIPUR, starts)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/api/admin/events").header("Authorization", priya.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"x\",\"venue\":\"y\",\"placeId\":%d,\"startsAt\":\"%s\"}".formatted(JAIPUR, starts)))
			.andExpect(status().isForbidden());
		mvc.perform(get("/api/community/events").header("Authorization", priya.bearer())).andExpect(jsonPath("$[0].title").value("Jaipur Lit Fest"));
		mvc.perform(get("/api/community/events/" + event).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.rides", hasSize(1)))
			.andExpect(jsonPath("$.rides[0].toName").value("Jaipur"));

		// Vibe match: same habits and two shared interests.
		mvc.perform(put("/api/community/vibe/me").header("Authorization", priya.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"interests\":[\"Cricket\",\"Films\",\"Not a real interest\"]}"))
			.andExpect(jsonPath("$.interests", hasSize(2)));
		mvc.perform(put("/api/community/vibe/me").header("Authorization", ravi.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"interests\":[\"Cricket\",\"Films\",\"Food\"]}"));
		mvc.perform(get("/api/community/vibe").param("ids", ravi.userId()).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$['" + ravi.userId() + "']").value(99));

		send(ravi, "/api/community/squads/" + squadId + "/leave", null).andExpect(status().isNoContent());
		mvc.perform(get("/api/community/squads").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$[0].owner").value(true))
			.andExpect(jsonPath("$[0].members").value(1));
	}

	// ----- Helpers -----

	/** A ride from Delhi to Jaipur that already happened, with this passenger on it. */
	private void completedRide(LoggedIn driver, LoggedIn passenger) throws Exception {
		String ride = publishDelhiJaipur(driver, 3, true);
		book(passenger, ride, 0, 2, 1).andExpect(status().isCreated());
		jdbc.update("UPDATE rides SET status = 'COMPLETED', departure_at = now() - interval '2 days' WHERE id = ?::uuid", ride);
		jdbc.update("UPDATE bookings SET status = 'COMPLETED' WHERE ride_id = ?::uuid", ride);
	}

	private void verifiedEmail(LoggedIn who, String email) {
		jdbc.update("UPDATE users SET email = ?, email_verified = TRUE WHERE id = ?::uuid", email, who.userId());
	}

	private ResultActions send(LoggedIn who, String path, String json) throws Exception {
		var request = post(path).header("Authorization", who.bearer());
		if (json != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mvc.perform(request);
	}
}
