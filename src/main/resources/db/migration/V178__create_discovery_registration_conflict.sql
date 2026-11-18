CREATE TABLE discovery_registration_conflict (
    item_id BIGINT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    observed_execution_id BIGINT NOT NULL,
    evidence_sha256 BINARY(32) NOT NULL,
    evidence_json JSON NOT NULL,
    observed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (observed_execution_id, item_id, reason, evidence_sha256),
    KEY idx_registration_conflict_item (item_id),
    CONSTRAINT fk_registration_conflict_item FOREIGN KEY (item_id) REFERENCES discovery_verification_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
