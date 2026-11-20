package com.saathchalo.user;

import com.saathchalo.common.ApiException;
import com.saathchalo.config.AppConfig;
import com.saathchalo.files.AvatarStorage;
import com.saathchalo.user.UserDtos.MeResponse;
import com.saathchalo.user.UserDtos.PublicProfileResponse;
import com.saathchalo.user.UserDtos.UpdateProfileRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Member profiles, as used by members themselves (staff use {@link MemberDirectory}). */
@Service
public class UserService {

	static final int MIN_AGE = 18;

	/** The result of signing in with a verified phone number. {@code restored}: it cancelled a pending deletion. */
	public record LoginResult(MeResponse me, boolean created, boolean restored) {
	}

	/** What a member gave us when signing up, kept for a while after they leave (see the privacy module). */
	public record RegistrationRecord(String phone, String firstName, String lastName, String email, Instant joinedAt) {
	}

	private final UserRepository users;

	private final AvatarStorage avatars;

	private final Clock clock;

	private final ObjectProvider<RatingProvider> ratings;

	private final MemberAccessService access;

	private final ApplicationEventPublisher events;

	public UserService(UserRepository users, AvatarStorage avatars, Clock clock, ObjectProvider<RatingProvider> ratings,
			MemberAccessService access, ApplicationEventPublisher events) {
		this.users = users;
		this.avatars = avatars;
		this.clock = clock;
		this.ratings = ratings;
		this.access = access;
		this.events = events;
	}

	/** Signs in, or signs up, the owner of a phone number that has just passed OTP verification. */
	@Transactional
	public LoginResult loginByPhone(String phone) {
		Optional<User> existing = users.findByPhone(phone);
		User user = existing.orElseGet(() -> users.save(new User(phone)));
		user.setPhoneVerified(true);
		// Logging in during the 30 days after asking to delete the account cancels the deletion.
		boolean restored = user.getDeletionDueAt() != null;
		if (restored) {
			user.cancelDeletion();
			access.evict(user.getId());
		}
		return new LoginResult(toMe(user), existing.isEmpty(), restored);
	}

	@Transactional(readOnly = true)
	public Optional<UUID> idByPhone(String phone) {
		return users.findByPhone(phone).map(User::getId);
	}

	@Transactional(readOnly = true)
	public MeResponse me(UUID userId) {
		return toMe(load(userId));
	}

	@Transactional(readOnly = true)
	public String displayName(UUID userId) {
		return users.findById(userId).map(User::displayName).orElse("Deleted member");
	}

	/** A member's profile page. Members who deleted (or are deleting) their account are gone from SaathChalo. */
	@Transactional(readOnly = true)
	public PublicProfileResponse profilePage(UUID userId) {
		if (users.findById(userId).filter(User::isGone).isPresent()) {
			throw ApiException.notFound("user_not_found", "This member isn't on SaathChalo any more.");
		}
		return publicProfile(userId);
	}

	/** How a member appears on rides; still works for former members (shown as "Former member"). */
	@Transactional(readOnly = true)
	public PublicProfileResponse publicProfile(UUID userId) {
		User u = users.findById(userId)
			.orElseThrow(() -> ApiException.notFound("user_not_found", "We couldn't find that member."));
		RatingProvider provider = ratings.getIfAvailable();
		RatingProvider.Rating rating = provider == null ? null : provider.ratings(List.of(userId)).get(userId);
		return new PublicProfileResponse(u.getId(), u.displayName(), avatarUrl(u), u.getBio(), age(u.getDateOfBirth()),
				u.getCity(), u.getPrefChat(), u.isPrefMusic(), u.isPrefSmoking(), u.isPrefPets(), u.isPhoneVerified(),
				u.isIdVerified(), u.getCreatedAt(), rating == null ? null : rating.average(), rating == null ? 0 : rating.count(),
				u.isEmailVerified());
	}

