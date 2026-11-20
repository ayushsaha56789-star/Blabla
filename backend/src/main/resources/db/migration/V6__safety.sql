-- Phase 4, part 3: safety — emergency contacts, trip sharing with optional live location, and SOS alerts.

CREATE TABLE emergency_contacts (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(60)  NOT NULL,
    phone_enc   VARCHAR(255) NOT NULL,                  -- encrypted (AES-GCM); someone else's number
    phone_hint  VARCHAR(8)   NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_emergency_contacts_user ON emergency_contacts (user_id);

-- A secret link family can open to follow a trip. Only the latest location point is kept, and only until shortly
-- after the trip.
CREATE TABLE trip_shares (
    id             UUID         PRIMARY KEY,
    token_hash     VARCHAR(64)  NOT NULL UNIQUE,           -- SHA-256 of the link's secret
    token_enc      VARCHAR(255) NOT NULL,                  -- encrypted, so the member can copy the same link again
    ride_id        UUID         NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    live_lat       DOUBLE PRECISION,
    live_lng       DOUBLE PRECISION,
    live_accuracy  INT,
    live_at        TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_trip_shares_active ON trip_shares (ride_id, user_id) WHERE revoked_at IS NULL;

CREATE TABLE sos_alerts (
    id                 UUID          PRIMARY KEY,
    user_id            UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ride_id            UUID          REFERENCES rides (id) ON DELETE SET NULL,
    lat                DOUBLE PRECISION,
    lng                DOUBLE PRECISION,
    accuracy           INT,
    status             VARCHAR(16)   NOT NULL,                 -- OPEN, ACKNOWLEDGED, RESOLVED
    contacts_notified  INT           NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL,
    acknowledged_at    TIMESTAMPTZ,
    acknowledged_by    UUID,
    resolved_at        TIMESTAMPTZ,
    resolved_by        UUID,
    notes              VARCHAR(1000)
);
CREATE INDEX idx_sos_alerts_open ON sos_alerts (created_at DESC) WHERE status <> 'RESOLVED';
CREATE INDEX idx_sos_alerts_user ON sos_alerts (user_id, created_at DESC);

-- Safety tools are built now: switch them on where nobody has changed the switches by hand.
UPDATE feature_flags SET enabled = TRUE WHERE key IN ('safety', 'safety.sos', 'safety.trip_share') AND updated_by IS NULL;
