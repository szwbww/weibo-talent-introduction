SELECT COUNT(*) INTO @col_exists FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expert_contact' AND COLUMN_NAME = 'operator_status';

SET @add_col = 'ALTER TABLE expert_contact ADD COLUMN operator_status VARCHAR(32) NOT NULL DEFAULT ''NOT_CONTACTED'' COMMENT ''运营视角专家状态: NOT_CONTACTED / CONTACTED / REPLIED / MATERIALS_RECEIVED / INVITED / COMPLETED''';
SET @noop = 'SELECT ''Column operator_status exists, skipping ALTER'' AS msg';

SET @sql = IF(@col_exists = 0, @add_col, @noop);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @idx_exists FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expert_contact' AND INDEX_NAME = 'idx_expert_contact_operator_status';

SET @add_idx = 'ALTER TABLE expert_contact ADD INDEX idx_expert_contact_operator_status (operator_status, updated_at)';
SET @skip_idx = 'SELECT ''Index idx_expert_contact_operator_status exists, skipping ALTER'' AS msg';

SET @sql = IF(@idx_exists = 0, @add_idx, @skip_idx);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE expert_contact
   SET operator_status = CASE
       WHEN current_status IN ('MEETING_SCHEDULING', 'MEETING_SCHEDULED', 'MEETING_INVITATION_SENT', 'WAITING_MEETING_CONFIRMATION') THEN 'INVITED'
       WHEN current_status IN ('MATERIALS_PARTIAL', 'MATERIALS_RECEIVED') THEN 'MATERIALS_RECEIVED'
       WHEN last_reply_at IS NOT NULL THEN 'REPLIED'
       WHEN last_mail_at IS NOT NULL THEN 'CONTACTED'
       ELSE 'NOT_CONTACTED'
   END;

CREATE TABLE IF NOT EXISTS operator_action_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    target_type VARCHAR(64) NOT NULL COMMENT 'EXPERT_CONTACT / INBOUND_MAIL_PROCESSING / DOCUMENT',
    target_id BIGINT NOT NULL,
    expert_contact_id BIGINT NULL,
    inbound_processing_id BIGINT NULL,
    action_type VARCHAR(64) NOT NULL COMMENT 'CHANGE_OPERATOR_STATUS / CHANGE_INDEX_LEVEL / SWITCH_REPLY_MODE / BIND_INBOUND_MAIL / SEND_QA_REPLY / SEND_MANUAL_RICH_REPLY / MARK_INBOUND_RESOLVED',
    action_summary VARCHAR(255) NOT NULL,
    before_value TEXT NULL,
    after_value TEXT NULL,
    operator_name VARCHAR(128) NULL,
    note TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_operator_action_contact_created (expert_contact_id, created_at),
    KEY idx_operator_action_inbound_created (inbound_processing_id, created_at),
    KEY idx_operator_action_type_created (action_type, created_at),
    KEY idx_operator_action_operator_created (operator_name, created_at),
    CONSTRAINT fk_operator_action_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
    CONSTRAINT fk_operator_action_inbound
        FOREIGN KEY (inbound_processing_id) REFERENCES inbound_mail_processing(id)
);
