-- Phase 10: promotions. Members promote their business or project, always labelled "Sponsored". Each promotion passes
-- automatic checks and then a moderator before it goes live. Free during the launch offer.

CREATE TABLE promotions (
    id            UUID          PRIMARY KEY,
    owner_id      UUID          NOT NULL REFERENCES users (id),
    business      VARCHAR(60)   NOT NULL,
    title         VARCHAR(80)   NOT NULL,
    body          VARCHAR(300)  NOT NULL,
    link_url      VARCHAR(300),
    category      VARCHAR(20)   NOT NULL,
    image_path    VARCHAR(120),                -- public picture, re-encoded (promos/<name>.jpg)
    all_india     BOOLEAN       NOT NULL DEFAULT FALSE,
    starts_on     DATE          NOT NULL,
    ends_on       DATE          NOT NULL,
    status        VARCHAR(12)   NOT NULL,      -- DRAFT, IN_REVIEW, APPROVED, REJECTED, STOPPED, TAKEN_DOWN
    flags         VARCHAR(300),                -- what the automatic checks noticed, for the moderator
    review_note   VARCHAR(300),
    reviewed_by   UUID,
    reviewed_at   TIMESTAMPTZ,
    price_paise   BIGINT        NOT NULL DEFAULT 0,
    views         BIGINT        NOT NULL DEFAULT 0,
    clicks        BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    CHECK (ends_on >= starts_on)
);
CREATE INDEX idx_promotions_owner ON promotions (owner_id, created_at DESC);
CREATE INDEX idx_promotions_live ON promotions (status, starts_on, ends_on);

-- The towns a promotion is shown in (unless it's for all of India).
CREATE TABLE promotion_cities (
    promotion_id  UUID          NOT NULL REFERENCES promotions (id) ON DELETE CASCADE,
    place_id      BIGINT        NOT NULL,
    place_name    VARCHAR(100)  NOT NULL,
    PRIMARY KEY (promotion_id, place_id)
);
CREATE INDEX idx_promotion_cities_place ON promotion_cities (place_id);

-- How often each member saw a promotion that day (at most 3 times a day) and whether they clicked. Kept a week.
CREATE TABLE promotion_views (
    promotion_id  UUID      NOT NULL REFERENCES promotions (id) ON DELETE CASCADE,
    user_id       UUID      NOT NULL REFERENCES users (id),
    day           DATE      NOT NULL,
    seen          INT       NOT NULL DEFAULT 0,
    clicked       BOOLEAN   NOT NULL DEFAULT FALSE,
    PRIMARY KEY (promotion_id, user_id, day)
);
