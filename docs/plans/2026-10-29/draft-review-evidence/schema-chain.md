
## src/main/resources/db/migration/V101__add_task_execution_id_to_mail_record.sql
```sql
-- 关联邮件到产生它的任务执行。刻意不加外键：task_execution 有 90 天硬删除保留策略
-- （见 V102 / TaskAuditRetentionScheduler），加 FK 会阻塞清理。悬垂值由读取侧兜底。
ALTER TABLE mail_record
    ADD COLUMN task_execution_id BIGINT NULL;

CREATE INDEX idx_mail_record_task_execution
    ON mail_record (task_execution_id, id);

```

## src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql
```sql
CREATE TABLE IF NOT EXISTS expert_email_alias (
                                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                  expert_contact_id BIGINT NOT NULL,
                                                  email VARCHAR(255) NOT NULL,
                                                  normalized_email VARCHAR(255) NOT NULL,
                                                  source VARCHAR(50) NOT NULL DEFAULT 'MANUAL_BIND',
                                                  verified BOOLEAN NOT NULL DEFAULT TRUE,
                                                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  CONSTRAINT uk_expert_email_alias_normalized_email UNIQUE (normalized_email),
                                                  CONSTRAINT fk_expert_email_alias_expert_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);

DELIMITER //

DROP PROCEDURE IF EXISTS add_column_if_missing//
CREATE PROCEDURE add_column_if_missing(
    IN target_table VARCHAR(64),
    IN target_column VARCHAR(64),
    IN ddl_statement TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = target_table
          AND COLUMN_NAME = target_column
    ) THEN
        SET @migration_ddl = ddl_statement;
        PREPARE migration_statement FROM @migration_ddl;
        EXECUTE migration_statement;
        DEALLOCATE PREPARE migration_statement;
    END IF;
END//

DROP PROCEDURE IF EXISTS add_index_if_missing//
CREATE PROCEDURE add_index_if_missing(
    IN target_table VARCHAR(64),
    IN target_index VARCHAR(64),
    IN ddl_statement TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = target_table
          AND INDEX_NAME = target_index
    ) THEN
        SET @migration_ddl = ddl_statement;
        PREPARE migration_statement FROM @migration_ddl;
        EXECUTE migration_statement;
        DEALLOCATE PREPARE migration_statement;
    END IF;
END//

DROP PROCEDURE IF EXISTS add_foreign_key_if_missing//
CREATE PROCEDURE add_foreign_key_if_missing(
    IN target_table VARCHAR(64),
    IN target_constraint VARCHAR(64),
    IN ddl_statement TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = target_table
          AND CONSTRAINT_NAME = target_constraint
          AND CONSTRAINT_TYPE = 'FOREIGN KEY'
    ) THEN
        SET @migration_ddl = ddl_statement;
        PREPARE migration_statement FROM @migration_ddl;
        EXECUTE migration_statement;
        DEALLOCATE PREPARE migration_statement;
    END IF;
END//

DELIMITER ;

CALL add_index_if_missing(
        'expert_email_alias',
        'uk_expert_email_alias_normalized_email',
        'CREATE UNIQUE INDEX uk_expert_email_alias_normalized_email ON expert_email_alias(normalized_email)'
     );
CALL add_index_if_missing(
        'expert_email_alias',
        'idx_expert_email_alias_expert_contact_id',
        'CREATE INDEX idx_expert_email_alias_expert_contact_id ON expert_email_alias(expert_contact_id)'
     );
CALL add_foreign_key_if_missing(
        'expert_email_alias',
        'fk_expert_email_alias_expert_contact',
        'ALTER TABLE expert_email_alias ADD CONSTRAINT fk_expert_email_alias_expert_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)'
     );

CALL add_column_if_missing(
        'inbound_mail_processing',
        'in_reply_to',
        'ALTER TABLE inbound_mail_processing ADD COLUMN in_reply_to VARCHAR(255) DEFAULT NULL AFTER message_id'
     );
CALL add_column_if_missing(
        'inbound_mail_processing',
        'body',
        'ALTER TABLE inbound_mail_processing ADD COLUMN body TEXT DEFAULT NULL AFTER subject'
     );
CALL add_column_if_missing(
        'inbound_mail_processing',
        'cleaned_body',
        'ALTER TABLE inbound_mail_processing ADD COLUMN cleaned_body TEXT DEFAULT NULL AFTER body'
     );
CALL add_column_if_missing(
        'inbound_mail_processing',
        'resolved_at',
        'ALTER TABLE inbound_mail_processing ADD COLUMN resolved_at DATETIME DEFAULT NULL AFTER process_reason'
     );
CALL add_column_if_missing(
        'inbound_mail_processing',
        'resolved_by',
        'ALTER TABLE inbound_mail_processing ADD COLUMN resolved_by VARCHAR(100) DEFAULT NULL AFTER resolved_at'
     );

DROP PROCEDURE IF EXISTS add_foreign_key_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing;
DROP PROCEDURE IF EXISTS add_column_if_missing;

```

