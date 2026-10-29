-- BL-05 (issue #92): private-account follow requests.
-- Engine: H2 (verified by H2MigrationScriptVerificationTest). Additive only:
-- existing follows / users rows are untouched.

CREATE TABLE IF NOT EXISTS follow_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_follow_requests_pair UNIQUE (requester_id, target_id),
    CONSTRAINT fk_follow_requests_requester FOREIGN KEY (requester_id) REFERENCES users (id),
    CONSTRAINT fk_follow_requests_target FOREIGN KEY (target_id) REFERENCES users (id)
);
CREATE INDEX IF NOT EXISTS idx_follow_requests_target_status ON follow_requests (target_id, status);
