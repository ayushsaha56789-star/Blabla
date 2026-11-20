package com.saathchalo.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.saathchalo.rides.RideTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class GamesTest extends RideTestSupport {

	@BeforeEach
	void gamesOn() {
		jdbc.update("UPDATE feature_flags SET enabled = TRUE WHERE key = 'games'");
		featureService.reload();
	}

	@Test
	void chessAgainstTheComputer() throws Exception {
		LoggedIn asha = member("9400000001", "Asha", "FEMALE");
		String game = JsonPath.read(send(asha, "/api/games", "{\"game\":\"CHESS\",\"mode\":\"COMPUTER\",\"level\":\"EASY\",\"colour\":\"WHITE\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PLAYING"))
			.andExpect(jsonPath("$.mySeat").value(0))
			.andExpect(jsonPath("$.seats[1].computer").value(true))
			.andExpect(jsonPath("$.chess.legal", hasSize(20)))
			.andExpect(jsonPath("$.chess.whiteMs").doesNotExist())
			.andReturn().getResponse().getContentAsString(), "$.id");

		send(asha, "/api/games/" + game + "/move", "{\"move\":\"e2e5\"}").andExpect(jsonPath("$.code").value("illegal_move"));
		send(asha, "/api/games/" + game + "/move", "{\"move\":\"e2e4\"}")
			.andExpect(jsonPath("$.chess.moves[0]").value("e4"))
			.andExpect(jsonPath("$.turnSeat").value(1))
			.andExpect(jsonPath("$.chess.legal", hasSize(0)));
		send(asha, "/api/games/" + game + "/move", "{\"move\":\"d2d4\"}").andExpect(jsonPath("$.code").value("not_your_turn"));

		// The computer answers once its short pause is over (the page asks for news; here the pause is skipped).
		int version = JsonPath.read(poll(asha, game, null).andReturn().getResponse().getContentAsString(), "$.version");
		poll(asha, game, version).andExpect(status().isNoContent());
		jdbc.update("UPDATE game_rooms SET deadline = now() - interval '1 second' WHERE id = ?::uuid", game);
		poll(asha, game, version).andExpect(status().isOk())
			.andExpect(jsonPath("$.chess.moves", hasSize(2)))
			.andExpect(jsonPath("$.turnSeat").value(0));

		send(asha, "/api/games/" + game + "/resign", null)
			.andExpect(jsonPath("$.status").value("FINISHED"))
			.andExpect(jsonPath("$.result").value("White resigned"))
			.andExpect(jsonPath("$.seats[0].outcome").value("LOSS"))
			.andExpect(jsonPath("$.rated").value(false));
	}

	@Test
	void friendsPlayChessWithClocksChatAndRatings() throws Exception {
		LoggedIn asha = member("9400000001", "Asha", "FEMALE");
		LoggedIn ravi = member("9400000002", "Ravi", "MALE");
		LoggedIn outsider = member("9400000003", "Kiran", "MALE");
		String body = send(asha, "/api/games", "{\"game\":\"CHESS\",\"mode\":\"FRIENDS\"}")
			.andExpect(jsonPath("$.status").value("WAITING"))
			.andExpect(jsonPath("$.code").isString())
			.andReturn().getResponse().getContentAsString();
		String game = JsonPath.read(body, "$.id");
		String code = JsonPath.read(body, "$.code");
		send(ravi, "/api/games/join", "{\"code\":\"" + code.toLowerCase() + "\"}")
			.andExpect(jsonPath("$.status").value("PLAYING"))
			.andExpect(jsonPath("$.rated").value(true))
			.andExpect(jsonPath("$.chess.blackMs").value(600_000));
		poll(outsider, game, null).andExpect(status().isNotFound());

		// Whoever got white plays the fool's mate against themselves... and loses to black.
		String state = poll(asha, game, null).andReturn().getResponse().getContentAsString();
		int ashaSeat = JsonPath.read(state, "$.mySeat");
		LoggedIn white = ashaSeat == 0 ? asha : ravi;
		LoggedIn black = ashaSeat == 0 ? ravi : asha;
		for (String[] m : new String[][] { { "W", "f2f3" }, { "B", "e7e5" }, { "W", "g2g4" } }) {
			send(m[0].equals("W") ? white : black, "/api/games/" + game + "/move", "{\"move\":\"" + m[1] + "\"}").andExpect(status().isOk());
		}
		send(black, "/api/games/" + game + "/move", "{\"move\":\"d8h4\"}")
			.andExpect(jsonPath("$.status").value("FINISHED"))
			.andExpect(jsonPath("$.result").value("Black won by checkmate"))
			.andExpect(jsonPath("$.chess.moves[3]").value("Qh4#"))
			.andExpect(jsonPath("$.seats[1].ratingChange").value(16))
			.andExpect(jsonPath("$.seats[0].ratingChange").value(-16));

		// Chat: set phrases only, not too fast.
		send(asha, "/api/games/" + game + "/say", "{\"phrase\":\"GG\"}").andExpect(jsonPath("$.messages", hasSize(1)));
		send(asha, "/api/games/" + game + "/say", "{\"phrase\":\"WP\"}").andExpect(status().isTooManyRequests());
		send(ravi, "/api/games/" + game + "/say", "{\"phrase\":\"call me on 98...\"}").andExpect(status().isBadRequest());

		mvc.perform(get("/api/games/leaderboard").param("game", "CHESS").header("Authorization", white.bearer()))
			.andExpect(jsonPath("$.top", hasSize(2)))
			.andExpect(jsonPath("$.top[0].rating").value(1216))
			.andExpect(jsonPath("$.me.rank").value(2))
			.andExpect(jsonPath("$.me.rating").value(1184));
		mvc.perform(get("/api/games/mine").header("Authorization", asha.bearer()))
			.andExpect(jsonPath("$[0].status").value("FINISHED"))
			.andExpect(jsonPath("$[0].players[0]").value("Ravi"));
	}

	@Test
	void ludoWithTheComputerPlaysItselfWhenSomeoneIsTooSlow() throws Exception {
		LoggedIn asha = member("9400000001", "Asha", "FEMALE");
		String game = JsonPath.read(send(asha, "/api/games", "{\"game\":\"LUDO\",\"mode\":\"COMPUTER\",\"players\":2,\"level\":\"EASY\"}")
			.andExpect(jsonPath("$.ludo.seats[0]").value(0))
			.andExpect(jsonPath("$.ludo.seats[1]").value(2))
			.andExpect(jsonPath("$.ludo.canRoll").value(true))
			.andReturn().getResponse().getContentAsString(), "$.id");

		// From the yard only a 6 moves, and then all four tokens are the same: the move plays itself.
		String after = send(asha, "/api/games/" + game + "/roll", null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		int current = JsonPath.read(after, "$.ludo.current");
		List<Integer> mine = JsonPath.read(after, "$.ludo.tokens[0]");
		if (current == 0) {
			assertThat(mine).containsExactly(0, -1, -1, -1);
			assertThat((Object) JsonPath.read(after, "$.ludo.dice")).isNull();
		}
		else {
			assertThat(mine).containsOnly(-1);
			assertThat(current).isEqualTo(2);
		}

		// Whoever's turn it is, letting the clock run out makes the computer play it; people who time out are counted.
		for (int i = 0; i < 6; i++) {
			jdbc.update("UPDATE game_rooms SET deadline = now() - interval '1 second' WHERE id = ?::uuid", game);
			poll(asha, game, null).andExpect(status().isOk());
		}
		Map<String, Object> me = jdbc.queryForMap("SELECT timeouts, away FROM game_players WHERE room_id = ?::uuid AND seat = 0", game);
		int rolls = jdbc.queryForObject("SELECT version FROM game_rooms WHERE id = ?::uuid", Integer.class, game);
		assertThat(rolls).isGreaterThan(6);
		if ((int) me.get("timeouts") >= 2) {
			assertThat(me.get("away")).isEqualTo(true);
		}
	}

	@Test
	void theGameRoomOfARideIsForThePeopleOnIt() throws Exception {
		LoggedIn driver = member("9400000001", "Ravi", "MALE");
		LoggedIn passenger = member("9400000002", "Priya", "FEMALE");
		LoggedIn stranger = member("9400000003", "Kiran", "MALE");
		String ride = publishDelhiJaipur(driver, 3, true);
		book(passenger, ride, 0, 2, 1).andExpect(status().isCreated());

		send(stranger, "/api/games/ride/" + ride, "{\"game\":\"LUDO\"}").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_on_ride"));
		String game = JsonPath.read(send(driver, "/api/games/ride/" + ride, "{\"game\":\"LUDO\"}")
			.andExpect(jsonPath("$.status").value("WAITING"))
			.andExpect(jsonPath("$.host").value(true))
			.andReturn().getResponse().getContentAsString(), "$.id");
		send(passenger, "/api/games/ride/" + ride, "{\"game\":\"LUDO\"}")
			.andExpect(jsonPath("$.id").value(game))
			.andExpect(jsonPath("$.seats", hasSize(2)));
		send(passenger, "/api/games/" + game + "/start", "{\"fillWithComputer\":true}").andExpect(status().isForbidden());
		send(driver, "/api/games/" + game + "/start", "{\"fillWithComputer\":true}")
			.andExpect(jsonPath("$.status").value("PLAYING"))
			.andExpect(jsonPath("$.seats", hasSize(4)))
			.andExpect(jsonPath("$.ludo.seats", hasSize(4)))
			.andExpect(jsonPath("$.rated").value(false));
	}

	// ----- Helpers -----

	private ResultActions send(LoggedIn who, String path, String json) throws Exception {
		var request = post(path).header("Authorization", who.bearer());
		if (json != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mvc.perform(request);
	}

	private ResultActions poll(LoggedIn who, String game, Integer since) throws Exception {
		var request = get("/api/games/" + game).header("Authorization", who.bearer());
		if (since != null) {
			request.param("since", String.valueOf(since));
		}
		return mvc.perform(request);
	}
}
