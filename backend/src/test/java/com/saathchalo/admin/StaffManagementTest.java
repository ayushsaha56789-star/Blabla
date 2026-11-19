package com.saathchalo.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saathchalo.IntegrationTest;
import com.saathchalo.access.AccessService;
import com.saathchalo.audit.Actor;
import com.saathchalo.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class StaffManagementTest extends IntegrationTest {

	@Autowired
	private AccessService access;

	@Test
	void addingStaffNeedsAConfirmationCodeAndAnExistingMember() throws Exception {
		Staff owner = superAdmin();
		String body = "{\"phone\":\"98765 43210\",\"role\":\"MODERATOR\"}";
		mvc.perform(post("/api/admin/staff").header("Authorization", owner.console())
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("confirmation_required"));
		mvc.perform(post("/api/admin/staff").header("Authorization", owner.console()).header("X-Confirm-Code", confirmCode(owner))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("member_not_found"));

		LoggedIn priya = login("9876543210");
		mvc.perform(post("/api/admin/staff").header("Authorization", owner.console()).header("X-Confirm-Code", confirmCode(owner))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("MODERATOR"))
			.andExpect(jsonPath("$.totpEnabled").value(false));
		mvc.perform(get("/api/auth/staff/status").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.role").value("MODERATOR"));
		mvc.perform(get("/api/admin/staff").header("Authorization", owner.console()))
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].role").value("SUPER_ADMIN"))
			.andExpect(jsonPath("$[0].you").value(true));
	}

	@Test
	void nobodyChangesTheirOwnRoleAndSuperAdminsCanHandOver() throws Exception {
		Staff owner = superAdmin();
		mvc.perform(put("/api/admin/staff/" + owner.id()).header("Authorization", owner.console())
				.header("X-Confirm-Code", confirmCode(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("self_change"));

		Staff second = staff("9000000041", "ADMIN");
		mvc.perform(put("/api/admin/staff/" + second.id()).header("Authorization", owner.console())
				.header("X-Confirm-Code", confirmCode(owner)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"SUPER_ADMIN\"}"))
			.andExpect(status().isOk());
		// With two super admins, the new one may move the owner to Admin.
		mvc.perform(put("/api/admin/staff/" + owner.id()).header("Authorization", second.console())
				.header("X-Confirm-Code", confirmCode(second)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\":\"ADMIN\"}"))
			.andExpect(status().isOk());
		// The owner, now an Admin, can no longer manage staff.
		mvc.perform(get("/api/admin/staff").header("Authorization", owner.console())).andExpect(status().isForbidden());
	}

	@Test
	void theLastSuperAdminCanNeverBeDemotedOrRemoved() throws Exception {
		Staff owner = superAdmin();
		Actor someone = new Actor(null, "Test");
		assertThatThrownBy(() -> access.changeRole(owner.id(), "ADMIN", someone, "Owner"))
			.isInstanceOf(ApiException.class).hasMessageContaining("at least one super admin");
		assertThatThrownBy(() -> access.remove(owner.id(), someone, "Owner"))
			.isInstanceOf(ApiException.class).hasMessageContaining("at least one super admin");
	}

	@Test
	void rolesListTheirPermissionsInPlainWords() throws Exception {
		Staff support = staff("9000000042", "SUPPORT");
		mvc.perform(get("/api/admin/roles").header("Authorization", support.console()))
			.andExpect(jsonPath("$[0].code").value("SUPER_ADMIN"))
			.andExpect(jsonPath("$[3].code").value("SUPPORT"))
			.andExpect(jsonPath("$[3].permissions[?(@.code == 'users.view_contact')].label")
				.value("Reveal a member's phone number"));
	}
}
