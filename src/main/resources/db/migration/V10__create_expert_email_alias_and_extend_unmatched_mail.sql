CREATE TABLE expert_email_alias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    expert_contact_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    normalized_email VARCHAR(255) NOT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'MANUAL_BIND',
    verified BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_expert_email_alias_normalized_email UNIQUE (normalized_email),
    CONSTRAINT fk_expert_email_alias_expert_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);

CREATE INDEX idx_expert_email_alias_expert_contact_id ON expert_email_alias(expert_contact_id);
CREATE INDEX idx_expert_email_alias_normalized_email ON expert_email_alias(normalized_email);

ALTER TABLE inbound_mail_processing
    ADD COLUMN in_reply_to VARCHAR(255) DEFAULT NULL AFTER message_id,
    ADD COLUMN body TEXT DEFAULT NULL AFTER subject,
    ADD COLUMN cleaned_body TEXT DEFAULT NULL AFTER body,
    ADD COLUMN resolved_at DATETIME DEFAULT NULL AFTER process_reason,
    ADD COLUMN resolved_by VARCHAR(100) DEFAULT NULL AFTER resolved_at;
