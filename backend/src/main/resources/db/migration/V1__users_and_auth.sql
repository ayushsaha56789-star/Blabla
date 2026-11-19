-- PostGIS powers route matching ("rides passing near my city") from phase 2 on.
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE users (
    id             UUID PRIMARY KEY,
    phone          VARCHAR(16)  NOT NULL UNIQUE,
    first_name     VARCHAR(50),
    last_name      VARCHAR(50),
    email          VARCHAR(254),
    gender         VARCHAR(16),
    date_of_birth  DATE,
    bio            VARCHAR(500),
    avatar_path    VARCHAR(255),
    pref_chat      VARCHAR(16)  NOT NULL DEFAULT 'SOMETIMES',
    pref_music     BOOLEAN      NOT NULL DEFAULT TRUE,
    pref_smoking   BOOLEAN      NOT NULL DEFAULT FALSE,
    pref_pets      BOOLEAN      NOT NULL DEFAULT FALSE,
    role           VARCHAR(16)  NOT NULL DEFAULT 'USER',
    phone_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    id_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE otp_challenges (
    id          UUID PRIMARY KEY,
    phone       VARCHAR(16) NOT NULL,
    code_hash   VARCHAR(64) NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_otp_challenges_phone_created ON otp_challenges (phone, created_at DESC);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
