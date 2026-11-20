-- Phase 6: car rentals. Owners list their cars (self-drive, or with the owner driving), staff check the documents,
-- renters book a period and pay the rent and a refundable deposit, and both sides record the car at pickup and return.

-- For the rule below that stops a car being booked twice for the same time.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Payments now pay for rentals too: what kind of thing is paid for, and a refundable deposit.
-- (booking_id now means the ride booking or the rental booking being paid for; driver_id the person paid.)
ALTER TABLE payments ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'RIDE';
ALTER TABLE payments ADD COLUMN deposit_paise BIGINT NOT NULL DEFAULT 0 CHECK (deposit_paise >= 0);
ALTER TABLE payments DROP CONSTRAINT payments_check;
ALTER TABLE payments ADD CONSTRAINT payments_amounts_add_up
    CHECK (credits_paise + online_paise = seat_paise + fee_paise + deposit_paise);

CREATE TABLE rental_listings (
    id              UUID              PRIMARY KEY,
    owner_id        UUID              NOT NULL REFERENCES users (id),
    vehicle_id      UUID              NOT NULL,
    self_drive      BOOLEAN           NOT NULL,
    with_driver     BOOLEAN           NOT NULL,
    day_price       INT               NOT NULL CHECK (day_price BETWEEN 100 AND 50000),     -- ₹ per day
    hour_price      INT               CHECK (hour_price BETWEEN 50 AND 5000),               -- ₹ per hour, optional
    deposit         INT               NOT NULL DEFAULT 0 CHECK (deposit BETWEEN 0 AND 100000),
    km_per_day      INT               NOT NULL CHECK (km_per_day BETWEEN 0 AND 2000),
    extra_km_price  INT               NOT NULL CHECK (extra_km_price BETWEEN 0 AND 100),     -- ₹ per extra km
    fuel_policy     VARCHAR(16)       NOT NULL,       -- SAME_LEVEL (return as you got it) or INCLUDED
    min_age         INT               NOT NULL DEFAULT 21 CHECK (min_age BETWEEN 18 AND 30),
    place_id        BIGINT            NOT NULL,
    place_name      VARCHAR(100)      NOT NULL,
    state           VARCHAR(60),
    lat             DOUBLE PRECISION  NOT NULL,
    lng             DOUBLE PRECISION  NOT NULL,
    location        geography(Point, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED,
    area            VARCHAR(120)      NOT NULL,       -- shown to everyone, e.g. "Near MG Road metro"
    pickup_address  VARCHAR(300),                     -- shown only once the owner accepts
    description     VARCHAR(1000),
    rules           VARCHAR(500),
    instant         BOOLEAN           NOT NULL DEFAULT FALSE,
    status          VARCHAR(16)       NOT NULL,       -- DRAFT, IN_REVIEW, LIVE, PAUSED, REJECTED, REMOVED
    verified        BOOLEAN           NOT NULL DEFAULT FALSE,   -- staff checked the documents
    review_note     VARCHAR(300),
    reviewed_by     UUID,
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ       NOT NULL,
    updated_at      TIMESTAMPTZ       NOT NULL,
    CHECK (self_drive OR with_driver)
);
CREATE UNIQUE INDEX uq_rental_listings_vehicle ON rental_listings (vehicle_id) WHERE status <> 'REMOVED';
CREATE INDEX idx_rental_listings_live ON rental_listings USING gist (location) WHERE status = 'LIVE';
CREATE INDEX idx_rental_listings_owner ON rental_listings (owner_id, created_at DESC);
CREATE INDEX idx_rental_listings_status ON rental_listings (status, updated_at);

-- Public photos of the car (re-encoded, so no hidden location data).
CREATE TABLE rental_photos (
    id          UUID          PRIMARY KEY,
    listing_id  UUID          NOT NULL REFERENCES rental_listings (id) ON DELETE CASCADE,
    path        VARCHAR(120)  NOT NULL,
    position    INT           NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_rental_photos_listing ON rental_photos (listing_id, position);

-- Registration, insurance and permit: encrypted, seen only by the owner and staff.
CREATE TABLE rental_documents (
    id          UUID          PRIMARY KEY,
    listing_id  UUID          NOT NULL REFERENCES rental_listings (id) ON DELETE CASCADE,
    kind        VARCHAR(16)   NOT NULL,       -- RC, INSURANCE, PERMIT
    file_name   VARCHAR(80)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_rental_documents_listing ON rental_documents (listing_id);

-- Times the owner has blocked (the car isn't available).
CREATE TABLE rental_blocks (
    id          UUID          PRIMARY KEY,
    listing_id  UUID          NOT NULL REFERENCES rental_listings (id) ON DELETE CASCADE,
    starts_at   TIMESTAMPTZ   NOT NULL,
    ends_at     TIMESTAMPTZ   NOT NULL,
    period      TSTZRANGE     GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED,
    note        VARCHAR(100),
    created_at  TIMESTAMPTZ   NOT NULL,
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_rental_blocks_period ON rental_blocks USING gist (listing_id, period);

CREATE TABLE rental_bookings (
    id              UUID          PRIMARY KEY,
    listing_id      UUID          NOT NULL REFERENCES rental_listings (id),
    renter_id       UUID          NOT NULL REFERENCES users (id),
    owner_id        UUID          NOT NULL REFERENCES users (id),
    mode            VARCHAR(16)   NOT NULL,       -- SELF_DRIVE or WITH_DRIVER
    starts_at       TIMESTAMPTZ   NOT NULL,
    ends_at         TIMESTAMPTZ   NOT NULL,
    period          TSTZRANGE     GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED,
    rent            INT           NOT NULL CHECK (rent > 0),          -- ₹ for the whole period, fixed when booked
    deposit         INT           NOT NULL CHECK (deposit >= 0),      -- ₹, refundable
    km_included     INT           NOT NULL,
    extra_km_price  INT           NOT NULL,
    pay_online      BOOLEAN       NOT NULL,       -- false: rent and deposit are paid to the owner at pickup
    status          VARCHAR(16)   NOT NULL,       -- REQUESTED, ACCEPTED, PAID, PICKED_UP, RETURNED, DISPUTED, COMPLETED,
                                                  -- DECLINED, EXPIRED, CANCELLED
    message         VARCHAR(300),
    created_at      TIMESTAMPTZ   NOT NULL,
    decided_at      TIMESTAMPTZ,
    pay_by          TIMESTAMPTZ,
    paid_at         TIMESTAMPTZ,
    picked_up_at    TIMESTAMPTZ,
    returned_at     TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    cancelled_by    VARCHAR(16),                  -- RENTER, OWNER, STAFF, SYSTEM
    extra_charges   INT           NOT NULL DEFAULT 0 CHECK (extra_charges >= 0),   -- ₹ kept from the deposit
    extra_note      VARCHAR(300),
    dispute_note    VARCHAR(500),
    resolution      VARCHAR(300),
    CHECK (ends_at > starts_at),
    -- The database itself stops a car being booked twice for the same time.
    EXCLUDE USING gist (listing_id WITH =, period WITH &&)
        WHERE (status IN ('ACCEPTED', 'PAID', 'PICKED_UP', 'RETURNED', 'DISPUTED'))
);
CREATE INDEX idx_rental_bookings_renter ON rental_bookings (renter_id, created_at DESC);
CREATE INDEX idx_rental_bookings_owner ON rental_bookings (owner_id, created_at DESC);
CREATE INDEX idx_rental_bookings_status ON rental_bookings (status);

-- What each side recorded at pickup and return: odometer, fuel, notes and photos (encrypted, private).
CREATE TABLE rental_checks (
    id          UUID           PRIMARY KEY,
    booking_id  UUID           NOT NULL REFERENCES rental_bookings (id) ON DELETE CASCADE,
    stage       VARCHAR(8)     NOT NULL,       -- PICKUP or RETURN
    by_role     VARCHAR(8)     NOT NULL,       -- OWNER or RENTER
    odometer    INT            NOT NULL CHECK (odometer BETWEEN 0 AND 2000000),
    fuel        VARCHAR(16)    NOT NULL,       -- EMPTY, QUARTER, HALF, THREE_QUARTERS, FULL
    notes       VARCHAR(500),
    photos      VARCHAR(1000)  NOT NULL DEFAULT '',   -- document-vault file names, comma separated
    created_at  TIMESTAMPTZ    NOT NULL,
    UNIQUE (booking_id, stage, by_role)
);
