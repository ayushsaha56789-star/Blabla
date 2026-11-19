package com.saathchalo.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class UserDtos {

	private UserDtos() {
	}

	/** Everything about the logged-in member — only ever sent to that member. */
	public record MeResponse(UUID id, String phone, String firstName, String lastName, String displayName,
			String email, Gender gender, LocalDate dateOfBirth, String city, String bio, String avatarUrl,
			ChatPreference prefChat, boolean prefMusic, boolean prefSmoking, boolean prefPets,
			boolean phoneVerified, boolean idVerified, boolean betaTester, boolean profileComplete, Instant memberSince) {
	}

	/** What other members can see. No phone number, email or exact birth date. */
	public record PublicProfileResponse(UUID id, String displayName, String avatarUrl, String bio, Integer age,
			String city, ChatPreference prefChat, boolean prefMusic, boolean prefSmoking, boolean prefPets,
			boolean phoneVerified, boolean idVerified, Instant memberSince) {
	}

	/** Letters in any script (so Devanagari, Tamil, … names work), spaces, dots, apostrophes, hyphens. Empty is left to @NotBlank. */
	private static final String OPTIONAL_NAME = "^$|^[\\p{L}\\p{M} .'-]+$";

	public record UpdateProfileRequest(
			@NotBlank(message = "Please enter your first name")
			@Size(max = 50, message = "First name is too long")
			@Pattern(regexp = OPTIONAL_NAME, message = "Please use letters only") String firstName,
			@Size(max = 50, message = "Last name is too long")
			@Pattern(regexp = OPTIONAL_NAME, message = "Please use letters only") String lastName,
			@Email(message = "Please enter a valid email") @Size(max = 254) String email,
			@NotNull(message = "Please choose an option") Gender gender,
			@Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
			@Size(max = 60, message = "City name is too long")
			@Pattern(regexp = OPTIONAL_NAME, message = "Please use letters only") String city,
			@Size(max = 500, message = "Keep your bio under 500 characters") String bio,
			@NotNull(message = "Please choose an option") ChatPreference prefChat,
			Boolean prefMusic, Boolean prefSmoking, Boolean prefPets) {
	}
}
