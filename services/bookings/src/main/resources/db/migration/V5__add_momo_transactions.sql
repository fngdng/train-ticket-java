CREATE TABLE IF NOT EXISTS momo_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id BIGINT NOT NULL,
    order_id VARCHAR(255) NOT NULL UNIQUE,
    amount BIGINT NOT NULL,
    request_id VARCHAR(255) NOT NULL,
    pay_url TEXT,
    qr_code_url TEXT,
    status VARCHAR(50) DEFAULT 'CREATED',
    result_code INTEGER,
    result_message VARCHAR(255),
    trans_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking FOREIGN KEY (booking_id) REFERENCES bookings(id)
);

CREATE INDEX IF NOT EXISTS idx_momo_order_id ON momo_transactions(order_id);
CREATE INDEX IF NOT EXISTS idx_momo_booking_id ON momo_transactions(booking_id);
CREATE INDEX IF NOT EXISTS idx_momo_status ON momo_transactions(status);
