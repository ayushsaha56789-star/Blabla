package com.saathchalo.user;

import com.saathchalo.user.UserDtos.MeResponse;
import com.saathchalo.user.UserDtos.PublicProfileResponse;
import com.saathchalo.user.UserDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		return userService.me(userId(jwt));
	}

	@PutMapping("/me")
	public MeResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
		return userService.updateProfile(userId(jwt), request);
	}

	@PostMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public MeResponse uploadAvatar(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
		return userService.updateAvatar(userId(jwt), file);
	}

	@DeleteMapping("/me/avatar")
	public MeResponse deleteAvatar(@AuthenticationPrincipal Jwt jwt) {
		return userService.removeAvatar(userId(jwt));
	}

	@GetMapping("/{id}")
	public PublicProfileResponse publicProfile(@PathVariable UUID id) {
		return userService.publicProfile(id);
	}

	static UUID userId(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}
}
