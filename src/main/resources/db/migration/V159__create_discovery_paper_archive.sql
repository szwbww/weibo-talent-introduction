CREATE TABLE discovery_paper_archive_setting (
    id INT NOT NULL PRIMARY KEY,
    enabled TINYINT(1) NOT NULL,
    max_bytes BIGINT NOT NULL,
    min_free_bytes BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT chk_paper_setting_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE discovery_paper_archive_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    source_name VARCHAR(32) NOT NULL,
    paper_key VARCHAR(160) NOT NULL,
    metadata_json MEDIUMTEXT NOT NULL,
    outcome_json MEDIUMTEXT NULL,
    analysis_json MEDIUMTEXT NULL,
    protected TINYINT(1) NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_paper_identity (execution_id, source_name, paper_key),
    INDEX idx_paper_execution (execution_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE discovery_paper_archive_blob (
    sha256 CHAR(64) NOT NULL PRIMARY KEY,
    size_bytes BIGINT NOT NULL,
    media_type VARCHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT chk_paper_blob_state CHECK (state IN ('AVAILABLE','DELETING','MISSING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE discovery_paper_archive_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    attempt_key CHAR(64) NOT NULL UNIQUE,
    kind VARCHAR(24) NOT NULL,
    source_url TEXT NULL,
    status VARCHAR(24) NOT NULL,
    blob_sha256 CHAR(64) NULL,
    reused TINYINT(1) NOT NULL DEFAULT 0,
    http_status INT NULL,
    reason_code VARCHAR(64) NULL,
    completed_bytes BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    INDEX idx_paper_attempt_record (record_id, id),
    INDEX idx_paper_attempt_blob (blob_sha256, status),
    CONSTRAINT fk_paper_attempt_record FOREIGN KEY (record_id) REFERENCES discovery_paper_archive_record(id),
    CONSTRAINT fk_paper_attempt_blob FOREIGN KEY (blob_sha256) REFERENCES discovery_paper_archive_blob(sha256),
    CONSTRAINT chk_paper_attempt_status CHECK (status IN ('STARTED','SAVED','DOWNLOAD_FAILED','TOO_LARGE','SAVE_ERROR','DELETED')),
    CONSTRAINT chk_paper_attempt_kind CHECK (kind IN ('PDF_URL','PMC_XML','PMC_OA_XML','EMBEDDED_TEXT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
