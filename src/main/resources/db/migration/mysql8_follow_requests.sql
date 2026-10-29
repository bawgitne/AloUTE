-- BL-05 (issue #92): private-account follow requests.
-- Engine: MySQL 8.0+ (run once; no ADD COLUMN IF NOT EXISTS support).
-- Additive only: existing follows / users rows are untouched. No production data
-- is modified by this script — it creates one new table and one index.
--
-- DBA runbook:
--   1. Run once per environment before deploying the BL-05 build.
--   2. Rollback (safe — the table is new and unreferenced by legacy data):
--        DROP TABLE follow_requests;

CREATE TABLE follow_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_follow_requests_pair UNIQUE (requester_id, target_id),
    CONSTRAINT fk_follow_requests_requester FOREIGN KEY (requester_id) REFERENCES users (id),
    CONSTRAINT fk_follow_requests_target FOREIGN KEY (target_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX idx_follow_requests_target_status ON follow_requests (target_id, status);
