package com.saathchalo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.saathchalo.IntegrationTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Every console API × every role, checked against the permission table in docs/ARCHITECTURE.md. */
class PermissionMatrixTest extends IntegrationTest {

	/** {@code request} builds a fresh request each time (builders collect headers, so they can't be reused). */
	private record Check(String name, Supplier<MockHttpServletRequestBuilder> request, int superAdmin, int admin,
			int moderator, int support) {
	}

	@Test
	void eachRoleCanDoExactlyWhatItsPermissionsAllow() throws Exception {
		Map<String, Staff> staff = new LinkedHashMap<>();
		staff.put("SUPER_ADMIN", superAdmin());
		staff.put("ADMIN", staff("9000000002", "ADMIN"));
		staff.put("MODERATOR", staff("9000000003", "MODERATOR"));
		staff.put("SUPPORT", staff("9000000004", "SUPPORT"));
		LoggedIn member = login("9000000005");
		String m = "/api/admin/members/" + member.userId();

		List<Check> checks = List.of(
				new Check("me", () -> get("/api/admin/me"), 200, 200, 200, 200),
				new Check("roles", () -> get("/api/admin/roles"), 200, 200, 200, 200),
				new Check("dashboard", () -> get("/api/admin/dashboard"), 200, 200, 200, 200),
				new Check("member search", () -> get("/api/admin/members?q=9000"), 200, 200, 200, 200),
				new Check("member details", () -> get(m), 200, 200, 200, 200),
				new Check("reveal phone", () -> post(m + "/contact"), 200, 200, 403, 200),
				new Check("features", () -> get("/api/admin/features"), 200, 403, 403, 403),
				new Check("staff list", () -> get("/api/admin/staff"), 200, 403, 403, 403),
				new Check("audit log", () -> get("/api/admin/audit"), 200, 200, 403, 403),
				new Check("audit verify", () -> get("/api/admin/audit/verify"), 200, 200, 403, 403),
				// Areas added in phases 3–10.
				new Check("rides", () -> get("/api/admin/rides"), 200, 200, 200, 200),
				new Check("SOS alerts", () -> get("/api/admin/safety/sos"), 200, 200, 200, 200),
				new Check("help requests", () -> get("/api/admin/support/tickets"), 200, 200, 200, 200),
				new Check("reports", () -> get("/api/admin/reports"), 200, 200, 200, 403),
				new Check("ID checks", () -> get("/api/admin/verifications"), 200, 200, 200, 403),
				new Check("events", () -> get("/api/admin/events"), 200, 200, 200, 403),
				new Check("promotions", () -> get("/api/admin/promotions"), 200, 200, 200, 403),
				new Check("rental listings", () -> get("/api/admin/rentals/listings"), 200, 200, 403, 403),
				new Check("rental bookings", () -> get("/api/admin/rentals/bookings"), 200, 200, 403, 403),
				new Check("drivers", () -> get("/api/admin/drivers"), 200, 200, 403, 403),
				new Check("driver trips", () -> get("/api/admin/drivers/trips"), 200, 200, 403, 403),
				new Check("payments summary", () -> get("/api/admin/payments/summary"), 200, 200, 403, 403),
				new Check("payments", () -> get("/api/admin/payments"), 200, 200, 403, 403),
				new Check("owed to drivers", () -> get("/api/admin/payouts/owed"), 200, 200, 403, 403),
				new Check("payouts", () -> get("/api/admin/payouts"), 200, 200, 403, 403));

		List<String> wrong = new ArrayList<>();
		for (Check check : checks) {
			int[] expected = { check.superAdmin(), check.admin(), check.moderator(), check.support() };
			int i = 0;
			for (Map.Entry<String, Staff> s : staff.entrySet()) {
				int actual = status(check.request().get().header("Authorization", s.getValue().console()));
				if (actual != expected[i]) {
					wrong.add(check.name() + " as " + s.getKey() + ": expected " + expected[i] + " got " + actual);
				}
				i++;
			}
			int asMember = status(check.request().get().header("Authorization", member.bearer()));
			if (asMember != 403) {
				wrong.add(check.name() + " as plain member: expected 403 got " + asMember);
			}
			int anonymous = status(check.request().get());
			if (anonymous != 401) {
				wrong.add(check.name() + " anonymously: expected 401 got " + anonymous);
			}
		}

		// Restricting needs users.restrict: everyone except Support. Each role restricts its own fresh member.
		int n = 6;
		for (Map.Entry<String, Staff> s : staff.entrySet()) {
			LoggedIn target = login("900000001" + n++);
			int actual = status(post("/api/admin/members/" + target.userId() + "/restrictions")
				.header("Authorization", s.getValue().console())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"area\":\"games\",\"reason\":\"Spamming game rooms\"}"));
			int expected = s.getKey().equals("SUPPORT") ? 403 : 200;
			if (actual != expected) {
				wrong.add("restrict as " + s.getKey() + ": expected " + expected + " got " + actual);
			}
		}
		assertThat(wrong).isEmpty();
	}

	private int status(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request).andReturn().getResponse().getStatus();
	}
}
