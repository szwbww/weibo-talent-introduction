CREATE TABLE expert_contact_note (
    expert_contact_id BIGINT NOT NULL,
    note VARCHAR(2000) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (expert_contact_id),
    CONSTRAINT fk_expert_contact_note_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
