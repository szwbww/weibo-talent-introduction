ALTER TABLE mail_record
    ADD COLUMN cleaned_body LONGTEXT AFTER body;

CREATE TABLE inbound_intent (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mail_record_id BIGINT NOT NULL,
    expert_contact_id BIGINT NOT NULL,
    intent_code VARCHAR(64) NOT NULL,
    confidence INT NOT NULL DEFAULT 0,
    matched_keywords TEXT,
    auto_action VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_inbound_intent_contact (expert_contact_id, created_at),
    KEY idx_inbound_intent_code (intent_code, created_at),
    CONSTRAINT fk_inbound_intent_mail_record
        FOREIGN KEY (mail_record_id) REFERENCES mail_record(id),
    CONSTRAINT fk_inbound_intent_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);
