package com.saathchalo.rentals;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.payments.PaymentDtos.CheckoutView;
import com.saathchalo.rentals.RentalDtos.BlockRequest;
import com.saathchalo.rentals.RentalDtos.DisputeRequest;
import com.saathchalo.rentals.RentalDtos.DocumentKind;
import com.saathchalo.rentals.RentalDtos.Fuel;
import com.saathchalo.rentals.RentalDtos.ListingCard;
import com.saathchalo.rentals.RentalDtos.ListingDetail;
import com.saathchalo.rentals.RentalDtos.ListingRequest;
import com.saathchalo.rentals.RentalDtos.Mode;
import com.saathchalo.rentals.RentalDtos.MyListing;
import com.saathchalo.rentals.RentalDtos.MyRentals;
import com.saathchalo.rentals.RentalDtos.PayRequest;
import com.saathchalo.rentals.RentalDtos.Quote;
import com.saathchalo.rentals.RentalDtos.RentRequest;
import com.saathchalo.rentals.RentalDtos.RentalView;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/rentals")
@RequiresFeature("rentals")
public class RentalController {

	private final ListingService listings;

	private final RentalService rentals;

	public RentalController(ListingService listings, RentalService rentals) {
		this.listings = listings;
		this.rentals = rentals;
	}

	// ----- Finding a car (public) -----

	@GetMapping("/search")
	public List<ListingCard> search(@RequestParam long placeId, @RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(required = false) Mode mode) {
		return listings.search(CurrentUser.id().orElse(null), placeId, from, to, mode);
	}

	@GetMapping("/listings/{id}")
	public ListingDetail listing(@PathVariable UUID id) {
		return listings.detail(CurrentUser.id().orElse(null), id);
	}

	@GetMapping("/listings/{id}/quote")
	public Quote quote(@PathVariable UUID id, @RequestParam Instant from, @RequestParam Instant to, @RequestParam Mode mode) {
		return listings.quote(CurrentUser.id().orElse(null), id, from, to, mode);
	}

	// ----- Owners: listings -----

	@GetMapping("/listings/mine")
	public List<MyListing> myListings() {
		return listings.mine(CurrentUser.require());
	}

	@GetMapping("/listings/{id}/manage")
	public MyListing myListing(@PathVariable UUID id) {
		return listings.myListing(CurrentUser.require(), id);
	}

	@PostMapping("/listings")
	@ResponseStatus(HttpStatus.CREATED)
	public MyListing create(@Valid @RequestBody ListingRequest request) {
		return listings.create(CurrentUser.require(), request);
	}

	@PutMapping("/listings/{id}")
	public MyListing update(@PathVariable UUID id, @Valid @RequestBody ListingRequest request) {
		return listings.update(CurrentUser.require(), id, request);
	}

	@PostMapping(value = "/listings/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public MyListing addPhoto(@PathVariable UUID id, @RequestParam("photo") MultipartFile photo) {
		return listings.addPhoto(CurrentUser.require(), id, photo);
	}

	@DeleteMapping("/listings/{id}/photos/{photoId}")
	public MyListing removePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
		return listings.removePhoto(CurrentUser.require(), id, photoId);
	}

	@PostMapping(value = "/listings/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public MyListing addDocument(@PathVariable UUID id, @RequestParam DocumentKind kind, @RequestParam("file") MultipartFile file) {
		return listings.addDocument(CurrentUser.require(), id, kind, file);
	}

	@PostMapping("/listings/{id}/submit")
	public MyListing submit(@PathVariable UUID id) {
		return listings.submit(CurrentUser.require(), id);
	}

	@PostMapping("/listings/{id}/pause")
	public MyListing pause(@PathVariable UUID id) {
		return listings.pause(CurrentUser.require(), id);
	}

	@PostMapping("/listings/{id}/resume")
	public MyListing resume(@PathVariable UUID id) {
		return listings.resume(CurrentUser.require(), id);
	}

	@DeleteMapping("/listings/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(@PathVariable UUID id) {
		listings.remove(CurrentUser.require(), id);
	}

	@PostMapping("/listings/{id}/blocks")
	public MyListing addBlock(@PathVariable UUID id, @Valid @RequestBody BlockRequest request) {
		return listings.addBlock(CurrentUser.require(), id, request);
	}

	@DeleteMapping("/listings/{id}/blocks/{blockId}")
	public MyListing removeBlock(@PathVariable UUID id, @PathVariable UUID blockId) {
		return listings.removeBlock(CurrentUser.require(), id, blockId);
	}

	// ----- Bookings -----

	@PostMapping("/bookings")
	@ResponseStatus(HttpStatus.CREATED)
	public RentalView rent(@Valid @RequestBody RentRequest request) {
		return rentals.rent(CurrentUser.require(), request);
	}

	@GetMapping("/bookings")
	public MyRentals myRentals() {
		return rentals.mine(CurrentUser.require());
	}

	@GetMapping("/bookings/{id}")
	public RentalView rental(@PathVariable UUID id) {
		return rentals.get(CurrentUser.require(), id);
	}

	@PostMapping("/bookings/{id}/accept")
	public RentalView accept(@PathVariable UUID id) {
		return rentals.accept(CurrentUser.require(), id);
	}

	@PostMapping("/bookings/{id}/decline")
	public RentalView decline(@PathVariable UUID id) {
		return rentals.decline(CurrentUser.require(), id);
	}

	@PostMapping("/bookings/{id}/cancel")
	public RentalView cancel(@PathVariable UUID id) {
		return rentals.cancel(CurrentUser.require(), id);
	}

	@PostMapping("/bookings/{id}/pay")
	public CheckoutView pay(@PathVariable UUID id, @RequestBody(required = false) PayRequest request) {
		return rentals.pay(CurrentUser.require(), id, request != null && Boolean.TRUE.equals(request.useCredits()));
	}

	@PostMapping(value = "/bookings/{id}/pickup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public RentalView pickup(@PathVariable UUID id, @RequestParam int odometer, @RequestParam Fuel fuel,
			@RequestParam(required = false) String notes, @RequestParam(value = "photos", required = false) List<MultipartFile> photos) {
		return rentals.pickup(CurrentUser.require(), id, odometer, fuel, notes, photos);
	}

	@PostMapping(value = "/bookings/{id}/return", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public RentalView returnCar(@PathVariable UUID id, @RequestParam int odometer, @RequestParam Fuel fuel,
			@RequestParam(required = false) String notes, @RequestParam(defaultValue = "0") int otherCharges,
			@RequestParam(required = false) String chargesNote,
			@RequestParam(value = "photos", required = false) List<MultipartFile> photos) {
		return rentals.returnCar(CurrentUser.require(), id, odometer, fuel, notes, otherCharges, chargesNote, photos);
	}

	@PostMapping("/bookings/{id}/accept-return")
	public RentalView acceptReturn(@PathVariable UUID id) {
		return rentals.acceptReturn(CurrentUser.require(), id);
	}

	@PostMapping("/bookings/{id}/dispute")
	public RentalView dispute(@PathVariable UUID id, @Valid @RequestBody DisputeRequest request) {
		return rentals.dispute(CurrentUser.require(), id, request.note());
	}

	@GetMapping("/bookings/{id}/photos/{fileName}")
	public ResponseEntity<byte[]> photo(@PathVariable UUID id, @PathVariable String fileName) {
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.cacheControl(CacheControl.noStore())
			.header("X-Content-Type-Options", "nosniff")
			.body(rentals.photo(CurrentUser.require(), id, fileName));
	}
}
