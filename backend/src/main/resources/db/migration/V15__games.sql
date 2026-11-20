-- Phase 8: games. Free chess and ludo: against the computer, with friends (a room code), a quick match with someone
-- online, or with the people on a ride. Every game runs on the server, which checks every move and rolls every die.

CREATE TABLE game_rooms (
    id            UUID          PRIMARY KEY,
    game          VARCHAR(8)    NOT NULL,      -- CHESS or LUDO
    mode          VARCHAR(12)   NOT NULL,      -- COMPUTER, FRIENDS, QUICK, RIDE
    code          VARCHAR(6)    UNIQUE,        -- to invite friends
    ride_id       UUID,                        -- RIDE rooms: the ride whose members can join
    host_id       UUID          NOT NULL REFERENCES users (id),
    status        VARCHAR(12)   NOT NULL,      -- WAITING, PLAYING, FINISHED, ABANDONED
    max_players   INT           NOT NULL CHECK (max_players BETWEEN 2 AND 4),
    level         VARCHAR(8),                  -- EASY, MEDIUM, HARD: how computer players play
    state         TEXT,                        -- the game itself, as JSON
    version       INT           NOT NULL DEFAULT 0,
    deadline      TIMESTAMPTZ,                 -- when the turn plays itself (computer) or runs out of time
    rated         BOOLEAN       NOT NULL DEFAULT FALSE,
    result        VARCHAR(200),
    created_at    TIMESTAMPTZ   NOT NULL,
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_game_rooms_waiting ON game_rooms (game, mode, status, created_at);
CREATE INDEX idx_game_rooms_deadline ON game_rooms (deadline) WHERE status = 'PLAYING';
CREATE INDEX idx_game_rooms_ride ON game_rooms (ride_id) WHERE ride_id IS NOT NULL;

CREATE TABLE game_players (
    room_id        UUID          NOT NULL REFERENCES game_rooms (id) ON DELETE CASCADE,
    seat           INT           NOT NULL,     -- chess: 0 white, 1 black; ludo: the board colour 0–3
    user_id        UUID          REFERENCES users (id),   -- null: a computer player (or a deleted account)
    name           VARCHAR(60)   NOT NULL,
    timeouts       INT           NOT NULL DEFAULT 0,
    away           BOOLEAN       NOT NULL DEFAULT FALSE,  -- the computer plays for them until they're back
    outcome        VARCHAR(8),                 -- WIN, LOSS, DRAW
    rating_change  INT,
    joined_at      TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (room_id, seat)
);
CREATE UNIQUE INDEX uq_game_players_member ON game_players (room_id, user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_game_players_user ON game_players (user_id, joined_at DESC);

-- Ratings come from online games between people only.
CREATE TABLE game_ratings (
    user_id     UUID          NOT NULL REFERENCES users (id),
    game        VARCHAR(8)    NOT NULL,
    rating      INT           NOT NULL DEFAULT 1200,
    played      INT           NOT NULL DEFAULT 0,
    won         INT           NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (user_id, game)
);
CREATE INDEX idx_game_ratings_board ON game_ratings (game, rating DESC);

-- In-game chat is emojis and set phrases only, kept for a week.
CREATE TABLE game_messages (
    id          BIGSERIAL     PRIMARY KEY,
    room_id     UUID          NOT NULL REFERENCES game_rooms (id) ON DELETE CASCADE,
    seat        INT           NOT NULL,
    user_id     UUID          REFERENCES users (id),
    phrase      VARCHAR(16)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_game_messages_room ON game_messages (room_id, id);