## src/main/resources/db/migration/V120__scope_inbound_uid_by_validity.sql
```sql
-- ============================================================================
-- V120 inbound_mail_processing: UID 唯一性以 UIDVALIDITY 界定（fast-p 04）
--
-- 问题：旧唯一键 (sender_account_code, imap_uid) 不含 UIDVALIDITY。邮箱代际
-- 变更（UIDVALIDITY 变化）后服务器可能重用相同 UID 号指向不同邮件，旧键会把
-- 新代际来信误判为已处理（吞信），或把旧附件定位到新信。
--
-- 关键不变量（I-1）：
--   * 新增 uid_validity BIGINT NOT NULL DEFAULT 0：0 仅表示历史未知代际；
--     新接收必须保存实际正值，绝不把当前邮箱值回填到历史 0 行（不猜测来源）。
--   * 唯一键替换为 (sender_account_code, uid_validity, imap_uid)：新写按真实
--     远端身份判重；历史 (account, uid) 判重路径仅保留给兼容读取/旧行代际
--     认领核验（代码层，非索引层）。
--   * 历史数据不 BACKFILL：默认 0 落库，旧 (account, uid) 在该列仍唯一
--     （旧键已保证 (account, uid) 无重复），不产生迁移冲突。
-- ============================================================================
ALTER TABLE inbound_mail_processing
    ADD COLUMN uid_validity BIGINT NOT NULL DEFAULT 0 COMMENT '远端邮箱代际；0 仅历史未知，新接收必须为实际正值';

ALTER TABLE inbound_mail_processing
    DROP INDEX uk_inbound_mail_processing_uid,
    ADD UNIQUE KEY uk_inbound_mail_processing_uid_validity (sender_account_code, uid_validity, imap_uid);

```

## src/main/resources/db/migration/V123__add_mail_record_calendar_attachment.sql
```sql
-- ============================================================================
-- V123 mail_record 会议日历附件存档列（fast-p 02）
--
-- 只新增一列：calendar_attachment_json LONGTEXT NULL。
--   * NULL = 无会议日历（唯一 absence 形态；禁止用空串/{} 表示无附件，I-1）；
--   * 非 NULL 必须是 01 CalendarAttachmentSnapshot 规范快照 JSON
--     （schemaVersion=1，UTF-8 字节 sha256 与语义 sha256 均与 icsText 一致），
--     由应用层 CalendarAttachmentCodec 严格生成/读取验证（I-1），本迁移不设
--     CHECK（应用 01 codec 是唯一校验方）。
-- 历史行不回填（旧行保持 NULL），不加默认值、不加索引。
-- ============================================================================

ALTER TABLE mail_record
    ADD COLUMN calendar_attachment_json LONGTEXT NULL;

```

## src/main/resources/db/migration/V127__add_outbound_attachments_snapshot.sql
```sql
-- ============================================================================
-- V127 mail_record 人工回复通用附件存档列（fast-p 05）
--
-- 只新增一列：outbound_attachments_json LONGTEXT NULL。
--   * NULL = 无通用附件（唯一 absence 形态；禁止用空数组/空串表示无附件，I-1）；
--   * 非 NULL 必须是 04 OutboundAttachmentSnapshotCodec.serialize 生成的有序快照
--     JSON 数组（schemaVersion=1，按选取顺序，逐项 filename/contentType/
--     byteLength/sha256；不含上传 UUID/磁盘路径/上传时间），由应用层 codec
--     严格生成与读取校验（I-1/I-2），本迁移不设 CHECK。
-- 历史行不回填（旧行保持 NULL），不加默认值、不加索引。
-- ============================================================================

ALTER TABLE mail_record
    ADD COLUMN outbound_attachments_json LONGTEXT NULL;

```

