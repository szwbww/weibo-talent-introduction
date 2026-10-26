ALTER TABLE discovery_verification_run
    ADD COLUMN recheck_json MEDIUMTEXT NULL;

ALTER TABLE discovery_verification_item
    ADD COLUMN review_epoch BIGINT NOT NULL DEFAULT 0,
    DROP INDEX uk_dvi_identity,
    ADD UNIQUE KEY uk_dvi_identity (expert_doc_id, identity_hash, policy_version, review_epoch),
    ADD CONSTRAINT chk_dvi_review_epoch CHECK (review_epoch = 0 OR review_epoch = owner_execution_id);

CREATE TABLE discovery_verification_step (
    id BIGINT NOT NULL AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    step_no INT NOT NULL,
    attempt_no INT NOT NULL,
    state VARCHAR(24) NOT NULL,
    input_hash CHAR(64) NULL,
    input_json MEDIUMTEXT NULL,
    output_json MEDIUMTEXT NULL,
    reason_json TEXT NULL,
    version_json TEXT NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dvs_attempt (item_id, step_no, attempt_no),
    KEY idx_dvs_item (item_id, id),
    CONSTRAINT chk_dvs_step CHECK (step_no BETWEEN 1 AND 8 AND attempt_no > 0),
    CONSTRAINT chk_dvs_state CHECK (
        (state = 'NOT_STARTED' AND input_hash IS NULL AND started_at IS NULL AND finished_at IS NULL)
        OR (state = 'RUNNING' AND input_hash IS NOT NULL AND started_at IS NOT NULL AND finished_at IS NULL)
        OR (state IN ('SUCCESS','INSUFFICIENT','SKIPPED','ERROR','CANCELLED')
            AND input_hash IS NOT NULL AND started_at IS NOT NULL AND finished_at IS NOT NULL
            AND finished_at >= started_at)
    )
);
