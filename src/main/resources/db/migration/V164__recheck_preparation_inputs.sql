-- Bounded, durable input spool; never makes an expert eligible or activates a verification item.
CREATE TABLE discovery_recheck_input (
    execution_id BIGINT NOT NULL,
    doc_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    payload_json MEDIUMTEXT NOT NULL,
    PRIMARY KEY (execution_id, doc_id),
    CONSTRAINT fk_recheck_input_run FOREIGN KEY (execution_id) REFERENCES discovery_verification_run(execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
