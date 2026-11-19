package com.saathchalo.admin;

import com.saathchalo.access.Perm;
import com.saathchalo.admin.AdminDtos.CancelRideRequest;
import com.saathchalo.auth.RequiresConfirmation;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.PageResult;
import com.saathchalo.rides.RideAdmin;
import com.saathchalo.rides.RideAdmin.AdminRideDetails;
import com.saathchalo.rides.RideAdmin.Show;
import com.saathchalo.rides.RideDtos.AdminRideRow;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/rides")
public class AdminRidesController {

	private final AdminContext context;

	private final RideAdmin rides;

	public AdminRidesController(AdminContext context, RideAdmin rides) {
		this.context = context;
		this.rides = rides;
	}

	@GetMapping
	@PreAuthorize(Perm.USERS_VIEW)
	public PageResult<AdminRideRow> list(@RequestParam(defaultValue = "upcoming") String show,
			@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size) {
		if (q.length() > 80) {
			throw ApiException.badRequest("query_too_long", "Please type a shorter place name.");
		}
		return rides.list(show(show), q, page, size);
	}

	@GetMapping("/{id}")
	@PreAuthorize(Perm.USERS_VIEW)
	public AdminRideDetails details(@PathVariable UUID id) {
		return rides.details(id);
	}

	/** Cancels a ride for everyone on it, for example after a safety report. Needs a fresh 2FA code. */
	@PostMapping("/{id}/cancel")
	@PreAuthorize(Perm.CONTENT_MODERATE)
	@RequiresConfirmation
	public AdminRideDetails cancel(@PathVariable UUID id, @Valid @RequestBody CancelRideRequest request) {
		return rides.cancel(id, request.reason().strip(), context.current().actor());
	}

	private static Show show(String value) {
		try {
			return Show.valueOf(value.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException e) {
			throw ApiException.badRequest("bad_filter", "Choose upcoming, past, cancelled or all.");
		}
	}
}
