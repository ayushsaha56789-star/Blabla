package com.saathchalo.promotions;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.rides.RideTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class PromotionsTest extends RideTestSupport {

	@BeforeEach
	void promotionsOn() {
		jdbc.update("UPDATE feature_flags SET enabled = TRUE WHERE key = 'promotions'");
		featureService.reload();
	}

	@Test
	void membersPromoteAndModeratorsCheckFirst() throws Exception {
		LoggedIn priya = member("9600000001", "Priya", "FEMALE");
		LoggedIn ravi = member("9600000002", "Ravi", "MALE");
		LoggedIn asha = member("9600000003", "Asha", "FEMALE");
		Staff moderator = staff("9600000010", "MODERATOR");

		// Promoting needs a confirmed email or ID.
		create(priya, "Free beer with every thali").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("verification_needed"));
		jdbc.update("UPDATE users SET email = 'priya@sharmasweets.in', email_verified = TRUE WHERE id = ?::uuid", priya.userId());
		String id = JsonPath.read(create(priya, "Free beer with every thali").andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.cities[0].name").value("Delhi"))
			.andReturn().getResponse().getContentAsString(), "$.card.id");

		// The automatic checks refuse alcohol; a fixed promotion goes to a moderator, with soft signs flagged.
		send(priya, "/api/promotions/" + id + "/submit").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("not_allowed"));
		update(priya, id, "Fresh jalebis every morning. Call 98110 12345").andExpect(jsonPath("$.status").value("DRAFT"));
		mvc.perform(multipart("/api/promotions/" + id + "/image").file(new MockMultipartFile("file", "shop.png", "image/png", png()))
				.header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.card.imageUrl").value(startsWith("/api/files/promos/")));
		send(priya, "/api/promotions/" + id + "/submit").andExpect(jsonPath("$.status").value("IN_REVIEW"));
		feed(ravi, "DISCOVER", null).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/admin/promotions").header("Authorization", moderator.console()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].flags").value("Phone number in the text"))
			.andExpect(jsonPath("$[0].owner.displayName").value("Priya"));
		mvc.perform(post("/api/admin/promotions/" + id + "/approve").header("Authorization", moderator.console()))
			.andExpect(jsonPath("$.promotion.status").value("APPROVED"))
			.andExpect(jsonPath("$.promotion.live").value(true));

		// Delhi members see it, at most three times a day; never the owner; clicks count once a day.
		for (int i = 0; i < 3; i++) {
			feed(ravi, "DISCOVER", null).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].business").value("Sharma Sweets"));
		}
		feed(ravi, "DISCOVER", null).andExpect(jsonPath("$", hasSize(0)));
		feed(priya, "DISCOVER", null).andExpect(jsonPath("$", hasSize(0)));
		feed(asha, "SEARCH", DELHI).andExpect(jsonPath("$", hasSize(1)));
		send(ravi, "/api/promotions/" + id + "/click").andExpect(status().isNoContent());
		send(ravi, "/api/promotions/" + id + "/click").andExpect(status().isNoContent());
		send(priya, "/api/promotions/" + id + "/click").andExpect(status().isNoContent());
		mvc.perform(get("/api/promotions/mine").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$[0].views").value(4))
			.andExpect(jsonPath("$[0].clicks").value(1));
		String image = JsonPath.read(mvc.perform(get("/api/promotions/mine").header("Authorization", priya.bearer()))
			.andReturn().getResponse().getContentAsString(), "$[0].card.imageUrl");
		mvc.perform(get(image)).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"));

		// The search slot has its own switch.
		jdbc.update("UPDATE feature_flags SET enabled = FALSE WHERE key = 'promotions.in_search'");
		featureService.reload();
		LoggedIn kiran = member("9600000004", "Kiran", "MALE");
		feed(kiran, "SEARCH", DELHI).andExpect(jsonPath("$", hasSize(0)));
		feed(kiran, "DISCOVER", null).andExpect(jsonPath("$", hasSize(1)));

		// Moderators can take a live promotion down.
		mvc.perform(post("/api/admin/promotions/" + id + "/take-down").header("Authorization", moderator.console())
				.contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Misleading offer\"}"))
			.andExpect(jsonPath("$.promotion.status").value("TAKEN_DOWN"));
		LoggedIn meera = member("9600000005", "Meera", "FEMALE");
		feed(meera, "DISCOVER", null).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/promotions/mine").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$[0].reviewNote").value("Misleading offer"));
	}

	// ----- Helpers -----

	private String body(String text) {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
		return """
				{"business":"Sharma Sweets","title":"Hot jalebis near the metro","body":"%s","linkUrl":"https://sharmasweets.in",
				 "category":"FOOD","allIndia":false,"placeIds":[%d],"startsOn":"%s","endsOn":"%s"}"""
			.formatted(text, DELHI, today, today.plusDays(13));
	}

	private ResultActions create(LoggedIn who, String text) throws Exception {
		return mvc.perform(post("/api/promotions").header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON).content(body(text)));
	}

	private ResultActions update(LoggedIn who, String id, String text) throws Exception {
		return mvc.perform(put("/api/promotions/" + id).header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content(body(text)));
	}

	private ResultActions feed(LoggedIn who, String slot, Long placeId) throws Exception {
		var request = get("/api/promotions/feed").param("slot", slot).param("limit", "5").header("Authorization", who.bearer());
		if (placeId != null) {
			request.param("placeIds", String.valueOf(placeId));
		}
		return mvc.perform(request);
	}

	private ResultActions send(LoggedIn who, String path) throws Exception {
		return mvc.perform(post(path).header("Authorization", who.bearer()));
	}

	private static byte[] png() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}
}
