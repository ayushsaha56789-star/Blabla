-- Phase 9: the fun layer. Rewards (XP, levels and badges are worked out from completed trips, so only referrals and
-- leaderboard choices are stored), squads, event rides and vibe match.

-- One row per member who opened their rewards: their invite code, who invited them, and leaderboard choices.
CREATE TABLE reward_profiles (
    user_id          UUID          PRIMARY KEY REFERENCES users (id),
    referral_code    VARCHAR(12)   NOT NULL UNIQUE,
    referred_by      UUID          REFERENCES users (id),
    referral_status  VARCHAR(12),                 -- PENDING (waiting for the friend's first trip) or REWARDED
    referred_at      TIMESTAMPTZ,
    rewarded_at      TIMESTAMPTZ,
    on_board         BOOLEAN       NOT NULL DEFAULT FALSE,   -- shown on the eco leaderboard (by nickname only)
    nickname         VARCHAR(24),
    created_at       TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_reward_profiles_referrer ON reward_profiles (referred_by);
CREATE INDEX idx_reward_profiles_pending ON reward_profiles (referral_status) WHERE referral_status = 'PENDING';

-- Squads: private groups for a college, an office or friends. A squad may require a confirmed email at its domain.
CREATE TABLE squads (
    id            UUID          PRIMARY KEY,
    name          VARCHAR(60)   NOT NULL,
    kind          VARCHAR(12)   NOT NULL,       -- COLLEGE, OFFICE, FRIENDS
    email_domain  VARCHAR(100),                 -- e.g. "iitd.ac.in"; null: anyone with the code
    code          VARCHAR(8)    NOT NULL UNIQUE,
    owner_id      UUID          NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ   NOT NULL
);

CREATE TABLE squad_members (
    squad_id   UUID          NOT NULL REFERENCES squads (id) ON DELETE CASCADE,
    user_id    UUID          NOT NULL REFERENCES users (id),
    role       VARCHAR(8)    NOT NULL,          -- OWNER or MEMBER
    joined_at  TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (squad_id, user_id)
);
CREATE INDEX idx_squad_members_user ON squad_members (user_id);

-- Events (concerts, fests, matches) that staff add, with the rides going there.
CREATE TABLE events (
    id           UUID              PRIMARY KEY,
    title        VARCHAR(100)      NOT NULL,
    venue        VARCHAR(150)      NOT NULL,
    place_id     BIGINT            NOT NULL,
    place_name   VARCHAR(100)      NOT NULL,
    state        VARCHAR(60),
    lat          DOUBLE PRECISION  NOT NULL,
    lng          DOUBLE PRECISION  NOT NULL,
    starts_at    TIMESTAMPTZ       NOT NULL,
    description  VARCHAR(1000),
    status       VARCHAR(12)       NOT NULL,    -- LIVE or CANCELLED
    created_by   UUID              REFERENCES users (id),
    created_at   TIMESTAMPTZ       NOT NULL,
    updated_at   TIMESTAMPTZ       NOT NULL
);
CREATE INDEX idx_events_upcoming ON events (status, starts_at);

-- Interests for vibe match (music, sports, films, …). Only the match score is shown to others.
CREATE TABLE vibe_profiles (
    user_id     UUID           PRIMARY KEY REFERENCES users (id),
    interests   VARCHAR(400)   NOT NULL DEFAULT '',   -- comma separated, from a fixed list
    updated_at  TIMESTAMPTZ    NOT NULL
);
