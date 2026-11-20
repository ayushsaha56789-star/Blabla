package com.saathchalo.rewards;

import static org.assertj.core.api.Assertions.assertThat;

import com.saathchalo.rewards.RewardDtos.Badge;
import com.saathchalo.rewards.RewardRules.Stats;
import com.saathchalo.rides.RideLookup.SharedTrip;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RewardRulesTest {

	private static final UUID A = UUID.randomUUID();

	private static final UUID B = UUID.randomUUID();

	private static SharedTrip trip(String when, boolean driver, int km, int passengerKm, UUID... mates) {
		return new SharedTrip(UUID.randomUUID(), LocalDateTime.parse(when).atZone(RewardRules.INDIA).toInstant(), driver, km, passengerKm,
				List.of(mates), 500);
	}

	private static final ZonedDateTime NOW = LocalDateTime.parse("2026-11-20T12:00").atZone(RewardRules.INDIA);

	@Test
	void tripsAddUpToXpLevelsAndCo2() {
		List<SharedTrip> trips = List.of(
				trip("2026-11-18T08:00", true, 300, 600, A, B), // drove two people 300 km
				trip("2026-11-10T23:30", false, 120, 120, A), // rode at night
				trip("2026-11-03T06:15", false, 40, 40, B)); // rode early
		Stats s = RewardRules.stats(trips, NOW);
		assertThat(s.trips()).isEqualTo(3);
		assertThat(s.driven()).isEqualTo(1);
		assertThat(s.people()).isEqualTo(2);
		assertThat(s.co2Kg()).isEqualTo(91.2);
		assertThat(s.monthTrips()).isEqualTo(3);
		assertThat(s.challengesDone()).isEqualTo(1);
		// 3 trips × 50 + driving 20 + 2 people × 5 + a monthly challenge 100 + one referral 100.
		int xp = RewardRules.xp(s, 1);
		assertThat(xp).isEqualTo(380);
		assertThat(RewardRules.LEVEL_NAMES[RewardRules.level(xp)]).isEqualTo("Road buddy");
		assertThat(RewardRules.level(0)).isZero();
		assertThat(RewardRules.level(12_000)).isEqualTo(RewardRules.LEVELS.length - 1);
	}

	@Test
	void countsWeeksInARow() {
		List<SharedTrip> trips = List.of(
				trip("2026-11-17T09:00", false, 10, 10, A), // this week (Mon 16 Nov)
				trip("2026-11-10T09:00", false, 10, 10, A),
				trip("2026-11-03T09:00", false, 10, 10, A),
				trip("2026-10-13T09:00", false, 10, 10, A), // a gap
				trip("2026-10-06T09:00", false, 10, 10, A));
		Stats s = RewardRules.stats(trips, NOW);
		assertThat(s.streak()).isEqualTo(3);
		assertThat(s.bestStreak()).isEqualTo(3);
		// No ride yet this week: the streak still counts up to last week.
		assertThat(RewardRules.stats(trips.subList(1, 5), NOW).streak()).isEqualTo(2);
	}

	@Test
	void earnsBadgesWithProgress() {
		Stats s = RewardRules.stats(List.of(trip("2026-11-10T23:30", true, 320, 640, A, B)), NOW);
		List<Badge> badges = RewardRules.badges(s, 0);
		assertThat(badges).filteredOn(Badge::earned).extracting(Badge::key)
			.containsExactlyInAnyOrder("FIRST_RIDE", "ECO_HERO", "ROAD_TRIPPER", "NIGHT_OWL");
		Badge regular = badges.stream().filter(b -> b.key().equals("REGULAR")).findFirst().orElseThrow();
		assertThat(regular.progress()).isEqualTo(1);
		assertThat(regular.goal()).isEqualTo(10);
	}
}
