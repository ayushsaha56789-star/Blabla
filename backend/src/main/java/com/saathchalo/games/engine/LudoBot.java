package com.saathchalo.games.engine;

import java.util.List;

/**
 * Picks a ludo move for a computer player, or for someone who ran out of time: capture if possible, then get a token
 * home, bring one out, reach a safe square, stay out of reach of opponents just behind, and otherwise move the token
 * that's furthest along.
 */
public final class LudoBot {

	private LudoBot() {
	}

	public static int choose(Ludo g) {
		List<Integer> options = g.movable();
		if (options.isEmpty()) {
			return -1;
		}
		int colour = g.current();
		int best = options.getFirst();
		int bestScore = Integer.MIN_VALUE;
		for (int token : options) {
			int score = score(g, colour, token);
			if (score > bestScore) {
				bestScore = score;
				best = token;
			}
		}
		return best;
	}

	private static int score(Ludo g, int colour, int token) {
		int from = g.tokens[colour][token];
		int to = from == Ludo.YARD ? 0 : from + g.dice;
		int score = to;
		if (to == Ludo.HOME) {
			score += 400;
		}
		if (from == Ludo.YARD) {
			score += 300;
		}
		if (to <= Ludo.LAST_TRACK) {
			int sq = Ludo.square(colour, to);
			if (Ludo.safe(sq)) {
				score += 150;
			}
			else {
				if (opponentOn(g, colour, sq)) {
					score += 1000;
				}
				if (threatened(g, colour, sq)) {
					score -= 250;
				}
			}
		}
		else if (from <= Ludo.LAST_TRACK && from >= 0) {
			score += 200;
		}
		if (from >= 0 && from <= Ludo.LAST_TRACK && !Ludo.safe(Ludo.square(colour, from)) && threatened(g, colour, Ludo.square(colour, from))) {
			score += 120;
		}
		return score;
	}

	private static boolean opponentOn(Ludo g, int colour, int sq) {
		for (int other : g.seats) {
			if (other != colour) {
				for (int p : g.tokens[other]) {
					if (p >= 0 && p <= Ludo.LAST_TRACK && Ludo.square(other, p) == sq) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** An opponent 1–6 squares behind could land on this square next. */
	private static boolean threatened(Ludo g, int colour, int sq) {
		for (int other : g.seats) {
			if (other == colour) {
				continue;
			}
			for (int p : g.tokens[other]) {
				if (p < 0 || p > Ludo.LAST_TRACK) {
					continue;
				}
				int behind = (sq - Ludo.square(other, p) + 52) % 52;
				if (behind >= 1 && behind <= 6 && p + behind <= Ludo.LAST_TRACK) {
					return true;
				}
			}
		}
		return false;
	}
}
