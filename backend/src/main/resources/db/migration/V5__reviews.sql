-- Phase 4, part 2: reviews after a completed trip. Drivers and passengers review each other once per trip.

CREATE TABLE reviews (
    id            UUID         PRIMARY KEY,
    ride_id       UUID         NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    author_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    subject_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    subject_role  VARCHAR(16)  NOT NULL,                  -- DRIVER or PASSENGER: what the reviewed member was on this trip
    rating        INT          NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment       VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL,
    hidden_at     TIMESTAMPTZ,                             -- set when staff remove a review after a report
    UNIQUE (ride_id, author_id, subject_id)
);
CREATE INDEX idx_reviews_subject ON reviews (subject_id, created_at DESC);
CREATE INDEX idx_reviews_author ON reviews (author_id, ride_id);

-- Reviews are built now: switch them on where nobody has changed the switch by hand.
UPDATE feature_flags SET enabled = TRUE WHERE key = 'reviews' AND updated_by IS NULL;
