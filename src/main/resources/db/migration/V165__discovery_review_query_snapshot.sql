-- Read-only list materialization. Never used as admission authority.
CREATE TABLE discovery_review_query (
    query_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    generation CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    owner_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state VARCHAR(16) NOT NULL,
    matched BIGINT NOT NULL DEFAULT 0,
    error_message VARCHAR(300) NULL,
    started_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    INDEX idx_review_query_expiry(expires_at)
) ENGINE=InnoDB;
CREATE TABLE discovery_review_query_row (
    generation CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ordinal BIGINT NOT NULL,
    row_json MEDIUMTEXT NOT NULL,
    PRIMARY KEY (generation, ordinal),
    CONSTRAINT fk_review_query_generation FOREIGN KEY (generation) REFERENCES discovery_review_query(generation) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
