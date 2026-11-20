package com.saathchalo.games.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.saathchalo.games.engine.ChessBot.Level;
import com.saathchalo.games.engine.ChessPosition.Move;
import com.saathchalo.games.engine.ChessPosition.Outcome;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The move generator is checked with "perft": counting every position reachable in n moves and comparing with the
 * well-known totals. These positions cover castling, en passant, promotions, pins and checks.
 */
class ChessPositionTest {

	private static long perft(ChessPosition p, int depth) {
		if (depth == 0) {
			return 1;
		}
		long n = 0;
		for (Move m : p.legalMoves()) {
			n += perft(p.play(m), depth - 1);
		}
		return n;
	}

	@Test
	void countsMovesFromTheStart() {
		ChessPosition start = ChessPosition.start();
		assertThat(perft(start, 1)).isEqualTo(20);
		assertThat(perft(start, 2)).isEqualTo(400);
		assertThat(perft(start, 3)).isEqualTo(8_902);
		assertThat(perft(start, 4)).isEqualTo(197_281);
	}

	@Test
	void countsMovesInTrickyPositions() {
		// "Kiwipete": castling both ways, en passant, pins.
		ChessPosition kiwipete = ChessPosition.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
		assertThat(perft(kiwipete, 1)).isEqualTo(48);
		assertThat(perft(kiwipete, 2)).isEqualTo(2_039);
		assertThat(perft(kiwipete, 3)).isEqualTo(97_862);
		// Rook and pawn endgame with en passant pins.
		ChessPosition endgame = ChessPosition.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1");
		assertThat(perft(endgame, 1)).isEqualTo(14);
		assertThat(perft(endgame, 2)).isEqualTo(191);
		assertThat(perft(endgame, 3)).isEqualTo(2_812);
		assertThat(perft(endgame, 4)).isEqualTo(43_238);
		// Promotions and checks.
		ChessPosition promotions = ChessPosition.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1");
		assertThat(perft(promotions, 1)).isEqualTo(6);
		assertThat(perft(promotions, 2)).isEqualTo(264);
		assertThat(perft(promotions, 3)).isEqualTo(9_467);
		ChessPosition five = ChessPosition.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8");
		assertThat(perft(five, 1)).isEqualTo(44);
		assertThat(perft(five, 2)).isEqualTo(1_486);
		assertThat(perft(five, 3)).isEqualTo(62_379);
	}

	@Test
	void writesMovesTheWayPlayersDo() {
		ChessPosition p = ChessPosition.start();
		assertThat(p.san(p.parse("g1f3"))).isEqualTo("Nf3");
		assertThat(p.san(p.parse("e2e4"))).isEqualTo("e4");
		// Scholar's mate.
		for (String m : new String[] { "e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6" }) {
			p = p.play(p.parse(m));
		}
		assertThat(p.san(p.parse("h5f7"))).isEqualTo("Qxf7#");
		ChessPosition mated = p.play(p.parse("h5f7"));
		assertThat(mated.outcome()).isEqualTo(Outcome.CHECKMATE);
		assertThat(mated.fen()).isEqualTo("r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4");
		// Castling, promotion and telling two knights apart.
		ChessPosition castle = ChessPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
		assertThat(castle.san(castle.parse("e1g1"))).isEqualTo("O-O");
		assertThat(castle.san(castle.parse("e1c1"))).isEqualTo("O-O-O");
		ChessPosition promote = ChessPosition.fromFen("7k/P7/8/8/8/8/8/K7 w - - 0 1");
		assertThat(promote.san(promote.parse("a7a8q"))).isEqualTo("a8=Q+");
		assertThat(promote.parse("a7a8")).isNull();
		ChessPosition knights = ChessPosition.fromFen("k7/8/8/8/8/8/8/KN3N2 w - - 0 1");
		assertThat(knights.san(knights.parse("b1d2"))).isEqualTo("Nbd2");
	}

	@Test
	void knowsWhenAGameIsDrawn() {
		assertThat(ChessPosition.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").outcome()).isEqualTo(Outcome.STALEMATE);
		assertThat(ChessPosition.fromFen("8/8/4k3/8/8/2B5/4K3/8 w - - 0 1").outcome()).isEqualTo(Outcome.INSUFFICIENT_MATERIAL);
		assertThat(ChessPosition.fromFen("8/8/4k3/8/8/2B5/4K3/7r w - - 0 1").outcome()).isEqualTo(Outcome.NONE);
		assertThat(ChessPosition.fromFen("8/8/4k3/8/8/2R5/4K3/8 w - - 100 80").outcome()).isEqualTo(Outcome.FIFTY_MOVES);
	}

	@Test
	void theComputerTakesFreePiecesAndMates() {
		Random random = new Random(7);
		// A queen left hanging.
		ChessPosition hanging = ChessPosition.fromFen("4k3/8/8/3q4/8/8/3R4/4K3 w - - 0 1");
		assertThat(ChessBot.choose(hanging, Level.MEDIUM, random).uci()).isEqualTo("d2d5");
		// Mate in one.
		ChessPosition mateInOne = ChessPosition.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1");
		assertThat(ChessBot.choose(mateInOne, Level.HARD, random).uci()).isEqualTo("a1a8");
		// Every level always answers with a legal move.
		ChessPosition p = ChessPosition.start();
		for (Level level : Level.values()) {
			Move m = ChessBot.choose(p, level, random);
			assertThat(p.legalMoves()).contains(m);
		}
	}
}
