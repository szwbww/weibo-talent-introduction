CREATE TABLE expert_contact_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    from_status VARCHAR(64),
    to_status VARCHAR(64) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    source VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_expert_contact_status_history_contact (expert_contact_id, created_at),
    KEY idx_expert_contact_status_history_status (to_status, created_at),
    CONSTRAINT fk_expert_contact_status_history_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);
