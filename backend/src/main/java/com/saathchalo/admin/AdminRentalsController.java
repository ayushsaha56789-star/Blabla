package com.saathchalo.admin;

import com.saathchalo.access.Perm;
import com.saathchalo.auth.RequiresConfirmation;
import com.saathchalo.common.ApiException;
import com.saathchalo.rentals.ListingService;
import com.saathchalo.rentals.RentalDtos.AdminListingRow;
import com.saathchalo.rentals.RentalDtos.RentalView;
import com.saathchalo.rentals.RentalDtos.ResolveRequest;
import com.saathchalo.rentals.RentalDtos.ReviewRequest;
import com.saathchalo.rentals.RentalService;
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

/** Rentals in the console: checking listings' documents, disputes and bookings. */
@RestController
@RequestMapping("/api/admin/rentals")
public class AdminRentalsController {

	private final AdminContext context;

	private final ListingService listings;

	private final RentalService rentals;

	public AdminRentalsController(AdminContext context, ListingService listings, RentalService rentals) {
		this.context = context;
		this.listings = listings;
		this.rentals = rentals;
	}

	/** {@code status}: IN_REVIEW (waiting, oldest first) or ALL. */
	@GetMapping("/listings")
	@PreAuthorize(Perm.RENTALS_MANAGE)
	public List<AdminListingRow> listings(@RequestParam(defaultValue = "IN_REVIEW") String status) {
		String s = status.toUpperCase(Locale.ROOT);
		if (!s.equals("IN_REVIEW") && !s.equals("ALL")) {
			throw ApiException.badRequest("bad_filter", "Choose waiting or all.");
		}
		return listings.queue(s);
	}

	@GetMapping("/listings/{id}/documents/{documentId}")
	@PreAuthorize(Perm.RENTALS_MANAGE)
	public ResponseEntity<byte[]> document(@PathVariable UUID id, @PathVariable UUID documentId) {
		return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.body(listings.document(id, documentId, context.current().actor()));
	}

	@PostMapping("/listings/{id}/approve")
	@PreAuthorize(Perm.RENTALS_MANAGE)
	public AdminListingRow approve(@PathVariable UUID id) {
		return listings.approve(id, context.current().actor());
	}

	@PostMapping("/listings/{id}/reject")
	@PreAuthorize(Perm.RENTALS_MANAGE)
	public AdminListingRow reject(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
		return listings.reject(id, request.note(), context.current().actor());
	}

	/** {@code filter}: all (latest first) or disputes. */
	@GetMapping("/bookings")
	@PreAuthorize(Perm.RENTALS_MANAGE)
	public List<RentalView> bookings(@RequestParam(defaultValue = "all") String filter) {
		return rentals.forStaff(filter);
	}

	@GetMapping("/bookings/{id}/photos/{fileName}")
	@PreAuthorize(Perm.RENTALS_MANAGE)
	public ResponseEntity<byte[]> photo(@PathVariable UUID id, @PathVariable String fileName) {
		return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.body(rentals.staffPhoto(id, fileName, context.current().actor()));
	}

	@PostMapping("/bookings/{id}/resolve")
	@PreAuthorize(Perm.RENTALS_MANAGE)
	@RequiresConfirmation
	public RentalView resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest request) {
		return rentals.resolve(id, request.extraCharges(), request.resolution(), context.current().actor());
	}

	@PostMapping("/bookings/{id}/cancel")
	@PreAuthorize(Perm.RENTALS_MANAGE)
	@RequiresConfirmation
	public RentalView cancel(@PathVariable UUID id) {
		return rentals.staffCancel(id, context.current().actor());
	}
}
