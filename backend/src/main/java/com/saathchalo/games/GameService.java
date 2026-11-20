package com.saathchalo.games;

import com.saathchalo.common.ApiException;
import com.saathchalo.features.FeatureService;
import com.saathchalo.games.GameDtos.ChessView;
import com.saathchalo.games.GameDtos.Colour;
import com.saathchalo.games.GameDtos.CreateRequest;
import com.saathchalo.games.GameDtos.Game;
import com.saathchalo.games.GameDtos.GameSummary;
import com.saathchalo.games.GameDtos.GameView;
import com.saathchalo.games.GameDtos.LeaderRow;
import com.saathchalo.games.GameDtos.Leaderboard;
import com.saathchalo.games.GameDtos.Level;
import com.saathchalo.games.GameDtos.LudoView;
import com.saathchalo.games.GameDtos.MessageView;
import com.saathchalo.games.GameDtos.Mode;
import com.saathchalo.games.GameDtos.SeatView;
import com.saathchalo.games.engine.ChessBot;
import com.saathchalo.games.engine.ChessPosition;
import com.saathchalo.games.engine.ChessPosition.Move;
import com.saathchalo.games.engine.Ludo;
import com.saathchalo.games.engine.LudoBot;
import com.saathchalo.games.internal.GameRoom;
import com.saathchalo.games.internal.GameRoomRepository;
import com.saathchalo.rides.RideLookup;
import com.saathchalo.rides.RideLookup.RideParticipants;
import com.saathchalo.user.BlockService;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Chess and ludo rooms. The server runs every game: it checks each move, rolls each die with a secure random number
 * generator, plays for computer players (after a short pause, so people can follow) and for people who run out of
 * time, keeps chess clocks, and rates online games between people. Pages ask for news every second or so; each change
 * bumps the room's version.
 */
@Service
public class GameService {

	static final Duration BOT_PAUSE = Duration.ofMillis(900);

	static final Duration LUDO_TURN = Duration.ofSeconds(30);

	static final long CHESS_CLOCK_MS = 10 * 60_000L;

	static final Duration WAITING_FOR = Duration.ofMinutes(30);

	private static final int MAX_CATCH_UP = 60;

	private static final String CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	/** The chess game as stored. Clocks in milliseconds; {@code turnSince}: when the side to move started thinking. */
	public static final class ChessState {

		public String fen = ChessPosition.START;

		public List<String> san = new ArrayList<>();

		public List<String> uci = new ArrayList<>();

		public List<String> keys = new ArrayList<>();

		public boolean clock;

		public long whiteMs;

		public long blackMs;

		public long turnSince;
	}

	record Player(int seat, UUID userId, String name, int timeouts, boolean away, String outcome, Integer ratingChange) {

		boolean computer() {
			return userId == null;
		}
	}

	private final GameRoomRepository rooms;

	private final MemberProfiles members;

	private final RideLookup rides;

	private final BlockService blocks;

	private final FeatureService features;

