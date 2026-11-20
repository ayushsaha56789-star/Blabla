package com.saathchalo.games.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The rules of ludo, for 2 to 4 players with 4 tokens each. A token's progress is -1 in the yard, 0–50 on the shared
 * track (0 is its own start square), 51–55 in its own home column, and 56 home. A 6 brings a token out and gives
 * another roll (three sixes in a row lose the turn); landing on an opponent outside the safe squares sends it back to
 * the yard and gives another roll, as does getting a token home. Reaching home needs the exact number. The first to
 * get all four tokens home wins.
 *
 * <p>Public fields so the state can be stored as JSON. {@code seats} are board colours (0–3) in turn order.
 */
public final class Ludo {

	public static final int YARD = -1;

	public static final int LAST_TRACK = 50;

	public static final int HOME = 56;

	/** Track squares (counted from colour 0's start) where nobody can be captured: the start squares and the stars. */
	static final int[] SAFE = { 0, 8, 13, 21, 26, 34, 39, 47 };

	public int[] seats;

	/** tokens[colour][token] = progress. */
	public int[][] tokens;

	/** Index into {@link #seats} of the player whose turn it is. */
	public int turn;

	/** The roll waiting to be used, or null when the player still has to roll. */
	public Integer dice;

	public int sixes;

	/** The colour that won, or -1. */
	public int winner = -1;

	/** What just happened, in words, e.g. "Green captured Blue". */
	public String last;

	/** The last move (colour, token, from, to), for the board to show. */
	public int[] lastMove;

	public Ludo() {
	}

	/** Two players sit opposite each other; three or four go round the board. */
	public static Ludo start(int players) {
		Ludo g = new Ludo();
		g.seats = switch (players) {
			case 2 -> new int[] { 0, 2 };
			case 3 -> new int[] { 0, 1, 2 };
			case 4 -> new int[] { 0, 1, 2, 3 };
			default -> throw new IllegalArgumentException("Ludo needs 2 to 4 players");
		};
		g.tokens = new int[4][4];
		for (int[] t : g.tokens) {
			Arrays.fill(t, YARD);
		}
		return g;
	}

	public int current() {
		return seats[turn];
	}

	public boolean over() {
		return winner >= 0;
	}

	/** The shared-track square (0–51) a token on the track stands on. */
	public static int square(int colour, int progress) {
		return (colour * 13 + progress) % 52;
	}

	public static boolean safe(int square) {
		return Arrays.stream(SAFE).anyMatch(s -> s == square);
	}

	/** Tokens the current player can move with the roll. */
	public List<Integer> movable() {
		List<Integer> out = new ArrayList<>();
		if (dice == null || over()) {
			return out;
		}
		int[] mine = tokens[current()];
		for (int i = 0; i < 4; i++) {
			if (mine[i] == YARD ? dice == 6 : mine[i] < HOME && mine[i] + dice <= HOME) {
				out.add(i);
			}
		}
		return out;
	}

	/** Uses a roll of 1–6. Returns false when the turn passed straight away (no move possible, or a third six). */
	public boolean roll(int value) {
		if (dice != null || over()) {
			throw new IllegalStateException("Not the time to roll");
		}
		String who = name(current());
		if (value != 6) {
			sixes = 0;
		}
		else if (++sixes == 3) {
			last = who + " rolled three sixes: turn over";
			next();
			return false;
		}
		dice = value;
		if (movable().isEmpty()) {
			last = who + " rolled " + value + ": no move";
			dice = null;
			if (value != 6) {
				next();
			}
			return false;
		}
		last = who + " rolled " + value;
		return true;
	}

	public void move(int token) {
		if (!movable().contains(token)) {
			throw new IllegalArgumentException("That token can't move");
		}
		int colour = current();
		int roll = dice;
		int from = tokens[colour][token];
		int to = from == YARD ? 0 : from + roll;
		tokens[colour][token] = to;
		lastMove = new int[] { colour, token, from, to };
		String who = name(colour);
		boolean again = roll == 6;
		last = who + (from == YARD ? " brought a token out" : " moved " + roll);
		if (to <= LAST_TRACK) {
			int sq = square(colour, to);
			if (!safe(sq)) {
				List<String> hit = new ArrayList<>();
				for (int other : seats) {
					if (other == colour) {
						continue;
					}
					for (int i = 0; i < 4; i++) {
						int p = tokens[other][i];
						if (p >= 0 && p <= LAST_TRACK && square(other, p) == sq) {
							tokens[other][i] = YARD;
							hit.add(name(other));
						}
					}
				}
				if (!hit.isEmpty()) {
					again = true;
					last = who + " captured " + String.join(" and ", hit.stream().distinct().toList());
				}
			}
		}
		if (to == HOME) {
			again = true;
			last = who + " got a token home";
			if (Arrays.stream(tokens[colour]).allMatch(p -> p == HOME)) {
				winner = colour;
				last = who + " won!";
				dice = null;
				return;
			}
		}
		dice = null;
		if (again) {
			if (roll != 6) {
				sixes = 0;
			}
		}
		else {
			next();
		}
	}

	/** How far each colour has got, for placing players when a game ends early. */
	public int progress(int colour) {
		return Arrays.stream(tokens[colour]).map(p -> p + 1).sum();
	}

	private void next() {
		turn = (turn + 1) % seats.length;
		dice = null;
		sixes = 0;
	}

	public static String name(int colour) {
		return switch (colour) {
			case 0 -> "Orange";
			case 1 -> "Green";
			case 2 -> "Blue";
			default -> "Purple";
		};
	}
}
