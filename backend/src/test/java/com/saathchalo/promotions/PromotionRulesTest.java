package com.saathchalo.promotions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PromotionRulesTest {

	@Test
	void refusesMoneyGamesAlcoholTobaccoAdultContentWeaponsAndFalsePromises() {
		assertThat(PromotionRules.bannedContent("Sharma Sweets", "Fresh jalebis every morning")).isNull();
		assertThat(PromotionRules.bannedContent("Wine & Dine", "Great food")).isNotNull();
		assertThat(PromotionRules.bannedContent("Win big", "Play rummy for cash")).isNotNull();
		assertThat(PromotionRules.bannedContent("Invest now", "Guaranteed returns in 30 days")).isNotNull();
		assertThat(PromotionRules.bannedContent("Cool vapes in Bandra")).isNotNull();
		assertThat(PromotionRules.bannedContent("Fantasy cricket league")).isNotNull();
		// Words that merely contain a banned one are fine.
		assertThat(PromotionRules.bannedContent("Rumali roti and kebabs", "Best Betta fish store", "Gunjan's bakery")).isNull();
	}

	@Test
	void allowsOnlyPlainHttpsLinksToNamedWebsites() {
		assertThat(PromotionRules.linkProblem(null)).isNull();
		assertThat(PromotionRules.linkProblem("https://sharmasweets.in/menu")).isNull();
		assertThat(PromotionRules.linkProblem("http://sharmasweets.in")).isNotNull();
		assertThat(PromotionRules.linkProblem("https://bit.ly/abc")).contains("Short links");
		assertThat(PromotionRules.linkProblem("https://103.21.4.7/offer")).isNotNull();
		assertThat(PromotionRules.linkProblem("javascript:alert(1)")).isNotNull();
		assertThat(PromotionRules.linkProblem("https://user:pass@shop.in")).isNotNull();
	}

	@Test
	void flagsShoutingAndPhoneNumbersForTheModerator() {
		assertThat(PromotionRules.flags("BEST CHAI IN TOWN", "Call 98110 12345 now!!!!"))
			.containsExactly("Headline in capitals", "Phone number in the text", "Many exclamation marks");
		assertThat(PromotionRules.flags("Best chai in town", "Open 7 am to 10 pm")).isEmpty();
	}

	@Test
	void runsUpToThirtyDaysFromToday() {
		LocalDate today = LocalDate.of(2026, 11, 20);
		assertThat(PromotionRules.datesProblem(today, today.plusDays(29), today)).isNull();
		assertThat(PromotionRules.datesProblem(today, today.plusDays(30), today)).contains("30 days");
		assertThat(PromotionRules.datesProblem(today.minusDays(1), today, today)).contains("today");
		assertThat(PromotionRules.datesProblem(today.plusDays(61), today.plusDays(62), today)).contains("60 days");
	}
}