	private final ObjectMapper json;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	public GameService(GameRoomRepository rooms, MemberProfiles members, RideLookup rides, BlockService blocks, FeatureService features,
			ObjectMapper json, JdbcTemplate jdbc, Clock clock) {
		this.rooms = rooms;
		this.members = members;
		this.rides = rides;
		this.blocks = blocks;
		this.features = features;
		this.json = json;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// ----- Starting and joining -----

	@Transactional
	public GameView create(UUID me, CreateRequest req) {
		requireGame(me, req.game());
		Instant now = clock.instant();
		int seats = req.game() == Game.CHESS ? 2 : Math.max(2, Math.min(4, req.players() == null ? 4 : req.players()));
		if (req.mode() == Mode.COMPUTER) {
			require(me, "games.vs_computer");
			Level level = req.level() == null ? Level.MEDIUM : req.level();
			GameRoom room = rooms.saveAndFlush(new GameRoom(req.game().name(), Mode.COMPUTER.name(), null, null, me, seats, level.name(), now));
			Colour colour = req.colour() == null ? Colour.WHITE : req.colour();
			int mySeat = req.game() == Game.CHESS && (colour == Colour.BLACK || (colour == Colour.RANDOM && random.nextBoolean())) ? 1 : 0;
			addPlayer(room.getId(), mySeat, me, now);
			for (int seat = 0; seat < seats; seat++) {
				if (seat != mySeat) {
					addComputer(room.getId(), seat, level, now);
				}
			}
			begin(room, now);
			return view(room, me);
		}
		if (req.mode() != Mode.FRIENDS) {
			throw ApiException.badRequest("bad_mode", "Start a game against the computer or with friends.");
		}
		require(me, "games.online");
		GameRoom room = rooms.saveAndFlush(new GameRoom(req.game().name(), Mode.FRIENDS.name(), newCode(), null, me, seats, Level.MEDIUM.name(), now));
		addPlayer(room.getId(), 0, me, now);
		return view(room, me);
	}

	@Transactional
	public GameView join(UUID me, String code) {
		GameRoom found = rooms.findByCode(code.strip().toUpperCase(Locale.ROOT))
			.orElseThrow(() -> ApiException.notFound("room_not_found", "There's no game with that code. Check it with your friend."));
		GameRoom room = lock(found.getId());
		requireGame(me, Game.valueOf(room.getGame()));
		require(me, Mode.RIDE.name().equals(room.getMode()) ? "games.ride_lobby" : "games.online");
		if (Mode.RIDE.name().equals(room.getMode())) {
			onRide(me, room.getRideId());
		}
		if (seatOf(room.getId(), me) == null) {
			takeSeat(room, me);
		}
		return view(room, me);
	}

	/** Plays the next person who asks for a quick game of the same kind, or waits for them. */
	@Transactional
	public GameView quick(UUID me, Game game) {
		requireGame(me, game);
		require(me, "games.online");
		Instant now = clock.instant();
		List<UUID> waiting = jdbc.queryForList("""
				SELECT r.id FROM game_rooms r JOIN game_players p ON p.room_id = r.id
				WHERE r.mode = 'QUICK' AND r.game = ? AND r.status = 'WAITING' AND p.user_id = ?""", UUID.class, game.name(), me);
		if (!waiting.isEmpty()) {
			return view(lock(waiting.getFirst()), me);
		}
		UUID[] blocked = blocks.blockedEitherWay(me).toArray(new UUID[0]);
		List<UUID> open = jdbc.queryForList("""
				SELECT r.id FROM game_rooms r
				WHERE r.mode = 'QUICK' AND r.game = ? AND r.status = 'WAITING' AND r.created_at > ?
				  AND NOT EXISTS (SELECT 1 FROM game_players p WHERE p.room_id = r.id AND p.user_id = ANY (?))
				ORDER BY r.created_at LIMIT 1 FOR UPDATE SKIP LOCKED""", UUID.class, game.name(), Timestamp.from(now.minus(WAITING_FOR)),
				blocked);
		if (!open.isEmpty()) {
			GameRoom room = lock(open.getFirst());
			takeSeat(room, me);
			return view(room, me);
		}
		GameRoom room = rooms.saveAndFlush(new GameRoom(game.name(), Mode.QUICK.name(), null, null, me, 2, Level.MEDIUM.name(), now));
		addPlayer(room.getId(), 0, me, now);
		return view(room, me);
	}

	/** The game room for the people on a ride: joins the one that's open, or opens one. */
	@Transactional
	public GameView ride(UUID me, UUID rideId, Game game) {
		requireGame(me, game);
		require(me, "games.ride_lobby");
		onRide(me, rideId);
		Instant now = clock.instant();
		List<UUID> current = jdbc.queryForList("""
				SELECT r.id FROM game_rooms r JOIN game_players p ON p.room_id = r.id
				WHERE r.ride_id = ? AND r.game = ? AND r.status IN ('WAITING', 'PLAYING') AND p.user_id = ?
				ORDER BY r.created_at DESC LIMIT 1""", UUID.class, rideId, game.name(), me);
		if (!current.isEmpty()) {
			return view(lock(current.getFirst()), me);
		}
		List<UUID> open = jdbc.queryForList("""
				SELECT id FROM game_rooms WHERE ride_id = ? AND game = ? AND status = 'WAITING' ORDER BY created_at LIMIT 1""",
				UUID.class, rideId, game.name());
		if (!open.isEmpty()) {
			GameRoom room = lock(open.getFirst());
			if (players(room.getId()).size() < room.getMaxPlayers()) {
				takeSeat(room, me);
				return view(room, me);
			}
		}
		GameRoom room = rooms.saveAndFlush(new GameRoom(game.name(), Mode.RIDE.name(), newCode(), rideId, me, game == Game.CHESS ? 2 : 4,
				Level.MEDIUM.name(), now));
		addPlayer(room.getId(), 0, me, now);
		return view(room, me);
	}

	/** The host starts a waiting room; empty ludo seats can be given to computer players. */
	@Transactional
	public GameView start(UUID me, UUID id, boolean fillWithComputer) {
		GameRoom room = lock(id);
		requirePlayer(room, me);
		if (!GameRoom.WAITING.equals(room.getStatus())) {
			throw ApiException.conflict("already_started", "This game has already started.");
		}
		if (!room.getHostId().equals(me)) {
			throw ApiException.forbidden("not_host", "Only the person who opened the room can start it.");
		}
		Instant now = clock.instant();
		List<Player> ps = players(id);
		if (Game.CHESS.name().equals(room.getGame())) {
			if (ps.size() < 2) {
				throw ApiException.conflict("need_players", "Chess needs two players. Share the code with a friend.");
			}
		}
		else if (fillWithComputer) {
			for (int seat = 0; seat < room.getMaxPlayers(); seat++) {
				int s = seat;
				if (ps.stream().noneMatch(p -> p.seat() == s)) {
					addComputer(id, seat, Level.valueOf(room.getLevel()), now);
				}
			}
		}
		else if (ps.size() < 2) {
			throw ApiException.conflict("need_players",
					"Ludo needs at least two players. Share the code, or fill the empty seats with computer players.");
		}
		begin(room, now);
		return view(room, me);
	}

	/** Leaving a waiting room frees the seat; leaving a game in play hands it to the computer (chess: resigns). */
	@Transactional
	public void leave(UUID me, UUID id) {
		GameRoom room = lock(id);
		Player p = requirePlayer(room, me);
		Instant now = clock.instant();
		if (GameRoom.WAITING.equals(room.getStatus())) {
			jdbc.update("DELETE FROM game_players WHERE room_id = ? AND user_id = ?", id, me);
			List<Player> people = players(id).stream().filter(x -> !x.computer()).toList();
			if (people.isEmpty()) {
				room.abandon("Everyone left", now);
			}
			else if (room.getHostId().equals(me)) {
				room.host(people.getFirst().userId(), now);
			}
			else {
				room.touch(now);
			}
			return;
		}
		if (!GameRoom.PLAYING.equals(room.getStatus())) {
			return;
		}
		if (Game.CHESS.name().equals(room.getGame())) {
			resignAs(room, p, now);
			return;
		}
		jdbc.update("UPDATE game_players SET away = TRUE WHERE room_id = ? AND seat = ?", id, p.seat());
		Ludo g = ludo(room);
		room.play(room.getState(), g.current() == p.seat() ? now.plus(BOT_PAUSE) : room.getDeadline(), now);
	}

	// ----- Playing -----

	@Transactional
	public GameView move(UUID me, UUID id, String uci) {
		GameRoom room = lock(id);
		Instant now = clock.instant();
		catchUp(room, now);
		Player p = requireTurn(room, me, Game.CHESS);
		ChessState s = chess(room);
		ChessPosition pos = ChessPosition.fromFen(s.fen);
		Move m = pos.parse(uci);
		if (m == null) {
			throw ApiException.badRequest("illegal_move", "That move isn't allowed.");
		}
		back(room, p);
		playChess(room, s, pos, m, now);
		return view(room, me);
	}

	@Transactional
	public GameView resign(UUID me, UUID id) {
		GameRoom room = lock(id);
		Instant now = clock.instant();
		catchUp(room, now);
		Player p = requirePlayer(room, me);
		if (!Game.CHESS.name().equals(room.getGame()) || !GameRoom.PLAYING.equals(room.getStatus())) {
			throw ApiException.conflict("not_playing", "There's no chess game in play to resign.");
		}
		resignAs(room, p, now);
		return view(room, me);
	}

	@Transactional
	public GameView roll(UUID me, UUID id) {
		GameRoom room = lock(id);
		Instant now = clock.instant();
		catchUp(room, now);
		Player p = requireTurn(room, me, Game.LUDO);
		Ludo g = ludo(room);
		if (g.dice != null) {
			throw ApiException.conflict("already_rolled", "Move a token first.");
		}
		back(room, p);
		rollFor(room, g, now);
		return view(room, me);
	}

	@Transactional
	public GameView token(UUID me, UUID id, int token) {
		GameRoom room = lock(id);
		Instant now = clock.instant();
		catchUp(room, now);
		Player p = requireTurn(room, me, Game.LUDO);
		Ludo g = ludo(room);
		if (g.dice == null) {
			throw ApiException.conflict("roll_first", "Roll the dice first.");
		}
		if (!g.movable().contains(token)) {
			throw ApiException.badRequest("cant_move", "That token can't move with this roll.");
		}
		back(room, p);
		g.move(token);
		saveLudo(room, g, now);
		return view(room, me);
	}

	@Transactional
	public GameView say(UUID me, UUID id, String phrase) {
		if (!GameDtos.PHRASES.contains(phrase)) {
			throw ApiException.badRequest("unknown_phrase", "Pick one of the phrases.");
		}
		GameRoom room = lock(id);
		Player p = requirePlayer(room, me);
		Instant now = clock.instant();
		Boolean recent = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM game_messages WHERE room_id = ? AND user_id = ? AND created_at > ?)",
				Boolean.class, id, me, Timestamp.from(now.minusSeconds(2)));
		if (Boolean.TRUE.equals(recent)) {
			throw ApiException.tooManyRequests("slow_down", "One message every two seconds, please.");
		}
		jdbc.update("INSERT INTO game_messages (room_id, seat, user_id, phrase, created_at) VALUES (?, ?, ?, ?, ?)", id, p.seat(), me,
				phrase, Timestamp.from(now));
		room.touch(now);
		return view(room, me);
	}

	/**
	 * The room as this player sees it, or empty when nothing changed since {@code since}. Turns that are due (computer
	 * players, timeouts) are played first.
	 */
	@Transactional
	public Optional<GameView> poll(UUID me, UUID id, Integer since) {
		Map<String, Object> head;
		try {
			head = jdbc.queryForMap("SELECT status, deadline FROM game_rooms WHERE id = ?", id);
		}
		catch (EmptyResultDataAccessException e) {
			throw notFound();
		}
		Instant now = clock.instant();
		Timestamp deadline = (Timestamp) head.get("deadline");
		boolean due = GameRoom.PLAYING.equals(head.get("status")) && deadline != null && !deadline.toInstant().isAfter(now);
		GameRoom room = due ? lock(id) : rooms.findById(id).orElseThrow(GameService::notFound);
		if (due) {
			catchUp(room, now);
		}
		requirePlayer(room, me);
		return since != null && room.getVersion() == since ? Optional.empty() : Optional.of(view(room, me));
	}

	/** Plays any turns that are due in this room (used by the housekeeping job for games nobody is watching). */
	@Transactional
	public void catchUp(UUID id) {
		rooms.lockById(id).ifPresent(room -> catchUp(room, clock.instant()));
	}

	@Transactional(readOnly = true)
	public List<GameSummary> mine(UUID me) {
		List<UUID> ids = jdbc.queryForList("""
				SELECT r.id FROM game_rooms r JOIN game_players p ON p.room_id = r.id
				WHERE p.user_id = ? AND r.status <> 'ABANDONED' ORDER BY r.updated_at DESC LIMIT 30""", UUID.class, me);
		List<GameSummary> out = new ArrayList<>();
		for (GameRoom room : rooms.findAllById(ids)) {
			List<Player> ps = players(room.getId());
			Player mine = ps.stream().filter(p -> me.equals(p.userId())).findFirst().orElse(null);
			if (mine == null) {
				continue;
			}
			Integer turn = GameRoom.PLAYING.equals(room.getStatus()) ? turnSeat(room) : null;
			out.add(new GameSummary(room.getId(), Game.valueOf(room.getGame()), Mode.valueOf(room.getMode()), room.getStatus(),
					ps.stream().filter(p -> p != mine).map(Player::name).toList(), turn != null && turn == mine.seat(), mine.outcome(),
					room.getResult(), room.getUpdatedAt()));
		}
		out.sort(Comparator.comparing(GameSummary::updatedAt).reversed());
		return out;
	}

	@Transactional(readOnly = true)
	public Leaderboard leaderboard(UUID me, Game game) {
		List<Map<String, Object>> top = jdbc.queryForList("""
				SELECT user_id, rating, played, won FROM game_ratings WHERE game = ? AND played > 0
				ORDER BY rating DESC, played DESC LIMIT 20""", game.name());
		Map<UUID, MemberCard> cards = members.cards(top.stream().map(r -> (UUID) r.get("user_id")).toList());
		List<LeaderRow> rows = new ArrayList<>();
		for (int i = 0; i < top.size(); i++) {
			Map<String, Object> r = top.get(i);
			rows.add(new LeaderRow(i + 1, cards.getOrDefault((UUID) r.get("user_id"), MemberProfiles.deleted()), (int) r.get("rating"),
					(int) r.get("played"), (int) r.get("won")));
		}
		LeaderRow mine = null;
		if (me != null) {
			List<Map<String, Object>> my = jdbc.queryForList("SELECT rating, played, won FROM game_ratings WHERE user_id = ? AND game = ?",
					me, game.name());
			if (!my.isEmpty() && (int) my.getFirst().get("played") > 0) {
				int rating = (int) my.getFirst().get("rating");
				Integer above = jdbc.queryForObject("SELECT count(*) FROM game_ratings WHERE game = ? AND played > 0 AND rating > ?",
						Integer.class, game.name(), rating);
				mine = new LeaderRow((above == null ? 0 : above) + 1, members.card(me), rating, (int) my.getFirst().get("played"),
						(int) my.getFirst().get("won"));
			}
		}
		return new Leaderboard(game, rows, mine);
	}

	/** Waiting rooms nobody joined, games nobody touched for a day, and week-old chat go. */
	@Transactional
	public void tidy() {
		Instant now = clock.instant();
		jdbc.update("""
				UPDATE game_rooms SET status = 'ABANDONED', result = 'Nobody else joined', finished_at = ?, updated_at = ?, version = version + 1
				WHERE status = 'WAITING' AND created_at < ?""", Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.minus(WAITING_FOR)));
		jdbc.update("""
				UPDATE game_rooms SET status = 'ABANDONED', result = 'Not finished', deadline = NULL, finished_at = ?, updated_at = ?,
				    version = version + 1
				WHERE status = 'PLAYING' AND updated_at < ?""", Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.minus(Duration.ofDays(1))));
		jdbc.update("DELETE FROM game_messages WHERE created_at < ?", Timestamp.from(now.minus(Duration.ofDays(7))));
	}

	/** Rooms whose turn is due, for the housekeeping job. */
	List<UUID> due(int limit) {
		return jdbc.queryForList("SELECT id FROM game_rooms WHERE status = 'PLAYING' AND deadline <= ? ORDER BY deadline LIMIT ?", UUID.class,
				Timestamp.from(clock.instant()), limit);
	}

	// ----- Inside: the games -----

	private void begin(GameRoom room, Instant now) {
		List<Player> ps = players(room.getId());
		boolean rated = !Mode.COMPUTER.name().equals(room.getMode()) && ps.stream().noneMatch(Player::computer);
		if (Game.LUDO.name().equals(room.getGame())) {
			Ludo g = Ludo.start(ps.size());
			// Seats become board colours; two players sit opposite each other.
			jdbc.update("UPDATE game_players SET seat = seat + 10 WHERE room_id = ?", room.getId());
			for (int i = 0; i < ps.size(); i++) {
				jdbc.update("UPDATE game_players SET seat = ? WHERE room_id = ? AND seat = ?", g.seats[i], room.getId(), ps.get(i).seat() + 10);
			}
			g.last = "Game on! " + Ludo.name(g.current()) + " rolls first.";
			room.start(write(g), rated, null, now);
			room.play(room.getState(), deadline(room, g, now), now);
			return;
		}
		if (!Mode.COMPUTER.name().equals(room.getMode()) && random.nextBoolean()) {
			// A coin toss decides who plays white.
			jdbc.update("UPDATE game_players SET seat = 11 - seat WHERE room_id = ?", room.getId());
			jdbc.update("UPDATE game_players SET seat = seat - 10 WHERE room_id = ?", room.getId());
		}
		ChessState s = new ChessState();
		s.keys.add(ChessPosition.start().key());
		s.clock = !Mode.COMPUTER.name().equals(room.getMode());
		s.whiteMs = CHESS_CLOCK_MS;
		s.blackMs = CHESS_CLOCK_MS;
		s.turnSince = now.toEpochMilli();
		room.start(write(s), rated, null, now);
		room.play(room.getState(), deadline(room, s, now), now);
	}

	/** Plays every turn that has come due: computer players' moves, and people who ran out of time. */
	private void catchUp(GameRoom room, Instant now) {
		for (int i = 0; i < MAX_CATCH_UP && GameRoom.PLAYING.equals(room.getStatus()) && room.getDeadline() != null
				&& !room.getDeadline().isAfter(now); i++) {
			Instant at = room.getDeadline();
			if (Game.CHESS.name().equals(room.getGame())) {
				ChessState s = chess(room);
				ChessPosition pos = ChessPosition.fromFen(s.fen);
				int seat = pos.whiteToMove() ? 0 : 1;
				Player p = player(room.getId(), seat);
				if (p == null || p.computer() || p.away()) {
					playChess(room, s, pos, ChessBot.choose(pos, ChessBot.Level.valueOf(room.getLevel()), random), at);
				}
				else {
					if (pos.whiteToMove()) {
						s.whiteMs = 0;
					}
					else {
						s.blackMs = 0;
					}
					finish(room, write(s), (seat == 0 ? "Black" : "White") + " won on time", Map.of(seat, "LOSS", 1 - seat, "WIN"), at);
				}
			}
			else {
				Ludo g = ludo(room);
				Player p = player(room.getId(), g.current());
				if (p != null && !p.computer() && !p.away()) {
					// They ran out of time: the computer plays this turn; after two in a row it plays for them until they're back.
					jdbc.update("UPDATE game_players SET timeouts = timeouts + 1, away = timeouts + 1 >= 2 WHERE room_id = ? AND seat = ?",
							room.getId(), p.seat());
				}
				if (g.dice == null) {
					rollFor(room, g, at);
				}
				else {
					g.move(LudoBot.choose(g));
					saveLudo(room, g, at);
				}
			}
		}
	}

	private void playChess(GameRoom room, ChessState s, ChessPosition pos, Move m, Instant at) {
		long ms = at.toEpochMilli();
		if (s.clock) {
			long used = Math.max(0, ms - s.turnSince);
			if (pos.whiteToMove()) {
				s.whiteMs = Math.max(0, s.whiteMs - used);
			}
			else {
				s.blackMs = Math.max(0, s.blackMs - used);
			}
		}
		s.san.add(pos.san(m));
		s.uci.add(m.uci());
		ChessPosition next = pos.play(m);
		s.fen = next.fen();
		s.keys.add(next.key());
		s.turnSince = ms;
		String draw = switch (next.outcome()) {
			case STALEMATE -> "Draw: stalemate";
			case INSUFFICIENT_MATERIAL -> "Draw: not enough pieces left to checkmate";
			case FIFTY_MOVES -> "Draw: 50 moves without a capture or a pawn move";
			case NONE -> Collections.frequency(s.keys, next.key()) >= 3 ? "Draw: the same position three times" : null;
			case CHECKMATE -> null;
		};
		if (next.outcome() == ChessPosition.Outcome.CHECKMATE) {
			int winner = next.whiteToMove() ? 1 : 0;
			finish(room, write(s), (winner == 0 ? "White" : "Black") + " won by checkmate", Map.of(winner, "WIN", 1 - winner, "LOSS"), at);
		}
		else if (draw != null) {
			finish(room, write(s), draw, Map.of(0, "DRAW", 1, "DRAW"), at);
		}
		else {
			room.play(write(s), deadline(room, s, at), at);
		}
	}

	private void resignAs(GameRoom room, Player p, Instant now) {
		ChessState s = chess(room);
		finish(room, write(s), (p.seat() == 0 ? "White" : "Black") + " resigned", Map.of(p.seat(), "LOSS", 1 - p.seat(), "WIN"), now);
	}

	/** Rolls for the player whose turn it is. When only one move is possible (or all are the same), it plays itself. */
	private void rollFor(GameRoom room, Ludo g, Instant at) {
		if (g.roll(1 + random.nextInt(6))) {
			List<Integer> options = g.movable();
			boolean allSame = options.stream().map(t -> g.tokens[g.current()][t]).distinct().count() == 1;
			if (options.size() == 1 || allSame) {
				g.move(options.getFirst());
			}
		}
		saveLudo(room, g, at);
	}

	private void saveLudo(GameRoom room, Ludo g, Instant at) {
		if (!g.over()) {
			room.play(write(g), deadline(room, g, at), at);
			return;
		}
		Map<Integer, String> outcomes = new LinkedHashMap<>();
		for (int colour : g.seats) {
			outcomes.put(colour, colour == g.winner ? "WIN" : "LOSS");
		}
		finish(room, write(g), Ludo.name(g.winner) + " won!", outcomes, at);
	}

	/** When the current turn plays itself: soon for computer players, at the end of a person's time otherwise. */
	private Instant deadline(GameRoom room, Object state, Instant at) {
		if (state instanceof ChessState s) {
			boolean white = ChessPosition.fromFen(s.fen).whiteToMove();
			Player p = player(room.getId(), white ? 0 : 1);
			if (p == null || p.computer() || p.away()) {
				return at.plus(BOT_PAUSE);
			}
			return s.clock ? Instant.ofEpochMilli(s.turnSince + (white ? s.whiteMs : s.blackMs)) : null;
		}
		Ludo g = (Ludo) state;
		Player p = player(room.getId(), g.current());
		return p == null || p.computer() || p.away() ? at.plus(BOT_PAUSE) : at.plus(LUDO_TURN);
	}

	private void finish(GameRoom room, String state, String result, Map<Integer, String> outcomes, Instant at) {
		outcomes.forEach((seat, outcome) -> jdbc.update("UPDATE game_players SET outcome = ? WHERE room_id = ? AND seat = ?", outcome,
				room.getId(), seat));
		if (room.isRated()) {
			rate(room, at);
		}
		room.finish(state, result, at);
	}

	/** Elo: each pair of players is a match (a ludo winner beats everyone else; losers don't play each other). */
	private void rate(GameRoom room, Instant at) {
		List<Player> ps = players(room.getId()).stream().filter(p -> !p.computer()).toList();
		if (ps.size() < 2) {
			return;
		}
		Map<UUID, Integer> ratings = new HashMap<>();
		for (Player p : ps) {
			jdbc.update("INSERT INTO game_ratings (user_id, game, updated_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", p.userId(), room.getGame(),
					Timestamp.from(at));
			ratings.put(p.userId(), jdbc.queryForObject("SELECT rating FROM game_ratings WHERE user_id = ? AND game = ?", Integer.class,
					p.userId(), room.getGame()));
		}
		double k = Game.CHESS.name().equals(room.getGame()) ? 32 : 32.0 / (ps.size() - 1);
		Map<UUID, Double> change = new HashMap<>();
		for (int i = 0; i < ps.size(); i++) {
			for (int j = i + 1; j < ps.size(); j++) {
				Player a = ps.get(i);
				Player b = ps.get(j);
				if ("LOSS".equals(a.outcome()) && "LOSS".equals(b.outcome())) {
					continue;
				}
				double score = "WIN".equals(a.outcome()) ? 1 : "DRAW".equals(a.outcome()) ? 0.5 : 0;
				double expected = 1 / (1 + Math.pow(10, (ratings.get(b.userId()) - ratings.get(a.userId())) / 400.0));
				double d = k * (score - expected);
				change.merge(a.userId(), d, Double::sum);
				change.merge(b.userId(), -d, Double::sum);
			}
		}
		for (Player p : ps) {
			int d = (int) Math.round(change.getOrDefault(p.userId(), 0.0));
			jdbc.update("""
					UPDATE game_ratings SET rating = rating + ?, played = played + 1, won = won + ?, updated_at = ?
					WHERE user_id = ? AND game = ?""", d, "WIN".equals(p.outcome()) ? 1 : 0, Timestamp.from(at), p.userId(), room.getGame());
			jdbc.update("UPDATE game_players SET rating_change = ? WHERE room_id = ? AND seat = ?", d, room.getId(), p.seat());
		}
	}

	private Integer turnSeat(GameRoom room) {
		if (room.getState() == null) {
			return null;
		}
		return Game.CHESS.name().equals(room.getGame()) ? (ChessPosition.fromFen(chess(room).fen).whiteToMove() ? 0 : 1) : ludo(room).current();
	}

	// ----- Inside: rooms and seats -----

	private void takeSeat(GameRoom room, UUID me) {
		if (!GameRoom.WAITING.equals(room.getStatus())) {
			throw ApiException.conflict("already_started", "This game has already started.");
		}
		List<Player> ps = players(room.getId());
		if (ps.size() >= room.getMaxPlayers()) {
			throw ApiException.conflict("room_full", "This game is full.");
		}
		if (ps.stream().anyMatch(p -> p.userId() != null && blocks.blockedEitherWay(me, p.userId()))) {
			throw ApiException.forbidden("blocked", "You can't join this game.");
		}
		int seat = 0;
		while (seatTaken(ps, seat)) {
			seat++;
		}
		Instant now = clock.instant();
		addPlayer(room.getId(), seat, me, now);
		room.touch(now);
		if (ps.size() + 1 == room.getMaxPlayers()) {
			begin(room, now);
		}
	}

	private static boolean seatTaken(List<Player> ps, int seat) {
		return ps.stream().anyMatch(p -> p.seat() == seat);
	}

	private void addPlayer(UUID room, int seat, UUID me, Instant now) {
		String name = members.card(me).displayName();
		jdbc.update("INSERT INTO game_players (room_id, seat, user_id, name, joined_at) VALUES (?, ?, ?, ?, ?)", room, seat, me, name,
				Timestamp.from(now));
	}

	private void addComputer(UUID room, int seat, Level level, Instant now) {
		String name = "Computer (" + level.name().toLowerCase(Locale.ROOT) + ")";
		jdbc.update("INSERT INTO game_players (room_id, seat, user_id, name, joined_at) VALUES (?, ?, NULL, ?, ?)", room, seat, name,
				Timestamp.from(now));
	}

	List<Player> players(UUID room) {
		return jdbc.query("""
				SELECT seat, user_id, name, timeouts, away, outcome, rating_change FROM game_players WHERE room_id = ? ORDER BY seat""",
				(rs, n) -> new Player(rs.getInt("seat"), rs.getObject("user_id", UUID.class), rs.getString("name"), rs.getInt("timeouts"),
						rs.getBoolean("away"), rs.getString("outcome"), (Integer) rs.getObject("rating_change")), room);
	}

	private Player player(UUID room, int seat) {
		return players(room).stream().filter(p -> p.seat() == seat).findFirst().orElse(null);
	}

	private Integer seatOf(UUID room, UUID me) {
		return players(room).stream().filter(p -> me.equals(p.userId())).map(Player::seat).findFirst().orElse(null);
	}

	/** Someone who plays again is no longer "away", and their timeouts are forgotten. */
	private void back(GameRoom room, Player p) {
		if (p.away() || p.timeouts() > 0) {
			jdbc.update("UPDATE game_players SET away = FALSE, timeouts = 0 WHERE room_id = ? AND seat = ?", room.getId(), p.seat());
		}
	}

	private Player requirePlayer(GameRoom room, UUID me) {
		return players(room.getId()).stream().filter(p -> me.equals(p.userId())).findFirst().orElseThrow(GameService::notFound);
	}

	private Player requireTurn(GameRoom room, UUID me, Game game) {
		Player p = requirePlayer(room, me);
		if (!game.name().equals(room.getGame()) || !GameRoom.PLAYING.equals(room.getStatus())) {
			throw ApiException.conflict("not_playing", "This game isn't in play.");
		}
		Integer turn = turnSeat(room);
		if (turn == null || turn != p.seat()) {
			throw ApiException.conflict("not_your_turn", "It's not your turn.");
		}
		return p;
	}

	private void onRide(UUID me, UUID rideId) {
		RideParticipants trip = rideId == null ? null : rides.participants(rideId).orElse(null);
		if (trip == null || (!trip.isDriver(me) && !trip.isConfirmedPassenger(me))) {
			throw ApiException.forbidden("not_on_ride", "Only people with a confirmed seat on this ride can play its games.");
		}
	}

	private void requireGame(UUID me, Game game) {
		require(me, game == Game.CHESS ? "games.chess" : "games.ludo");
	}

	private void require(UUID me, String feature) {
		if (!features.isOnFor(feature, me)) {
			throw ApiException.forbidden("feature_unavailable", "This isn't available right now.").with("feature", feature);
		}
	}

	private String newCode() {
		for (int attempt = 0; attempt < 20; attempt++) {
			StringBuilder s = new StringBuilder();
			for (int i = 0; i < 6; i++) {
				s.append(CODE_LETTERS.charAt(random.nextInt(CODE_LETTERS.length())));
			}
			if (rooms.findByCode(s.toString()).isEmpty()) {
				return s.toString();
			}
		}
		throw new IllegalStateException("Could not find a free room code");
	}

	private GameRoom lock(UUID id) {
		return rooms.lockById(id).orElseThrow(GameService::notFound);
	}

	private ChessState chess(GameRoom room) {
		return json.readValue(room.getState(), ChessState.class);
	}

	private Ludo ludo(GameRoom room) {
		return json.readValue(room.getState(), Ludo.class);
	}

	private String write(Object state) {
		return json.writeValueAsString(state);
	}

	private GameView view(GameRoom room, UUID me) {
		List<Player> ps = players(room.getId());
		Player mine = ps.stream().filter(p -> me.equals(p.userId())).findFirst().orElseThrow(GameService::notFound);
		Map<UUID, MemberCard> cards = members.cards(ps.stream().map(Player::userId).filter(java.util.Objects::nonNull).toList());
		List<SeatView> seats = ps.stream()
			.map(p -> new SeatView(p.seat(), p.userId() == null ? null : cards.get(p.userId()), p.name(), p.computer(), p.away(),
					p == mine, p.outcome(), p.ratingChange()))
			.toList();
		boolean playing = GameRoom.PLAYING.equals(room.getStatus());
		ChessView chess = null;
		LudoView ludo = null;
		Integer turn = null;
		if (room.getState() != null && Game.CHESS.name().equals(room.getGame())) {
			ChessState s = chess(room);
			ChessPosition pos = ChessPosition.fromFen(s.fen);
			turn = playing ? (pos.whiteToMove() ? 0 : 1) : null;
			List<String> legal = playing && turn == mine.seat() ? pos.legalMoves().stream().map(Move::uci).toList() : List.of();
			Long white = null;
			Long black = null;
			if (s.clock) {
				long used = playing ? Math.max(0, clock.millis() - s.turnSince) : 0;
				white = pos.whiteToMove() ? Math.max(0, s.whiteMs - used) : s.whiteMs;
				black = pos.whiteToMove() ? s.blackMs : Math.max(0, s.blackMs - used);
			}
			chess = new ChessView(s.fen, pos.whiteToMove(), legal, s.san, s.uci.isEmpty() ? null : s.uci.getLast(), pos.inCheck(), white, black);
		}
		else if (room.getState() != null) {
			Ludo g = ludo(room);
			boolean mineNow = playing && g.current() == mine.seat();
			turn = playing ? g.current() : null;
			ludo = new LudoView(g.seats, g.tokens, g.current(), g.dice, mineNow ? g.movable() : List.of(), mineNow && g.dice == null, g.last,
					g.lastMove);
		}
		Map<Integer, String> names = new HashMap<>();
		ps.forEach(p -> names.put(p.seat(), p.name()));
		List<MessageView> messages = jdbc.query("""
				SELECT seat, phrase, created_at FROM (SELECT * FROM game_messages WHERE room_id = ? ORDER BY id DESC LIMIT 20) m ORDER BY id""",
				(rs, n) -> new MessageView(rs.getInt("seat"), names.getOrDefault(rs.getInt("seat"), "Player"), rs.getString("phrase"),
						rs.getTimestamp("created_at").toInstant()), room.getId());
		return new GameView(room.getId(), Game.valueOf(room.getGame()), Mode.valueOf(room.getMode()), room.getCode(), room.getStatus(),
				room.getVersion(), room.getMaxPlayers(), room.getLevel() == null ? null : Level.valueOf(room.getLevel()), seats, mine.seat(),
				turn, room.getDeadline(), room.getHostId().equals(me), room.isRated(), room.getResult(), chess, ludo, messages, clock.instant());
	}

	static ApiException notFound() {
		return ApiException.notFound("game_not_found", "We couldn't find that game.");
	}
}
