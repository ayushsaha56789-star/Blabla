package com.saathchalo.rides;

import static org.assertj.core.api.Assertions.assertThat;

import com.saathchalo.config.AppProperties;
import com.saathchalo.geo.GeoMath.RouteEstimate;
import com.saathchalo.rides.RideDtos.Quote;
import com.saathchalo.rides.internal.Booking;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RideRulesTest {

	private static final UUID RIDE = UUID.randomUUID();

	private static final AppProperties.Rides CONFIG = new AppProperties.Rides(2.0, 3.5, 20, 30, 90, Duration.ofHours(12),
			Duration.ofMinutes(15));

	@Test
	void aSeatIsUsedOnlyOnTheStretchesItIsBookedFor() {
		// Stops 0 → 1 → 2 → 3, three seats. One seat from 0 to 2, two seats from 1 to 3.
		List<Booking> live = List.of(booking(0, 2, 1), booking(1, 3, 2));
		int[] left = RideRules.seatsLeftByLeg(3, 3, live);
		assertThat(left).containsExactly(2, 0, 1);
		assertThat(RideRules.seatsLeft(left, 0, 1)).isEqualTo(2);
		assertThat(RideRules.seatsLeft(left, 0, 3)).isZero();
		assertThat(RideRules.seatsLeft(left, 2, 3)).isEqualTo(1);
	}

	@Test
	void theSameSeatCanBeSoldForStretchesThatDontOverlap() {
		int[] left = RideRules.seatsLeftByLeg(1, 2, List.of(booking(0, 1, 1)));
		assertThat(RideRules.seatsLeft(left, 1, 2)).isEqualTo(1);
		assertThat(RideRules.seatsLeft(left, 0, 2)).isZero();
	}

	@Test
	void partOfARouteCostsItsShareOfTheDistanceRoundedToTenRupees() {
		List<Integer> km = List.of(0, 34, 295);
		assertThat(RideRules.segmentPrice(600, km, 0, 2)).isEqualTo(600);
		assertThat(RideRules.segmentPrice(600, km, 1, 2)).isEqualTo(530);
		assertThat(RideRules.segmentPrice(600, km, 0, 1)).isEqualTo(70);
		// Never below ₹10, never above the whole route.
		assertThat(RideRules.segmentPrice(40, List.of(0, 1, 300), 0, 1)).isEqualTo(10);
		assertThat(RideRules.priceMatrix(600, km)).containsExactly(List.of(0, 70, 600), List.of(0, 0, 530), List.of(0, 0, 0));
	}

	@Test
	void suggestsAFairPriceAndCapsItToSharingCosts() {
		Quote quote = RideRules.quote(new RouteEstimate(295, 345, List.of(), List.of(0, 295), List.of(0, 345)), CONFIG);
		assertThat(quote.suggestedPrice()).isEqualTo(590);
		assertThat(quote.maxPrice()).isEqualTo(1040);
		assertThat(quote.minPrice()).isEqualTo(20);
		Quote shortTrip = RideRules.quote(new RouteEstimate(10, 15, List.of(), List.of(0, 10), List.of(0, 15)), CONFIG);
		assertThat(shortTrip.suggestedPrice()).isEqualTo(20);
		assertThat(shortTrip.maxPrice()).isEqualTo(40);
	}

	private static Booking booking(int from, int to, int seats) {
		return new Booking(RIDE, UUID.randomUUID(), from, to, seats, 100, true, null, Instant.now());
	}
}
