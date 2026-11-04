# 已读迁移原文

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

## src/main/resources/db/migration/V42__mail_record_qa_rule.sql

```sql
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

```

## src/main/resources/db/migration/V101__add_task_execution_id_to_mail_record.sql

```sql
-- 关联邮件到产生它的任务执行。刻意不加外键：task_execution 有 90 天硬删除保留策略
-- （见 V102 / TaskAuditRetentionScheduler），加 FK 会阻塞清理。悬垂值由读取侧兜底。
ALTER TABLE mail_record
    ADD COLUMN task_execution_id BIGINT NULL;

CREATE INDEX idx_mail_record_task_execution
    ON mail_record (task_execution_id, id);

```

## src/main/resources/db/migration/V113__create_mail_record_rag_fact.sql

```sql
-- ============================================================================
-- V113 mail_record_rag_fact（plan 03b: 03b-rag-send-bridge.md）
--
-- RAG 回信发出后的事实存证表：与 mail_record_qa_rule 并列、互不干扰 ——
-- 一封信只会写其中一张（I-39：三条发送路径互斥，RAG 路径绝不写 mail_record_qa_rule）。
--
-- 关键不变量：
--   I-39  存证与旧表互斥：本表行存在 ⇔ 该信走 RAG 发送路径（fact_code 证据）。
--   I-41  corpus_fingerprint 记录发出时的语料版本，用于事后复盘「当时发的原文是哪一版」
--         （与 04 的 rag_fact_audit.fingerprint_before/after 闭环，D-16）。
--   I-42  ordinal 保存请求中 ragFactCodes 的原始顺序，不排序、不去重；
--         UNIQUE(mail_record_id, ordinal) 对齐 mail_record_qa_rule.ordinal 语义
--         （MailRecordQaRuleRepository.findByMailRecordIdOrderByOrdinalAsc）。
--   G-1   fact_code 是唯一业务标识；自增 id 只作分页/外键，绝不进入任何响应。
--
-- 不声明外键到 rag_fact / mail_record：事实可能被停用或改写，存证不应随之失效
-- （与 mail_record_qa_rule 不声明 ON DELETE CASCADE 的既有基线一致）；RAG 回信
-- 的 mail_record 生命周期独立于本表。
-- ============================================================================
CREATE TABLE mail_record_rag_fact (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    mail_record_id    BIGINT       NOT NULL COMMENT '已发出的 RAG 回信 mail_record.id',
    fact_code         VARCHAR(32)  NOT NULL COMMENT 'RAG 事实业务键（G-1），如 KB-FUND-033',
    ordinal           INT          NOT NULL COMMENT '请求中 ragFactCodes 的原始下标，不排序不去重（I-42）',
    corpus_fingerprint VARCHAR(64) NOT NULL COMMENT '发出时的语料指纹（I-41），用于复盘原文版本',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mail_record_rag_fact (mail_record_id, ordinal),
    KEY idx_mail_record_rag_fact_record (mail_record_id),
    KEY idx_mail_record_rag_fact_code (fact_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'RAG 回信的事实存证：与 mail_record_qa_rule 并列，一封信只会写其中一张（I-39）';

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

## src/main/resources/db/migration/V125__create_meeting_calendar_event.sql

```sql
CREATE TABLE meeting_calendar_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    source_mail_record_id BIGINT NULL,
    starts_at_utc DATETIME(6) NOT NULL,
    ends_at_utc DATETIME(6) NOT NULL,
    meeting_link VARCHAR(1024) NULL,
    note VARCHAR(200) NULL,
    cancel_reason VARCHAR(200) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_meeting_calendar_status_start_id (status, starts_at_utc, id),
    KEY idx_meeting_calendar_contact_status_start_id (expert_contact_id, status, starts_at_utc, id),
    UNIQUE KEY uk_meeting_calendar_source_mail (source_mail_record_id),
    CONSTRAINT fk_meeting_calendar_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE RESTRICT,
    CONSTRAINT fk_meeting_calendar_source_mail
        FOREIGN KEY (source_mail_record_id) REFERENCES mail_record(id) ON DELETE RESTRICT
);

