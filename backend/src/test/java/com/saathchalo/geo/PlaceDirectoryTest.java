package com.saathchalo.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlaceDirectoryTest {

	static final long DELHI = 1273294L;

	static final long NEW_DELHI = 1261481L;

	static final long GURUGRAM = 1270642L;

	static final long JAIPUR = 1269515L;

	static final long MUMBAI = 1275339L;

	static final long BENGALURU = 1277333L;

	static final long CHENNAI = 1264527L;

	static final long CHANDIGARH = 1274746L;

	static PlaceDirectory places;

	@BeforeAll
	static void load() {
		places = new PlaceDirectory();
	}

	@Test
	void loadsEveryIndianTownInTheList() {
		assertThat(places.size()).isGreaterThan(6000);
		assertThat(places.find(DELHI)).get().extracting(Place::name, Place::state).containsExactly("Delhi", "Delhi");
	}

	@Test
	void findsCitiesByTheirOldAndEverydayNames() {
		assertThat(first("Bangalore")).isEqualTo(BENGALURU);
		assertThat(first("bombay")).isEqualTo(MUMBAI);
		assertThat(first("Madras")).isEqualTo(CHENNAI);
		assertThat(first("gurgaon")).isEqualTo(GURUGRAM);
		assertThat(name(first("vizag"))).isEqualTo("Visakhapatnam");
		assertThat(name(first("Trivandrum"))).isEqualTo("Thiruvananthapuram");
		assertThat(name(first("goa"))).isEqualTo("Panaji");
		assertThat(name(first("Ooty"))).isEqualTo("Ooty");
		assertThat(name(first("Ernakulam"))).isEqualTo("Kochi");
		assertThat(name(first("Closepet"))).isEqualTo("Ramanagara");
	}

	@Test
	void findsCitiesWrittenInIndianScripts() {
		assertThat(first("दिल्ली")).isEqualTo(DELHI);
		assertThat(first("ಬೆಂಗಳೂರು")).isEqualTo(BENGALURU);
		assertThat(first("சென்னை")).isEqualTo(CHENNAI);
	}

	@Test
	void ignoresCaseSpacesAndAccents() {
		assertThat(name(first("NAINI TAL"))).isEqualTo("Nainital");
		assertThat(name(first("nainital"))).isEqualTo("Nainital");
		assertThat(name(first("Manāli"))).isEqualTo("Manali");
		assertThat(first("  new   delhi ")).isEqualTo(NEW_DELHI);
	}

	@Test
	void prefersTheRealCityOverPlacesThatShareItsName() {
		// GeoNames also lists a neighbourhood with "Visakhapatnam" as another name.
		assertThat(name(first("Visakhapatnam"))).isEqualTo("Visakhapatnam");
		// "Delhi" is Delhi first, and New Delhi is offered too.
		List<Long> delhi = places.search("Delhi", 8).stream().map(Place::id).toList();
		assertThat(delhi.getFirst()).isEqualTo(DELHI);
		assertThat(delhi).contains(NEW_DELHI);
	}

	@Test
	void completesWhatPeopleAreTyping() {
		assertThat(places.search("jai", 5)).extracting(Place::id).first().isEqualTo(JAIPUR);
		assertThat(places.search("hyd", 5)).extracting(Place::name).first().isEqualTo("Hyderabad");
		assertThat(places.search("pun", 5)).extracting(Place::name).contains("Pune");
	}

	@Test
	void showsPopularCitiesBeforeAnythingIsTyped() {
		assertThat(places.search("", 3)).extracting(Place::id).containsExactly(DELHI, MUMBAI, BENGALURU);
		assertThat(places.search("   ", 20)).hasSizeLessThanOrEqualTo(PlaceDirectory.MAX_RESULTS);
	}

	@Test
	void returnsNothingForNonsense() {
		assertThat(places.search("zzqqxx", 8)).isEmpty();
		assertThat(places.search("!!!", 8)).extracting(Place::id).first().isEqualTo(DELHI);
	}

	@Test
	void suggestsTownsOnTheWayButNotNeighbourhoodsOfTheStartOrEnd() {
		Place delhi = places.find(DELHI).orElseThrow();
		Place jaipur = places.find(JAIPUR).orElseThrow();
		List<Place> along = places.along(delhi, jaipur, 5);
		assertThat(along).extracting(Place::id).contains(GURUGRAM);
		assertThat(along).extracting(Place::state).doesNotContain("Delhi");
		assertThat(along).extracting(Place::name).doesNotContain("Najafgarh");
		// Highways bend: towns a little off the straight line still count, in travel order.
		assertThat(places.along(delhi, places.find(CHANDIGARH).orElseThrow(), 5)).extracting(Place::name)
			.containsSubsequence("Panipat", "Karnal", "Ambala");
		// Short trips get no suggestions.
		assertThat(places.along(delhi, places.find(NEW_DELHI).orElseThrow(), 5)).isEmpty();
	}

	@Test
	void findsTheNearestTownOnlyInsideIndia() {
		assertThat(places.nearest(28.62, 77.21)).get().extracting(Place::state).isEqualTo("Delhi");
		assertThat(places.nearest(51.5, -0.12)).isEmpty();
	}

	@Test
	void estimatesRoadDistanceAndTime() {
		Place delhi = places.find(DELHI).orElseThrow();
		Place gurugram = places.find(GURUGRAM).orElseThrow();
		Place jaipur = places.find(JAIPUR).orElseThrow();
		GeoMath.RouteEstimate route = GeoMath.estimate(List.of(delhi, gurugram, jaipur));
		assertThat(route.distanceKm()).isBetween(260, 320);
		assertThat(route.durationMin()).isBetween(270, 400);
		assertThat(route.kmFromStart()).hasSize(3).first().isEqualTo(0);
		assertThat(route.kmFromStart().get(1)).isBetween(25, 50);
		assertThat(route.minutesFromStart().get(2)).isEqualTo(route.durationMin());
	}

	@Test
	void foldsNamesForComparison() {
		assertThat(PlaceDirectory.fold("Rishīkesh")).isEqualTo("rishikesh");
		assertThat(PlaceDirectory.fold("Pimpri-Chinchwad")).isEqualTo("pimprichinchwad");
		assertThat(PlaceDirectory.fold("दिल्ली")).isEqualTo("दिल्ली");
		assertThat(PlaceDirectory.fold(null)).isEmpty();
	}

	private static long first(String q) {
		return places.search(q, 1).getFirst().id();
	}

	private static String name(long id) {
		return places.find(id).orElseThrow().name();
	}
}
