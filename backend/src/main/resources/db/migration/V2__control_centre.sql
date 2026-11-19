-- Phase 2: the owner control centre.

-- Members: staff roles now live in their own tables; add home city and beta-tester flag.
ALTER TABLE users DROP COLUMN role;
ALTER TABLE users ADD COLUMN city VARCHAR(60);
ALTER TABLE users ADD COLUMN beta_tester BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------------------
-- Staff roles and permissions
-- ---------------------------------------------------------------------------
CREATE TABLE staff_roles (
    code        VARCHAR(32)  PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    rank        INT          NOT NULL
);

CREATE TABLE staff_role_permissions (
    role_code  VARCHAR(32) NOT NULL REFERENCES staff_roles (code) ON DELETE CASCADE,
    permission VARCHAR(64) NOT NULL,
    PRIMARY KEY (role_code, permission)
);

INSERT INTO staff_roles (code, name, description, rank) VALUES
    ('SUPER_ADMIN', 'Super admin', 'The owner. Everything, including feature switches, staff, prices and payouts.', 100),
    ('ADMIN',       'Admin',       'Runs day-to-day operations: members, rentals, refunds and moderation.', 80),
    ('MODERATOR',   'Moderator',   'Handles reports, chats, reviews, verifications and promotions; can restrict members.', 50),
    ('SUPPORT',     'Support',     'Helps members: reads profiles and answers help tickets.', 20);

INSERT INTO staff_role_permissions (role_code, permission)
SELECT 'SUPER_ADMIN', p FROM unnest(ARRAY[
    'features.manage', 'staff.manage', 'settings.manage', 'payouts.approve', 'users.view', 'users.view_contact',
    'users.restrict', 'verification.review', 'content.moderate', 'rentals.manage', 'payments.refund', 'audit.view',
    'support.tickets']) AS p;
INSERT INTO staff_role_permissions (role_code, permission)
SELECT 'ADMIN', p FROM unnest(ARRAY[
    'users.view', 'users.view_contact', 'users.restrict', 'verification.review', 'content.moderate',
    'rentals.manage', 'payments.refund', 'audit.view', 'support.tickets']) AS p;
INSERT INTO staff_role_permissions (role_code, permission)
SELECT 'MODERATOR', p FROM unnest(ARRAY[
    'users.view', 'users.restrict', 'verification.review', 'content.moderate', 'support.tickets']) AS p;
INSERT INTO staff_role_permissions (role_code, permission)
SELECT 'SUPPORT', p FROM unnest(ARRAY['users.view', 'users.view_contact', 'support.tickets']) AS p;

CREATE TABLE staff_members (
    user_id    UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    role_code  VARCHAR(32) NOT NULL REFERENCES staff_roles (code),
    added_by   UUID,
    added_at   TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- ---------------------------------------------------------------------------
-- Staff two-step verification (authenticator app) and console sessions
-- ---------------------------------------------------------------------------
CREATE TABLE staff_totp (
    user_id           UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    secret_enc        VARCHAR(255) NOT NULL,      -- AES-GCM encrypted with the app's data key
    enabled_at        TIMESTAMPTZ,                -- NULL while setup is not finished
    last_login_step   BIGINT       NOT NULL DEFAULT 0,
    last_confirm_step BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL
);

CREATE TABLE staff_recovery_codes (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_staff_recovery_codes_user ON staff_recovery_codes (user_id);

CREATE TABLE staff_sessions (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    ip           VARCHAR(45)
);
CREATE INDEX idx_staff_sessions_user ON staff_sessions (user_id);

-- ---------------------------------------------------------------------------
-- Member restrictions: suspend a whole account (area = 'ALL') or one area such as 'games'
-- ---------------------------------------------------------------------------
CREATE TABLE member_restrictions (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    area       VARCHAR(64)  NOT NULL,
    reason     VARCHAR(500) NOT NULL,
    starts_at  TIMESTAMPTZ  NOT NULL,
    ends_at    TIMESTAMPTZ,
    created_by UUID,
    lifted_at  TIMESTAMPTZ,
    lifted_by  UUID
);
CREATE INDEX idx_member_restrictions_active ON member_restrictions (user_id) WHERE lifted_at IS NULL;

-- ---------------------------------------------------------------------------
-- Feature switches. Rows are created by the app from its built-in catalogue at start-up.
-- ---------------------------------------------------------------------------
CREATE TABLE feature_flags (
    key        VARCHAR(64)   PRIMARY KEY,
    enabled    BOOLEAN       NOT NULL,
    audience   VARCHAR(16)   NOT NULL DEFAULT 'EVERYONE',
    cities     VARCHAR(1000) NOT NULL DEFAULT '',
    updated_by UUID,
    updated_at TIMESTAMPTZ   NOT NULL
);

-- ---------------------------------------------------------------------------
-- Audit log: append-only and hash-chained. No foreign keys, so it outlives the records it describes.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGSERIAL    PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL,
    actor_id    UUID,
    actor_label VARCHAR(120) NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(32),
    target_id   VARCHAR(64),
    summary     VARCHAR(500) NOT NULL,
    details     TEXT,
    ip          VARCHAR(45),
    request_id  VARCHAR(64),
    prev_hash   CHAR(64)     NOT NULL,
    hash        CHAR(64)     NOT NULL
);
CREATE INDEX idx_audit_log_occurred ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_actor ON audit_log (actor_id);
CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_action ON audit_log (action);

CREATE FUNCTION audit_log_is_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: rows cannot be changed or removed';
END;
$$;
CREATE TRIGGER audit_log_no_update_or_delete BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_is_append_only();
CREATE TRIGGER audit_log_no_truncate BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_is_append_only();
