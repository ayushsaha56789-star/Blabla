package com.saathchalo.privacy;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.config.AppProperties;
import com.saathchalo.privacy.PrivacyDtos.DeleteAccountRequest;
import com.saathchalo.privacy.PrivacyDtos.DeletionScheduled;
import com.saathchalo.privacy.PrivacyDtos.LegalContacts;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrivacyController {

	private final PrivacyService privacy;

	private final AppProperties props;

	public PrivacyController(PrivacyService privacy, AppProperties props) {
		this.privacy = privacy;
		this.props = props;
	}

	/** Everything we hold about the member, as a JSON file. */
	@GetMapping("/api/privacy/export")
	public ResponseEntity<Map<String, Object>> export() {
		String file = "saathchalo-my-data-" + LocalDate.now(ZoneId.of("Asia/Kolkata")) + ".json";
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
			.body(privacy.export(CurrentUser.require()));
	}

	@PostMapping("/api/privacy/delete-account")
	public DeletionScheduled deleteAccount(@RequestBody(required = false) DeleteAccountRequest request) {
		return new DeletionScheduled(privacy.requestDeletion(CurrentUser.require(), request == null ? null : request.reason()));
	}

	/** Public on purpose: the law asks us to publish who members can complain to. */
	@GetMapping("/api/legal/contacts")
	public LegalContacts legalContacts() {
		AppProperties.Legal l = props.legal();
		return l == null ? new LegalContacts(null, null, null, null)
				: new LegalContacts(blankToNull(l.company()), blankToNull(l.grievanceOfficer()), blankToNull(l.grievanceEmail()),
						blankToNull(l.address()));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}
}
