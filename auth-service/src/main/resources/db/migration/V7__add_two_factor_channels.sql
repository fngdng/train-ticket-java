ALTER TABLE users
    ADD COLUMN IF NOT EXISTS two_factor_channels VARCHAR(255) NOT NULL DEFAULT 'email';

UPDATE users
SET two_factor_channels = CASE
    WHEN two_factor_channels IS NULL OR BTRIM(two_factor_channels) = '' THEN COALESCE(NULLIF(BTRIM(two_factor_channel), ''), 'email')
    ELSE two_factor_channels
END;
