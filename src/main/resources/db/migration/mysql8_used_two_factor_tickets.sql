-- BL-08 2FA ticket single-use store.
-- Engine: MySQL 8.0+ (run once; no ADD COLUMN IF NOT EXISTS support).

CREATE TABLE used_two_factor_tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_hash VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    consumed_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_2fa_used_tickets_hash UNIQUE (ticket_hash)
);
CREATE INDEX idx_2fa_used_tickets_consumed_at ON used_two_factor_tickets (consumed_at);
