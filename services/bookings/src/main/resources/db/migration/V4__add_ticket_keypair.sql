CREATE TABLE IF NOT EXISTS ticket_key_pairs (
  id BIGSERIAL PRIMARY KEY,
  private_key TEXT NOT NULL,
  public_key TEXT NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now()
);
