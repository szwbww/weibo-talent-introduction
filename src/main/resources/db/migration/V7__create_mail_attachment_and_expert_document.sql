CREATE TABLE mail_attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mail_record_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    file_size BIGINT NOT NULL DEFAULT 0,
    storage_path VARCHAR(1024) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_mail_attachment_record (mail_record_id, created_at),
    CONSTRAINT fk_mail_attachment_record
        FOREIGN KEY (mail_record_id) REFERENCES mail_record(id)
);

CREATE TABLE expert_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    mail_attachment_id BIGINT NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    document_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
    review_note TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_expert_document_contact (expert_contact_id, document_type, document_status),
    CONSTRAINT fk_expert_document_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
    CONSTRAINT fk_expert_document_attachment
        FOREIGN KEY (mail_attachment_id) REFERENCES mail_attachment(id)
);
