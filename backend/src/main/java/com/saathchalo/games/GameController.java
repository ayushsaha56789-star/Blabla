package com.saathchalo.games;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.games.GameDtos.CreateRequest;
import com.saathchalo.games.GameDtos.Game;
import com.saathchalo.games.GameDtos.GameRequest;
import com.saathchalo.games.GameDtos.GameSummary;
import com.saathchalo.games.GameDtos.GameView;
import com.saathchalo.games.GameDtos.JoinRequest;
import com.saathchalo.games.GameDtos.Leaderboard;
import com.saathchalo.games.GameDtos.MoveRequest;
import com.saathchalo.games.GameDtos.SayRequest;
import com.saathchalo.games.GameDtos.StartRequest;
import com.saathchalo.games.GameDtos.TokenRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Chess and ludo. Every game needs a login; which kinds are open is decided by the feature switches. */
@RestController
@RequestMapping("/api/games")
@RequiresFeature("games")
public class GameController {

	private final GameService games;

	public GameController(GameService games) {
		this.games = games;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GameView create(@Valid @RequestBody CreateRequest request) {
		return games.create(CurrentUser.require(), request);
	}

	@PostMapping("/join")
	public GameView join(@Valid @RequestBody JoinRequest request) {
		return games.join(CurrentUser.require(), request.code());
	}

	@PostMapping("/quick")
	public GameView quick(@Valid @RequestBody GameRequest request) {
		return games.quick(CurrentUser.require(), request.game());
	}

	@PostMapping("/ride/{rideId}")
	public GameView ride(@PathVariable UUID rideId, @Valid @RequestBody GameRequest request) {
		return games.ride(CurrentUser.require(), rideId, request.game());
	}

	@GetMapping("/mine")
	public List<GameSummary> mine() {
		return games.mine(CurrentUser.require());
	}

	@GetMapping("/leaderboard")
	@RequiresFeature("games.leaderboards")
	public Leaderboard leaderboard(@RequestParam(defaultValue = "CHESS") Game game) {
		return games.leaderboard(CurrentUser.require(), game);
	}

	/** The room, or 204 when nothing changed since {@code since} (the page asks every second or so). */
	@GetMapping("/{id}")
	public ResponseEntity<GameView> room(@PathVariable UUID id, @RequestParam(required = false) Integer since) {
		return games.poll(CurrentUser.require(), id, since).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{id}/start")
	public GameView start(@PathVariable UUID id, @RequestBody(required = false) StartRequest request) {
		return games.start(CurrentUser.require(), id, request != null && Boolean.TRUE.equals(request.fillWithComputer()));
	}

	@PostMapping("/{id}/leave")
	public ResponseEntity<Void> leave(@PathVariable UUID id) {
		games.leave(CurrentUser.require(), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/move")
	public GameView move(@PathVariable UUID id, @Valid @RequestBody MoveRequest request) {
		return games.move(CurrentUser.require(), id, request.move());
	}

	@PostMapping("/{id}/resign")
	public GameView resign(@PathVariable UUID id) {
		return games.resign(CurrentUser.require(), id);
	}

	@PostMapping("/{id}/roll")
	public GameView roll(@PathVariable UUID id) {
		return games.roll(CurrentUser.require(), id);
	}

	@PostMapping("/{id}/token")
	public GameView token(@PathVariable UUID id, @Valid @RequestBody TokenRequest request) {
		return games.token(CurrentUser.require(), id, request.token());
	}

	@PostMapping("/{id}/say")
	public GameView say(@PathVariable UUID id, @Valid @RequestBody SayRequest request) {
		return games.say(CurrentUser.require(), id, request.phrase());
	}
}
