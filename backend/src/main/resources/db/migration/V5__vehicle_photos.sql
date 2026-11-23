ALTER TABLE vehicles ADD COLUMN photo_version uuid;

CREATE TABLE vehicle_photos (
    vehicle_id bigint PRIMARY KEY REFERENCES vehicles(id) ON DELETE CASCADE,
    jpeg bytea NOT NULL CHECK (octet_length(jpeg) BETWEEN 1 AND 393216)
);
