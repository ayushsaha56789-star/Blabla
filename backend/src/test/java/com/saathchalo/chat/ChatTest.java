package com.saathchalo.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.rides.RideTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ChatTest extends RideTestSupport {

	@Autowired
	ChatHousekeeping housekeeping;

	@Test
	void aPassengerCanAskBeforeBookingAndBothSidesSeeUnreadMessages() throws Exception {
		LoggedIn driver = member("9400000001", "Rahul", "MALE");
		LoggedIn asker = member("9400000002", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, false);

		String chat = open(asker, rideId, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.other.displayName").value("Rahul"))
			.andExpect(jsonPath("$.otherIsDriver").value(true))
			.andExpect(jsonPath("$.passengerBooking").doesNotExist())
			.andExpect(jsonPath("$.canSend").value(true))
			.andExpect(jsonPath("$.ride.fromName").value("Delhi"))
			.andExpect(jsonPath("$.messages", hasSize(0)))
			.andReturn().getResponse().getContentAsString();
		String chatId = JsonPath.read(chat, "$.id");
		// Opening again finds the same chat.
		open(asker, rideId, null).andExpect(jsonPath("$.id").value(chatId));

		send(asker, chatId, "Hi! Can you pick me up at IFFCO Chowk?").andExpect(status().isCreated())
			.andExpect(jsonPath("$.mine").value(true));
		unread(driver).andExpect(jsonPath("$.conversations").value(1));
		mvc.perform(get("/api/chats").header("Authorization", driver.bearer()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].other.displayName").value("Priya"))
			.andExpect(jsonPath("$[0].otherIsDriver").value(false))
			.andExpect(jsonPath("$[0].unread").value(1))
			.andExpect(jsonPath("$[0].lastMessage").value("Hi! Can you pick me up at IFFCO Chowk?"))
			.andExpect(jsonPath("$[0].lastFromMe").value(false));

		// The driver reads it (which clears the badge) and replies.
		String driverView = mvc.perform(get("/api/chats/" + chatId).header("Authorization", driver.bearer()))
			.andExpect(jsonPath("$.messages", hasSize(1)))
			.andExpect(jsonPath("$.messages[0].mine").value(false))
			.andReturn().getResponse().getContentAsString();
		unread(driver).andExpect(jsonPath("$.conversations").value(0));
		String firstAt = JsonPath.read(driverView, "$.messages[0].createdAt");
		send(driver, chatId, "Sure, see you there at 8:10.").andExpect(status().isCreated());
		unread(asker).andExpect(jsonPath("$.conversations").value(1));

		// Polling with "after" returns only what's new, and shows the driver has seen the question.
		mvc.perform(get("/api/chats/" + chatId).param("after", firstAt).header("Authorization", asker.bearer()))
			.andExpect(jsonPath("$.messages", hasSize(1)))
			.andExpect(jsonPath("$.messages[0].body").value("Sure, see you there at 8:10."))
			.andExpect(jsonPath("$.seenAt").exists());
		unread(asker).andExpect(jsonPath("$.conversations").value(0));

		// After booking, the chat shows the booking to both sides.
		book(asker, rideId, 1, 2, 1).andExpect(status().isCreated());
		mvc.perform(get("/api/chats/" + chatId).header("Authorization", driver.bearer()))
			.andExpect(jsonPath("$.passengerBooking").value("REQUESTED"));
	}

	@Test
	void onlyThePeopleInAChatCanSeeIt() throws Exception {
		LoggedIn driver = member("9400000003", "Rahul", "MALE");
		LoggedIn passenger = member("9400000004", "Priya", "FEMALE");
		LoggedIn stranger = member("9400000005", "Vikram", "MALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		String chatId = JsonPath.read(open(passenger, rideId, null).andReturn().getResponse().getContentAsString(), "$.id");
		send(passenger, chatId, "Hello").andExpect(status().isCreated());

		mvc.perform(get("/api/chats/" + chatId).header("Authorization", stranger.bearer())).andExpect(status().isNotFound());
		send(stranger, chatId, "Let me in").andExpect(status().isNotFound());
		mvc.perform(get("/api/chats/" + chatId)).andExpect(status().isUnauthorized());
		// The driver can start a chat only with someone who booked.
		open(driver, rideId, stranger.userId()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("not_a_passenger"));
		book(stranger, rideId, 0, 2, 1).andExpect(status().isCreated());
		open(driver, rideId, stranger.userId()).andExpect(status().isOk())
			.andExpect(jsonPath("$.other.displayName").value("Vikram"))
			.andExpect(jsonPath("$.passengerBooking").value("CONFIRMED"));
		send(passenger, chatId, "  ").andExpect(status().isBadRequest());
		send(passenger, chatId, "x".repeat(1001)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.body").exists());
	}

	@Test
	void ladiesOnlyRidesAreOpenForQuestionsFromWomenOnly() throws Exception {
		LoggedIn priya = member("9400000006", "Priya", "FEMALE");
		LoggedIn rahul = member("9400000007", "Rahul", "MALE");
		LoggedIn anita = member("9400000008", "Anita", "FEMALE");
		String body = publishRaw(priya, rideJson(addCar(priya, 3), inDays(1), "08:00", 3, 500, true, true, DELHI, JAIPUR))
			.andReturn().getResponse().getContentAsString();
		String rideId = JsonPath.read(body, "$.id");
		open(rahul, rideId, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ladies_only"));
		open(anita, rideId, null).andExpect(status().isOk());
	}

	@Test
	void chatsCloseAWeekAfterTheTripAndAreRemovedAfterSixMonths() throws Exception {
		LoggedIn driver = member("9400000009", "Rahul", "MALE");
		LoggedIn passenger = member("9400000010", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		String chatId = JsonPath.read(open(passenger, rideId, null).andReturn().getResponse().getContentAsString(), "$.id");
		send(passenger, chatId, "Thanks for the ride!").andExpect(status().isCreated());

		jdbc.update("UPDATE rides SET departure_at = now() - interval '10 days', status = 'COMPLETED' WHERE id = ?::uuid", rideId);
		mvc.perform(get("/api/chats/" + chatId).header("Authorization", driver.bearer()))
			.andExpect(jsonPath("$.canSend").value(false))
			.andExpect(jsonPath("$.cannotSendReason").value("This chat closed a week after the trip."));
		send(driver, chatId, "You're welcome").andExpect(status().isConflict());

		jdbc.update("UPDATE rides SET departure_at = now() - interval '200 days' WHERE id = ?::uuid", rideId);
		housekeeping.removeOldChats();
		mvc.perform(get("/api/chats/" + chatId).header("Authorization", driver.bearer())).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages", Integer.class)).isZero();
	}

	@Test
	void theOwnerCanSwitchChatOff() throws Exception {
		Staff owner = superAdmin();
		LoggedIn passenger = member("9400000011", "Priya", "FEMALE");
		mvc.perform(put("/api/admin/features/chat").header("Authorization", owner.console())
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
			.andExpect(status().isOk());
		mvc.perform(get("/api/chats").header("Authorization", passenger.bearer()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("feature_unavailable"));
	}

	private ResultActions open(LoggedIn who, String rideId, String passengerId) throws Exception {
		String json = passengerId == null ? "{\"rideId\":\"%s\"}".formatted(rideId)
				: "{\"rideId\":\"%s\",\"passengerId\":\"%s\"}".formatted(rideId, passengerId);
		return mvc.perform(post("/api/chats").header("Authorization", who.bearer())
			.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions send(LoggedIn who, String chatId, String text) throws Exception {
		return mvc.perform(post("/api/chats/" + chatId + "/messages").header("Authorization", who.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"body\":\"" + text + "\"}"));
	}

	private ResultActions unread(LoggedIn who) throws Exception {
		return mvc.perform(get("/api/chats/unread").header("Authorization", who.bearer()));
	}
}
