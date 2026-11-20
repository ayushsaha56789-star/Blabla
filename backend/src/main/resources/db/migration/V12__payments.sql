-- Phase 5: online payments, SaathChalo credits, refunds, driver earnings and payouts, all recorded in a double-entry
-- ledger. Amounts are in paise (₹1 = 100 paise). Card and UPI details never reach SaathChalo: Razorpay handles them.

CREATE TABLE payments (
    id                  UUID          PRIMARY KEY,
    booking_id          UUID          NOT NULL UNIQUE,      -- the rides module owns bookings
    payer_id            UUID          NOT NULL REFERENCES users (id),
    driver_id           UUID          NOT NULL REFERENCES users (id),
    seat_paise          BIGINT        NOT NULL CHECK (seat_paise > 0),
    fee_paise           BIGINT        NOT NULL CHECK (fee_paise >= 0),
    credits_paise       BIGINT        NOT NULL CHECK (credits_paise >= 0),
    online_paise        BIGINT        NOT NULL CHECK (online_paise >= 0),
    gateway             VARCHAR(16)   NOT NULL,             -- RAZORPAY, FAKE, or CREDITS when credits paid it all
    gateway_order_id    VARCHAR(64)   UNIQUE,
    gateway_payment_id  VARCHAR(64)   UNIQUE,
    status              VARCHAR(16)   NOT NULL,             -- CREATED (waiting for the money), PAID, EXPIRED
    outcome             VARCHAR(16),                        -- when it's all over: SETTLED, REFUNDED or SPLIT
    created_at          TIMESTAMPTZ   NOT NULL,
    hold_until          TIMESTAMPTZ   NOT NULL,
    paid_at             TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    CHECK (credits_paise + online_paise = seat_paise + fee_paise)
);
CREATE INDEX idx_payments_payer ON payments (payer_id, created_at DESC);
CREATE INDEX idx_payments_open ON payments (status, created_at) WHERE outcome IS NULL;

CREATE TABLE refunds (
    id                 UUID          PRIMARY KEY,
    payment_id         UUID          NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    online_paise       BIGINT        NOT NULL CHECK (online_paise >= 0),
    credits_paise      BIGINT        NOT NULL CHECK (credits_paise >= 0),
    reason             VARCHAR(200)  NOT NULL,
    status             VARCHAR(16)   NOT NULL,              -- PENDING (on its way), PROCESSED, FAILED
    gateway_refund_id  VARCHAR(64)   UNIQUE,
    attempts           INT           NOT NULL DEFAULT 0,
    last_error         VARCHAR(300),
    created_by         UUID,                                -- a staff member, or NULL for automatic refunds
    created_at         TIMESTAMPTZ   NOT NULL,
    processed_at       TIMESTAMPTZ
);
CREATE INDEX idx_refunds_payment ON refunds (payment_id);
CREATE INDEX idx_refunds_open ON refunds (status, created_at) WHERE status <> 'PROCESSED';

CREATE TABLE payouts (
    id                 UUID          PRIMARY KEY,
    driver_id          UUID          NOT NULL REFERENCES users (id),
    amount_paise       BIGINT        NOT NULL CHECK (amount_paise > 0),
    method             VARCHAR(16)   NOT NULL,              -- LINK (Razorpay payout link) or MANUAL (paid outside the app)
    status             VARCHAR(16)   NOT NULL,              -- SENT (link sent), PAID, CANCELLED
    gateway_payout_id  VARCHAR(64)   UNIQUE,
    link_url           VARCHAR(300),
    reference          VARCHAR(120),
    created_by         UUID          NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    paid_at            TIMESTAMPTZ
);
CREATE INDEX idx_payouts_driver ON payouts (driver_id, created_at DESC);

-- Balances per member, kept in step with the ledger (the console checks that they agree).
CREATE TABLE wallets (
    member_id       UUID          PRIMARY KEY REFERENCES users (id),
    credits_paise   BIGINT        NOT NULL DEFAULT 0 CHECK (credits_paise >= 0),
    earnings_paise  BIGINT        NOT NULL DEFAULT 0 CHECK (earnings_paise >= 0),
    updated_at      TIMESTAMPTZ   NOT NULL
);

-- Double-entry ledger: in every journal the debits equal the credits.
CREATE TABLE ledger_entries (
    id            BIGSERIAL     PRIMARY KEY,
    journal_id    UUID          NOT NULL,
    kind          VARCHAR(24)   NOT NULL,       -- CREDITS_HOLD, CREDITS_RELEASE, PAYMENT, SETTLE, REFUND, GOODWILL, PAYOUT, PAYOUT_CANCELLED
    account       VARCHAR(16)   NOT NULL,       -- GATEWAY, BANK, ESCROW, CREDITS, EARNINGS, FEES, GOODWILL
    member_id     UUID,                         -- for the members' own accounts (CREDITS, EARNINGS)
    debit_paise   BIGINT        NOT NULL DEFAULT 0 CHECK (debit_paise >= 0),
    credit_paise  BIGINT        NOT NULL DEFAULT 0 CHECK (credit_paise >= 0),
    payment_id    UUID,
    memo          VARCHAR(200),
    created_at    TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_ledger_journal ON ledger_entries (journal_id);
CREATE INDEX idx_ledger_member ON ledger_entries (member_id, created_at DESC) WHERE member_id IS NOT NULL;
CREATE INDEX idx_ledger_payment ON ledger_entries (payment_id) WHERE payment_id IS NOT NULL;

-- Webhook events already handled: Razorpay may deliver the same event more than once.
CREATE TABLE payment_events (
    event_id     VARCHAR(80)   PRIMARY KEY,
    type         VARCHAR(60)   NOT NULL,
    received_at  TIMESTAMPTZ   NOT NULL
);
