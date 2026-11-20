package com.saathchalo.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.rides.RideHousekeeping;
import com.saathchalo.rides.RideTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PaymentsTest extends RideTestSupport {

	@Autowired
	Settlement settlement;

	@Autowired
	RideHousekeeping housekeeping;

	@Autowired
	PaymentsConfig.Gateways gateways;

	@BeforeEach
	void onlinePaymentsOn() {
		switchOn("payments.online", true);
		switchOn("wallet", true);
	}

	@Test
	void payingOnlineHoldsTheSeatAndTheDriverIsPaidAfterTheTrip() throws Exception {
		LoggedIn driver = member("9300000001", "Rahul", "MALE");
		LoggedIn priya = member("9300000002", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		mvc.perform(get("/api/payments/options").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.online").value(true))
			.andExpect(jsonPath("$.cash").value(true))
			.andExpect(jsonPath("$.gateway").value("FAKE"));

		String checkout = checkout(priya, rideId, false).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("CREATED"))
			.andExpect(jsonPath("$.onlinePaise").value(60000))
			.andExpect(jsonPath("$.orderId", startsWith("order_fake_")))
			.andExpect(jsonPath("$.description", startsWith("Seat Delhi → Jaipur")))
			.andReturn().getResponse().getContentAsString();
		String paymentId = JsonPath.read(checkout, "$.paymentId");
		ride(priya, rideId).andExpect(jsonPath("$.myBooking.status").value("AWAITING_PAYMENT"))
			.andExpect(jsonPath("$.seatsLeftByLeg[0]").value(2));

		confirm(priya, paymentId, "pay_fake_x", "nope").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("payment_unverified"));
		pay(priya, paymentId).andExpect(jsonPath("$.status").value("PAID")).andExpect(jsonPath("$.seatHeld").value(true));
		ride(priya, rideId).andExpect(jsonPath("$.myBooking.status").value("CONFIRMED"));

		// After the trip the money moves to the driver's earnings; staff pay it out.
		jdbc.update("UPDATE rides SET departure_at = now() - interval '12 hours' WHERE id = ?::uuid", rideId);
		housekeeping.run();
		settlement.run();
		wallet(driver).andExpect(jsonPath("$.earningsPaise").value(60000));
		Staff owner = superAdmin();
		summary(owner).andExpect(jsonPath("$.owedToDriversPaise").value(60000))
			.andExpect(jsonPath("$.heldForTripsPaise").value(0));
		mvc.perform(post("/api/admin/payouts").header("Authorization", owner.console()).header("X-Confirm-Code", confirmCode(owner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"driverId\":\"" + driver.userId() + "\",\"method\":\"MANUAL\",\"reference\":\"UPI 4521\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PAID"))
			.andExpect(jsonPath("$.amountPaise").value(60000));
		wallet(driver).andExpect(jsonPath("$.earningsPaise").value(0)).andExpect(jsonPath("$.paidOutPaise").value(60000));
		summary(owner).andExpect(jsonPath("$.owedToDriversPaise").value(0));
	}

	@Test
	void declinedAndCancelledSeatsAreRefundedByTheRules() throws Exception {
		LoggedIn driver = member("9300000001", "Rahul", "MALE");
		LoggedIn priya = member("9300000002", "Priya", "FEMALE");
		LoggedIn asha = member("9300000003", "Asha", "FEMALE");

		// A request the driver declines: everything back.
		String askRide = publishDelhiJaipur(driver, 3, false);
		String priyaPayment = JsonPath.read(checkout(priya, askRide, false).andReturn().getResponse().getContentAsString(), "$.paymentId");
		pay(priya, priyaPayment);
		String request = JsonPath.read(ride(priya, askRide).andExpect(jsonPath("$.myBooking.status").value("REQUESTED"))
			.andReturn().getResponse().getContentAsString(), "$.myBooking.id");
		mvc.perform(post("/api/bookings/" + request + "/decline").header("Authorization", driver.bearer())).andExpect(status().isOk());
		settlement.run();
		paymentOf(priya, request).andExpect(jsonPath("$[0].outcome").value("REFUNDED"))
			.andExpect(jsonPath("$[0].refunds[0].onlinePaise").value(60000))
			.andExpect(jsonPath("$[0].refunds[0].status").value("PROCESSED"));

		// A confirmed seat cancelled 5 hours before pickup: half back, half to the driver.
		jdbc.update("UPDATE rides SET status = 'CANCELLED' WHERE id = ?::uuid", askRide);
		String soonRide = publishRaw(driver, rideJson(addCar(driver, 4), inDays(3), "09:00", 3, 600, true, false, DELHI, GURUGRAM, JAIPUR))
			.andReturn().getResponse().getContentAsString();
		String soon = JsonPath.read(soonRide, "$.id");
		String ashaPayment = JsonPath.read(checkout(asha, soon, false).andReturn().getResponse().getContentAsString(), "$.paymentId");
		pay(asha, ashaPayment);
		String seat = JsonPath.read(ride(asha, soon).andReturn().getResponse().getContentAsString(), "$.myBooking.id");
		mvc.perform(get("/api/payments/bookings/" + seat + "/refund-preview").header("Authorization", asha.bearer()))
			.andExpect(jsonPath("$.backOnlinePaise").value(60000));
		jdbc.update("UPDATE rides SET departure_at = now() + interval '5 hours' WHERE id = ?::uuid", soon);
		mvc.perform(get("/api/payments/bookings/" + seat + "/refund-preview").header("Authorization", asha.bearer()))
			.andExpect(jsonPath("$.backOnlinePaise").value(30000))
			.andExpect(jsonPath("$.keptPaise").value(30000));
		mvc.perform(post("/api/bookings/" + seat + "/cancel").header("Authorization", asha.bearer())).andExpect(status().isOk());
		settlement.run();
		paymentOf(asha, seat).andExpect(jsonPath("$[0].outcome").value("SPLIT"))
			.andExpect(jsonPath("$[0].refunds[0].onlinePaise").value(30000));
		wallet(driver).andExpect(jsonPath("$.earningsPaise").value(30000));
		healthy(superAdmin());
	}

	@Test
	void creditsPayForSeatsAndUnfinishedCheckoutsGiveThemBack() throws Exception {
		LoggedIn driver = member("9300000001", "Rahul", "MALE");
		LoggedIn other = member("9300000004", "Vikram", "MALE");
		LoggedIn priya = member("9300000002", "Priya", "FEMALE");
		Staff owner = superAdmin();
		mvc.perform(post("/api/admin/members/" + priya.userId() + "/credits").header("Authorization", owner.console())
				.header("X-Confirm-Code", confirmCode(owner)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountPaise\":70000,\"reason\":\"Sorry about the cancelled trip\"}"))
			.andExpect(jsonPath("$.creditsPaise").value(70000));

		// Credits cover the whole seat: paid at once, no checkout needed.
		String first = publishDelhiJaipur(driver, 3, true);
		checkout(priya, first, true).andExpect(jsonPath("$.status").value("PAID"))
			.andExpect(jsonPath("$.gateway").value("CREDITS"))
			.andExpect(jsonPath("$.creditsPaise").value(60000));
		ride(priya, first).andExpect(jsonPath("$.myBooking.status").value("CONFIRMED"));
		wallet(priya).andExpect(jsonPath("$.creditsPaise").value(10000));

		// Credits cover part of the next seat; the member never finishes paying, so the credits come back.
		String second = publishDelhiJaipur(other, 3, true);
		String started = checkout(priya, second, true).andExpect(jsonPath("$.creditsPaise").value(10000))
			.andExpect(jsonPath("$.onlinePaise").value(50000))
			.andReturn().getResponse().getContentAsString();
		wallet(priya).andExpect(jsonPath("$.creditsPaise").value(0));
		jdbc.update("UPDATE bookings SET created_at = now() - interval '1 hour' WHERE status = 'AWAITING_PAYMENT'");
		jdbc.update("UPDATE payments SET hold_until = now() - interval '10 minutes' WHERE status = 'CREATED'");
		housekeeping.run();
		settlement.run();
		wallet(priya).andExpect(jsonPath("$.creditsPaise").value(10000));
		ride(priya, second).andExpect(jsonPath("$.myBooking.status").value("EXPIRED"))
			.andExpect(jsonPath("$.seatsLeftByLeg[0]").value(3));

		// The money arrives anyway (the gateway tells us later): it goes straight back, once.
		String orderId = JsonPath.read(started, "$.orderId");
		String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_fake_late\",\"order_id\":\""
				+ orderId + "\",\"amount\":50000}}}}";
		FakeGateway fake = (FakeGateway) gateways.active().orElseThrow();
		webhook(body, "wrong", "evt_1").andExpect(status().isBadRequest());
		webhook(body, fake.sign(body), "evt_1").andExpect(status().isOk());
		webhook(body, fake.sign(body), "evt_1").andExpect(status().isOk());
		String paymentId = JsonPath.read(started, "$.paymentId");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM refunds WHERE payment_id = ?::uuid", Long.class, paymentId)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT outcome FROM payments WHERE id = ?::uuid", String.class, paymentId)).isEqualTo("REFUNDED");
		healthy(owner);
	}

	@Test
	void theOwnerCanMakeOnlinePaymentTheOnlyWay() throws Exception {
		LoggedIn driver = member("9300000001", "Rahul", "MALE");
		LoggedIn priya = member("9300000002", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		switchOn("payments.cash", false);
		book(priya, rideId, 0, 2, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("pay_online"));
		mvc.perform(get("/api/payments/options").header("Authorization", priya.bearer())).andExpect(jsonPath("$.cash").value(false));

		// Without online payments, paying the driver directly always works.
		switchOn("payments.online", false);
		book(priya, rideId, 0, 2, 1).andExpect(status().isCreated());
		checkout(priya, rideId, false).andExpect(status().isForbidden());
	}

	@Test
	void driversCantLeaveWhileMoneyIsOwedToThem() throws Exception {
		LoggedIn driver = member("9300000001", "Rahul", "MALE");
		LoggedIn priya = member("9300000002", "Priya", "FEMALE");
		String rideId = publishDelhiJaipur(driver, 3, true);
		pay(priya, JsonPath.read(checkout(priya, rideId, false).andReturn().getResponse().getContentAsString(), "$.paymentId"));
		jdbc.update("UPDATE rides SET departure_at = now() - interval '12 hours' WHERE id = ?::uuid", rideId);
		housekeeping.run();
		settlement.run();
		mvc.perform(post("/api/privacy/delete-account").header("Authorization", driver.bearer())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("cannot_leave_yet"));
		mvc.perform(get("/api/privacy/export").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.money.payments", hasSize(1)));
	}

	private void switchOn(String key, boolean on) {
		jdbc.update("UPDATE feature_flags SET enabled = ? WHERE key = ?", on, key);
		featureService.reload();
	}

	private ResultActions checkout(LoggedIn who, String rideId, boolean useCredits) throws Exception {
		return mvc.perform(post("/api/payments/checkout").header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"rideId\":\"%s\",\"fromPosition\":0,\"toPosition\":2,\"seats\":1,\"useCredits\":%b}".formatted(rideId, useCredits)));
	}

	private ResultActions confirm(LoggedIn who, String paymentId, String gatewayPaymentId, String signature) throws Exception {
		return mvc.perform(post("/api/payments/" + paymentId + "/confirm").header("Authorization", who.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"gatewayPaymentId\":\"%s\",\"signature\":\"%s\"}".formatted(gatewayPaymentId, signature)));
	}

	/** Pays with the pretend checkout and confirms, as the website does. */
	private ResultActions pay(LoggedIn who, String paymentId) throws Exception {
		String answer = mvc.perform(post("/api/payments/" + paymentId + "/pretend-pay").header("Authorization", who.bearer()))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		ResultActions first = confirm(who, paymentId, JsonPath.read(answer, "$.gatewayPaymentId"), JsonPath.read(answer, "$.signature"))
			.andExpect(status().isOk());
		// Confirming twice is harmless.
		confirm(who, paymentId, JsonPath.read(answer, "$.gatewayPaymentId"), JsonPath.read(answer, "$.signature"))
			.andExpect(jsonPath("$.status").value("PAID"));
		return first;
	}

	private ResultActions paymentOf(LoggedIn who, String bookingId) throws Exception {
		return mvc.perform(get("/api/payments/bookings").param("ids", bookingId).header("Authorization", who.bearer()));
	}

	private ResultActions wallet(LoggedIn who) throws Exception {
		return mvc.perform(get("/api/wallet").header("Authorization", who.bearer()));
	}

	private ResultActions summary(Staff staff) throws Exception {
		return mvc.perform(get("/api/admin/payments/summary").header("Authorization", staff.console()))
			.andExpect(jsonPath("$.ledgerBalanced").value(true))
			.andExpect(jsonPath("$.walletsMatch").value(true))
			.andExpect(jsonPath("$.escrowMatches").value(true));
	}

	private void healthy(Staff staff) throws Exception {
		summary(staff).andExpect(jsonPath("$.refundsFailed").value(0));
	}

	private ResultActions webhook(String body, String signature, String eventId) throws Exception {
		return mvc.perform(post("/api/payments/webhook").contentType(MediaType.APPLICATION_JSON).content(body)
			.header("X-Razorpay-Signature", signature).header("X-Razorpay-Event-Id", eventId));
	}
}
