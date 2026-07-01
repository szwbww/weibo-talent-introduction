CREATE TABLE inbound_mail_tag (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    inbound_processing_id BIGINT       NOT NULL,
    tag_type              VARCHAR(16)  NOT NULL,
    qa_rule_id            BIGINT       NULL,
    label                 VARCHAR(255) NOT NULL,
    source                VARCHAR(16)  NOT NULL,
    created_by            VARCHAR(64)  NULL,
    created_at            DATETIME     NOT NULL,
    UNIQUE KEY uk_inbound_qa (inbound_processing_id, qa_rule_id),
    KEY idx_inbound (inbound_processing_id),
    KEY idx_qa_rule (qa_rule_id),
    KEY idx_label (label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
