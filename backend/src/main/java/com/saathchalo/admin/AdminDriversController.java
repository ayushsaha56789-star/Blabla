package com.saathchalo.admin;

import com.saathchalo.access.Perm;
import com.saathchalo.auth.RequiresConfirmation;
import com.saathchalo.common.ApiException;
import com.saathchalo.drivers.DriverDtos.AdminDriverRow;
import com.saathchalo.drivers.DriverDtos.ApproveRequest;
import com.saathchalo.drivers.DriverDtos.CancelRequest;
import com.saathchalo.drivers.DriverDtos.LicenceNumber;
import com.saathchalo.drivers.DriverDtos.StaffNote;
import com.saathchalo.drivers.DriverDtos.TripView;
import com.saathchalo.drivers.DriverProfileService;
import com.saathchalo.drivers.DriverTripService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Driver partners in the console: checking new drivers, taking one off the road, and their trips. */
@RestController
@RequestMapping("/api/admin/drivers")
public class AdminDriversController {

	private final AdminContext context;

	private final DriverProfileService drivers;

	private final DriverTripService trips;

	public AdminDriversController(AdminContext context, DriverProfileService drivers, DriverTripService trips) {
		this.context = context;
		this.drivers = drivers;
		this.trips = trips;
	}

	/** {@code status}: IN_REVIEW (waiting, oldest first) or ALL. */
	@GetMapping
	@PreAuthorize(Perm.DRIVERS_MANAGE)
	public List<AdminDriverRow> list(@RequestParam(defaultValue = "IN_REVIEW") String status) {
		String s = status.toUpperCase(Locale.ROOT);
		if (!s.equals("IN_REVIEW") && !s.equals("ALL")) {
			throw ApiException.badRequest("bad_filter", "Choose waiting or all.");
		}
		return drivers.forStaff(s);
	}

	/** The full licence number, to compare with the licence photo. Every view is recorded. */
	@PostMapping("/{id}/licence-number")
	@PreAuthorize(Perm.DRIVERS_MANAGE)
	public LicenceNumber licenceNumber(@PathVariable UUID id) {
		return drivers.revealLicence(id, context.current().actor());
	}

	@GetMapping("/{id}/police-certificate")
	@PreAuthorize(Perm.DRIVERS_MANAGE)
	public ResponseEntity<byte[]> policeCertificate(@PathVariable UUID id) {
		return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.body(drivers.policeCertificate(id, context.current().actor()));
	}

	@PostMapping("/{id}/approve")
	@PreAuthorize(Perm.DRIVERS_MANAGE)
	public AdminDriverRow approve(@PathVariable UUID id, @RequestBody(required = false) ApproveRequest request) {
		return drivers.approve(id, request != null && Boolean.TRUE.equals(request.policeChecked()), context.current().actor());
	}

	/** Sends a waiting driver back, or takes a live one off the road (their upcoming trips are cancelled and refunded). */
	@PostMapping("/{id}/reject")
	@PreAuthorize(Perm.DRIVERS_MANAGE)
	@RequiresConfirmation
	public AdminDriverRow reject(@PathVariable UUID id, @Valid @RequestBody StaffNote request) {
		return drivers.reject(id, request.note(), context.current().actor());
	}

	@PostMapping("/{id}/police-checked")
	@PreAuthorize(Perm.DRIVERS_MANAGE)
	public AdminDriverRow policeChecked(@PathVariable UUID id) {
		return drivers.markPoliceChecked(id, context.current().actor());
	}

	@GetMapping("/trips")
	@PreAuthorize(Perm.DRIVERS_MANAGE)
	public List<TripView> trips() {
		return trips.forStaff();
	}

	@PostMapping("/trips/{id}/cancel")
	@PreAuthorize(Perm.DRIVERS_MANAGE)
	@RequiresConfirmation
	public TripView cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelRequest request) {
		return trips.staffCancel(id, request == null ? null : request.reason(), context.current().actor());
	}
}