## src/main/resources/db/migration/V134__shared_inbox_owner.sql
```sql
-- ============================================================================
-- V134 共享物理收件箱归属：mail_sender_account / inbound_mail_processing（fast-p 01）
--
-- 目标：任意逻辑发件账号可被管理员显式关联到一个物理收件箱主账号。
-- 本迁移只加结构，不改写任何既有行；生产上 inbound_mailbox_code 在本组
-- 02/03 完成验证前必须保持 NULL（发布闸门）。
--
-- 关键不变量（I-1、I-2）：
--   * I-1 关联是显式单层关系：inbound_mailbox_code NULL = 本账号独立收件；
--     非空由外键保证指向存在的账号代码，服务层另行保证非模拟器、非自己、
--     且主账号自身为 NULL（不成链、不成环、不级联）。
--   * I-2 旧行不可伪造物理代际：mailbox_owner_code 默认 NULL，历史行保持
--     NULL（尤其 uid_validity=0），绝不用当前 UIDVALIDITY 回填。
--     唯一键 (mailbox_owner_code, uid_validity, imap_uid) 只约束非空物理身份；
--     MySQL 唯一键允许多个 NULL，故历史行不受影响，两个不同物理 owner 也可
--     各自持有相同 UID 号。
--   * 该唯一键是「新收件物理唯一」的库层兜底，不替换既有
--     uk_inbound_mail_processing_uid_validity（逻辑账号 + 代际 + UID）。
-- ============================================================================
ALTER TABLE mail_sender_account
    ADD COLUMN inbound_mailbox_code VARCHAR(64) NULL
        COMMENT '共享收件箱主账号代码；NULL=本账号独立收件（单层，不级联）';

ALTER TABLE mail_sender_account
    ADD CONSTRAINT fk_mail_sender_account_inbound_mailbox
        FOREIGN KEY (inbound_mailbox_code) REFERENCES mail_sender_account (account_code);

ALTER TABLE inbound_mail_processing
    ADD COLUMN mailbox_owner_code VARCHAR(64) NULL
        COMMENT '物理收件箱主账号代码；NULL=历史未知行（不回填）';

ALTER TABLE inbound_mail_processing
    ADD UNIQUE KEY uk_inbound_mail_processing_owner_uid (mailbox_owner_code, uid_validity, imap_uid);

```

## src/main/resources/db/migration/V141__create_mail_open_tracking.sql
```sql
CREATE TABLE mail_open_tracking (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    token CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    first_open_at DATETIME(6) NULL,
    last_open_at DATETIME(6) NULL,
    UNIQUE KEY uq_mail_open_tracking_token (token)
) ENGINE=InnoDB;

ALTER TABLE mail_record
    ADD COLUMN open_tracking_id BIGINT NULL,
    ADD UNIQUE KEY uq_mail_record_open_tracking (open_tracking_id),
    ADD CONSTRAINT fk_mail_record_open_tracking
        FOREIGN KEY (open_tracking_id) REFERENCES mail_open_tracking(id);

```

