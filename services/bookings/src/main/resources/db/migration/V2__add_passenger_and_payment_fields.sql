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
  created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now(),
  passenger_name VARCHAR(200),
  passenger_phone VARCHAR(50),
  passenger_email VARCHAR(200),
  payment_method VARCHAR(50),
  payment_status VARCHAR(50)
);

ALTER TABLE bookings
  ADD COLUMN IF NOT EXISTS passenger_name VARCHAR(200),
  ADD COLUMN IF NOT EXISTS passenger_phone VARCHAR(50),
  ADD COLUMN IF NOT EXISTS passenger_email VARCHAR(200),
  ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50),
  ADD COLUMN IF NOT EXISTS payment_status VARCHAR(50);

CREATE UNIQUE INDEX IF NOT EXISTS idx_bookings_booking_id ON bookings(booking_id);
