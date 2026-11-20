package com.saathchalo.safety;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.safety.SafetyDtos.ContactRequest;
import com.saathchalo.safety.SafetyDtos.ContactView;
import com.saathchalo.safety.SafetyDtos.LocationRequest;
import com.saathchalo.safety.SafetyDtos.ShareView;
import com.saathchalo.safety.SafetyDtos.SharedTrip;
import com.saathchalo.safety.SafetyDtos.SosRequest;
import com.saathchalo.safety.SafetyDtos.SosView;
import com.saathchalo.user.MemberProfiles;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SafetyController {

	private final EmergencyContactService contacts;

	private final TripShareService shares;

	private final SosService sos;

	private final MemberProfiles members;

	public SafetyController(EmergencyContactService contacts, TripShareService shares, SosService sos,
			MemberProfiles members) {
		this.contacts = contacts;
		this.shares = shares;
		this.sos = sos;
		this.members = members;
	}

	// ----- Emergency contacts -----

	@GetMapping("/api/safety/contacts")
	@RequiresFeature("safety.sos")
	public List<ContactView> contacts() {
		return contacts.list(CurrentUser.require());
	}

	@PostMapping("/api/safety/contacts")
	@ResponseStatus(HttpStatus.CREATED)
	@RequiresFeature("safety.sos")
	public ContactView addContact(@Valid @RequestBody ContactRequest request) {
		UUID me = CurrentUser.require();
		return contacts.add(me, request, members.phone(me).orElse(null));
	}

	@DeleteMapping("/api/safety/contacts/{id}")
	@RequiresFeature("safety.sos")
	public ResponseEntity<Void> removeContact(@PathVariable UUID id) {
		contacts.remove(CurrentUser.require(), id);
		return ResponseEntity.noContent().build();
	}

	// ----- Trip sharing -----

	@PostMapping("/api/rides/{rideId}/share")
	@RequiresFeature("safety.trip_share")
	public ShareView share(@PathVariable UUID rideId) {
		return shares.share(CurrentUser.require(), rideId);
	}

	/** The member's current link for this ride, or 204 if they aren't sharing it. */
	@GetMapping("/api/rides/{rideId}/share")
	@RequiresFeature("safety.trip_share")
	public ResponseEntity<ShareView> currentShare(@PathVariable UUID rideId) {
		return shares.current(CurrentUser.require(), rideId).map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@DeleteMapping("/api/rides/{rideId}/share")
	@RequiresFeature("safety.trip_share")
	public ResponseEntity<Void> stopSharing(@PathVariable UUID rideId) {
		shares.stop(CurrentUser.require(), rideId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/rides/{rideId}/share/location")
	@RequiresFeature("safety.trip_share")
	public ShareView shareLocation(@PathVariable UUID rideId, @Valid @RequestBody LocationRequest request) {
		return shares.updateLocation(CurrentUser.require(), rideId, request);
	}

	/** Public on purpose: family open the link without an account. The secret in the link is the key. */
	@GetMapping("/api/shared-trips/{token}")
	@RequiresFeature("safety.trip_share")
	public SharedTrip sharedTrip(@PathVariable String token) {
		return shares.open(token);
	}

	// ----- SOS -----

	@PostMapping("/api/sos")
	@ResponseStatus(HttpStatus.CREATED)
	@RequiresFeature("safety.sos")
	public SosView sos(@Valid @RequestBody SosRequest request) {
		return sos.raise(CurrentUser.require(), request);
	}

	@PostMapping("/api/sos/{id}/location")
	@RequiresFeature("safety.sos")
	public ResponseEntity<Void> sosLocation(@PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
		sos.updateLocation(CurrentUser.require(), id, request);
		return ResponseEntity.noContent().build();
	}
}
