package com.saathchalo.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import org.mockito.Mockito;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.auth.SmsSender;
import com.saathchalo.rides.RideTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

class SafetyTest extends RideTestSupport {

	@MockitoSpyBean
	SmsSender sms;

	@Autowired
	SafetyHousekeeping housekeeping;

	@Test
	void membersKeepUpToThreeEmergencyContactsStoredEncrypted() throws Exception {
		LoggedIn priya = member("9600000001", "Priya", "FEMALE");
		addContact(priya, "Amma", "98765 43210").andExpect(status().isCreated())
			.andExpect(jsonPath("$.phone").value("+919876543210"));
		addContact(priya, "Ravi", "9123456780").andExpect(status().isCreated());
		addContact(priya, "Me", "9600000001").andExpect(jsonPath("$.code").value("own_number"));
		addContact(priya, "Amma again", "+91 98765 43210").andExpect(status().isConflict());
		addContact(priya, "Nobody", "12345").andExpect(jsonPath("$.code").value("invalid_phone"));
		String third = JsonPath.read(addContact(priya, "Didi", "9988776655").andReturn().getResponse().getContentAsString(), "$.id");
		addContact(priya, "Fourth", "9988776644").andExpect(jsonPath("$.code").value("too_many_contacts"));

		assertThat(jdbc.queryForList("SELECT phone_enc FROM emergency_contacts", String.class))
			.hasSize(3).allSatisfy(enc -> assertThat(enc).startsWith("v1:").doesNotContain("98765"));
		mvc.perform(delete("/api/safety/contacts/" + third).header("Authorization", priya.bearer())).andExpect(status().isNoContent());
		mvc.perform(get("/api/safety/contacts").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].name").value("Amma"));
		LoggedIn other = member("9600000002", "Ravi", "MALE");
		mvc.perform(get("/api/safety/contacts").header("Authorization", other.bearer())).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void familyCanFollowATripWithTheSecretLinkButSeeNoPhoneNumbers() throws Exception {
		LoggedIn driver = member("9600000003", "Rahul", "MALE");
		LoggedIn priya = member("9600000004", "Priya", "FEMALE");
		LoggedIn stranger = member("9600000005", "Vikram", "MALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		book(priya, rideId, 0, 2, 1).andExpect(status().isCreated());

		mvc.perform(post("/api/rides/" + rideId + "/share").header("Authorization", stranger.bearer()))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("not_on_trip"));
		mvc.perform(get("/api/rides/" + rideId + "/share").header("Authorization", priya.bearer())).andExpect(status().isNoContent());
		String path = JsonPath.read(mvc.perform(post("/api/rides/" + rideId + "/share").header("Authorization", priya.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.path");
		assertThat(path).startsWith("/trip/");
		// Asking again gives the same link (it may already be with family).
		mvc.perform(post("/api/rides/" + rideId + "/share").header("Authorization", priya.bearer())).andExpect(jsonPath("$.path").value(path));
		String token = path.substring("/trip/".length());

		mvc.perform(post("/api/rides/" + rideId + "/share/location").header("Authorization", priya.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"lat\":28.4601,\"lng\":77.0263,\"accuracy\":25}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.liveAt").exists());
		mvc.perform(get("/api/shared-trips/" + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sharerName").value("Priya"))
			.andExpect(jsonPath("$.sharerIsDriver").value(false))
			.andExpect(jsonPath("$.ride.fromName").value("Delhi"))
			.andExpect(jsonPath("$.stops", hasSize(3)))
			.andExpect(jsonPath("$.driver.displayName").value("Rahul"))
			.andExpect(jsonPath("$.car.plate").value("MH 12 AB 1234"))
			.andExpect(jsonPath("$.live.near").value("Gurugram, Haryana"))
			.andExpect(jsonPath("$.sosAt").doesNotExist())
			.andExpect(content().string(not(containsString("+91"))));
		mvc.perform(get("/api/shared-trips/not-a-real-token")).andExpect(status().isNotFound());

		// After the trip the link expires and the location is wiped.
		jdbc.update("UPDATE trip_shares SET expires_at = now() - interval '1 minute'");
		mvc.perform(get("/api/shared-trips/" + token)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("trip_link_expired"));
		housekeeping.run();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_shares WHERE live_lat IS NOT NULL", Integer.class)).isZero();
	}

	@Test
	void stoppingSharingTurnsTheLinkOff() throws Exception {
		LoggedIn driver = member("9600000006", "Rahul", "MALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		String path = JsonPath.read(mvc.perform(post("/api/rides/" + rideId + "/share").header("Authorization", driver.bearer()))
			.andReturn().getResponse().getContentAsString(), "$.path");
		mvc.perform(get("/api/shared-trips/" + path.substring(6))).andExpect(jsonPath("$.sharerIsDriver").value(true));
		mvc.perform(delete("/api/rides/" + rideId + "/share").header("Authorization", driver.bearer())).andExpect(status().isNoContent());
		mvc.perform(get("/api/shared-trips/" + path.substring(6))).andExpect(status().isNotFound());
	}

	@Test
	void sosAlertsSupportTextsContactsAndIsHandledInTheConsole() throws Exception {
		Staff support = staff("9600000010", "SUPPORT");
		LoggedIn driver = member("9600000007", "Rahul", "MALE");
		LoggedIn priya = member("9600000008", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		book(priya, rideId, 0, 2, 1).andExpect(status().isCreated());
		addContact(priya, "Amma", "9876543210").andExpect(status().isCreated());
		addContact(priya, "Ravi", "9123456780").andExpect(status().isCreated());
		clearInvocations(sms);

		String alert = sos(priya, "{\"rideId\":\"%s\",\"lat\":28.46,\"lng\":77.03,\"accuracy\":30}".formatted(rideId))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.contactsNotified").value(2))
			.andExpect(jsonPath("$.sharePath").exists())
			.andReturn().getResponse().getContentAsString();
		String alertId = JsonPath.read(alert, "$.id");
		Mockito.verify(sms).send(eq("+919876543210"), contains("SOS from Priya on SaathChalo"));
		Mockito.verify(sms).send(eq("+919123456780"), contains("Follow the trip: http://localhost:4200/trip/"));
		// Pressing again during the same trip updates the same alert; contacts aren't texted twice.
		sos(priya, "{\"rideId\":\"%s\",\"lat\":28.30,\"lng\":76.90}".formatted(rideId)).andExpect(jsonPath("$.id").value(alertId));
		Mockito.verify(sms, times(2)).send(anyString(), anyString());
		// Family following the trip see that SOS was pressed.
		String path = JsonPath.read(alert, "$.sharePath");
		mvc.perform(get("/api/shared-trips/" + path.substring(6))).andExpect(jsonPath("$.sosAt").exists());

		mvc.perform(get("/api/admin/safety/sos").header("Authorization", support.console()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].status").value("OPEN"))
			.andExpect(jsonPath("$[0].member.displayName").value("Priya"))
			.andExpect(jsonPath("$[0].maskedPhone").value("+91 96••• ••008"))
			.andExpect(jsonPath("$[0].ride.fromName").value("Delhi"))
			.andExpect(jsonPath("$[0].lat").value(28.30))
			.andExpect(jsonPath("$[0].contactsNotified").value(2));
		mvc.perform(post("/api/admin/safety/sos/" + alertId + "/acknowledge").header("Authorization", support.console()))
			.andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
		mvc.perform(post("/api/admin/safety/sos/" + alertId + "/resolve").header("Authorization", support.console())
				.contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"Called Priya, she is safe. Driver had a flat tyre.\"}"))
			.andExpect(jsonPath("$.status").value("RESOLVED"))
			.andExpect(jsonPath("$.resolvedBy").exists());
		mvc.perform(get("/api/admin/safety/sos").header("Authorization", support.console())).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/admin/safety/sos?open=false").header("Authorization", support.console())).andExpect(jsonPath("$", hasSize(1)));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action IN ('sos.acknowledged', 'sos.resolved') AND target_id = ?",
				Integer.class, alertId)).isEqualTo(2);
		// Members can't use the console endpoints.
		mvc.perform(get("/api/admin/safety/sos").header("Authorization", priya.bearer())).andExpect(status().isForbidden());
	}

	@Test
	void sosUsesTheLatestSharedLocationWhenThePhoneSendsNone() throws Exception {
		Staff support = staff("9600000011", "SUPPORT");
		LoggedIn driver = member("9600000012", "Rahul", "MALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		mvc.perform(post("/api/rides/" + rideId + "/share").header("Authorization", driver.bearer())).andExpect(status().isOk());
		mvc.perform(post("/api/rides/" + rideId + "/share/location").header("Authorization", driver.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"lat\":27.5,\"lng\":76.6,\"accuracy\":40}"))
			.andExpect(status().isOk());
		sos(driver, "{\"rideId\":\"%s\"}".formatted(rideId)).andExpect(status().isCreated());
		mvc.perform(get("/api/admin/safety/sos").header("Authorization", support.console()))
			.andExpect(jsonPath("$[0].lat").value(27.5))
			.andExpect(jsonPath("$[0].accuracy").value(40));
	}

	@Test
	void sosWorksEvenWithoutARideOrContacts() throws Exception {
		LoggedIn asha = member("9600000009", "Asha", "FEMALE");
		sos(asha, "{}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.contactsNotified").value(0))
			.andExpect(jsonPath("$.sharePath").doesNotExist());
	}

	private ResultActions addContact(LoggedIn who, String name, String phone) throws Exception {
		return mvc.perform(post("/api/safety/contacts").header("Authorization", who.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\",\"phone\":\"%s\"}".formatted(name, phone)));
	}

	private ResultActions sos(LoggedIn who, String json) throws Exception {
		return mvc.perform(post("/api/sos").header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON).content(json));
	}
}
