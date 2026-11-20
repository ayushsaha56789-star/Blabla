package com.saathchalo.support;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class SupportTest extends IntegrationTest {

	@Autowired
	SupportHousekeeping housekeeping;

	@Test
	void membersWriteInAndStaffAnswer() throws Exception {
		Staff support = staff("9800000010", "SUPPORT");
		LoggedIn priya = loginWithProfile("9800000001", "Priya", "FEMALE", "Pune");
		LoggedIn other = login("9800000002");

		String ticket = mvc.perform(post("/api/support/tickets").header("Authorization", priya.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"BOOKING\",\"subject\":\"Driver didn't come\",\"body\":\"I waited 40 minutes at the pickup point.\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.messages", hasSize(1)))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(ticket, "$.id");
		mvc.perform(get("/api/support/tickets/" + id).header("Authorization", other.bearer())).andExpect(status().isNotFound());
		mvc.perform(post("/api/support/tickets").header("Authorization", priya.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"NOPE\",\"subject\":\"x\",\"body\":\"y\"}"))
			.andExpect(status().isBadRequest());

		mvc.perform(get("/api/admin/support/tickets").header("Authorization", support.console()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].member.displayName").value("Priya"))
			.andExpect(jsonPath("$[0].overdue").value(false))
			.andExpect(jsonPath("$[0].respondBy").exists());
		mvc.perform(get("/api/admin/support/tickets/" + id).header("Authorization", support.console()))
			.andExpect(jsonPath("$.maskedPhone").value("+91 98••• ••001"))
			.andExpect(jsonPath("$.messages[0].body").value("I waited 40 minutes at the pickup point."));
		mvc.perform(post("/api/admin/support/tickets/" + id + "/messages").header("Authorization", support.console())
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Sorry about that! We've spoken to the driver.\"}"))
			.andExpect(jsonPath("$.ticket.status").value("ANSWERED"))
			.andExpect(jsonPath("$.ticket.firstResponseAt").exists());

		mvc.perform(get("/api/support/tickets/answered").header("Authorization", priya.bearer())).andExpect(jsonPath("$.count").value(1));
		mvc.perform(get("/api/support/tickets").header("Authorization", priya.bearer())).andExpect(jsonPath("$[0].newReply").value(true));
		mvc.perform(get("/api/support/tickets/" + id).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.messages", hasSize(2)))
			.andExpect(jsonPath("$.messages[1].fromStaff").value(true))
			.andExpect(jsonPath("$.messages[1].author").value("SaathChalo support"));
		// Opening the request marks the reply as read.
		mvc.perform(get("/api/support/tickets/answered").header("Authorization", priya.bearer())).andExpect(jsonPath("$.count").value(0));
		mvc.perform(get("/api/support/tickets").header("Authorization", priya.bearer())).andExpect(jsonPath("$[0].newReply").value(false));
		mvc.perform(post("/api/support/tickets/" + id + "/messages").header("Authorization", priya.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Thanks!\"}"))
			.andExpect(jsonPath("$.status").value("OPEN"));
		mvc.perform(post("/api/support/tickets/" + id + "/close").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.status").value("CLOSED"));
		mvc.perform(get("/api/support/tickets").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].status").value("CLOSED"));
		mvc.perform(get("/api/admin/support/tickets?status=all").header("Authorization", support.console())).andExpect(jsonPath("$", hasSize(1)));
		mvc.perform(get("/api/admin/support/tickets").header("Authorization", priya.bearer())).andExpect(status().isForbidden());

		// A request nobody answered within a day is overdue.
		mvc.perform(post("/api/support/tickets").header("Authorization", priya.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"GRIEVANCE\",\"subject\":\"Complaint\",\"body\":\"About a review.\"}"))
			.andExpect(status().isCreated());
		jdbc.update("UPDATE support_tickets SET created_at = now() - interval '2 days' WHERE status = 'OPEN'");
		mvc.perform(get("/api/admin/support/tickets").header("Authorization", support.console())).andExpect(jsonPath("$[0].overdue").value(true));
		mvc.perform(get("/api/admin/dashboard").header("Authorization", support.console())).andExpect(jsonPath("$.ticketsWaiting").value(1));
	}

	@Test
	void answeredRequestsCloseAfterAQuietWeek() throws Exception {
		Staff support = staff("9800000010", "SUPPORT");
		LoggedIn priya = loginWithProfile("9800000001", "Priya", "FEMALE", "Pune");
		String ticket = mvc.perform(post("/api/support/tickets").header("Authorization", priya.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"ACCOUNT\",\"subject\":\"Change my name\",\"body\":\"Please fix the spelling.\"}"))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(ticket, "$.id");
		mvc.perform(post("/api/admin/support/tickets/" + id + "/messages").header("Authorization", support.console())
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Done!\"}"))
			.andExpect(jsonPath("$.ticket.status").value("ANSWERED"));

		housekeeping.closeQuietRequests();
		mvc.perform(get("/api/support/tickets").header("Authorization", priya.bearer())).andExpect(jsonPath("$[0].status").value("ANSWERED"));

		jdbc.update("UPDATE support_tickets SET updated_at = now() - interval '8 days'");
		housekeeping.closeQuietRequests();
		mvc.perform(get("/api/support/tickets").header("Authorization", priya.bearer())).andExpect(jsonPath("$[0].status").value("CLOSED"));
		mvc.perform(get("/api/support/tickets/answered").header("Authorization", priya.bearer())).andExpect(jsonPath("$.count").value(0));
	}
}
