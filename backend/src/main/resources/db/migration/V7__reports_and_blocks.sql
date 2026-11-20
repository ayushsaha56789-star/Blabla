-- Phase 4, part 4: members report people, rides, messages and reviews; members block each other.

CREATE TABLE reports (
    id                UUID          PRIMARY KEY,
    reporter_id       UUID          REFERENCES users (id) ON DELETE SET NULL,
    target_type       VARCHAR(16)   NOT NULL,              -- MEMBER, RIDE, MESSAGE, REVIEW
    target_id         UUID          NOT NULL,
    reported_user_id  UUID          REFERENCES users (id) ON DELETE SET NULL,
    reason            VARCHAR(32)   NOT NULL,              -- SAFETY, HARASSMENT, FAKE_PROFILE, NO_SHOW, SPAM, INAPPROPRIATE, OTHER
    details           VARCHAR(1000),
    snapshot          VARCHAR(2000),                       -- a copy of the reported text, kept with the report
    status            VARCHAR(16)   NOT NULL,              -- OPEN, ACTIONED, DISMISSED
    created_at        TIMESTAMPTZ   NOT NULL,
    acknowledged_at   TIMESTAMPTZ,                         -- IT Rules 2021: acknowledge within 24 hours
    resolved_at       TIMESTAMPTZ,                         -- … and resolve within 15 days
    handled_by        UUID,
    resolution        VARCHAR(500)
);
CREATE INDEX idx_reports_open ON reports (created_at) WHERE status = 'OPEN';
CREATE INDEX idx_reports_target ON reports (target_type, target_id);
CREATE INDEX idx_reports_reporter ON reports (reporter_id, created_at DESC);
-- One open report per person per thing they report.
CREATE UNIQUE INDEX uq_reports_open_per_reporter ON reports (reporter_id, target_type, target_id) WHERE status = 'OPEN';

CREATE TABLE blocks (
    blocker_id  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_blocks_blocked ON blocks (blocked_id);
