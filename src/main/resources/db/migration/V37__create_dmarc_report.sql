CREATE TABLE dmarc_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id VARCHAR(255) NOT NULL,
    org_name VARCHAR(255),
    domain VARCHAR(255) NOT NULL,
    date_begin DATETIME NOT NULL,
    date_end DATETIME NOT NULL,
    total_count BIGINT NOT NULL DEFAULT 0,
    dkim_pass_count BIGINT NOT NULL DEFAULT 0,
    spf_pass_count BIGINT NOT NULL DEFAULT 0,
    dmarc_pass_count BIGINT NOT NULL DEFAULT 0,
    top_source_ip VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dmarc_report_id (report_id)
);
