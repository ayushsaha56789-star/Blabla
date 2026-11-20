-- Phase 7: driver partners. Members with a checked driving licence drive other members' cars, in the areas they
-- choose: local trips by the hour, or outstation trips by the day. Staff check each driver before they go live.

-- Staff who check drivers and look after their trips.
INSERT INTO staff_role_permissions (role_code, permission) VALUES
    ('SUPER_ADMIN', 'drivers.manage'),
    ('ADMIN', 'drivers.manage');

CREATE TABLE driver_profiles (
    user_id              UUID          PRIMARY KEY REFERENCES users (id),
    status               VARCHAR(16)   NOT NULL,      -- DRAFT, IN_REVIEW, LIVE, REJECTED, REMOVED
    licence_number       VARCHAR(255)  NOT NULL,      -- encrypted (AES-GCM)
    licence_class        VARCHAR(16)   NOT NULL,      -- LMV (private cars) or TRANSPORT (commercial vehicles too)
    licence_valid_until  DATE          NOT NULL,
    experience_years     INT           NOT NULL CHECK (experience_years BETWEEN 0 AND 60),
    languages            VARCHAR(200)  NOT NULL DEFAULT '',     -- comma separated, e.g. "Hindi,English"
    manual               BOOLEAN       NOT NULL DEFAULT TRUE,   -- drives cars with gears
    automatic            BOOLEAN       NOT NULL DEFAULT TRUE,   -- drives automatic cars
    outstation           BOOLEAN       NOT NULL DEFAULT TRUE,   -- takes trips to other towns
    hour_price           INT           NOT NULL CHECK (hour_price BETWEEN 50 AND 2000),          -- ₹ per hour, local
    day_price            INT           NOT NULL CHECK (day_price BETWEEN 300 AND 10000),         -- ₹ for up to 12 hours, local
    outstation_price     INT           NOT NULL CHECK (outstation_price BETWEEN 300 AND 15000),  -- ₹ per day away
    allowance            INT           NOT NULL DEFAULT 0 CHECK (allowance BETWEEN 0 AND 3000),  -- ₹ per day away: food and stay
    night_charge         INT           NOT NULL DEFAULT 0 CHECK (night_charge BETWEEN 0 AND 2000), -- ₹ when a local trip runs 10 pm–6 am
    about                VARCHAR(500),
    police_certificate   VARCHAR(80),                 -- document-vault file name (optional, encrypted, staff only)
    police_checked       BOOLEAN       NOT NULL DEFAULT FALSE,
    taking_bookings      BOOLEAN       NOT NULL DEFAULT TRUE,
    review_note          VARCHAR(300),
    reviewed_by          UUID,
    reviewed_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    CHECK (manual OR automatic)
);
CREATE INDEX idx_driver_profiles_status ON driver_profiles (status, updated_at);

