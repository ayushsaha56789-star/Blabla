-- Phase 4, part 7: verification. Members confirm their email with a code, and can have an ID checked by staff.

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- The latest email code per member, stored only as a keyed hash (like login codes).
CREATE TABLE email_codes (
    user_id     UUID          PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    email       VARCHAR(254)  NOT NULL,
    code_hash   VARCHAR(64)   NOT NULL,
    attempts    INT           NOT NULL DEFAULT 0,
    sent_at     TIMESTAMPTZ   NOT NULL,
    expires_at  TIMESTAMPTZ   NOT NULL
);

-- ID checks. The photo is re-encoded (no hidden location data) and stored encrypted; it is deleted 30 days after
-- staff decide, or straight away if it shows a full Aadhaar number. The decision itself is kept.
CREATE TABLE id_checks (
    id               UUID          PRIMARY KEY,
    user_id          UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    document_type    VARCHAR(24)   NOT NULL,      -- DRIVING_LICENCE, AADHAAR, PAN, PASSPORT, VOTER_ID
    file_name        VARCHAR(80),
    status           VARCHAR(16)   NOT NULL,      -- PENDING, APPROVED, REJECTED
    submitted_at     TIMESTAMPTZ   NOT NULL,
    decided_at       TIMESTAMPTZ,
    decided_by       UUID,
    reject_reason    VARCHAR(32),
    reject_note      VARCHAR(300),
    file_deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_id_checks_user ON id_checks (user_id, submitted_at DESC);
CREATE INDEX idx_id_checks_status ON id_checks (status, submitted_at);
CREATE UNIQUE INDEX uq_id_checks_one_pending ON id_checks (user_id) WHERE status = 'PENDING';
