package com.saathchalo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 16)
	private String phone;

	private String firstName;

	private String lastName;

	private String email;

	@Enumerated(EnumType.STRING)
	private Gender gender;

	private LocalDate dateOfBirth;

	private String bio;

	private String avatarPath;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChatPreference prefChat = ChatPreference.SOMETIMES;

	private boolean prefMusic = true;

	private boolean prefSmoking;

	private boolean prefPets;

	private boolean phoneVerified;

	private boolean idVerified;

	private String city;

	private boolean betaTester;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	public User(String phone) {
		this.phone = phone;
	}

	/** Name and gender are needed before someone can offer or book rides. */
	public boolean isProfileComplete() {
		return firstName != null && !firstName.isBlank() && gender != null;
	}

	/** "Priya S." — first name plus last initial, as shown to other members. */
	public String displayName() {
		if (firstName == null || firstName.isBlank()) {
			return "SaathChalo member";
		}
		if (lastName == null || lastName.isBlank()) {
			return firstName;
		}
		return firstName + " " + lastName.substring(0, 1).toUpperCase() + ".";
	}

	public UUID getId() { return id; }
	public String getPhone() { return phone; }
	public String getFirstName() { return firstName; }
	public void setFirstName(String firstName) { this.firstName = firstName; }
	public String getLastName() { return lastName; }
	public void setLastName(String lastName) { this.lastName = lastName; }
	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public Gender getGender() { return gender; }
	public void setGender(Gender gender) { this.gender = gender; }
	public LocalDate getDateOfBirth() { return dateOfBirth; }
	public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
	public String getBio() { return bio; }
	public void setBio(String bio) { this.bio = bio; }
	public String getAvatarPath() { return avatarPath; }
	public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }
	public ChatPreference getPrefChat() { return prefChat; }
	public void setPrefChat(ChatPreference prefChat) { this.prefChat = prefChat; }
	public boolean isPrefMusic() { return prefMusic; }
	public void setPrefMusic(boolean prefMusic) { this.prefMusic = prefMusic; }
	public boolean isPrefSmoking() { return prefSmoking; }
	public void setPrefSmoking(boolean prefSmoking) { this.prefSmoking = prefSmoking; }
	public boolean isPrefPets() { return prefPets; }
	public void setPrefPets(boolean prefPets) { this.prefPets = prefPets; }
	public boolean isPhoneVerified() { return phoneVerified; }
	public void setPhoneVerified(boolean phoneVerified) { this.phoneVerified = phoneVerified; }
	public boolean isIdVerified() { return idVerified; }
	public String getCity() { return city; }
	public void setCity(String city) { this.city = city; }
	public boolean isBetaTester() { return betaTester; }
	public void setBetaTester(boolean betaTester) { this.betaTester = betaTester; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
