-- Phase 11: the notification inbox. Other modules leave members short notices ("Ravi accepted your request"),
-- each linking to the page where the member can act. Notices are deleted after 90 days.

CREATE TABLE notifications (
    id          BIGSERIAL      PRIMARY KEY,
    user_id     UUID           NOT NULL REFERENCES users (id),
    topic       VARCHAR(12)    NOT NULL,     -- TRIPS, REMINDERS, ACCOUNT, REWARDS (members can mute some topics)
    icon        VARCHAR(24)    NOT NULL,     -- an icon name from the web app
    title       VARCHAR(120)   NOT NULL,
    body        VARCHAR(300),
    link        VARCHAR(200),                -- a path inside the app, e.g. /rides/…
    created_at  TIMESTAMPTZ    NOT NULL,
    read_at     TIMESTAMPTZ
);
CREATE INDEX idx_notifications_user ON notifications (user_id, id DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
CREATE INDEX idx_notifications_created ON notifications (created_at);

-- Topics a member has muted. No row: nothing muted.
CREATE TABLE notification_settings (
    user_id     UUID           PRIMARY KEY REFERENCES users (id),
    muted       VARCHAR(60)    NOT NULL DEFAULT '',   -- comma-separated topics
    updated_at  TIMESTAMPTZ    NOT NULL
);
