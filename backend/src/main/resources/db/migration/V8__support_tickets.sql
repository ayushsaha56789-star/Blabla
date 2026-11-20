-- Phase 4, part 5: help requests from members, answered by staff in the console.

CREATE TABLE support_tickets (
    id                 UUID          PRIMARY KEY,
    user_id            UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category           VARCHAR(24)   NOT NULL,             -- ACCOUNT, BOOKING, OFFERING, PAYMENT, SAFETY, PRIVACY, BUG, GRIEVANCE, OTHER
    subject            VARCHAR(120)  NOT NULL,
    ride_id            UUID          REFERENCES rides (id) ON DELETE SET NULL,
    status             VARCHAR(16)   NOT NULL,             -- OPEN (waiting for staff), ANSWERED (waiting for the member), CLOSED
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    first_response_at  TIMESTAMPTZ,
    closed_at          TIMESTAMPTZ,
    assigned_to        UUID
);
CREATE INDEX idx_support_tickets_user ON support_tickets (user_id, updated_at DESC);
CREATE INDEX idx_support_tickets_status ON support_tickets (status, updated_at);

CREATE TABLE ticket_messages (
    id          UUID           PRIMARY KEY,
    ticket_id   UUID           NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    author_id   UUID           REFERENCES users (id) ON DELETE SET NULL,
    from_staff  BOOLEAN        NOT NULL,
    body        VARCHAR(4000)  NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_ticket_messages_ticket ON ticket_messages (ticket_id, created_at);
