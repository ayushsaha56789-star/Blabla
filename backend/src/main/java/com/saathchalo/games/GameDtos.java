package com.saathchalo.games;

import com.saathchalo.user.MemberProfiles.MemberCard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Games: chess and ludo rooms, the board as each player sees it, ratings. */
public final class GameDtos {

	private GameDtos() {
	}

	public enum Game {
		CHESS, LUDO
	}

	/** COMPUTER: against computer players. FRIENDS: a room with a code. QUICK and RIDE are joined, not created. */
	public enum Mode {
		COMPUTER, FRIENDS, QUICK, RIDE
	}

	public enum Colour {
		WHITE, BLACK, RANDOM
	}

	/** How well computer players play. */
	public enum Level {
		EASY, MEDIUM, HARD
	}

	/** {@code players}: ludo seats (2–4); {@code colour}: chess against the computer. */
	public record CreateRequest(
			@NotNull(message = "Chess or ludo?") Game game,
			@NotNull(message = "Against the computer or friends?") Mode mode,
			Level level,
			@Min(value = 2, message = "At least 2 players") @Max(value = 4, message = "At most 4 players") Integer players,
			Colour colour) {
	}

	public record JoinRequest(@NotBlank(message = "Enter the room code") @Size(max = 12, message = "That code is too long") String code) {
	}

	public record GameRequest(@NotNull(message = "Chess or ludo?") Game game) {
	}

	public record StartRequest(Boolean fillWithComputer) {
	}

	public record MoveRequest(@NotBlank(message = "Which move?") @Size(max = 8, message = "That isn't a move") String move) {
	}

	public record TokenRequest(@NotNull(message = "Which token?") @Min(0) @Max(3) Integer token) {
	}

	public record SayRequest(@NotBlank(message = "Pick something to say") @Size(max = 16) String phrase) {
	}

	/** A seat at the table. {@code member} is null for a computer player. */
	public record SeatView(int seat, MemberCard member, String name, boolean computer, boolean away, boolean you, String outcome,
			Integer ratingChange) {
	}

	/**
	 * Chess as a player sees it. {@code legal}: the viewer's moves in UCI ("e2e4", "e7e8q") when it's their turn.
	 * Clock times are what's left right now; the running clock is the side to move's.
	 */
	public record ChessView(String fen, boolean whiteToMove, List<String> legal, List<String> moves, String lastMove, boolean check,
			Long whiteMs, Long blackMs) {
	}

	/** Ludo as a player sees it. {@code movable}: the viewer's tokens that can move with the dice. */
	public record LudoView(int[] seats, int[][] tokens, int current, Integer dice, List<Integer> movable, boolean canRoll, String last,
			int[] lastMove) {
	}

	public record MessageView(int seat, String name, String phrase, Instant at) {
	}

	/**
	 * A game room. {@code turnSeat}: whose turn it is; {@code deadline}: when that turn plays itself (computer players)
	 * or runs out (people); {@code serverNow} lets the page count down with the server's clock.
	 */
	public record GameView(UUID id, Game game, Mode mode, String code, String status, int version, int maxPlayers, Level level,
			List<SeatView> seats, Integer mySeat, Integer turnSeat, Instant deadline, boolean host, boolean rated, String result,
			ChessView chess, LudoView ludo, List<MessageView> messages, Instant serverNow) {
	}

	public record GameSummary(UUID id, Game game, Mode mode, String status, List<String> players, boolean myTurn, String outcome,
			String result, Instant updatedAt) {
	}

	public record LeaderRow(int rank, MemberCard member, int rating, int played, int won) {
	}

	public record Leaderboard(Game game, List<LeaderRow> top, LeaderRow me) {
	}

	/** The set phrases (and emojis) players can send each other. */
	public static final List<String> PHRASES = List.of("HELLO", "GG", "WP", "NICE", "OOPS", "THINK", "THANKS", "HURRY", "👍", "😂",
			"😮", "😅", "🎉", "🔥", "🙏", "😎");
}
