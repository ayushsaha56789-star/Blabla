package com.saathchalo.reviews;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.reviews.ReviewDtos.PendingReview;
import com.saathchalo.reviews.ReviewDtos.ReviewPage;
import com.saathchalo.reviews.ReviewDtos.ReviewView;
import com.saathchalo.reviews.ReviewDtos.WriteRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresFeature("reviews")
public class ReviewController {

	private final ReviewService reviews;

	public ReviewController(ReviewService reviews) {
		this.reviews = reviews;
	}

	/** Co-travellers from recent trips this member can still review. */
	@GetMapping("/api/reviews/pending")
	public List<PendingReview> pending() {
		return reviews.pending(CurrentUser.require());
	}

	@PostMapping("/api/reviews")
	@ResponseStatus(HttpStatus.CREATED)
	public ReviewView write(@Valid @RequestBody WriteRequest request) {
		return reviews.write(CurrentUser.require(), request);
	}

	/** Public, like profiles: reviews help people decide who to travel with. */
	@GetMapping("/api/users/{id}/reviews")
	public ReviewPage about(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return reviews.about(id, page, size);
	}
}
