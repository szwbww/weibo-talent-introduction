CREATE TABLE expert_material_file (
    attachment_id BIGINT PRIMARY KEY,
    parent_attachment_id BIGINT NULL,
    entry_path VARCHAR(2048) NULL,
    display_name VARCHAR(255) NULL,
    content_note TEXT NULL,
    tags_json TEXT NOT NULL,
    deleted_at DATETIME(3) NULL,
    extraction_state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    extraction_error VARCHAR(500) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_material_parent (parent_attachment_id),
    CONSTRAINT fk_material_file_attachment FOREIGN KEY (attachment_id) REFERENCES mail_attachment(id),
    CONSTRAINT fk_material_file_parent FOREIGN KEY (parent_attachment_id) REFERENCES mail_attachment(id)
);
