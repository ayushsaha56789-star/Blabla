package com.saathchalo.games.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One game: who may join, its state (as JSON) and when its current turn plays itself or runs out. */
@Entity
@Table(name = "game_rooms")
public class GameRoom {

	public static final String WAITING = "WAITING";

	public static final String PLAYING = "PLAYING";

	public static final String FINISHED = "FINISHED";

	public static final String ABANDONED = "ABANDONED";

	@Id
	private UUID id;

	@Column(nullable = false, updatable = false)
	private String game;

	@Column(nullable = false, updatable = false)
	private String mode;

	private String code;

	private UUID rideId;

	@Column(nullable = false)
	private UUID hostId;

	@Column(nullable = false)
	private String status;

	private int maxPlayers;

	private String level;

	private String state;

	private int version;

	private Instant deadline;

	private boolean rated;

	private String result;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant startedAt;

	private Instant finishedAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected GameRoom() {
	}

	public GameRoom(String game, String mode, String code, UUID rideId, UUID hostId, int maxPlayers, String level, Instant now) {
		this.id = UUID.randomUUID();
		this.game = game;
		this.mode = mode;
		this.code = code;
		this.rideId = rideId;
		this.hostId = hostId;
		this.status = WAITING;
		this.maxPlayers = maxPlayers;
		this.level = level;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** Anything players should see changed. */
	public void touch(Instant now) {
		this.version++;
		this.updatedAt = now;
	}

	public void start(String state, boolean rated, Instant deadline, Instant now) {
		this.status = PLAYING;
		this.state = state;
		this.rated = rated;
		this.deadline = deadline;
		this.startedAt = now;
		touch(now);
	}

	public void play(String state, Instant deadline, Instant now) {
		this.state = state;
		this.deadline = deadline;
		touch(now);
	}

	public void finish(String state, String result, Instant now) {
		this.status = FINISHED;
		this.state = state;
		this.result = result;
		this.deadline = null;
		this.finishedAt = now;
		touch(now);
	}

	public void abandon(String result, Instant now) {
		this.status = ABANDONED;
		this.result = result;
		this.deadline = null;
		this.finishedAt = now;
		touch(now);
	}

	public void host(UUID hostId, Instant now) {
		this.hostId = hostId;
		touch(now);
	}

	public UUID getId() {
		return id;
	}

	public String getGame() {
		return game;
	}

	public String getMode() {
		return mode;
	}

	public String getCode() {
		return code;
	}

	public UUID getRideId() {
		return rideId;
	}

	public UUID getHostId() {
		return hostId;
	}

	public String getStatus() {
		return status;
	}

	public int getMaxPlayers() {
		return maxPlayers;
	}

	public String getLevel() {
		return level;
	}

	public String getState() {
		return state;
	}

	public int getVersion() {
		return version;
	}

	public Instant getDeadline() {
		return deadline;
	}

	public boolean isRated() {
		return rated;
	}

	public String getResult() {
		return result;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
