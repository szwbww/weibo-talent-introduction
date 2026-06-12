CREATE TABLE mail_send_attempt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    orcid_id VARCHAR(100) NOT NULL,
    mail_type VARCHAR(50) NOT NULL,
    account_code VARCHAR(100) NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL COMMENT 'PREPARE_FAILED, DELIVERY_UNKNOWN, SENT, FAILED_SAFE_TO_RETRY',
    error_summary TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_orcid_mail_type (orcid_id, mail_type),
    UNIQUE KEY uq_msa_message_id (message_id)
);

CREATE INDEX idx_msa_orcid_id ON mail_send_attempt(orcid_id);

ALTER TABLE mail_record ADD COLUMN error_summary VARCHAR(1024) DEFAULT NULL;
