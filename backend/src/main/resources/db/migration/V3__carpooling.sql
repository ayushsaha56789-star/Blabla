-- Phase 3: carpooling — members' cars, rides with stops, and seat bookings.
-- Place search (Indian cities and towns from GeoNames) runs in memory from geo/places-in.tsv, so it needs no tables.

-- ---------------------------------------------------------------------------
-- Members' cars. The registration number is encrypted; only its last characters are kept in clear.
-- ---------------------------------------------------------------------------
CREATE TABLE vehicles (
    id          UUID         PRIMARY KEY,
    owner_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    make        VARCHAR(40)  NOT NULL,
    model       VARCHAR(40)  NOT NULL,
    color       VARCHAR(30),
    seats       INT          NOT NULL CHECK (seats BETWEEN 1 AND 8),   -- seats for passengers
    plate_enc   VARCHAR(255) NOT NULL,
    plate_hint  VARCHAR(8)   NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_vehicles_owner ON vehicles (owner_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- Rides and their stops (origin = position 0, destination = last position).
-- ---------------------------------------------------------------------------
CREATE TABLE rides (
    id               UUID         PRIMARY KEY,
    driver_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    vehicle_id       UUID         NOT NULL REFERENCES vehicles (id),
    status           VARCHAR(16)  NOT NULL,              -- SCHEDULED, CANCELLED, COMPLETED
    departure_at     TIMESTAMPTZ  NOT NULL,
    seats_total      INT          NOT NULL CHECK (seats_total BETWEEN 1 AND 8),
    price_per_seat   INT          NOT NULL CHECK (price_per_seat > 0),   -- rupees, whole route
    distance_km      INT          NOT NULL,
    duration_min     INT          NOT NULL,
    ladies_only      BOOLEAN      NOT NULL DEFAULT FALSE,
    instant_booking  BOOLEAN      NOT NULL DEFAULT FALSE,
    notes            VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL,
    cancelled_at     TIMESTAMPTZ,
    cancelled_by     VARCHAR(16),                        -- DRIVER, STAFF
    cancel_reason    VARCHAR(300),
    completed_at     TIMESTAMPTZ
);
CREATE INDEX idx_rides_departure ON rides (departure_at) WHERE status = 'SCHEDULED';
CREATE INDEX idx_rides_driver ON rides (driver_id, departure_at DESC);
CREATE INDEX idx_rides_created ON rides (created_at);

CREATE TABLE ride_stops (
    ride_id            UUID         NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    position           INT          NOT NULL,
    place_id           BIGINT       NOT NULL,             -- GeoNames id
    name               VARCHAR(120) NOT NULL,
    state              VARCHAR(80)  NOT NULL DEFAULT '',
    meeting_point      VARCHAR(160),
    lat                DOUBLE PRECISION NOT NULL,
    lng                DOUBLE PRECISION NOT NULL,
    km_from_start      INT          NOT NULL,
    minutes_from_start INT          NOT NULL,
    location           GEOGRAPHY(Point, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED,
    PRIMARY KEY (ride_id, position)
);
CREATE INDEX idx_ride_stops_location ON ride_stops USING GIST (location);

-- ---------------------------------------------------------------------------
-- Bookings: a passenger's seats on one stretch of a ride (from_position → to_position).
-- ---------------------------------------------------------------------------
CREATE TABLE bookings (
    id              UUID         PRIMARY KEY,
    ride_id         UUID         NOT NULL REFERENCES rides (id) ON DELETE CASCADE,
    passenger_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    from_position   INT          NOT NULL,
    to_position     INT          NOT NULL,
    seats           INT          NOT NULL CHECK (seats BETWEEN 1 AND 8),
    price_per_seat  INT          NOT NULL,
    status          VARCHAR(16)  NOT NULL,              -- REQUESTED, CONFIRMED, DECLINED, CANCELLED, EXPIRED, COMPLETED
    message         VARCHAR(300),
    created_at      TIMESTAMPTZ  NOT NULL,
    decided_at      TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    cancelled_by    VARCHAR(16),                        -- PASSENGER, DRIVER, STAFF, SYSTEM
    CHECK (to_position > from_position)
);
CREATE INDEX idx_bookings_ride ON bookings (ride_id);
CREATE INDEX idx_bookings_passenger ON bookings (passenger_id, created_at DESC);
CREATE INDEX idx_bookings_requested ON bookings (created_at) WHERE status = 'REQUESTED';
-- A member holds at most one live booking per ride.
CREATE UNIQUE INDEX uq_bookings_live ON bookings (ride_id, passenger_id) WHERE status IN ('REQUESTED', 'CONFIRMED');