	@Transactional
	public MeResponse updateProfile(UUID userId, UpdateProfileRequest req) {
		Integer age = age(req.dateOfBirth());
		if (age != null && age < MIN_AGE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "too_young",
					"You must be at least " + MIN_AGE + " years old to use SaathChalo.");
		}
		User u = load(userId);
		u.setFirstName(req.firstName().strip());
		u.setLastName(blankToNull(req.lastName()));
		String email = blankToNull(req.email());
		if (email == null || !email.equalsIgnoreCase(u.getEmail() == null ? "" : u.getEmail())) {
			// A new address has to be confirmed again.
			u.setEmailVerified(false);
		}
		u.setEmail(email);
		u.setGender(req.gender());
		u.setDateOfBirth(req.dateOfBirth());
		u.setCity(cleanCity(req.city()));
		u.setBio(blankToNull(req.bio()));
		u.setPrefChat(req.prefChat());
		u.setPrefMusic(Boolean.TRUE.equals(req.prefMusic()));
		u.setPrefSmoking(Boolean.TRUE.equals(req.prefSmoking()));
		u.setPrefPets(Boolean.TRUE.equals(req.prefPets()));
		return toMe(u);
	}

	@Transactional
	public MeResponse updateAvatar(UUID userId, MultipartFile file) {
		User u = load(userId);
		String previous = u.getAvatarPath();
		u.setAvatarPath(avatars.store(userId, file));
		avatars.deleteQuietly(previous);
		return toMe(u);
	}

	@Transactional
	public MeResponse removeAvatar(UUID userId) {
		User u = load(userId);
		avatars.deleteQuietly(u.getAvatarPath());
		u.setAvatarPath(null);
		return toMe(u);
	}

	// ----- Verification (used by the verification module) -----

	/** The facts staff compare with an ID document. */
	public record Identity(String firstName, String lastName, Gender gender, LocalDate dateOfBirth) {
	}

	@Transactional(readOnly = true)
	public Optional<String> email(UUID userId) {
		return users.findById(userId).map(User::getEmail);
	}

	@Transactional(readOnly = true)
	public Optional<Identity> identity(UUID userId) {
		return users.findById(userId).map(u -> new Identity(u.getFirstName(), u.getLastName(), u.getGender(), u.getDateOfBirth()));
	}

	/** Marks the address verified, as long as it is still the one on the profile. */
	@Transactional
	public boolean markEmailVerified(UUID userId, String email) {
		User u = load(userId);
		if (u.getEmail() == null || !u.getEmail().equalsIgnoreCase(email)) {
			return false;
		}
		u.setEmailVerified(true);
		return true;
	}

	@Transactional
	public void setIdVerified(UUID userId, boolean verified) {
		load(userId).setIdVerified(verified);
		access.evict(userId);
	}

	// ----- Deleting an account (used by the privacy module) -----

	/** Switches the account off now and schedules its erasure. Ends every session. */
	@Transactional
	public void scheduleDeletion(UUID userId, Instant dueAt) {
		User u = load(userId);
		u.scheduleDeletion(clock.instant(), dueAt);
		access.evict(userId);
		events.publishEvent(new MemberLeaving(userId));
	}

	/** Accounts whose 30 days are over, oldest first. */
	@Transactional(readOnly = true)
	public List<UUID> dueForErasure(int max) {
		return users.findDueForErasure(clock.instant(), PageRequest.of(0, max));
	}

	@Transactional(readOnly = true)
	public Optional<RegistrationRecord> registrationRecord(UUID userId) {
		return users.findById(userId).filter(u -> u.getDeletedAt() == null)
			.map(u -> new RegistrationRecord(u.getPhone(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getCreatedAt()));
	}

	MeResponse toMe(User u) {
		return new MeResponse(u.getId(), u.getPhone(), u.getFirstName(), u.getLastName(), u.displayName(),
				u.getEmail(), u.getGender(), u.getDateOfBirth(), u.getCity(), u.getBio(), avatarUrl(u), u.getPrefChat(),
				u.isPrefMusic(), u.isPrefSmoking(), u.isPrefPets(), u.isPhoneVerified(), u.isIdVerified(),
				u.isBetaTester(), u.isProfileComplete(), u.getCreatedAt(), u.isEmailVerified());
	}

	private User load(UUID userId) {
		// A valid token for a member that no longer exists means the session is stale.
		return users.findById(userId)
			.orElseThrow(() -> ApiException.unauthorized("session_invalid", "Please log in again."));
	}

	Integer age(LocalDate dateOfBirth) {
		return dateOfBirth == null ? null : Period.between(dateOfBirth, LocalDate.now(clock.withZone(AppConfig.ZONE))).getYears();
	}

	static String avatarUrl(User u) {
		return u.getAvatarPath() == null ? null : "/api/files/" + u.getAvatarPath();
	}

	private static String cleanCity(String city) {
		String c = blankToNull(city);
		return c == null ? null : c.replaceAll("\\s+", " ");
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}
}