## src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql
```sql
-- 1. expert_contact 增加 ES 层级与人工待办标记
ALTER TABLE expert_contact
    ADD COLUMN current_index_level VARCHAR(16) NOT NULL DEFAULT 'CANDIDATE'
        COMMENT 'RAW / CANDIDATE / APPLICATION';

ALTER TABLE expert_contact
    ADD COLUMN needs_manual_attention BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '系统判定该 contact 需要人工介入，红条/badge 显隐依据';

-- 历史 application_indexed=true 的行回填为 APPLICATION
UPDATE expert_contact SET current_index_level = 'APPLICATION'
 WHERE application_indexed = TRUE;

-- CLOSED 状态清理：迁到 MANUAL_HANDOFF + 关掉自动回复 + 标红 + 转人工要求
UPDATE expert_contact
   SET current_status = 'MANUAL_HANDOFF',
       auto_reply_enabled = FALSE,
       needs_manual_attention = TRUE,
       manual_handoff_required = TRUE
 WHERE current_status = 'CLOSED';

-- 插入对应的 PENDING 手动派单工单，避免状态不一致
INSERT INTO manual_handoff (expert_contact_id, reason, handoff_status, assigned_to, note, created_at, updated_at)
SELECT ec.id, 'CLOSED_MIGRATION', 'PENDING', NULL, 'Migrated from CLOSED status', NOW(), NOW()
  FROM expert_contact ec
 WHERE ec.current_status = 'MANUAL_HANDOFF'
   AND ec.manual_handoff_required = TRUE
   AND NOT EXISTS (
       SELECT 1
         FROM manual_handoff mh
        WHERE mh.expert_contact_id = ec.id
          AND mh.handoff_status IN ('PENDING', 'ASSIGNED')
   );


-- 2. inbound_mail_processing 增加 reason_type 列，给前端按原因过滤用
ALTER TABLE inbound_mail_processing
    ADD COLUMN reason_type VARCHAR(32) NULL
        COMMENT 'UNMATCHED_CONTACT / QA_NO_MATCH / NOT_INTERESTED / UNCLEAR_INTENT / MANUAL_RESOLVED 等',
    ADD INDEX idx_imp_reason_type_status (reason_type, process_status);

-- 历史 MANUAL_REVIEW 行 backfill
UPDATE inbound_mail_processing
   SET reason_type = CASE
       WHEN process_status <> 'MANUAL_REVIEW' THEN reason_type
       WHEN expert_contact_id IS NULL THEN 'UNMATCHED_CONTACT'
       WHEN process_reason LIKE '%NOT_INTERESTED%' THEN 'NOT_INTERESTED'
       WHEN process_reason LIKE 'QA%' THEN 'QA_NO_MATCH'
       ELSE 'UNCLEAR_INTENT'
   END;

-- 3. qa_rule 增加 display_name
ALTER TABLE qa_rule
    ADD COLUMN display_name VARCHAR(120) NULL COMMENT '运营在 QA 管理页维护的中文显示名';

```

## src/main/resources/db/migration/V152__create_mailbox_reply_draft.sql
```sql
CREATE TABLE mailbox_reply_draft (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    expert_contact_id BIGINT NOT NULL,
    target_kind VARCHAR(8) COLLATE utf8mb4_bin NOT NULL,
    inbound_processing_id BIGINT NOT NULL,
    account_scope VARCHAR(100) COLLATE utf8mb4_bin NOT NULL DEFAULT '',
    version BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    subject VARCHAR(255) NULL,
    html_body LONGTEXT NULL,
    text_body LONGTEXT NULL,
    context_json LONGTEXT NULL,
    send_attempt_id BIGINT NULL,
    send_version BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mailbox_reply_draft_target (username, expert_contact_id, target_kind, inbound_processing_id, account_scope),
    KEY idx_mailbox_reply_draft_list (username, state, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

```

## src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql
```sql
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

```

## src/main/resources/db/migration/V166__create_expert_mail_read_access.sql
```sql
-- ============================================================================
-- V166 expert_mail_read_access：admin 授予内置 user1 的专家邮件查看权（01）
--
-- 关系即状态：行存在 = 允许查看该专家（expert_contact.id）的邮件；
-- 行不存在 = 禁止。不增加 enabled / role / read / send 等冗余列，也没有第二套
-- 状态词汇。主键固定为 (username, expert_contact_id)，username 只允许内置
-- `user1`（由 ExpertMailReadAccessService 固定写入，不来自请求体）。
--
-- granted_by 只记录当前授权的操作者（admin session），created_at = 首次授予时间；
-- 重复 PUT 不刷新，撤销后重新授予写新时间。撤销不保留历史审计（本步不建
-- audit_log 副作用）。
--
-- 唯一写者：ExpertMailReadAccessService（参数化 JDBC，INSERT ... ON DUPLICATE
-- KEY UPDATE / DELETE）。FK 级联是本次设计：专家联系人被删除时自动清理授权行。
-- 不设 admin_user 外键（允许账号开放前预先授权）；无初始 grant / 回填。
-- ============================================================================
CREATE TABLE expert_mail_read_access (
    username VARCHAR(64) NOT NULL,
    expert_contact_id BIGINT NOT NULL,
    granted_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (username, expert_contact_id),
    CONSTRAINT fk_expert_mail_read_access_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

```