-- Where a driver works: a town and how far around it.
CREATE TABLE driver_areas (
    id          UUID              PRIMARY KEY,
    driver_id   UUID              NOT NULL REFERENCES driver_profiles (user_id) ON DELETE CASCADE,
    place_id    BIGINT            NOT NULL,
    place_name  VARCHAR(100)      NOT NULL,
    state       VARCHAR(60),
    lat         DOUBLE PRECISION  NOT NULL,
    lng         DOUBLE PRECISION  NOT NULL,
    location    geography(Point, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED,
    radius_km   INT               NOT NULL CHECK (radius_km BETWEEN 2 AND 60),
    created_at  TIMESTAMPTZ       NOT NULL,
    UNIQUE (driver_id, place_id)
);
CREATE INDEX idx_driver_areas_location ON driver_areas USING gist (location);

-- Time off.
CREATE TABLE driver_blocks (
    id          UUID          PRIMARY KEY,
    driver_id   UUID          NOT NULL REFERENCES driver_profiles (user_id) ON DELETE CASCADE,
    starts_at   TIMESTAMPTZ   NOT NULL,
    ends_at     TIMESTAMPTZ   NOT NULL,
    period      TSTZRANGE     GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED,
    note        VARCHAR(100),
    created_at  TIMESTAMPTZ   NOT NULL,
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_driver_blocks_period ON driver_blocks USING gist (driver_id, period);

CREATE TABLE driver_trips (
    id                UUID              PRIMARY KEY,
    driver_id         UUID              NOT NULL REFERENCES users (id),
    customer_id       UUID              NOT NULL REFERENCES users (id),
    kind              VARCHAR(16)       NOT NULL,     -- LOCAL (by the hour) or OUTSTATION (by the day)
    starts_at         TIMESTAMPTZ       NOT NULL,
    ends_at           TIMESTAMPTZ       NOT NULL,
    period            TSTZRANGE         GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED,
    place_id          BIGINT            NOT NULL,     -- pickup town
    place_name        VARCHAR(100)      NOT NULL,
    lat               DOUBLE PRECISION  NOT NULL,
    lng               DOUBLE PRECISION  NOT NULL,
    pickup_address    VARCHAR(300)      NOT NULL,     -- the driver sees it once they accept
    destination_id    BIGINT,                         -- outstation trips: where to
    destination_name  VARCHAR(100),
    car               VARCHAR(100)      NOT NULL,     -- e.g. "White Maruti Swift"
    automatic         BOOLEAN           NOT NULL,
    fare              INT               NOT NULL CHECK (fare > 0),    -- ₹ for the booked time, fixed when booked
    extra_price       INT               NOT NULL,     -- ₹ per extra hour (local) or extra day (outstation)
    pay_online        BOOLEAN           NOT NULL,     -- false: the member pays the driver directly
    status            VARCHAR(16)       NOT NULL,     -- REQUESTED, ACCEPTED, PAID, ON_THE_WAY, STARTED, COMPLETED,
                                                      -- DECLINED, EXPIRED, CANCELLED
    start_code        VARCHAR(4)        NOT NULL,     -- the member tells the driver in person: proves the right driver came
    wrong_codes       INT               NOT NULL DEFAULT 0,
    message           VARCHAR(300),
    created_at        TIMESTAMPTZ       NOT NULL,
    answer_by         TIMESTAMPTZ       NOT NULL,
    decided_at        TIMESTAMPTZ,
    pay_by            TIMESTAMPTZ,
    paid_at           TIMESTAMPTZ,
    on_the_way_at     TIMESTAMPTZ,
    started_at        TIMESTAMPTZ,
    ended_at          TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    cancelled_by      VARCHAR(16),                    -- CUSTOMER, DRIVER, STAFF, SYSTEM
    cancel_reason     VARCHAR(300),
    extra_units       INT               NOT NULL DEFAULT 0,   -- hours or days over the booking (paid to the driver directly)
    CHECK (ends_at > starts_at),
    CHECK (driver_id <> customer_id),
    -- The database itself stops a driver being booked twice for the same time.
    EXCLUDE USING gist (driver_id WITH =, period WITH &&) WHERE (status IN ('ACCEPTED', 'PAID', 'ON_THE_WAY', 'STARTED'))
);
CREATE INDEX idx_driver_trips_customer ON driver_trips (customer_id, created_at DESC);
CREATE INDEX idx_driver_trips_driver ON driver_trips (driver_id, created_at DESC);
CREATE INDEX idx_driver_trips_status ON driver_trips (status);

-- After a trip, each side rates the other.
CREATE TABLE driver_reviews (
    id          UUID          PRIMARY KEY,
    trip_id     UUID          NOT NULL REFERENCES driver_trips (id) ON DELETE CASCADE,
    author_id   UUID          NOT NULL REFERENCES users (id),
    subject_id  UUID          NOT NULL REFERENCES users (id),
    by_role     VARCHAR(8)    NOT NULL,       -- CUSTOMER (about the driver) or DRIVER (about the member)
    stars       INT           NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment     VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL,
    UNIQUE (trip_id, by_role)
);
CREATE INDEX idx_driver_reviews_subject ON driver_reviews (subject_id, by_role, created_at DESC);
