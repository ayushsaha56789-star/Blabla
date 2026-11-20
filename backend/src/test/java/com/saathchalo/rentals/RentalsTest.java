package com.saathchalo.rentals;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.saathchalo.payments.Settlement;
import com.saathchalo.rides.RideTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class RentalsTest extends RideTestSupport {

	@Autowired
	Settlement settlement;

	@Autowired
	RentalHousekeeping housekeeping;

	@BeforeEach
	void rentalsOn() {
		switchOn("rentals", true);
	}

	@Test
	void ownersListCarsAndStaffCheckTheDocuments() throws Exception {
		LoggedIn owner = member("9200000001", "Ravi", "MALE");
		LoggedIn renter = member("9200000002", "Priya", "FEMALE");
		Staff admin = staff("9200000010", "ADMIN");
		String listing = createListing(owner, false);
		send(owner, "/api/rentals/listings/" + listing + "/submit").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("photos_missing"));
		addPhoto(owner, listing).andExpect(jsonPath("$.photos", hasSize(1)));
		send(owner, "/api/rentals/listings/" + listing + "/submit").andExpect(jsonPath("$.code").value("documents_missing"));
		addDocument(owner, listing, "RC");
		addDocument(owner, listing, "INSURANCE").andExpect(jsonPath("$.documents", hasSize(2)));
		send(owner, "/api/rentals/listings/" + listing + "/submit").andExpect(jsonPath("$.listing.status").value("IN_REVIEW"));
		search(renter, null, null).andExpect(jsonPath("$", hasSize(0)));

		String queue = mvc.perform(get("/api/admin/rentals/listings").header("Authorization", admin.console()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].pickupAddress").value("12 Janpath, Connaught Place"))
			.andReturn().getResponse().getContentAsString();
		String doc = JsonPath.read(queue, "$[0].documents[0].id");
		mvc.perform(get("/api/admin/rentals/listings/" + listing + "/documents/" + doc).header("Authorization", admin.console()))
			.andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/jpeg"));
		mvc.perform(get("/api/admin/rentals/listings").header("Authorization", member("9200000003", "Asha", "FEMALE").bearer()))
			.andExpect(status().isForbidden());
		mvc.perform(post("/api/admin/rentals/listings/" + listing + "/approve").header("Authorization", admin.console()))
			.andExpect(jsonPath("$.listing.status").value("LIVE"))
			.andExpect(jsonPath("$.listing.verified").value(true));

		String found = search(renter, null, null).andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].verified").value(true))
			.andExpect(jsonPath("$[0].car.name").value("Maruti Suzuki Swift"))
			.andExpect(jsonPath("$[0].photoUrl", startsWith("/api/files/cars/")))
			.andReturn().getResponse().getContentAsString();
		mvc.perform(get(JsonPath.<String>read(found, "$[0].photoUrl"))).andExpect(status().isOk());
		// The exact pickup address is only for renters the owner accepted.
		String detail = mvc.perform(get("/api/rentals/listings/" + listing)).andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(detail).doesNotContain("Janpath");

		// The owner can switch rentals off for everyone.
		switchOn("rentals", false);
		search(renter, null, null).andExpect(status().isForbidden());
	}

	@Test
	void rentingACarFromRequestToReturn() throws Exception {
		LoggedIn owner = member("9200000001", "Ravi", "MALE");
		LoggedIn priya = member("9200000002", "Priya", "FEMALE");
		LoggedIn asha = member("9200000003", "Asha", "FEMALE");
		Staff admin = staff("9200000010", "ADMIN");
		String listing = liveListing(owner, admin, false);
		Instant from = Instant.now().plus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MINUTES);
		Instant to = from.plus(Duration.ofHours(30));

		mvc.perform(get("/api/rentals/listings/" + listing + "/quote").param("from", from.toString()).param("to", to.toString())
				.param("mode", "SELF_DRIVE"))
			.andExpect(jsonPath("$.available").value(true))
			.andExpect(jsonPath("$.rent").value(4000))
			.andExpect(jsonPath("$.deposit").value(5000))
			.andExpect(jsonPath("$.kmIncluded").value(500));

		// Self-drive needs a verified driving licence and a date of birth.
		rent(priya, listing, "SELF_DRIVE", from, to).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("licence_needed"));
		licence(priya);
		rent(priya, listing, "SELF_DRIVE", from, to).andExpect(jsonPath("$.code").value("too_young"));
		birthday(priya, "1995-05-05");
		String booking = JsonPath.read(rent(priya, listing, "SELF_DRIVE", from, to).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REQUESTED"))
			.andExpect(jsonPath("$.payOnline").value(false))
			.andExpect(jsonPath("$.otherPhone").doesNotExist())
			.andReturn().getResponse().getContentAsString(), "$.id");
		String other = JsonPath.read(rent(asha, listing, "WITH_DRIVER", from.plus(Duration.ofHours(5)), to.plus(Duration.ofHours(5)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

		// Accepting one makes the other impossible: the database won't double-book the car.
		send(owner, "/api/rentals/bookings/" + booking + "/accept").andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.otherPhone").value("+919200000002"));
		send(owner, "/api/rentals/bookings/" + other + "/accept").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("not_available"));
		search(asha, from, to).andExpect(jsonPath("$", hasSize(0)));
		mvc.perform(get("/api/rentals/bookings/" + booking).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.pickupAddress").value("12 Janpath, Connaught Place"));

		// Pickup and return, with photos; 700 km driven, 500 included: 200 km × ₹10, plus ₹300 fuel.
		check(owner, booking, "pickup", 10000, "FULL", null, null).andExpect(jsonPath("$.status").value("PICKED_UP"));
		check(priya, booking, "pickup", 10000, "FULL", null, null).andExpect(jsonPath("$.checks", hasSize(2)));
		check(owner, booking, "return", 9000, "HALF", null, null).andExpect(jsonPath("$.code").value("odometer_too_low"));
		check(owner, booking, "return", 10700, "HALF", 300, "Fuel was full at pickup")
			.andExpect(jsonPath("$.status").value("RETURNED"))
			.andExpect(jsonPath("$.kmDriven").value(700))
			.andExpect(jsonPath("$.extraKmCharge").value(2000))
			.andExpect(jsonPath("$.extraCharges").value(2300));
		String view = mvc.perform(get("/api/rentals/bookings/" + booking).header("Authorization", priya.bearer()))
			.andReturn().getResponse().getContentAsString();
		String photo = JsonPath.read(view, "$.checks[0].photoUrls[0]");
		mvc.perform(get(photo).header("Authorization", priya.bearer())).andExpect(status().isOk());
		mvc.perform(get(photo).header("Authorization", asha.bearer())).andExpect(status().isNotFound());

		// Priya questions the fuel charge; staff settle it.
		mvc.perform(post("/api/rentals/bookings/" + booking + "/dispute").header("Authorization", priya.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"I filled the tank before returning.\"}"))
			.andExpect(jsonPath("$.status").value("DISPUTED"));
		mvc.perform(post("/api/admin/rentals/bookings/" + booking + "/resolve").header("Authorization", admin.console())
				.header("X-Confirm-Code", confirmCode(admin)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"extraCharges\":2000,\"resolution\":\"The fuel receipt shows a full tank; only extra km are charged.\"}"))
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.extraCharges").value(2000));

		// 90 days after the rental the photos are deleted; the readings stay.
		housekeeping.forgetOldPhotos();
		mvc.perform(get(photo).header("Authorization", priya.bearer())).andExpect(status().isOk());
		jdbc.update("UPDATE rental_bookings SET completed_at = now() - interval '91 days' WHERE id = ?::uuid", booking);
		housekeeping.forgetOldPhotos();
		mvc.perform(get(photo).header("Authorization", priya.bearer())).andExpect(status().isNotFound());
		mvc.perform(get("/api/rentals/bookings/" + booking).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.checks", hasSize(3)))
			.andExpect(jsonPath("$.checks[0].photoUrls", hasSize(0)))
			.andExpect(jsonPath("$.checks[0].odometer").value(10000));
	}

	@Test
	void payingOnlineKeepsTheDepositUntilTheCarIsBack() throws Exception {
		switchOn("payments.online", true);
		LoggedIn owner = member("9200000001", "Ravi", "MALE");
		LoggedIn priya = member("9200000002", "Priya", "FEMALE");
		Staff admin = staff("9200000010", "ADMIN");
		licence(priya);
		birthday(priya, "1995-05-05");
		String listing = liveListing(owner, admin, true);
		Instant from = Instant.now().plus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MINUTES);

		// Instant booking: accepted at once; then paid online (rent + refundable deposit).
		String booking = JsonPath.read(rent(priya, listing, "SELF_DRIVE", from, from.plus(Duration.ofHours(24)))
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.payOnline").value(true))
			.andReturn().getResponse().getContentAsString(), "$.id");
		check(owner, booking, "pickup", 5000, "FULL", null, null).andExpect(jsonPath("$.code").value("not_ready"));
		String checkout = mvc.perform(post("/api/rentals/bookings/" + booking + "/pay").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.kind").value("RENTAL"))
			.andExpect(jsonPath("$.seatPaise").value(200000))
			.andExpect(jsonPath("$.depositPaise").value(500000))
			.andExpect(jsonPath("$.onlinePaise").value(700000))
			.andReturn().getResponse().getContentAsString();
		String paymentId = JsonPath.read(checkout, "$.paymentId");
		String answer = send(priya, "/api/payments/" + paymentId + "/pretend-pay").andReturn().getResponse().getContentAsString();
		mvc.perform(post("/api/payments/" + paymentId + "/confirm").header("Authorization", priya.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(answer))
			.andExpect(jsonPath("$.status").value("PAID"));
		mvc.perform(get("/api/rentals/bookings/" + booking).header("Authorization", priya.bearer())).andExpect(jsonPath("$.status").value("PAID"));

		check(owner, booking, "pickup", 5000, "FULL", null, null).andExpect(jsonPath("$.status").value("PICKED_UP"));
		check(owner, booking, "return", 5250, "FULL", 1000, "Scratch on the rear bumper (photo)")
			.andExpect(jsonPath("$.extraCharges").value(1000));
		send(priya, "/api/rentals/bookings/" + booking + "/accept-return").andExpect(jsonPath("$.status").value("COMPLETED"));
		settlement.run();

		// The owner earns the rent and the agreed charge; the rest of the deposit goes back to Priya.
		mvc.perform(get("/api/wallet").header("Authorization", owner.bearer())).andExpect(jsonPath("$.earningsPaise").value(300000));
		mvc.perform(get("/api/payments/bookings").param("ids", booking).header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$[0].outcome").value("SPLIT"))
			.andExpect(jsonPath("$[0].refunds[0].onlinePaise").value(400000));
		mvc.perform(get("/api/admin/payments/summary").header("Authorization", superAdmin().console()))
			.andExpect(jsonPath("$.ledgerBalanced").value(true))
			.andExpect(jsonPath("$.escrowMatches").value(true));

		// An accepted booking that isn't paid in time lapses.
		String unpaid = JsonPath.read(rent(priya, listing, "SELF_DRIVE", from.plus(Duration.ofDays(3)), from.plus(Duration.ofDays(4)))
			.andReturn().getResponse().getContentAsString(), "$.id");
		jdbc.update("UPDATE rental_bookings SET pay_by = now() - interval '1 minute' WHERE id = ?::uuid", unpaid);
		housekeeping.run();
		mvc.perform(get("/api/rentals/bookings/" + unpaid).header("Authorization", priya.bearer())).andExpect(jsonPath("$.status").value("EXPIRED"));
	}

	// ----- Helpers -----

	private void switchOn(String key, boolean on) {
		jdbc.update("UPDATE feature_flags SET enabled = ? WHERE key = ?", on, key);
		featureService.reload();
	}

	private String createListing(LoggedIn owner, boolean instant) throws Exception {
		String car = addCar(owner, 4);
		String body = """
				{"vehicleId":"%s","selfDrive":true,"withDriver":true,"dayPrice":2000,"hourPrice":200,"deposit":5000,"kmPerDay":250,
				 "extraKmPrice":10,"fuelPolicy":"SAME_LEVEL","minAge":21,"placeId":%d,"area":"Near Rajiv Chowk metro",
				 "pickupAddress":"12 Janpath, Connaught Place","description":"Clean, AC, music","rules":"No smoking","instant":%b}"""
			.formatted(car, DELHI, instant);
		return JsonPath.read(mvc.perform(post("/api/rentals/listings").header("Authorization", owner.bearer())
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.listing.status").value("DRAFT"))
			.andReturn().getResponse().getContentAsString(), "$.listing.id");
	}

	private String liveListing(LoggedIn owner, Staff admin, boolean instant) throws Exception {
		String listing = createListing(owner, instant);
		addPhoto(owner, listing);
		addDocument(owner, listing, "RC");
		addDocument(owner, listing, "INSURANCE");
		send(owner, "/api/rentals/listings/" + listing + "/submit");
		mvc.perform(post("/api/admin/rentals/listings/" + listing + "/approve").header("Authorization", admin.console()))
			.andExpect(status().isOk());
		return listing;
	}

	private ResultActions addPhoto(LoggedIn who, String listing) throws Exception {
		return mvc.perform(multipart("/api/rentals/listings/" + listing + "/photos").file(new MockMultipartFile("photo", "car.png", "image/png", png()))
			.header("Authorization", who.bearer()));
	}

	private ResultActions addDocument(LoggedIn who, String listing, String kind) throws Exception {
		return mvc.perform(multipart("/api/rentals/listings/" + listing + "/documents")
			.file(new MockMultipartFile("file", "doc.png", "image/png", png())).param("kind", kind).header("Authorization", who.bearer()));
	}

	private ResultActions rent(LoggedIn who, String listing, String mode, Instant from, Instant to) throws Exception {
		return mvc.perform(post("/api/rentals/bookings").header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"listingId\":\"%s\",\"mode\":\"%s\",\"from\":\"%s\",\"to\":\"%s\"}".formatted(listing, mode, from, to)));
	}

	private ResultActions check(LoggedIn who, String booking, String stage, int odometer, String fuel, Integer charges, String note)
			throws Exception {
		var request = multipart("/api/rentals/bookings/" + booking + "/" + stage)
			.file(new MockMultipartFile("photos", "side.png", "image/png", png()))
			.param("odometer", String.valueOf(odometer)).param("fuel", fuel).header("Authorization", who.bearer());
		if (charges != null) {
			request.param("otherCharges", String.valueOf(charges)).param("chargesNote", note);
		}
		return mvc.perform(request);
	}

	private ResultActions search(LoggedIn who, Instant from, Instant to) throws Exception {
		var request = get("/api/rentals/search").param("placeId", String.valueOf(GURUGRAM)).header("Authorization", who.bearer());
		if (from != null) {
			request.param("from", from.toString()).param("to", to.toString());
		}
		return mvc.perform(request);
	}

	private ResultActions send(LoggedIn who, String path) throws Exception {
		return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization", who.bearer()));
	}

	/** Staff have approved this member's driving licence. */
	private void licence(LoggedIn who) {
		jdbc.update("""
				INSERT INTO id_checks (id, user_id, document_type, status, submitted_at, decided_at)
				VALUES (gen_random_uuid(), ?::uuid, 'DRIVING_LICENCE', 'APPROVED', now(), now())""", who.userId());
	}

	private void birthday(LoggedIn who, String date) throws Exception {
		mvc.perform(put("/api/users/me").header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Priya\",\"gender\":\"FEMALE\",\"city\":\"Delhi\",\"prefChat\":\"SOMETIMES\",\"dateOfBirth\":\"" + date + "\"}"))
			.andExpect(status().isOk());
	}

	private static byte[] png() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}
}
