CREATE TABLE domain_reputation_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    domain VARCHAR(255) NOT NULL,
    report_date DATE NOT NULL,
    spam_rate DOUBLE,
    domain_reputation VARCHAR(32),
    spf_success_rate DOUBLE,
    dkim_success_rate DOUBLE,
    dmarc_success_rate DOUBLE,
    raw_json TEXT,
    collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_domain_date (domain, report_date)
);
