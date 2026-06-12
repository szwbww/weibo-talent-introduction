CREATE TABLE mail_send_attempt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    orcid_id VARCHAR(100) NOT NULL,
    mail_type VARCHAR(50) NOT NULL,
    account_code VARCHAR(100) NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL COMMENT 'PREPARED, DELIVERY_IN_PROGRESS, DELIVERY_UNKNOWN, SENT, FAILED_SAFE_TO_RETRY',
    error_summary TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_orcid_mail_type (orcid_id, mail_type),
    UNIQUE KEY uq_msa_message_id (message_id)
);

ALTER TABLE mail_record ADD COLUMN error_summary VARCHAR(1024) DEFAULT NULL;
ALTER TABLE mail_record ADD COLUMN mail_send_attempt_id BIGINT DEFAULT NULL;
CREATE INDEX idx_mr_mail_send_attempt_id ON mail_record(mail_send_attempt_id);
