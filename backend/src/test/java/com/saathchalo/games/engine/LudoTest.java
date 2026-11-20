package com.saathchalo.games.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Random;
import org.junit.jupiter.api.Test;

class LudoTest {

	@Test
	void aSixBringsATokenOutAndGivesAnotherRoll() {
		Ludo g = Ludo.start(2);
		assertThat(g.seats).containsExactly(0, 2);
		assertThat(g.roll(3)).isFalse();
		assertThat(g.last).isEqualTo("Orange rolled 3: no move");
		assertThat(g.current()).isEqualTo(2);
		assertThat(g.roll(6)).isTrue();
		assertThat(g.movable()).containsExactly(0, 1, 2, 3);
		g.move(1);
		assertThat(g.tokens[2][1]).isZero();
		assertThat(g.current()).isEqualTo(2);
		assertThat(g.dice).isNull();
		g.roll(4);
		g.move(1);
		assertThat(g.tokens[2][1]).isEqualTo(4);
		assertThat(g.current()).isZero();
	}

	@Test
	void landingOnAnOpponentSendsItHome() {
		Ludo g = Ludo.start(2);
		g.tokens[0][0] = 0;
		g.tokens[2][0] = 31; // Blue's 31st square is the shared square 5.
		assertThat(Ludo.square(2, 31)).isEqualTo(5);
		g.roll(5);
		g.move(0);
		assertThat(g.tokens[2][0]).isEqualTo(Ludo.YARD);
		assertThat(g.last).isEqualTo("Orange captured Blue");
		assertThat(g.current()).as("a capture gives another roll").isZero();

		// Nobody is captured on a safe square (a star).
		g.tokens[0][1] = 3;
		g.tokens[2][1] = 34; // shared square 8, a star
		g.roll(5);
		g.move(1);
		assertThat(g.tokens[2][1]).isEqualTo(34);
		assertThat(g.current()).isEqualTo(2);
	}

	@Test
	void homeNeedsTheExactNumberAndAllFourWin() {
		Ludo g = Ludo.start(2);
		g.tokens[0] = new int[] { 53, Ludo.HOME, Ludo.HOME, Ludo.HOME };
		g.roll(4);
		assertThat(g.current()).as("4 overshoots home: no move").isEqualTo(2);
		g.roll(2);
		assertThat(g.current()).as("Blue has nothing out either").isZero();
		g.roll(3);
		g.move(0);
		assertThat(g.winner).isZero();
		assertThat(g.last).isEqualTo("Orange won!");
		assertThatThrownBy(() -> g.roll(1)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void threeSixesInARowLoseTheTurn() {
		Ludo g = Ludo.start(3);
		g.roll(6);
		g.move(0);
		g.roll(6);
		g.move(0);
		assertThat(g.roll(6)).isFalse();
		assertThat(g.last).isEqualTo("Orange rolled three sixes: turn over");
		assertThat(g.current()).isEqualTo(1);
		assertThat(g.tokens[0][0]).isEqualTo(6);
	}

	@Test
	void theComputerCapturesWhenItCan() {
		Ludo g = Ludo.start(2);
		g.tokens[0] = new int[] { 0, 20, Ludo.YARD, Ludo.YARD };
		g.tokens[2][0] = 31; // on shared square 5
		g.roll(5);
		assertThat(LudoBot.choose(g)).isZero();

		// A whole game between computers finishes, with every roll and move legal.
		Random random = new Random(11);
		Ludo game = Ludo.start(4);
		for (int i = 0; i < 5_000 && !game.over(); i++) {
			if (game.roll(1 + random.nextInt(6))) {
				game.move(LudoBot.choose(game));
			}
		}
		assertThat(game.over()).isTrue();
	}
}
