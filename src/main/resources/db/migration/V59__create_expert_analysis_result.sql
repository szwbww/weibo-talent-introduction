CREATE TABLE expert_analysis_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    field_label VARCHAR(128) NOT NULL,
    value TEXT NOT NULL,
    source_attachment_id BIGINT,
    source_excerpt TEXT,
    excerpt_verified TINYINT(1) NOT NULL DEFAULT 0,
    display_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_analysis_contact (expert_contact_id, display_order),
    CONSTRAINT fk_analysis_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);
