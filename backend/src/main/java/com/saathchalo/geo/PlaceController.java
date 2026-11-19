package com.saathchalo.geo;

import com.saathchalo.common.ApiException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Place search for the "From" and "To" boxes. Public on purpose: visitors search rides before logging in, and the data
 * is the public GeoNames list.
 */
@RestController
@RequestMapping("/api/places")
public class PlaceController {

	private static final CacheControl CACHE = CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic();

	private final PlaceDirectory places;

	public PlaceController(PlaceDirectory places) {
		this.places = places;
	}

	@GetMapping
	public ResponseEntity<List<Place>> search(@RequestParam(defaultValue = "") String q,
			@RequestParam(defaultValue = "8") int limit) {
		if (q.length() > 80) {
			throw ApiException.badRequest("query_too_long", "Please type a shorter place name.");
		}
		return ResponseEntity.ok().cacheControl(CACHE).body(places.search(q, limit));
	}

	@GetMapping("/{id}")
	public ResponseEntity<Place> one(@PathVariable long id) {
		return ResponseEntity.ok().cacheControl(CACHE).body(find(places, id));
	}

	/** For "use my location": the nearest known town. */
	@GetMapping("/nearest")
	public Place nearest(@RequestParam double lat, @RequestParam double lng) {
		if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
			throw ApiException.badRequest("bad_position", "That location doesn't look right.");
		}
		return places.nearest(lat, lng)
			.orElseThrow(() -> ApiException.notFound("outside_india",
					"We couldn't find a town near you. SaathChalo covers places in India."));
	}

	/** Towns on the way between two places, suggested as stops when offering a ride. */
	@GetMapping("/along")
	public ResponseEntity<List<Place>> along(@RequestParam long from, @RequestParam long to,
			@RequestParam(defaultValue = "5") int limit) {
		return ResponseEntity.ok()
			.cacheControl(CACHE)
			.body(places.along(find(places, from), find(places, to), Math.clamp(limit, 1, 8)));
	}

	static Place find(PlaceDirectory places, long id) {
		return places.find(id).orElseThrow(() -> ApiException.notFound("place_not_found", "We couldn't find that place."));
	}
}
