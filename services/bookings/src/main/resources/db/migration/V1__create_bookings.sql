-- Flyway migration: create bookings table
CREATE TABLE IF NOT EXISTS bookings (
  id BIGSERIAL PRIMARY KEY,
  booking_id VARCHAR(100) NOT NULL,
  train VARCHAR(200),
  origin VARCHAR(200),
  destination VARCHAR(200),
  departure VARCHAR(100),
  arrival VARCHAR(100),
  price BIGINT,
  user_id VARCHAR(200),
  created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_bookings_booking_id ON bookings(booking_id);
