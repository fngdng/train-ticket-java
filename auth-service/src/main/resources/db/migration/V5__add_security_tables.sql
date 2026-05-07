CREATE TABLE IF NOT EXISTS auth_key_pairs (
  id BIGSERIAL PRIMARY KEY,
  private_key TEXT NOT NULL,
  public_key TEXT NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now()
);

CREATE TABLE IF NOT EXISTS two_factor_challenges (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  code_hash VARCHAR(255) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  destination VARCHAR(200),
  expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  used_at TIMESTAMP WITHOUT TIME ZONE,
  created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_two_factor_username ON two_factor_challenges(username);