```

## src/main/resources/db/migration/V126__create_outbound_mail_attachment.sql

```sql
-- ============================================================================
-- V126 outbound_mail_attachment（fast-p 04：人工回复通用附件上传原件）
--
-- 一行 = 一次成功上传的通用附件原件元数据。id 是服务端随机 UUID（CHAR(36)），
-- 同时就是磁盘文件名 outbound/<id>（不再落库第二份 storage_key）。元数据与原
-- 字节创建后不可变：本表只有 INSERT 与按 id 批量读取，没有 UPDATE/DELETE API，
-- 「移除草稿附件」只解除草稿引用（客户端引用），不删本行、不删已上传文件。
--
-- 关键不变量：
--   I-1 上传只写本表与 outbound/ 目录：不写 mail_attachment/expert_document，
--       不调用 SMTP、不建排期、不变更专家状态；UUID 由主键保证永不复用。
--   I-2 expert_contact_id 必须是当前已存在专家（FK ON DELETE RESTRICT）；
--       created_by 取会话登录名，请求体 operatorName 不参与身份。
--   I-3 无扩展名/MIME 白名单列；file_name 只存展示名（最多 255 字符），
--       byte_length 与 sha256 都是原件真实字节口径；0 字节合法。
--   I-5 发送快照 JSON 由 05 写入 mail_record 新列；本表既不存字节也不存快照。
--
-- 索引 (expert_contact_id, created_at)：按专家列出本人上传附件的稳定顺序。
-- 未被引用的上传文件本期保留原样（不引入状态列/租约/清理调度）。
-- ============================================================================
CREATE TABLE outbound_mail_attachment (
    id                CHAR(36)     NOT NULL,
    expert_contact_id BIGINT       NOT NULL,
    created_by        VARCHAR(100) NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    content_type      VARCHAR(255) NOT NULL,
    byte_length       BIGINT       NOT NULL,
    sha256            CHAR(64)     NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_outbound_mail_attachment_contact (expert_contact_id, created_at),
    CONSTRAINT fk_outbound_mail_attachment_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '人工回复通用附件上传原件元数据（fast-p 04）';

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

## src/main/resources/db/migration/V169__create_mailbox_review_draft.sql

```sql
CREATE TABLE mailbox_review_draft (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_username VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    expert_contact_id BIGINT NOT NULL,
    client_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_kind VARCHAR(8) NOT NULL,
    target_id BIGINT NOT NULL,
    account_code VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    version BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    html_body LONGTEXT NOT NULL,
    text_body LONGTEXT NOT NULL,
    submitted_at DATETIME(3) NULL,
    return_reason VARCHAR(2000) NULL,
    reviewed_by VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    reviewed_at DATETIME(3) NULL,
    approved_context_token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    send_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    send_attempt_id BIGINT NULL,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_draft_request (owner_username, client_request_id),
    KEY idx_review_draft_owner (owner_username, expert_contact_id, deleted_at, updated_at, id),
    KEY idx_review_draft_pending (state, deleted_at, account_code, expert_contact_id),
    CONSTRAINT chk_review_draft_state CHECK (state IN ('DRAFT','SUBMITTED','RETURNED','APPROVED')),
    CONSTRAINT chk_review_draft_version CHECK (version >= 1),
    CONSTRAINT chk_review_draft_target CHECK (target_kind IN ('INBOUND','OUTBOUND')),
    CONSTRAINT chk_review_draft_approval CHECK (
        (state = 'APPROVED' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND approved_context_token IS NOT NULL AND send_request_id IS NOT NULL)
        OR (state <> 'APPROVED' AND reviewed_by IS NULL AND reviewed_at IS NULL AND approved_context_token IS NULL AND send_request_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Deliberately no contact/account FK: deleting existing business data must retain audit history.
CREATE TABLE mailbox_review_draft_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    draft_id BIGINT NOT NULL,
    draft_version BIGINT NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    actor_username VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    payload_json LONGTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_review_draft_event_cursor (draft_id, id),
    CONSTRAINT fk_review_draft_event FOREIGN KEY (draft_id) REFERENCES mailbox_review_draft(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

```