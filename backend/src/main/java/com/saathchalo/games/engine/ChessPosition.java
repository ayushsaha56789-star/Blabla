package com.saathchalo.games.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules of chess: a position (read and written as FEN), its legal moves, move notation (SAN) and how a game ends.
 * Squares are 0 (a1) to 63 (h8). Pieces are 1 pawn, 2 knight, 3 bishop, 4 rook, 5 queen, 6 king: positive for white,
 * negative for black. Positions never change; {@link #play} returns a new one.
 */
public final class ChessPosition {

	public static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	public static final int PAWN = 1;

	public static final int KNIGHT = 2;

	public static final int BISHOP = 3;

	public static final int ROOK = 4;

	public static final int QUEEN = 5;

	public static final int KING = 6;

	private static final int[][] KNIGHT_STEPS = { { 1, 2 }, { 2, 1 }, { 2, -1 }, { 1, -2 }, { -1, -2 }, { -2, -1 }, { -2, 1 }, { -1, 2 } };

	private static final int[][] KING_STEPS = { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 } };

	private static final int[][] DIAGONALS = { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

	private static final int[][] LINES = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

	/** Castling rights kept when a piece moves from or to each square (a rook or king leaving home loses them). */
	private static final int[] RIGHTS_KEPT = new int[64];

	static {
		java.util.Arrays.fill(RIGHTS_KEPT, 15);
		RIGHTS_KEPT[0] = ~2 & 15;
		RIGHTS_KEPT[4] = ~3 & 15;
		RIGHTS_KEPT[7] = ~1 & 15;
		RIGHTS_KEPT[56] = ~8 & 15;
		RIGHTS_KEPT[60] = ~12 & 15;
		RIGHTS_KEPT[63] = ~4 & 15;
	}

	public enum Outcome {
		NONE, CHECKMATE, STALEMATE, INSUFFICIENT_MATERIAL, FIFTY_MOVES
	}

	/** A move; {@code promotion} is the piece type a pawn becomes (2–5), or 0. */
	public record Move(int from, int to, int promotion) {

		public String uci() {
			return name(from) + name(to) + (promotion == 0 ? "" : String.valueOf("  nbrq".charAt(promotion)));
		}
	}

	private final int[] board;

	private final boolean whiteToMove;

	/** 1 white king side, 2 white queen side, 4 black king side, 8 black queen side. */
	private final int castling;

	/** The square a pawn may capture onto en passant, or -1. */
	private final int epSquare;

	private final int halfmoves;

	private final int fullmoves;

	private List<Move> legal;

	private ChessPosition(int[] board, boolean whiteToMove, int castling, int epSquare, int halfmoves, int fullmoves) {
		this.board = board;
		this.whiteToMove = whiteToMove;
		this.castling = castling;
		this.epSquare = epSquare;
		this.halfmoves = halfmoves;
		this.fullmoves = fullmoves;
	}

	public static ChessPosition start() {
		return fromFen(START);
	}

	public static ChessPosition fromFen(String fen) {
		String[] parts = fen.trim().split("\\s+");
		int[] b = new int[64];
		int rank = 7;
		int file = 0;
		for (char c : parts[0].toCharArray()) {
			if (c == '/') {
				rank--;
				file = 0;
			}
			else if (Character.isDigit(c)) {
				file += c - '0';
			}
			else {
				b[rank * 8 + file] = pieceOf(c);
				file++;
			}
		}
		int rights = 0;
		String c = parts.length > 2 ? parts[2] : "-";
		rights |= c.contains("K") ? 1 : 0;
		rights |= c.contains("Q") ? 2 : 0;
		rights |= c.contains("k") ? 4 : 0;
		rights |= c.contains("q") ? 8 : 0;
		int ep = parts.length > 3 && !parts[3].equals("-") ? square(parts[3]) : -1;
		int half = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
		int full = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
		return new ChessPosition(b, parts.length < 2 || parts[1].equals("w"), rights, ep, half, full);
	}

	public String fen() {
		return key() + " " + halfmoves + " " + fullmoves;
	}

	/** The position without the move counters: equal keys mean the same position came up again. */
	public String key() {
		StringBuilder s = new StringBuilder();
		for (int r = 7; r >= 0; r--) {
			int empty = 0;
			for (int f = 0; f < 8; f++) {
				int p = board[r * 8 + f];
				if (p == 0) {
					empty++;
					continue;
				}
				if (empty > 0) {
					s.append(empty);
					empty = 0;
				}
				s.append(letterOf(p));
			}
			if (empty > 0) {
				s.append(empty);
			}
			if (r > 0) {
				s.append('/');
			}
		}
		String rights = ((castling & 1) != 0 ? "K" : "") + ((castling & 2) != 0 ? "Q" : "") + ((castling & 4) != 0 ? "k" : "")
				+ ((castling & 8) != 0 ? "q" : "");
		return s + (whiteToMove ? " w " : " b ") + (rights.isEmpty() ? "-" : rights) + " " + (epSquare < 0 ? "-" : name(epSquare));
	}

	public boolean whiteToMove() {
		return whiteToMove;
	}

	public int piece(int square) {
		return board[square];
	}

	public int halfmoves() {
		return halfmoves;
	}

	public List<Move> legalMoves() {
		if (legal == null) {
			List<Move> out = new ArrayList<>();
			for (Move m : pseudoMoves()) {
				ChessPosition next = play(m);
				if (!next.attacked(next.kingSquare(whiteToMove), !whiteToMove)) {
					out.add(m);
				}
			}
			legal = List.copyOf(out);
		}
		return legal;
	}

	/** The legal move written as UCI ("e2e4", "e7e8q"), or null. */
	public Move parse(String uci) {
		if (uci == null) {
			return null;
		}
		String u = uci.strip().toLowerCase(java.util.Locale.ROOT);
		for (Move m : legalMoves()) {
			if (m.uci().equals(u)) {
				return m;
			}
		}
		return null;
	}

	public boolean inCheck() {
		return attacked(kingSquare(whiteToMove), !whiteToMove);
	}

	public Outcome outcome() {
		if (legalMoves().isEmpty()) {
			return inCheck() ? Outcome.CHECKMATE : Outcome.STALEMATE;
		}
		if (halfmoves >= 100) {
			return Outcome.FIFTY_MOVES;
		}
		return insufficientMaterial() ? Outcome.INSUFFICIENT_MATERIAL : Outcome.NONE;
	}

	public ChessPosition play(Move m) {
		int[] b = board.clone();
		int piece = b[m.from()];
		int type = Math.abs(piece);
		int captured = b[m.to()];
		int ep = -1;
		b[m.to()] = m.promotion() != 0 ? (piece > 0 ? m.promotion() : -m.promotion()) : piece;
		b[m.from()] = 0;
		if (type == PAWN) {
			if (m.to() == epSquare) {
				b[m.to() + (piece > 0 ? -8 : 8)] = 0;
			}
			if (Math.abs(m.to() - m.from()) == 16) {
				ep = (m.from() + m.to()) / 2;
			}
		}
		if (type == KING && Math.abs(m.to() - m.from()) == 2) {
			if (m.to() > m.from()) {
				b[m.from() + 1] = b[m.from() + 3];
				b[m.from() + 3] = 0;
			}
			else {
				b[m.from() - 1] = b[m.from() - 4];
				b[m.from() - 4] = 0;
			}
		}
		int rights = castling & RIGHTS_KEPT[m.from()] & RIGHTS_KEPT[m.to()];
		int half = type == PAWN || captured != 0 ? 0 : halfmoves + 1;
		return new ChessPosition(b, !whiteToMove, rights, ep, half, whiteToMove ? fullmoves : fullmoves + 1);
	}

	/** Standard algebraic notation: "Nf3", "exd5", "O-O", "e8=Q+", "Qxf7#". */
	public String san(Move m) {
		int piece = board[m.from()];
		int type = Math.abs(piece);
		String s;
		if (type == KING && Math.abs(m.to() - m.from()) == 2) {
			s = m.to() > m.from() ? "O-O" : "O-O-O";
		}
		else {
			boolean capture = board[m.to()] != 0 || (type == PAWN && m.to() == epSquare);
			StringBuilder b = new StringBuilder();
			if (type == PAWN) {
				if (capture) {
					b.append((char) ('a' + m.from() % 8));
				}
			}
			else {
				b.append("NBRQK".charAt(type - 2));
				boolean other = false;
				boolean sameFile = false;
				boolean sameRank = false;
				for (Move o : legalMoves()) {
					if (o.to() == m.to() && o.from() != m.from() && board[o.from()] == piece) {
						other = true;
						sameFile |= o.from() % 8 == m.from() % 8;
						sameRank |= o.from() / 8 == m.from() / 8;
					}
				}
				if (other) {
					if (!sameFile) {
						b.append((char) ('a' + m.from() % 8));
					}
					else if (!sameRank) {
						b.append((char) ('1' + m.from() / 8));
					}
					else {
						b.append(name(m.from()));
					}
				}
			}
			if (capture) {
				b.append('x');
			}
			b.append(name(m.to()));
			if (m.promotion() != 0) {
				b.append('=').append("NBRQ".charAt(m.promotion() - 2));
			}
			s = b.toString();
		}
		ChessPosition next = play(m);
		return next.inCheck() ? s + (next.legalMoves().isEmpty() ? "#" : "+") : s;
	}

	/** Whether a piece of the given side attacks the square. */
	boolean attacked(int sq, boolean byWhite) {
		int f = sq % 8;
		int r = sq / 8;
		int sign = byWhite ? 1 : -1;
		int pr = r - sign;
		if (pr >= 0 && pr <= 7) {
			if (f > 0 && board[pr * 8 + f - 1] == sign * PAWN) {
				return true;
			}
			if (f < 7 && board[pr * 8 + f + 1] == sign * PAWN) {
				return true;
			}
		}
		if (stepAttack(f, r, KNIGHT_STEPS, sign * KNIGHT) || stepAttack(f, r, KING_STEPS, sign * KING)) {
			return true;
		}
		return slideAttack(f, r, DIAGONALS, sign * BISHOP, sign * QUEEN) || slideAttack(f, r, LINES, sign * ROOK, sign * QUEEN);
	}

	private boolean stepAttack(int f, int r, int[][] steps, int attacker) {
		for (int[] d : steps) {
			int f1 = f + d[0];
			int r1 = r + d[1];
			if (f1 >= 0 && f1 <= 7 && r1 >= 0 && r1 <= 7 && board[r1 * 8 + f1] == attacker) {
				return true;
			}
		}
		return false;
	}

	private boolean slideAttack(int f, int r, int[][] dirs, int a, int b) {
		for (int[] d : dirs) {
			int f1 = f + d[0];
			int r1 = r + d[1];
			while (f1 >= 0 && f1 <= 7 && r1 >= 0 && r1 <= 7) {
				int p = board[r1 * 8 + f1];
				if (p != 0) {
					if (p == a || p == b) {
						return true;
					}
					break;
				}
				f1 += d[0];
				r1 += d[1];
			}
		}
		return false;
	}

	private int kingSquare(boolean white) {
		int king = white ? KING : -KING;
		for (int i = 0; i < 64; i++) {
			if (board[i] == king) {
				return i;
			}
		}
		throw new IllegalStateException("No king on the board");
	}

	private boolean own(int p) {
		return whiteToMove ? p > 0 : p < 0;
	}

	private boolean enemy(int p) {
		return whiteToMove ? p < 0 : p > 0;
	}

	private List<Move> pseudoMoves() {
		List<Move> out = new ArrayList<>(48);
		int sign = whiteToMove ? 1 : -1;
		for (int sq = 0; sq < 64; sq++) {
			int p = board[sq] * sign;
			if (p <= 0) {
				continue;
			}
			int f = sq % 8;
			int r = sq / 8;
			switch (p) {
				case PAWN -> pawnMoves(sq, f, r, out);
				case KNIGHT -> steps(sq, f, r, KNIGHT_STEPS, out);
				case BISHOP -> slides(sq, f, r, DIAGONALS, out);
				case ROOK -> slides(sq, f, r, LINES, out);
				case QUEEN -> {
					slides(sq, f, r, DIAGONALS, out);
					slides(sq, f, r, LINES, out);
				}
				default -> {
					steps(sq, f, r, KING_STEPS, out);
					castles(sq, out);
				}
			}
		}
		return out;
	}

	private void pawnMoves(int sq, int f, int r, List<Move> out) {
		int dir = whiteToMove ? 1 : -1;
		int r1 = r + dir;
		if (r1 < 0 || r1 > 7) {
			return;
		}
		boolean promotes = r1 == (whiteToMove ? 7 : 0);
		int one = r1 * 8 + f;
		if (board[one] == 0) {
			addPawn(sq, one, promotes, out);
			int r2 = r + 2 * dir;
			if (r == (whiteToMove ? 1 : 6) && board[r2 * 8 + f] == 0) {
				out.add(new Move(sq, r2 * 8 + f, 0));
			}
		}
		for (int df = -1; df <= 1; df += 2) {
			int f1 = f + df;
			if (f1 < 0 || f1 > 7) {
				continue;
			}
			int to = r1 * 8 + f1;
			if (enemy(board[to]) || to == epSquare) {
				addPawn(sq, to, promotes, out);
			}
		}
	}

	private static void addPawn(int from, int to, boolean promotes, List<Move> out) {
		if (!promotes) {
			out.add(new Move(from, to, 0));
			return;
		}
		for (int t = QUEEN; t >= KNIGHT; t--) {
			out.add(new Move(from, to, t));
		}
	}

	private void steps(int sq, int f, int r, int[][] deltas, List<Move> out) {
		for (int[] d : deltas) {
			int f1 = f + d[0];
			int r1 = r + d[1];
			if (f1 >= 0 && f1 <= 7 && r1 >= 0 && r1 <= 7 && !own(board[r1 * 8 + f1])) {
				out.add(new Move(sq, r1 * 8 + f1, 0));
			}
		}
	}

	private void slides(int sq, int f, int r, int[][] dirs, List<Move> out) {
		for (int[] d : dirs) {
			int f1 = f + d[0];
			int r1 = r + d[1];
			while (f1 >= 0 && f1 <= 7 && r1 >= 0 && r1 <= 7) {
				int to = r1 * 8 + f1;
				if (own(board[to])) {
					break;
				}
				out.add(new Move(sq, to, 0));
				if (board[to] != 0) {
					break;
				}
				f1 += d[0];
				r1 += d[1];
			}
		}
	}

	private void castles(int sq, List<Move> out) {
		int home = whiteToMove ? 4 : 60;
		if (sq != home) {
			return;
		}
		boolean byWhite = !whiteToMove;
		int rook = whiteToMove ? ROOK : -ROOK;
		if ((castling & (whiteToMove ? 1 : 4)) != 0 && board[home + 1] == 0 && board[home + 2] == 0 && board[home + 3] == rook
				&& !attacked(home, byWhite) && !attacked(home + 1, byWhite) && !attacked(home + 2, byWhite)) {
			out.add(new Move(home, home + 2, 0));
		}
		if ((castling & (whiteToMove ? 2 : 8)) != 0 && board[home - 1] == 0 && board[home - 2] == 0 && board[home - 3] == 0
				&& board[home - 4] == rook && !attacked(home, byWhite) && !attacked(home - 1, byWhite) && !attacked(home - 2, byWhite)) {
			out.add(new Move(home, home - 2, 0));
		}
	}

	/** King against king, with at most one knight, or with bishops all on squares of one colour. */
	private boolean insufficientMaterial() {
		int minors = 0;
		int knights = 0;
		int lightBishops = 0;
		int darkBishops = 0;
		for (int i = 0; i < 64; i++) {
			int t = Math.abs(board[i]);
			if (t == PAWN || t == ROOK || t == QUEEN) {
				return false;
			}
			if (t == KNIGHT) {
				minors++;
				knights++;
			}
			if (t == BISHOP) {
				minors++;
				if ((i % 8 + i / 8) % 2 == 0) {
					darkBishops++;
				}
				else {
					lightBishops++;
				}
			}
		}
		return minors <= 1 || (knights == 0 && (lightBishops == 0 || darkBishops == 0));
	}

	static int pieceOf(char c) {
		int t = "pnbrqk".indexOf(Character.toLowerCase(c)) + 1;
		return Character.isUpperCase(c) ? t : -t;
	}

	static char letterOf(int p) {
		char c = "pnbrqk".charAt(Math.abs(p) - 1);
		return p > 0 ? Character.toUpperCase(c) : c;
	}

	public static String name(int sq) {
		return "" + (char) ('a' + sq % 8) + (char) ('1' + sq / 8);
	}

	public static int square(String name) {
		return (name.charAt(1) - '1') * 8 + (name.charAt(0) - 'a');
	}
}
