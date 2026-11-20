package com.saathchalo.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.IntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

class VerificationTest extends IntegrationTest {

	@MockitoSpyBean
	EmailSender email;

	@Autowired
	VerificationHousekeeping housekeeping;

	@Test
	void membersConfirmTheirEmailWithACode() throws Exception {
		LoggedIn priya = loginWithProfile("9700000001", "Priya", "FEMALE", "Pune");
		mvc.perform(post("/api/verification/email/send").header("Authorization", priya.bearer()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("no_email"));

		setEmail(priya, "Priya@Example.com");
		String sent = mvc.perform(post("/api/verification/email/send").header("Authorization", priya.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sentTo").value("Pr•••@Example.com"))
			.andExpect(jsonPath("$.devCode", matchesPattern("\\d{6}")))
			.andReturn().getResponse().getContentAsString();
		String code = JsonPath.read(sent, "$.devCode");
		Mockito.verify(email).send(eq("Priya@Example.com"), contains(code), contains(code));
		mvc.perform(post("/api/verification/email/send").header("Authorization", priya.bearer()))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("code_cooldown"));

		String wrong = code.equals("000000") ? "111111" : "000000";
		confirm(priya, wrong).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("wrong_code"))
			.andExpect(jsonPath("$.attemptsLeft").value(4));
		confirm(priya, "12ab").andExpect(status().isBadRequest());
		confirm(priya, code).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
		confirm(priya, code).andExpect(jsonPath("$.code").value("code_expired"));
		mvc.perform(get("/api/users/me").header("Authorization", priya.bearer())).andExpect(jsonPath("$.emailVerified").value(true));
		mvc.perform(get("/api/users/" + priya.userId())).andExpect(jsonPath("$.emailVerified").value(true));

		// A new address has to be confirmed again; the same address (in any case) stays verified.
		setEmail(priya, "priya@example.com").andExpect(jsonPath("$.emailVerified").value(true));
		setEmail(priya, "priya@new.example.com").andExpect(jsonPath("$.emailVerified").value(false));
	}

	@Test
	void staffCheckIdPhotosWhichAreStoredEncryptedAndDeletedLater() throws Exception {
		LoggedIn priya = loginWithProfile("9700000001", "Priya", "FEMALE", "Pune");
		Staff moderator = staff("9700000010", "MODERATOR");
		Staff support = staff("9700000011", "SUPPORT");

		sendId(priya, "DRIVING_LICENCE", png(1200, 800)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"));
		sendId(priya, "PAN", png(1200, 800)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("check_pending"));
		mvc.perform(get("/api/verification").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.idVerified").value(false))
			.andExpect(jsonPath("$.latestIdCheck.status").value("PENDING"))
			.andExpect(jsonPath("$.latestIdCheck.documentType").value("DRIVING_LICENCE"));

		// On disk the photo is encrypted: no JPEG header, and no way to fetch it without staff rights.
		try (Stream<Path> files = Files.list(Path.of("target/test-uploads/documents"))) {
			Path stored = files.filter(p -> p.toString().endsWith(".bin")).findFirst().orElseThrow();
			byte[] head = Files.readAllBytes(stored);
			assertThat(head[0] == (byte) 0xFF && head[1] == (byte) 0xD8).isFalse();
		}
		mvc.perform(get("/api/admin/verifications").header("Authorization", support.console())).andExpect(status().isForbidden());

		String queue = mvc.perform(get("/api/admin/verifications").header("Authorization", moderator.console()))
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].fullName").value("Priya"))
			.andExpect(jsonPath("$[0].gender").value("FEMALE"))
			.andExpect(jsonPath("$[0].photoAvailable").value(true))
			.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(queue, "$[0].id");
		byte[] photo = mvc.perform(get("/api/admin/verifications/" + id + "/photo").header("Authorization", moderator.console()))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/jpeg"))
			.andExpect(header().string("Cache-Control", "no-store"))
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(photo[0]).isEqualTo((byte) 0xFF);
		assertThat(photo[1]).isEqualTo((byte) 0xD8);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'id_check.photo_viewed' AND target_id = ?",
				Long.class, priya.userId())).isEqualTo(1);
		mvc.perform(get("/api/admin/dashboard").header("Authorization", moderator.console())).andExpect(jsonPath("$.idChecksWaiting").value(1));

		mvc.perform(post("/api/admin/verifications/" + id + "/approve").header("Authorization", moderator.console()))
			.andExpect(jsonPath("$.status").value("APPROVED"))
			.andExpect(jsonPath("$.decidedBy").exists());
		mvc.perform(post("/api/admin/verifications/" + id + "/approve").header("Authorization", moderator.console()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("already_decided"));
		mvc.perform(get("/api/users/" + priya.userId())).andExpect(jsonPath("$.idVerified").value(true));
		sendId(priya, "PAN", png(1200, 800)).andExpect(jsonPath("$.code").value("already_verified"));

		// 30 days after the decision the photo is deleted; the decision stays.
		housekeeping.run();
		mvc.perform(get("/api/admin/verifications/" + id + "/photo").header("Authorization", moderator.console())).andExpect(status().isOk());
		jdbc.update("UPDATE id_checks SET decided_at = now() - interval '31 days'");
		housekeeping.run();
		mvc.perform(get("/api/admin/verifications/" + id + "/photo").header("Authorization", moderator.console()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("photo_deleted"));
		mvc.perform(get("/api/admin/verifications?status=approved").header("Authorization", moderator.console()))
			.andExpect(jsonPath("$[0].photoAvailable").value(false));
		mvc.perform(get("/api/users/" + priya.userId())).andExpect(jsonPath("$.idVerified").value(true));
	}

	@Test
	void aFullAadhaarNumberIsDeletedStraightAwayAndTheMemberCanTryAgain() throws Exception {
		LoggedIn priya = loginWithProfile("9700000001", "Priya", "FEMALE", "Pune");
		Staff moderator = staff("9700000010", "MODERATOR");
		String id = JsonPath.read(sendId(priya, "AADHAAR", png(1200, 800)).andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(post("/api/admin/verifications/" + id + "/reject").header("Authorization", moderator.console())
				.contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(post("/api/admin/verifications/" + id + "/reject").header("Authorization", moderator.console())
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"AADHAAR_NOT_MASKED\"}"))
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.photoAvailable").value(false));
		mvc.perform(get("/api/verification").header("Authorization", priya.bearer()))
			.andExpect(jsonPath("$.latestIdCheck.status").value("REJECTED"))
			.andExpect(jsonPath("$.latestIdCheck.rejectReason").value("AADHAAR_NOT_MASKED"));
		sendId(priya, "AADHAAR", png(1200, 800)).andExpect(status().isCreated());

		// Only photos: other files are refused, and so are tiny images.
		LoggedIn ravi = loginWithProfile("9700000002", "Ravi", "MALE", "Delhi");
		mvc.perform(multipart("/api/verification/id").file(new MockMultipartFile("photo", "id.pdf", "application/pdf", "%PDF-1.4".getBytes()))
				.param("documentType", "PAN").header("Authorization", ravi.bearer()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("unsupported_image"));
		sendId(ravi, "PAN", png(60, 40)).andExpect(jsonPath("$.code").value("image_too_small"));
	}

	@Test
	void theOwnerCanSwitchIdChecksOff() throws Exception {
		Staff owner = superAdmin();
		LoggedIn priya = loginWithProfile("9700000001", "Priya", "FEMALE", "Pune");
		mvc.perform(put("/api/admin/features/verification.id").header("Authorization", owner.console())
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
			.andExpect(status().isOk());
		sendId(priya, "PAN", png(1200, 800)).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("feature_unavailable"));
		mvc.perform(get("/api/verification").header("Authorization", priya.bearer())).andExpect(status().isOk());
	}

	private ResultActions setEmail(LoggedIn who, String address) throws Exception {
		return mvc.perform(put("/api/users/me").header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"firstName\":\"Priya\",\"gender\":\"FEMALE\",\"city\":\"Pune\",\"prefChat\":\"SOMETIMES\",\"email\":\"" + address + "\"}"))
			.andExpect(status().isOk());
	}

	private ResultActions confirm(LoggedIn who, String code) throws Exception {
		return mvc.perform(post("/api/verification/email/confirm").header("Authorization", who.bearer())
			.contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
	}

	private ResultActions sendId(LoggedIn who, String type, byte[] image) throws Exception {
		return mvc.perform(multipart("/api/verification/id").file(new MockMultipartFile("photo", "id.png", "image/png", image))
			.param("documentType", type).header("Authorization", who.bearer()));
	}

	private static byte[] png(int width, int height) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
		return out.toByteArray();
	}
}
