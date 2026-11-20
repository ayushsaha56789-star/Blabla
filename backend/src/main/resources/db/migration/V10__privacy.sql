-- Phase 4, part 6: the privacy centre. A member can delete their account: it stops working at once and is erased
-- 30 days later. Logging in again before then cancels the deletion.

ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN deletion_due_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
-- Erased accounts keep their row, so other members' trip history still makes sense, but lose the phone number.
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;
CREATE INDEX idx_users_deletion_due ON users (deletion_due_at) WHERE deletion_due_at IS NOT NULL;

-- India's IT Rules 2021 (rule 3(1)(h)) ask us to keep a member's sign-up details for 180 days after they leave.
-- Kept encrypted, shown nowhere in the app, and removed automatically when the 180 days are over.
CREATE TABLE closed_accounts (
    user_id      UUID           PRIMARY KEY,
    details_enc  VARCHAR(2000)  NOT NULL,      -- phone, name and email, encrypted together
    joined_at    TIMESTAMPTZ    NOT NULL,
    closed_at    TIMESTAMPTZ    NOT NULL,
    purge_after  TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_closed_accounts_purge ON closed_accounts (purge_after);