## src/main/resources/db/migration/V167__scope_inbound_by_folder.sql
```sql
-- 所有旧接收路径只读 INBOX，默认值准确保留其来源；不回填 UIDVALIDITY。
ALTER TABLE mail_inbox_cursor
    ADD COLUMN folder VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'INBOX',
    DROP INDEX uk_mail_inbox_cursor_account,
    ADD UNIQUE KEY uk_mail_inbox_cursor_folder (sender_account_code, folder);

ALTER TABLE inbound_mail_processing
    ADD COLUMN folder VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'INBOX',
    DROP INDEX uk_inbound_mail_processing_owner_uid,
    DROP INDEX uk_inbound_mail_processing_uid_validity,
    ADD UNIQUE KEY uk_inbound_mail_processing_source (mailbox_owner_code, folder, uid_validity, imap_uid),
    ADD KEY idx_inbound_logical_uid (sender_account_code, uid_validity, imap_uid),
    ADD KEY idx_inbound_message_copy (sender_account_code, message_id);
-- 逻辑账号可从原邮箱和转发邮箱收信，不能再用逻辑账号约束物理 UID 唯一性。

```

## src/main/resources/db/migration/V1__create_business_tables.sql
```sql
CREATE TABLE mail_sender_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_code VARCHAR(64) NOT NULL UNIQUE,
    sender_email VARCHAR(255) NOT NULL,
    sender_name VARCHAR(128) NOT NULL,
    sender_title VARCHAR(128),
    sender_display_name VARCHAR(128),
    team_name VARCHAR(128),
    country_name VARCHAR(64),
    smtp_host VARCHAR(255) NOT NULL,
    smtp_port INT NOT NULL,
    smtp_username VARCHAR(255) NOT NULL,
    smtp_password VARCHAR(255) NOT NULL,
    imap_host VARCHAR(255) NOT NULL,
    imap_port INT NOT NULL,
    imap_username VARCHAR(255) NOT NULL,
    imap_password VARCHAR(255) NOT NULL,
    strategy_weight INT NOT NULL DEFAULT 100,
    daily_send_limit INT NOT NULL DEFAULT 100,
    today_sent_count INT NOT NULL DEFAULT 0,
    last_sent_at DATETIME,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE mail_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_code VARCHAR(64) NOT NULL UNIQUE,
    template_name VARCHAR(128) NOT NULL,
    subject VARCHAR(255),
    body TEXT NOT NULL,
    language VARCHAR(16) NOT NULL DEFAULT 'en',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE qa_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_code VARCHAR(64) NOT NULL UNIQUE,
    category_name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE qa_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    keywords TEXT NOT NULL,
    match_mode VARCHAR(16) NOT NULL DEFAULT 'ANY',
    priority INT NOT NULL DEFAULT 100,
    reply_subject VARCHAR(255),
    reply_body TEXT NOT NULL,
    auto_reply_enabled TINYINT(1) NOT NULL DEFAULT 1,
    handoff_required TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_qa_rule_category
        FOREIGN KEY (category_id) REFERENCES qa_category(id)
);

CREATE TABLE campaign (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_code VARCHAR(64) NOT NULL UNIQUE,
    campaign_name VARCHAR(128) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    sender_account_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_campaign_sender
        FOREIGN KEY (sender_account_id) REFERENCES mail_sender_account(id)
);

CREATE TABLE expert_contact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    orcid_id VARCHAR(64) NOT NULL,
    expert_email VARCHAR(255) NOT NULL,
    expert_name VARCHAR(255),
    current_status VARCHAR(64) NOT NULL DEFAULT 'NEW',
    last_mail_at DATETIME,
    last_reply_at DATETIME,
    manual_handoff_required TINYINT(1) NOT NULL DEFAULT 0,
    closed_reason VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_campaign_expert (campaign_id, orcid_id),
    CONSTRAINT fk_expert_contact_campaign
        FOREIGN KEY (campaign_id) REFERENCES campaign(id)
);

CREATE TABLE mail_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    mail_type VARCHAR(64) NOT NULL,
    message_id VARCHAR(255),
    in_reply_to VARCHAR(255),
    subject VARCHAR(255),
    body LONGTEXT,
    matched_qa_rule_id BIGINT,
    send_status VARCHAR(32),
    received_at DATETIME,
    sent_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mail_record_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
    CONSTRAINT fk_mail_record_rule
        FOREIGN KEY (matched_qa_rule_id) REFERENCES qa_rule(id)
);

CREATE TABLE manual_handoff (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    handoff_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    assigned_to VARCHAR(128),
    note TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_manual_handoff_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);

```

