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

	/** Null once an account is erased. */
	@Column(unique = true, length = 16)
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

	private boolean emailVerified;

	private String city;

	private boolean betaTester;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	/** Set while the member's request to delete their account waits out its 30 days. */
	private Instant deletionRequestedAt;

	private Instant deletionDueAt;

	/** When the account was erased. The row stays so other members' trip history still makes sense. */
	private Instant deletedAt;

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
		if (deletedAt != null) {
			return "Former member";
		}
		if (firstName == null || firstName.isBlank()) {
			return "SaathChalo member";
		}
		if (lastName == null || lastName.isBlank()) {
			return firstName;
		}
		return firstName + " " + lastName.substring(0, 1).toUpperCase() + ".";
	}

	public void scheduleDeletion(Instant requestedAt, Instant dueAt) {
		this.deletionRequestedAt = requestedAt;
		this.deletionDueAt = dueAt;
	}

	public void cancelDeletion() {
		this.deletionRequestedAt = null;
		this.deletionDueAt = null;
	}

	/** Removes everything personal. The row stays so other members' trip history still makes sense. */
	public void erase(Instant now) {
		phone = null;
		firstName = null;
		lastName = null;
		email = null;
		gender = null;
		dateOfBirth = null;
		bio = null;
		avatarPath = null;
		city = null;
		prefChat = ChatPreference.SOMETIMES;
		prefMusic = true;
		prefSmoking = false;
		prefPets = false;
		phoneVerified = false;
		idVerified = false;
		emailVerified = false;
		betaTester = false;
		deletionRequestedAt = null;
		deletionDueAt = null;
		deletedAt = now;
	}

	/** Deleting their account, or already erased: hidden from other members. */
	public boolean isGone() {
		return deletionDueAt != null || deletedAt != null;
	}

	public Instant getDeletionRequestedAt() { return deletionRequestedAt; }
	public Instant getDeletionDueAt() { return deletionDueAt; }
	public Instant getDeletedAt() { return deletedAt; }
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
	public boolean isEmailVerified() { return emailVerified; }
	public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
	public void setIdVerified(boolean idVerified) { this.idVerified = idVerified; }
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
