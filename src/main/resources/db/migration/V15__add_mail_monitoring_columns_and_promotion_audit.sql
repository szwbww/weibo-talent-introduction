-- 1. mail_record monitoring fields
ALTER TABLE mail_record
    ADD COLUMN sender_account_code VARCHAR(64) NULL
        COMMENT '发件 / 收件账号 code；INBOUND=接收账号；OUTBOUND=发件账号' AFTER mail_type,
    ADD COLUMN triggered_by VARCHAR(16) NULL
        COMMENT 'SYSTEM=系统自动；OPERATOR=运营手动；INBOUND 行此列为 NULL' AFTER sender_account_code,
    ADD COLUMN source_inbound_id BIGINT NULL
        COMMENT '触发本条 OUTBOUND 的 INBOUND mail_record.id' AFTER triggered_by;

ALTER TABLE mail_record
    ADD INDEX idx_mail_record_dir_type_sent (direction, mail_type, sent_at),
    ADD INDEX idx_mail_record_dir_received (direction, received_at),
    ADD INDEX idx_mail_record_sender_sent (sender_account_code, sent_at),
    ADD INDEX idx_mail_record_triggered_sent (triggered_by, sent_at),
    ADD INDEX idx_mail_record_source_inbound (source_inbound_id);

-- 2. Extend reason_type comments without changing the column shape.
ALTER TABLE inbound_mail_processing
    MODIFY COLUMN reason_type VARCHAR(32) NULL
        COMMENT 'UNMATCHED_CONTACT / UNCLEAR_INTENT / QA_NO_MATCH / NOT_INTERESTED / MANUAL_RESOLVED / AUTO_QA_REPLIED / AUTO_MEETING_INVITED / AUTO_NOOP';

-- 3. Backfill historical processed rows into an explicit noop bucket.
UPDATE inbound_mail_processing
   SET reason_type = 'AUTO_NOOP'
 WHERE process_status = 'PROCESSED' AND reason_type IS NULL;

-- 4. Application promotion audit.
CREATE TABLE expert_application_promotion (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    orcid_id VARCHAR(64) NOT NULL,
    source_inbound_id BIGINT NULL COMMENT '触发本次晋级的 INBOUND mail_record.id',
    triggered_by VARCHAR(16) NOT NULL COMMENT 'SYSTEM / OPERATOR',
    promotion_status VARCHAR(16) NOT NULL COMMENT 'PENDING / SUCCESS / FAILED / REVERTED',
    from_level VARCHAR(16) NOT NULL DEFAULT 'CANDIDATE',
    to_level VARCHAR(16) NOT NULL DEFAULT 'APPLICATION',
    error_message TEXT NULL,
    operator_name VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_eap_status_created (promotion_status, created_at),
    KEY idx_eap_contact (expert_contact_id, created_at),
    CONSTRAINT fk_eap_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);

-- 5. Add one SUCCESS history row for contacts already in APPLICATION.
INSERT INTO expert_application_promotion
    (expert_contact_id, orcid_id, source_inbound_id, triggered_by, promotion_status,
     from_level, to_level, created_at, updated_at)
SELECT ec.id, ec.orcid_id, NULL, 'SYSTEM', 'SUCCESS',
       'CANDIDATE', 'APPLICATION', ec.updated_at, ec.updated_at
  FROM expert_contact ec
 WHERE ec.current_index_level = 'APPLICATION'
   AND NOT EXISTS (
       SELECT 1 FROM expert_application_promotion eap
        WHERE eap.expert_contact_id = ec.id
          AND eap.promotion_status = 'SUCCESS'
   );
