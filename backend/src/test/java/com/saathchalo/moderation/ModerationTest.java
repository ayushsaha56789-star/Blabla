package com.saathchalo.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.rides.RideHousekeeping;
import com.saathchalo.rides.RideTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ModerationTest extends RideTestSupport {

	@Autowired
	RideHousekeeping housekeeping;

	@Test
	void blockingWorksBothWays() throws Exception {
		LoggedIn rahul = member("9700000001", "Rahul", "MALE");
		LoggedIn priya = member("9700000002", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(rahul, 3, true);
		String chatId = JsonPath.read(openChat(priya, rideId).andReturn().getResponse().getContentAsString(), "$.id");
		sendChat(priya, chatId, "Hello?").andExpect(status().isCreated());

		mvc.perform(post("/api/users/" + priya.userId() + "/block").header("Authorization", rahul.bearer())).andExpect(status().isNoContent());
		mvc.perform(post("/api/users/" + rahul.userId() + "/block").header("Authorization", rahul.bearer()))
			.andExpect(jsonPath("$.code").value("self_block"));
		mvc.perform(get("/api/users/me/blocks").header("Authorization", rahul.bearer()))
			.andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].displayName").value("Priya"));

		// Priya (blocked) can't book, message or even find Rahul's rides — and Rahul can't message her either.
		book(priya, rideId, 0, 2, 1).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("blocked"));
		sendChat(priya, chatId, "Why?").andExpect(status().isConflict());
		sendChat(rahul, chatId, "Bye").andExpect(status().isConflict());
		mvc.perform(get("/api/chats/" + chatId).header("Authorization", rahul.bearer()))
			.andExpect(jsonPath("$.canSend").value(false))
			.andExpect(jsonPath("$.cannotSendReason").value("You can't message each other any more."));
		search(priya, "fromId=" + DELHI + "&toId=" + JAIPUR + "&date=" + inDays(1)).andExpect(jsonPath("$.rides", hasSize(0)));
		search(null, "fromId=" + DELHI + "&toId=" + JAIPUR + "&date=" + inDays(1)).andExpect(jsonPath("$.rides", hasSize(1)));

		mvc.perform(delete("/api/users/" + priya.userId() + "/block").header("Authorization", rahul.bearer())).andExpect(status().isNoContent());
		book(priya, rideId, 0, 2, 1).andExpect(status().isCreated());
	}

	@Test
	void reportsReachTheModerationQueueAndContentCanBeRemoved() throws Exception {
		Staff moderator = staff("9700000010", "MODERATOR");
		Staff support = staff("9700000011", "SUPPORT");
		LoggedIn rahul = member("9700000003", "Rahul", "MALE");
		LoggedIn priya = member("9700000004", "Priya", "FEMALE");
		LoggedIn asha = member("9700000005", "Asha", "FEMALE");
		String rideId = publishDelhiJaipur(rahul, 3, true);
		String chatId = JsonPath.read(openChat(priya, rideId).andReturn().getResponse().getContentAsString(), "$.id");
		sendChat(priya, chatId, "Hi, is the seat free?").andExpect(status().isCreated());
		String rude = JsonPath.read(sendChat(rahul, chatId, "Something rude and threatening").andReturn().getResponse().getContentAsString(), "$.id");

		// Only the other person's messages in your own chats can be reported.
		report(rahul, "MESSAGE", rude, "HARASSMENT", null).andExpect(status().isNotFound());
		report(asha, "MESSAGE", rude, "HARASSMENT", null).andExpect(status().isNotFound());
		String reportId = JsonPath.read(report(priya, "MESSAGE", rude, "HARASSMENT", "He threatened me").andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(), "$.id");
		report(priya, "MESSAGE", rude, "HARASSMENT", null).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("already_reported"));
		report(priya, "MEMBER", rahul.userId(), "FAKE_PROFILE", null).andExpect(status().isCreated());
		report(asha, "RIDE", rideId, "SPAM", "Same ride posted 5 times").andExpect(status().isCreated());
		report(priya, "MEMBER", priya.userId(), "SPAM", null).andExpect(jsonPath("$.code").value("self_report"));
		report(priya, "BANANA", rideId, "SPAM", null).andExpect(status().isBadRequest());

		mvc.perform(get("/api/admin/reports").header("Authorization", support.console())).andExpect(status().isForbidden());
		mvc.perform(get("/api/admin/reports").header("Authorization", moderator.console()))
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].targetType").value("MESSAGE"))
			.andExpect(jsonPath("$[0].snapshot").value("Something rude and threatening"))
			.andExpect(jsonPath("$[0].reporter.displayName").value("Priya"))
			.andExpect(jsonPath("$[0].reported.displayName").value("Rahul"))
			.andExpect(jsonPath("$[0].reportsAboutMember").value(3))
			.andExpect(jsonPath("$[0].acknowledgeBy").exists())
			.andExpect(jsonPath("$[0].overdue").value(false))
			.andExpect(jsonPath("$[2].targetType").value("RIDE"))
			.andExpect(jsonPath("$[2].rideId").value(rideId));

		mvc.perform(post("/api/admin/reports/" + reportId + "/acknowledge").header("Authorization", moderator.console()))
			.andExpect(jsonPath("$.acknowledgedAt").exists());
		resolve(moderator, reportId, "REMOVE_CONTENT", "Threat removed; member warned.").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIONED"));
		// Both people now see the message as removed.
		mvc.perform(get("/api/chats/" + chatId).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.messages[1].hidden").value(true))
			.andExpect(jsonPath("$.messages[1].body").doesNotExist());
		resolve(moderator, reportId, "DISMISS", "again").andExpect(status().isConflict());

		String memberReport = JsonPath.read(mvc.perform(get("/api/admin/reports").header("Authorization", moderator.console()))
			.andReturn().getResponse().getContentAsString(), "$[0].id");
		resolve(moderator, memberReport, "REMOVE_CONTENT", "n/a").andExpect(jsonPath("$.code").value("not_removable"));
		resolve(moderator, memberReport, "DISMISS", "Profile looks genuine.").andExpect(jsonPath("$.status").value("DISMISSED"));
		mvc.perform(get("/api/admin/reports?open=false").header("Authorization", moderator.console())).andExpect(jsonPath("$", hasSize(2)));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action IN ('report.actioned', 'report.dismissed')",
				Integer.class)).isGreaterThanOrEqualTo(2);

		// A report nobody looked at for two days is overdue, and the dashboard says so.
		jdbc.update("UPDATE reports SET created_at = now() - interval '2 days' WHERE status = 'OPEN'");
		mvc.perform(get("/api/admin/reports").header("Authorization", moderator.console())).andExpect(jsonPath("$[0].overdue").value(true));
		mvc.perform(get("/api/admin/dashboard").header("Authorization", moderator.console()))
			.andExpect(jsonPath("$.openReports").value(1))
			.andExpect(jsonPath("$.overdueReports").value(1));
	}

	@Test
	void aRemovedReviewNoLongerShowsOrCounts() throws Exception {
		Staff moderator = staff("9700000012", "MODERATOR");
		LoggedIn rahul = member("9700000006", "Rahul", "MALE");
		LoggedIn priya = member("9700000007", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(rahul, 3, true);
		book(priya, rideId, 0, 2, 1).andExpect(status().isCreated());
		jdbc.update("UPDATE rides SET departure_at = now() - interval '12 hours' WHERE id = ?::uuid", rideId);
		housekeeping.run();
		String review = JsonPath.read(mvc.perform(post("/api/reviews").header("Authorization", rahul.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"rideId\":\"%s\",\"subjectId\":\"%s\",\"rating\":1,\"comment\":\"Abusive words here\"}".formatted(rideId, priya.userId())))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
		String reportId = JsonPath.read(report(priya, "REVIEW", review, "INAPPROPRIATE", null).andReturn().getResponse().getContentAsString(), "$.id");
		resolve(moderator, reportId, "REMOVE_CONTENT", "Abusive review removed.").andExpect(status().isOk());
		mvc.perform(get("/api/users/" + priya.userId() + "/reviews")).andExpect(jsonPath("$.items", hasSize(0)))
			.andExpect(jsonPath("$.count").value(0));
		mvc.perform(get("/api/users/" + priya.userId())).andExpect(jsonPath("$.rating").doesNotExist());
	}

	private ResultActions openChat(LoggedIn who, String rideId) throws Exception {
		return mvc.perform(post("/api/chats").header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"rideId\":\"" + rideId + "\"}"));
	}

	private ResultActions sendChat(LoggedIn who, String chatId, String text) throws Exception {
		return mvc.perform(post("/api/chats/" + chatId + "/messages").header("Authorization", who.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + text + "\"}"));
	}

	private ResultActions report(LoggedIn who, String type, String targetId, String reason, String details) throws Exception {
		return mvc.perform(post("/api/reports").header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"targetType\":\"%s\",\"targetId\":\"%s\",\"reason\":\"%s\",\"details\":%s}".formatted(type, targetId, reason,
					details == null ? "null" : "\"" + details + "\"")));
	}

	private ResultActions resolve(Staff staff, String reportId, String action, String note) throws Exception {
		return mvc.perform(post("/api/admin/reports/" + reportId + "/resolve").header("Authorization", staff.console())
			.contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"%s\",\"resolution\":\"%s\"}".formatted(action, note)));
	}
}
