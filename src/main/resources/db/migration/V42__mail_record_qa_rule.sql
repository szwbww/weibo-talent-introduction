-- Phase 2b-4: persist all matched QA rules for aggregated outbound replies.

CREATE TABLE mail_record_qa_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mail_record_id BIGINT NOT NULL,
    qa_rule_id BIGINT NOT NULL,
    ordinal INT NOT NULL,
    CONSTRAINT fk_mail_record_qa_rule_mail_record
        FOREIGN KEY (mail_record_id) REFERENCES mail_record(id) ON DELETE RESTRICT,
    CONSTRAINT fk_mail_record_qa_rule_qa_rule
        FOREIGN KEY (qa_rule_id) REFERENCES qa_rule(id) ON DELETE RESTRICT,
    CONSTRAINT uk_mail_record_qa_rule UNIQUE (mail_record_id, qa_rule_id)
);
