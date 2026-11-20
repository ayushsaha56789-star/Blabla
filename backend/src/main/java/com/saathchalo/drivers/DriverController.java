package com.saathchalo.drivers;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.drivers.DriverDtos.AreaRequest;
import com.saathchalo.drivers.DriverDtos.BlockRequest;
import com.saathchalo.drivers.DriverDtos.CancelRequest;
import com.saathchalo.drivers.DriverDtos.DriverCard;
import com.saathchalo.drivers.DriverDtos.DriverDetail;
import com.saathchalo.drivers.DriverDtos.DriverRequest;
import com.saathchalo.drivers.DriverDtos.MyDriverProfile;
import com.saathchalo.drivers.DriverDtos.MyTrips;
import com.saathchalo.drivers.DriverDtos.PayRequest;
import com.saathchalo.drivers.DriverDtos.Quote;
import com.saathchalo.drivers.DriverDtos.ReviewRequest;
import com.saathchalo.drivers.DriverDtos.Sort;
import com.saathchalo.drivers.DriverDtos.StartRequest;
import com.saathchalo.drivers.DriverDtos.TakingRequest;
import com.saathchalo.drivers.DriverDtos.TripKind;
import com.saathchalo.drivers.DriverDtos.TripRequest;
import com.saathchalo.drivers.DriverDtos.TripView;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.payments.PaymentDtos.CheckoutView;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

/**
 * Driver partners and hired trips. Finding a driver is public; signing up needs {@code drivers.register}, hiring needs
 * {@code drivers.hire}; handling trips you're part of needs only {@code drivers}.
 */
@RestController
@RequestMapping("/api/drivers")
@RequiresFeature("drivers")
public class DriverController {

	private final DriverProfileService drivers;

	private final DriverTripService trips;

	public DriverController(DriverProfileService drivers, DriverTripService trips) {
		this.drivers = drivers;
		this.trips = trips;
	}

	// ----- Finding a driver (public) -----

	@GetMapping("/search")
	@RequiresFeature("drivers.hire")
	public List<DriverCard> search(@RequestParam long placeId, @RequestParam(defaultValue = "LOCAL") TripKind kind,
			@RequestParam Instant from, @RequestParam(required = false) Integer hours, @RequestParam(required = false) Integer days,
			@RequestParam(defaultValue = "false") boolean automatic, @RequestParam(defaultValue = "false") boolean womenOnly,
			@RequestParam(defaultValue = "BEST") Sort sort) {
		return drivers.search(CurrentUser.id().orElse(null), placeId, kind, from, hours, days, automatic, womenOnly, sort);
	}

	@GetMapping("/profiles/{id}")
	public DriverDetail profile(@PathVariable UUID id) {
		return drivers.detail(CurrentUser.id().orElse(null), id);
	}

	@GetMapping("/profiles/{id}/quote")
	@RequiresFeature("drivers.hire")
	public Quote quote(@PathVariable UUID id, @RequestParam long placeId, @RequestParam TripKind kind, @RequestParam Instant from,
			@RequestParam(required = false) Integer hours, @RequestParam(required = false) Integer days,
			@RequestParam(defaultValue = "false") boolean automatic) {
		return drivers.quote(CurrentUser.id().orElse(null), id, placeId, kind, from, hours, days, automatic);
	}

	// ----- Being a driver partner -----

	/** The member's driver profile, or 204 when they haven't signed up. */
	@GetMapping("/me")
	public ResponseEntity<MyDriverProfile> me() {
		return drivers.mine(CurrentUser.require()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PutMapping("/me")
	@RequiresFeature("drivers.register")
	public MyDriverProfile save(@Valid @RequestBody DriverRequest request) {
		return drivers.save(CurrentUser.require(), request);
	}

	@PostMapping("/me/areas")
	public MyDriverProfile addArea(@Valid @RequestBody AreaRequest request) {
		return drivers.addArea(CurrentUser.require(), request);
	}

	@DeleteMapping("/me/areas/{id}")
	public MyDriverProfile removeArea(@PathVariable UUID id) {
		return drivers.removeArea(CurrentUser.require(), id);
	}

	@PostMapping("/me/police-certificate")
	public MyDriverProfile policeCertificate(@RequestParam("file") MultipartFile file) {
		return drivers.policeCertificate(CurrentUser.require(), file);
	}

	@DeleteMapping("/me/police-certificate")
	public MyDriverProfile removePoliceCertificate() {
		return drivers.removePoliceCertificate(CurrentUser.require());
	}

	@PostMapping("/me/submit")
	@RequiresFeature("drivers.register")
	public MyDriverProfile submit() {
		return drivers.submit(CurrentUser.require());
	}

	@PutMapping("/me/taking-bookings")
	public MyDriverProfile takingBookings(@Valid @RequestBody TakingRequest request) {
		return drivers.takingBookings(CurrentUser.require(), request.on());
	}

	@PostMapping("/me/blocks")
	public MyDriverProfile addBlock(@Valid @RequestBody BlockRequest request) {
		return drivers.addBlock(CurrentUser.require(), request);
	}

	@DeleteMapping("/me/blocks/{id}")
	public MyDriverProfile removeBlock(@PathVariable UUID id) {
		return drivers.removeBlock(CurrentUser.require(), id);
	}

	@DeleteMapping("/me")
	public ResponseEntity<Void> leave() {
		drivers.leave(CurrentUser.require());
		return ResponseEntity.noContent().build();
	}

	// ----- Trips -----

	@PostMapping("/trips")
	@RequiresFeature("drivers.hire")
	@ResponseStatus(HttpStatus.CREATED)
	public TripView request(@Valid @RequestBody TripRequest request) {
		return trips.request(CurrentUser.require(), request);
	}

	@GetMapping("/trips")
	public MyTrips myTrips() {
		return trips.mine(CurrentUser.require());
	}

	@GetMapping("/trips/{id}")
	public TripView trip(@PathVariable UUID id) {
		return trips.get(CurrentUser.require(), id);
	}

	@PostMapping("/trips/{id}/accept")
	public TripView accept(@PathVariable UUID id) {
		return trips.accept(CurrentUser.require(), id);
	}

	@PostMapping("/trips/{id}/decline")
	public TripView decline(@PathVariable UUID id) {
		return trips.decline(CurrentUser.require(), id);
	}

	@PostMapping("/trips/{id}/pay")
	@RequiresFeature("drivers.hire")
	public CheckoutView pay(@PathVariable UUID id, @RequestBody(required = false) PayRequest request) {
		return trips.pay(CurrentUser.require(), id, request == null || !Boolean.FALSE.equals(request.useCredits()));
	}

	@PostMapping("/trips/{id}/on-the-way")
	public TripView onTheWay(@PathVariable UUID id) {
		return trips.onTheWay(CurrentUser.require(), id);
	}

	@PostMapping("/trips/{id}/start")
	public TripView start(@PathVariable UUID id, @Valid @RequestBody(required = false) StartRequest request) {
		return trips.start(CurrentUser.require(), id, request == null ? null : request.code());
	}

	@PostMapping("/trips/{id}/end")
	public TripView end(@PathVariable UUID id) {
		return trips.end(CurrentUser.require(), id);
	}

	@PostMapping("/trips/{id}/cancel")
	public TripView cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelRequest request) {
		return trips.cancel(CurrentUser.require(), id, request == null ? null : request.reason());
	}

	@PostMapping("/trips/{id}/review")
	public TripView review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
		return trips.review(CurrentUser.require(), id, request);
	}
}
