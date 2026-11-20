-- Phase 4, part 1: chat between a driver and a passenger (or someone asking before they book) about one ride.

CREATE TABLE conversations (
    id                 UUID        PRIMARY KEY,
    ride_id            UUID        NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    driver_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    passenger_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at         TIMESTAMPTZ NOT NULL,
    last_message_at    TIMESTAMPTZ,
    driver_read_at     TIMESTAMPTZ,
    passenger_read_at  TIMESTAMPTZ,
    UNIQUE (ride_id, passenger_id)
);
CREATE INDEX idx_conversations_driver ON conversations (driver_id, last_message_at DESC);
CREATE INDEX idx_conversations_passenger ON conversations (passenger_id, last_message_at DESC);

CREATE TABLE chat_messages (
    id               UUID          PRIMARY KEY,
    conversation_id  UUID          NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id        UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body             VARCHAR(1000) NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    hidden_at        TIMESTAMPTZ               -- set when staff remove a reported message
);
CREATE INDEX idx_chat_messages_conversation ON chat_messages (conversation_id, created_at);

-- Chat is built now: switch it on where nobody has changed the switch by hand.
UPDATE feature_flags SET enabled = TRUE WHERE key = 'chat' AND updated_by IS NULL;
