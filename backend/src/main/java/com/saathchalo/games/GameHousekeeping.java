package com.saathchalo.games;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every ten seconds: plays the turns that are due in games nobody is looking at (computer players, people who ran out
 * of time), so every game finishes. Then tidies up: waiting rooms nobody joined, games untouched for a day, old chat.
 */
@Component
public class GameHousekeeping {

	private static final Logger log = LoggerFactory.getLogger(GameHousekeeping.class);

	private final GameService games;

	public GameHousekeeping(GameService games) {
		this.games = games;
	}

	@Scheduled(fixedDelay = 10_000, initialDelay = 60_000)
	public void run() {
		for (UUID id : games.due(200)) {
			try {
				games.catchUp(id);
			}
			catch (RuntimeException e) {
				log.warn("Could not play the due turns in game {}", id, e);
			}
		}
		games.tidy();
	}
}
