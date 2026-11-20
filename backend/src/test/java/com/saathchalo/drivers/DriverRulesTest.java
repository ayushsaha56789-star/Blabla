package com.saathchalo.drivers;

import static org.assertj.core.api.Assertions.assertThat;

import com.saathchalo.drivers.DriverDtos.Quote;
import com.saathchalo.drivers.DriverDtos.TripKind;
import com.saathchalo.drivers.internal.DriverProfile;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DriverRulesTest {

	private static Instant india(int day, int hour, int minute) {
		return LocalDateTime.of(2026, 12, day, hour, minute).atZone(DriverRules.INDIA).toInstant();
	}

	private static DriverProfile driver() {
		DriverProfile d = new DriverProfile(UUID.randomUUID(), Instant.now());
		d.details(5, "Hindi", true, true, true, 150, 1000, 1500, 300, 200, null, Instant.now());
		return d;
	}

	@Test
	void localTripsCostByTheHourButNeverMoreThanADay() {
		Quote four = DriverRules.quote(driver(), TripKind.LOCAL, india(10, 9, 0), 4, null);
		assertThat(four.fare()).isEqualTo(600);
		assertThat(four.dayPriceApplied()).isFalse();
		assertThat(four.to()).isEqualTo(india(10, 13, 0));

		Quote ten = DriverRules.quote(driver(), TripKind.LOCAL, india(10, 9, 0), 10, null);
		assertThat(ten.base()).isEqualTo(1000);
		assertThat(ten.dayPriceApplied()).isTrue();
		assertThat(ten.extraPrice()).isEqualTo(150);
	}

	@Test
	void lateTripsAddTheNightCharge() {
		assertThat(DriverRules.quote(driver(), TripKind.LOCAL, india(10, 19, 0), 4, null).night()).isEqualTo(200);
		assertThat(DriverRules.quote(driver(), TripKind.LOCAL, india(10, 4, 0), 3, null).night()).isEqualTo(200);
		assertThat(DriverRules.quote(driver(), TripKind.LOCAL, india(10, 6, 0), 4, null).night()).isZero();
		assertThat(DriverRules.touchesNight(india(10, 21, 0), india(10, 22, 0))).isFalse();
		assertThat(DriverRules.touchesNight(india(10, 21, 0), india(10, 22, 1))).isTrue();
	}

	@Test
	void outstationTripsCostByTheDayWithTheAllowance() {
		Quote q = DriverRules.quote(driver(), TripKind.OUTSTATION, india(10, 6, 0), null, 3);
		assertThat(q.base()).isEqualTo(4500);
		assertThat(q.allowance()).isEqualTo(900);
		assertThat(q.fare()).isEqualTo(5400);
		assertThat(q.night()).isZero();
		assertThat(q.extraPrice()).isEqualTo(1800);
		assertThat(q.to()).isEqualTo(india(13, 6, 0));
	}

	@Test
	void extraTimeIsRoundedUpAfterAShortGrace() {
		Instant end = india(10, 13, 0);
		assertThat(DriverRules.extraUnits(TripKind.LOCAL, end, india(10, 13, 15))).isZero();
		assertThat(DriverRules.extraUnits(TripKind.LOCAL, end, india(10, 13, 16))).isEqualTo(1);
		assertThat(DriverRules.extraUnits(TripKind.LOCAL, end, india(10, 15, 1))).isEqualTo(3);
		assertThat(DriverRules.extraUnits(TripKind.OUTSTATION, end, india(10, 15, 0))).isZero();
		assertThat(DriverRules.extraUnits(TripKind.OUTSTATION, end, india(10, 15, 30))).isEqualTo(1);
		assertThat(DriverRules.extraUnits(TripKind.OUTSTATION, end, india(11, 14, 0))).isEqualTo(2);
	}

	@Test
	void explainsTripsThatCantBeBooked() {
		Instant now = india(10, 9, 0);
		assertThat(DriverRules.periodProblem(TripKind.LOCAL, india(10, 12, 0), 1, null, now)).contains("2 to 12 hours");
		assertThat(DriverRules.periodProblem(TripKind.OUTSTATION, india(10, 12, 0), null, 16, now)).contains("1 to 15 days");
		assertThat(DriverRules.periodProblem(TripKind.LOCAL, india(10, 9, 30), 4, null, now)).contains("an hour ahead");
		assertThat(DriverRules.periodProblem(TripKind.LOCAL, now.plusSeconds(61L * 24 * 3600), 4, null, now)).contains("60 days");
		assertThat(DriverRules.periodProblem(TripKind.LOCAL, india(10, 11, 0), 4, null, now)).isNull();
	}

	@Test
	void tidiesLicenceNumbersAndLanguages() {
		assertThat(DriverProfileService.cleanLicence(" mh-14 2011/0062821 ")).isEqualTo("MH1420110062821");
		assertThat(DriverProfileService.cleanLicence("  ")).isNull();
		assertThat(DriverProfileService.mask("MH1420110062821")).isEqualTo("•••• 2821");
		assertThat(DriverProfileService.cleanLanguages(java.util.List.of("hindi", " English ", "HINDI", "", "a,b")))
			.containsExactly("Hindi", "English", "A b");
	}
}
