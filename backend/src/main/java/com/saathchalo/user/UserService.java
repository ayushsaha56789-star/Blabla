package com.saathchalo.user;

import com.saathchalo.common.ApiException;
import com.saathchalo.config.AppConfig;
import com.saathchalo.files.AvatarStorage;
import com.saathchalo.user.UserDtos.MeResponse;
import com.saathchalo.user.UserDtos.PublicProfileResponse;
import com.saathchalo.user.UserDtos.UpdateProfileRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Member profiles, as used by members themselves (staff use {@link MemberDirectory}). */
@Service
public class UserService {

	static final int MIN_AGE = 18;

	/** The result of signing in with a verified phone number. */
	public record LoginResult(MeResponse me, boolean created) {
	}

	private final UserRepository users;

	private final AvatarStorage avatars;

	private final Clock clock;

	public UserService(UserRepository users, AvatarStorage avatars, Clock clock) {
		this.users = users;
		this.avatars = avatars;
		this.clock = clock;
	}

	/** Signs in, or signs up, the owner of a phone number that has just passed OTP verification. */
	@Transactional
	public LoginResult loginByPhone(String phone) {
		Optional<User> existing = users.findByPhone(phone);
		User user = existing.orElseGet(() -> users.save(new User(phone)));
		user.setPhoneVerified(true);
		return new LoginResult(toMe(user), existing.isEmpty());
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

	@Transactional(readOnly = true)
	public PublicProfileResponse publicProfile(UUID userId) {
		User u = users.findById(userId)
			.orElseThrow(() -> ApiException.notFound("user_not_found", "We couldn't find that member."));
		return new PublicProfileResponse(u.getId(), u.displayName(), avatarUrl(u), u.getBio(), age(u.getDateOfBirth()),
				u.getCity(), u.getPrefChat(), u.isPrefMusic(), u.isPrefSmoking(), u.isPrefPets(), u.isPhoneVerified(),
				u.isIdVerified(), u.getCreatedAt());
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
		u.setEmail(blankToNull(req.email()));
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

	MeResponse toMe(User u) {
		return new MeResponse(u.getId(), u.getPhone(), u.getFirstName(), u.getLastName(), u.displayName(),
				u.getEmail(), u.getGender(), u.getDateOfBirth(), u.getCity(), u.getBio(), avatarUrl(u), u.getPrefChat(),
				u.isPrefMusic(), u.isPrefSmoking(), u.isPrefPets(), u.isPhoneVerified(), u.isIdVerified(),
				u.isBetaTester(), u.isProfileComplete(), u.getCreatedAt());
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
