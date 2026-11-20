package com.saathchalo.verification;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.verification.VerificationDtos.ConfirmEmailRequest;
import com.saathchalo.verification.VerificationDtos.DocumentType;
import com.saathchalo.verification.VerificationDtos.EmailCodeSent;
import com.saathchalo.verification.VerificationDtos.IdCheckView;
import com.saathchalo.verification.VerificationDtos.VerificationStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/verification")
public class VerificationController {

	private final IdCheckService ids;

	private final EmailVerificationService emails;

	public VerificationController(IdCheckService ids, EmailVerificationService emails) {
		this.ids = ids;
		this.emails = emails;
	}

	@GetMapping
	@RequiresFeature("verification")
	public VerificationStatus status() {
		return ids.status(CurrentUser.require());
	}

	@PostMapping("/email/send")
	@RequiresFeature("verification.email")
	public EmailCodeSent sendEmailCode() {
		return emails.sendCode(CurrentUser.require());
	}

	@PostMapping("/email/confirm")
	@RequiresFeature("verification.email")
	public VerificationStatus confirmEmail(@Valid @RequestBody ConfirmEmailRequest request) {
		emails.confirm(CurrentUser.require(), request.code());
		return ids.status(CurrentUser.require());
	}

	@PostMapping(value = "/id", consumes = "multipart/form-data")
	@ResponseStatus(HttpStatus.CREATED)
	@RequiresFeature("verification.id")
	public IdCheckView submitId(@RequestParam DocumentType documentType, @RequestParam("photo") MultipartFile photo) {
		return ids.submit(CurrentUser.require(), documentType, photo);
	}
}