## src/main/resources/db/migration/V23__create_mail_send_attempt_and_add_mail_record_error.sql
```sql
CREATE TABLE mail_send_attempt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    orcid_id VARCHAR(100) NOT NULL,
    mail_type VARCHAR(50) NOT NULL,
    account_code VARCHAR(100) NOT NULL,
    message_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL COMMENT 'PREPARED, DELIVERY_IN_PROGRESS, DELIVERY_UNKNOWN, SENT, FAILED_SAFE_TO_RETRY',
    error_summary TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_orcid_mail_type (orcid_id, mail_type),
    UNIQUE KEY uq_msa_message_id (message_id)
);

ALTER TABLE mail_record ADD COLUMN error_summary VARCHAR(1024) DEFAULT NULL;
ALTER TABLE mail_record ADD COLUMN mail_send_attempt_id BIGINT DEFAULT NULL;
CREATE INDEX idx_mr_mail_send_attempt_id ON mail_record(mail_send_attempt_id);

```

## src/main/resources/db/migration/V24__extend_mail_send_attempt_state_and_link_mail_record.sql
```sql
-- Preflight must complete before any persistent DDL because MySQL ALTER TABLE
-- implicitly commits and cannot be rolled back by Flyway.
CREATE TEMPORARY TABLE v24_mail_record_ambiguous_attempts AS
SELECT msa.id AS mail_send_attempt_id
  FROM mail_send_attempt msa
  JOIN mail_record mr
    ON mr.message_id = msa.message_id
   AND mr.direction = 'OUTBOUND'
   AND mr.mail_type = 'INTRODUCTION'
 GROUP BY msa.id
HAVING COUNT(*) > 1;

CREATE TEMPORARY TABLE v24_mail_record_ambiguous_guard (id BIGINT PRIMARY KEY);
INSERT INTO v24_mail_record_ambiguous_guard (id) VALUES (1);
INSERT INTO v24_mail_record_ambiguous_guard (id)
SELECT 1 FROM v24_mail_record_ambiguous_attempts LIMIT 1;

ALTER TABLE mail_send_attempt
    ADD COLUMN recipient VARCHAR(255) DEFAULT NULL,
    ADD COLUMN subject VARCHAR(255) DEFAULT NULL,
    ADD COLUMN body LONGTEXT DEFAULT NULL,
    ADD COLUMN content_type VARCHAR(64) DEFAULT NULL,
    ADD COLUMN quota_counted TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN account_counted_at DATETIME DEFAULT NULL;

UPDATE mail_record mr
JOIN mail_send_attempt msa
  ON mr.message_id = msa.message_id
 AND mr.direction = 'OUTBOUND'
 AND mr.mail_type = 'INTRODUCTION'
SET mr.mail_send_attempt_id = msa.id
WHERE mr.mail_send_attempt_id IS NULL;

-- Drop the ordinary index from V23 since it's replaced by a UNIQUE constraint
DROP INDEX idx_mr_mail_send_attempt_id ON mail_record;

ALTER TABLE mail_record
    ADD CONSTRAINT uq_mail_record_send_attempt UNIQUE (mail_send_attempt_id),
    ADD CONSTRAINT fk_mail_record_send_attempt
        FOREIGN KEY (mail_send_attempt_id) REFERENCES mail_send_attempt(id);

UPDATE mail_send_attempt msa
JOIN mail_record mr
  ON mr.mail_send_attempt_id = msa.id
 AND mr.direction = 'OUTBOUND'
 AND mr.mail_type = 'INTRODUCTION'
 AND mr.send_status = 'SENT'
SET msa.quota_counted = 1,
    msa.account_counted_at = COALESCE(mr.sent_at, msa.updated_at)
WHERE msa.quota_counted = 0;

```

## src/main/resources/db/migration/V31__add_mail_record_created_at_index.sql
```sql
ALTER TABLE mail_record
    ADD INDEX idx_mail_record_status_created (direction, send_status, created_at);

```

## src/main/resources/db/migration/V5__create_inbound_mail_processing.sql
```sql
CREATE TABLE inbound_mail_processing (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_account_code VARCHAR(64) NOT NULL,
    imap_uid BIGINT NOT NULL,
    message_id VARCHAR(255),
    from_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    received_at DATETIME NOT NULL,
    process_status VARCHAR(32) NOT NULL,
    process_reason VARCHAR(64) NOT NULL,
    expert_contact_id BIGINT,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inbound_mail_processing_uid (sender_account_code, imap_uid),
    KEY idx_inbound_mail_processing_status (process_status, received_at),
    CONSTRAINT fk_inbound_mail_processing_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);

```

## src/main/resources/db/migration/V6__add_inbound_cleaning_and_intent.sql
```sql
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

```