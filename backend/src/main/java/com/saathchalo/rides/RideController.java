package com.saathchalo.rides;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.rides.RideDtos.BookRequest;
import com.saathchalo.rides.RideDtos.BookingView;
import com.saathchalo.rides.RideDtos.CancelRideRequest;
import com.saathchalo.rides.RideDtos.PublishRequest;
import com.saathchalo.rides.RideDtos.Quote;
import com.saathchalo.rides.RideDtos.QuoteRequest;
import com.saathchalo.rides.RideDtos.RideDetails;
import com.saathchalo.rides.RideDtos.SearchResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rides")
public class RideController {

	private final RideService rides;

	private final RideSearch search;

	private final BookingService bookings;

	public RideController(RideService rides, RideSearch search, BookingService bookings) {
		this.rides = rides;
		this.search = search;
		this.bookings = bookings;
	}

	/** Public: visitors can look for rides before logging in. */
	@GetMapping("/search")
	@RequiresFeature("rides.booking")
	public SearchResponse search(@RequestParam(required = false) Long fromId, @RequestParam(required = false) String from,
			@RequestParam(required = false) Long toId, @RequestParam(required = false) String to,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) Integer seats) {
		if ((from != null && from.length() > 80) || (to != null && to.length() > 80)) {
			throw ApiException.badRequest("query_too_long", "Please type a shorter place name.");
		}
		return search.search(fromId, from, toId, to, date, seats, CurrentUser.id().orElse(null));
	}

	/** Public: a ride page can be shared. Ladies-only rides are shown to women only. */
	@GetMapping("/{id}")
	@RequiresFeature("rides")
	public RideDetails details(@PathVariable UUID id) {
		return rides.details(id, CurrentUser.id().orElse(null));
	}

	@PostMapping("/quote")
	@RequiresFeature("rides.publish")
	public Quote quote(@Valid @RequestBody QuoteRequest request) {
		return rides.quote(request.placeIds());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@RequiresFeature("rides.publish")
	public RideDetails publish(@Valid @RequestBody PublishRequest request) {
		return rides.publish(CurrentUser.require(), request);
	}

	/** Drivers can always cancel their own rides, even while offering new ones is switched off for them. */
	@PostMapping("/{id}/cancel")
	@RequiresFeature("rides")
	public RideDetails cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelRideRequest request) {
		return rides.cancelByDriver(id, CurrentUser.require(), request == null ? null : request.reason());
	}

	@PostMapping("/{id}/bookings")
	@ResponseStatus(HttpStatus.CREATED)
	@RequiresFeature("rides.booking")
	public BookingView book(@PathVariable UUID id, @Valid @RequestBody BookRequest request) {
		return bookings.book(id, CurrentUser.require(), request);
	}
}
