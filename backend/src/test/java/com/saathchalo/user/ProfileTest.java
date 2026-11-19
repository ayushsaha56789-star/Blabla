package com.saathchalo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.IntegrationTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class ProfileTest extends IntegrationTest {

	private static final String PROFILE = """
			{"firstName":"Priya","lastName":"sharma","email":"","gender":"FEMALE","dateOfBirth":"%s",
			 "bio":"  Weekend trips to Jaipur  ","prefChat":"CHATTY","prefMusic":true,"prefSmoking":false,"prefPets":true}
			""";

	@Test
	void completingTheProfile() throws Exception {
		LoggedIn me = login("9876543210");
		mvc.perform(put("/api/users/me").header("Authorization", me.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(PROFILE.formatted("1998-04-12")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Priya S."))
			.andExpect(jsonPath("$.email").doesNotExist())
			.andExpect(jsonPath("$.bio").value("Weekend trips to Jaipur"))
			.andExpect(jsonPath("$.profileComplete").value(true));
	}

	@Test
	void validationErrorsPointAtTheField() throws Exception {
		LoggedIn me = login("9876543210");
		mvc.perform(put("/api/users/me").header("Authorization", me.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"\",\"gender\":\"FEMALE\",\"prefChat\":\"QUIET\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.firstName").value("Please enter your first name"));
	}

	@Test
	void under18sAreRefused() throws Exception {
		LoggedIn me = login("9876543210");
		mvc.perform(put("/api/users/me").header("Authorization", me.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content(PROFILE.formatted(LocalDate.now().minusYears(17))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("too_young"));
	}

	@Test
	void publicProfileHidesPrivateDetails() throws Exception {
		LoggedIn me = login("9876543210");
		mvc.perform(put("/api/users/me").header("Authorization", me.bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content(PROFILE.formatted("1998-04-12")));

		mvc.perform(get("/api/users/" + me.userId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Priya S."))
			.andExpect(jsonPath("$.age").isNumber())
			.andExpect(jsonPath("$.phone").doesNotExist())
			.andExpect(jsonPath("$.email").doesNotExist())
			.andExpect(jsonPath("$.dateOfBirth").doesNotExist());
	}

	@Test
	void avatarIsCroppedToASquareJpeg() throws Exception {
		LoggedIn me = login("9876543210");
		BufferedImage wide = new BufferedImage(900, 500, BufferedImage.TYPE_INT_ARGB);
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(wide, "png", png);

		String body = mvc.perform(multipart("/api/users/me/avatar")
				.file(new MockMultipartFile("file", "me.png", "image/png", png.toByteArray()))
				.header("Authorization", me.bearer()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		String url = JsonPath.read(body, "$.avatarUrl");

		byte[] stored = mvc.perform(get(url))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("image/jpeg"))
			.andReturn().getResponse().getContentAsByteArray();
		BufferedImage avatar = ImageIO.read(new ByteArrayInputStream(stored));
		assertThat(avatar.getWidth()).isEqualTo(400);
		assertThat(avatar.getHeight()).isEqualTo(400);

		mvc.perform(delete("/api/users/me/avatar").header("Authorization", me.bearer()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.avatarUrl").doesNotExist());
		mvc.perform(get(url)).andExpect(status().isNotFound());
	}

	@Test
	void nonImagesAreRefused() throws Exception {
		LoggedIn me = login("9876543210");
		mvc.perform(multipart("/api/users/me/avatar")
				.file(new MockMultipartFile("file", "evil.png", "image/png", "<script>alert(1)</script>".getBytes()))
				.header("Authorization", me.bearer()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("unsupported_image"));
	}
}
