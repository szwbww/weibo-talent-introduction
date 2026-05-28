CREATE TABLE inbound_mail_processing (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_account_code VARCHAR(64) NOT NULL,
    imap_uid BIGINT NOT NULL,
    message_id VARCHAR(255),
    from_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    received_at DATETIME NOT NULL,
    process_status VARCHAR(32) NOT NULL,
    process_reason VARCHAR(64) NOT NULL,
    expert_contact_id BIGINT,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inbound_mail_processing_uid (sender_account_code, imap_uid),
    KEY idx_inbound_mail_processing_status (process_status, received_at),
    CONSTRAINT fk_inbound_mail_processing_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);
