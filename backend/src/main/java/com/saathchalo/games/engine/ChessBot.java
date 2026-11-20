package com.saathchalo.games.engine;

import com.saathchalo.games.engine.ChessPosition.Move;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The computer opponent: a small search (negamax with alpha-beta and capture search) over material and piece placement.
 * Easy looks one move ahead and sometimes plays a random move; medium looks two; hard looks three and follows captures.
 */
public final class ChessBot {

	public enum Level {
		EASY, MEDIUM, HARD
	}

	private static final int[] VALUE = { 0, 100, 320, 330, 500, 900, 0 };

	private static final int MATE = 100_000;

	/** Pawns are worth more the further they are; pieces like the centre. Indexed from white's side (a1 = 0). */
	private static final int[] PAWN_BONUS = { 0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 10, -20, -20, 10, 10, 5, 5, -5, -10, 0, 0, -10, -5, 5,
			0, 0, 0, 20, 20, 0, 0, 0, 5, 5, 10, 25, 25, 10, 5, 5, 10, 10, 20, 30, 30, 20, 10, 10, 50, 50, 50, 50, 50, 50, 50, 50,
			0, 0, 0, 0, 0, 0, 0, 0 };

	private static final int[] CENTRE = { -50, -40, -30, -30, -30, -30, -40, -50, -40, -20, 0, 5, 5, 0, -20, -40, -30, 5, 10, 15, 15,
			10, 5, -30, -30, 0, 15, 20, 20, 15, 0, -30, -30, 5, 15, 20, 20, 15, 5, -30, -30, 0, 10, 15, 15, 10, 0, -30, -40, -20, 0, 0, 0,
			0, -20, -40, -50, -40, -30, -30, -30, -30, -40, -50 };

	private static final int[] KING_HOME = { 20, 30, 10, 0, 0, 10, 30, 20, 20, 20, 0, 0, 0, 0, 20, 20, -10, -20, -20, -20, -20, -20,
			-20, -10, -20, -30, -30, -40, -40, -30, -30, -20, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40,
			-30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30 };

	/** A ceiling on the work per move, so the server always answers quickly. */
	private static final int NODE_BUDGET = 400_000;

	private int nodes;

	private ChessBot() {
	}

	public static Move choose(ChessPosition p, Level level, RandomGenerator random) {
		List<Move> moves = p.legalMoves();
		if (moves.isEmpty()) {
			return null;
		}
		if (level == Level.EASY && random.nextInt(100) < 30) {
			return moves.get(random.nextInt(moves.size()));
		}
		int depth = switch (level) {
			case EASY -> 1;
			case MEDIUM -> 2;
			case HARD -> 3;
		};
		ChessBot bot = new ChessBot();
		int noise = level == Level.HARD ? 3 : 12;
		Move best = null;
		int bestScore = Integer.MIN_VALUE;
		for (Move m : ordered(p, moves)) {
			int score = -bot.search(p.play(m), depth - 1, -MATE * 2, MATE * 2, level == Level.HARD) + random.nextInt(noise);
			if (score > bestScore) {
				bestScore = score;
				best = m;
			}
		}
		return best;
	}

	private int search(ChessPosition p, int depth, int alpha, int beta, boolean followCaptures) {
		nodes++;
		List<Move> moves = p.legalMoves();
		if (moves.isEmpty()) {
			return p.inCheck() ? -(MATE + depth) : 0;
		}
		if (p.halfmoves() >= 100) {
			return 0;
		}
		if (depth <= 0 || nodes > NODE_BUDGET) {
			return followCaptures && nodes <= NODE_BUDGET ? captures(p, alpha, beta, 4) : evaluate(p);
		}
		for (Move m : ordered(p, moves)) {
			int score = -search(p.play(m), depth - 1, -beta, -alpha, followCaptures);
			if (score >= beta) {
				return beta;
			}
			alpha = Math.max(alpha, score);
		}
		return alpha;
	}

	/** Keeps looking while captures are possible, so the bot doesn't stop in the middle of an exchange. */
	private int captures(ChessPosition p, int alpha, int beta, int left) {
		nodes++;
		int standPat = evaluate(p);
		if (standPat >= beta) {
			return beta;
		}
		if (left == 0) {
			return Math.max(alpha, standPat);
		}
		alpha = Math.max(alpha, standPat);
		for (Move m : ordered(p, p.legalMoves())) {
			if (p.piece(m.to()) == 0 && m.promotion() == 0) {
				continue;
			}
			int score = -captures(p.play(m), -beta, -alpha, left - 1);
			if (score >= beta) {
				return beta;
			}
			alpha = Math.max(alpha, score);
		}
		return alpha;
	}

	/** Captures first (most valuable victim, least valuable attacker), then promotions, then the rest. */
	private static List<Move> ordered(ChessPosition p, List<Move> moves) {
		List<Move> list = new ArrayList<>(moves);
		list.sort(Comparator.comparingInt((Move m) -> -(VALUE[Math.abs(p.piece(m.to()))] * 10 - VALUE[Math.abs(p.piece(m.from()))] / 10
				+ (m.promotion() != 0 ? 800 : 0) + (p.piece(m.to()) != 0 ? 1000 : 0))));
		return list;
	}

	/** The position's value for the side to move, in hundredths of a pawn. */
	static int evaluate(ChessPosition p) {
		int score = 0;
		for (int sq = 0; sq < 64; sq++) {
			int piece = p.piece(sq);
			if (piece == 0) {
				continue;
			}
			int type = Math.abs(piece);
			int index = piece > 0 ? sq : (7 - sq / 8) * 8 + sq % 8;
			int value = VALUE[type] + switch (type) {
				case ChessPosition.PAWN -> PAWN_BONUS[index];
				case ChessPosition.KNIGHT -> CENTRE[index];
				case ChessPosition.BISHOP, ChessPosition.QUEEN -> CENTRE[index] / 2;
				case ChessPosition.ROOK -> index / 8 == 6 ? 15 : 0;
				default -> KING_HOME[index] / 2;
			};
			score += piece > 0 ? value : -value;
		}
		return p.whiteToMove() ? score : -score;
	}
}
