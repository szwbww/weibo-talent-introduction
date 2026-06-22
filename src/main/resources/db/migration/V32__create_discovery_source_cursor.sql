CREATE TABLE discovery_source_cursor (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_name  VARCHAR(50)  NOT NULL,
    cursor_value TEXT,
    papers_processed_total BIGINT NOT NULL DEFAULT 0,
    last_run_at  DATETIME,
    updated_at   DATETIME     NOT NULL,
    UNIQUE KEY uk_source_name (source_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
