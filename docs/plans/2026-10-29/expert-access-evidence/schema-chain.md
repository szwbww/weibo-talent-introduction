## src/main/resources/db/migration/V10__create_expert_email_alias_and_extend_unmatched_mail.sql
```text
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

## src/main/resources/db/migration/V118__allow_attachment_metadata_only.sql
```text
-- 允许附件仅登记名称（元数据模式）：
--   file_size   NULL = 尚未取得真实字节数（真实零字节文件为 0，不用 NULL 表示）
--   storage_path NULL = 没有可用本地文件
--   file_name   放宽为 TEXT，长文件名（如 300 字中文名）登记不再失败
-- 保留 V36 的 owner XOR CHECK 与外键、全部历史数据；不新增状态列。
ALTER TABLE mail_attachment
    MODIFY file_size BIGINT NULL DEFAULT NULL;

ALTER TABLE mail_attachment
    MODIFY storage_path VARCHAR(1024) NULL;

ALTER TABLE mail_attachment
    MODIFY file_name TEXT NOT NULL;

```

## src/main/resources/db/migration/V119__create_mail_attachment_transfer.sql
```text
-- ============================================================================
-- V119 mail_attachment_transfer（fast-p 02：有界、持久化附件传输服务）
--
-- 一行 = 一个远端 part（account/folder/UIDVALIDITY/UID/part_path）与该 part 的
-- 传输状态；不再另建任务表/索引表。完整字段/状态/索引契约见总计划
-- 00-mailbox-materials-master.md「持久化契约（02，新表）」。
--
-- 关键不变量：
--   I-1 来源唯一身份 = 唯一键 (account_code, folder, uid_validity, imap_uid,
--       part_path)；attachment_id UNIQUE 且只允许 NULL 一行（DMARC 报表无附件）；
--       folder 列级 utf8mb4_bin，区分大小写。
--   I-2 state 仅 METADATA_ONLY/QUEUED/DOWNLOADING/STORED/FAILED/
--       SOURCE_UNAVAILABLE；CHECK 约束 purpose 与 attachment_id 组合
--       （MATERIAL 必须 attachment_id，DMARC 必须为空且不建立假来信关联）。
--   I-3 lease_until/worker_token 仅 DOWNLOADING 有值；所有 worker 提交按
--       id+token+租约 CAS（代码层 WHERE 完成），本迁移不引入触发器。
--   I-4 下载字节数 bytes_downloaded 与 attempt 是本次尝试口径；错误列只存脱敏
--       原因（不记密码/邮件正文）；created_at/updated_at DATETIME(3)。
--
-- 队列索引 (state, queued_at, id)；账号活动索引 (account_code, state,
-- lease_until)；processing 索引 (inbound_processing_id)。MATERIAL 的
-- inbound_processing_id 不设必填：必须兼容同事务先建附件再建 transfer 的顺序，
-- 完整性由 04 登记终结时校验。
-- ============================================================================
CREATE TABLE mail_attachment_transfer (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    attachment_id         BIGINT        NULL,
    purpose               VARCHAR(16)   NOT NULL,
    account_code          VARCHAR(64)   NOT NULL,
    folder                VARCHAR(255)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    uid_validity          BIGINT        NOT NULL,
    imap_uid              BIGINT        NOT NULL,
    part_path             VARCHAR(255)  NOT NULL,
    message_id            VARCHAR(255)  NULL,
    file_name             TEXT          NOT NULL,
    content_type          VARCHAR(255)  NULL,
    encoded_size          BIGINT        NULL,
    disposition           VARCHAR(32)   NULL,
    inbound_processing_id BIGINT        NULL,
    state                 VARCHAR(24)   NOT NULL DEFAULT 'METADATA_ONLY',
    requested_by          VARCHAR(64)   NULL,
    queued_at             DATETIME(3)   NULL,
    started_at            DATETIME(3)   NULL,
    lease_until           DATETIME(3)   NULL,
    worker_token          VARCHAR(36)   NULL,
    bytes_downloaded      BIGINT        NOT NULL DEFAULT 0,
    attempt               INT           NOT NULL DEFAULT 0,
    error_code            VARCHAR(64)   NULL,
    error_message         VARCHAR(500)  NULL,
    created_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mail_attachment_transfer_source
        (account_code, folder, uid_validity, imap_uid, part_path),
    UNIQUE KEY uk_mail_attachment_transfer_attachment (attachment_id),
    KEY idx_mail_attachment_transfer_queue (state, queued_at, id),
    KEY idx_mail_attachment_transfer_account (account_code, state, lease_until),
    KEY idx_mail_attachment_transfer_inbound (inbound_processing_id),
    CONSTRAINT fk_mail_attachment_transfer_attachment
        FOREIGN KEY (attachment_id) REFERENCES mail_attachment(id),
    CONSTRAINT fk_mail_attachment_transfer_inbound
        FOREIGN KEY (inbound_processing_id) REFERENCES inbound_mail_processing(id),
    CONSTRAINT chk_mail_attachment_transfer_purpose
        CHECK (purpose IN ('MATERIAL', 'DMARC')),
    CONSTRAINT chk_mail_attachment_transfer_state
        CHECK (state IN ('METADATA_ONLY', 'QUEUED', 'DOWNLOADING', 'STORED', 'FAILED', 'SOURCE_UNAVAILABLE')),
    CONSTRAINT chk_mail_attachment_transfer_purpose_attachment
        CHECK ((purpose = 'MATERIAL' AND attachment_id IS NOT NULL)
               OR (purpose = 'DMARC' AND attachment_id IS NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '远端附件 part 传输登记与有界下载状态（fast-p 02）';

```

## src/main/resources/db/migration/V120__scope_inbound_uid_by_validity.sql
```text
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

## src/main/resources/db/migration/V126__create_outbound_mail_attachment.sql
```text
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

## src/main/resources/db/migration/V130__add_manual_expert_material_upload.sql
```text
-- ============================================================================
-- V130 专家材料手动上传（fast-p manual-expert-material-upload backend 阶段 1）
--
-- 只做三件事，全部是加法，不改写任何历史行：
--   1. 新建上传来源表 manual_expert_material_upload：一行 = 一次成功的手动上传，
--      只存归属（expert_contact_id）、操作者（uploaded_by）与上传时间（created_at）；
--      不存文件路径、文件名、重复状态——文件名/大小/磁盘路径的唯一权威仍是
--      mail_attachment，材料类型/审核状态仍是 expert_document。
--   2. mail_attachment 新增唯一可空 manual_upload_id，成为第三个附件所有者
--      （owner 链：manual_upload_id → manual_expert_material_upload.expert_contact_id）。
--      MySQL 唯一索引允许多个 NULL，故既有 mail_record/inbound_processing 所有者行不受影响。
--   3. 把 V36 的「二选一」owner CHECK 换成「三选一」CHECK。
--
-- 不改 mail_record_id/inbound_processing_id 的读写语义，不建 transfer 行，
-- 不 UPDATE/回填任何历史附件（历史行 manual_upload_id 保持 NULL，owner 仍恰好一个）。
--
-- MySQL 兼容性（与 V129 同款，见 V129 头部说明）：生产库为 MySQL 5.7.41，命名 CHECK 根本
-- 不落地，而 `ALTER TABLE ... DROP CHECK` 是 8.0.16+ 语法，在 5.7 上直接 E1064 语法错误并
-- 中止发布。因此这里先用 TABLE_CONSTRAINTS（5.7/8.0 都有该视图）探测旧约束是否真实存在，
-- 仅在存在时动态 DROP；ADD CONSTRAINT ... CHECK 沿用 V36/V129 写法（5.7 解析并忽略，
-- 8.0.16+ 真实生效）。三选一谓词用 IS NOT NULL 的布尔和，恒为 0/1，不产生 NULL 语义歧义。
-- ============================================================================

CREATE TABLE manual_expert_material_upload (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    uploaded_by VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_manual_material_upload_contact (expert_contact_id, created_at, id),
    CONSTRAINT fk_manual_material_upload_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
);

ALTER TABLE mail_attachment
    ADD COLUMN manual_upload_id BIGINT NULL;

ALTER TABLE mail_attachment
    ADD CONSTRAINT uk_mail_attachment_manual_upload UNIQUE (manual_upload_id);

ALTER TABLE mail_attachment
    ADD CONSTRAINT fk_mail_attachment_manual_upload
        FOREIGN KEY (manual_upload_id) REFERENCES manual_expert_material_upload(id);

SET @has_mail_attachment_owner_check := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'mail_attachment'
      AND CONSTRAINT_NAME = 'chk_mail_attachment_owner'
      AND CONSTRAINT_TYPE = 'CHECK'
);

SET @drop_mail_attachment_owner_check := IF(
    @has_mail_attachment_owner_check > 0,
    'ALTER TABLE mail_attachment DROP CHECK chk_mail_attachment_owner',
    'SELECT 1'
);

PREPARE drop_mail_attachment_owner_check_stmt FROM @drop_mail_attachment_owner_check;
EXECUTE drop_mail_attachment_owner_check_stmt;
DEALLOCATE PREPARE drop_mail_attachment_owner_check_stmt;

ALTER TABLE mail_attachment
    ADD CONSTRAINT chk_mail_attachment_owner
        CHECK (((mail_record_id IS NOT NULL)
            + (inbound_processing_id IS NOT NULL)
            + (manual_upload_id IS NOT NULL)) = 1);

```

## src/main/resources/db/migration/V134__shared_inbox_owner.sql
```text
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

## src/main/resources/db/migration/V13__merge_manual_review_into_handoff.sql
```text
-- Merge MANUAL_REVIEW conversation status into MANUAL_HANDOFF.
-- After this migration, MANUAL_REVIEW is no longer a valid contact status.
-- The MANUAL_REVIEW value used by inbound_mail_processing.process_status is a
-- different concept (mail processing pipeline status) and is intentionally left
-- untouched.

UPDATE expert_contact
SET current_status = 'MANUAL_HANDOFF'
WHERE current_status = 'MANUAL_REVIEW';

UPDATE expert_contact_status_history
SET to_status = 'MANUAL_HANDOFF'
WHERE to_status = 'MANUAL_REVIEW';

UPDATE expert_contact_status_history
SET from_status = 'MANUAL_HANDOFF'
WHERE from_status = 'MANUAL_REVIEW';

-- Every contact currently sitting in MANUAL_HANDOFF should have at least one
-- open manual_handoff ticket so the "完成人工" workflow can close it. Back-fill
-- a synthetic PENDING ticket for contacts that have no open ticket (typically
-- contacts that were auto-routed into MANUAL_REVIEW before the merge and
-- therefore never had a ticket created).
INSERT INTO manual_handoff
    (expert_contact_id, reason, handoff_status, assigned_to, note, created_at, updated_at)
SELECT
    ec.id,
    'MIGRATED_FROM_MANUAL_REVIEW',
    'PENDING',
    NULL,
    'Backfilled by V13 migration when MANUAL_REVIEW was merged into MANUAL_HANDOFF.',
    NOW(),
    NOW()
FROM expert_contact ec
WHERE ec.current_status = 'MANUAL_HANDOFF'
  AND NOT EXISTS (
      SELECT 1
      FROM manual_handoff mh
      WHERE mh.expert_contact_id = ec.id
        AND mh.handoff_status IN ('PENDING', 'ASSIGNED')
  );

```

## src/main/resources/db/migration/V14__contact_index_level_and_reason_type_and_qa_display_name.sql
```text
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

## src/main/resources/db/migration/V153__create_expert_manual_profile.sql
```text
-- 01（I-1–I-23）：专家人工档案独立私有表。
-- 只由 ExpertManualProfileService 读写；不加 contact 外键（未联系专家也要能存），不加全局人才编号唯一约束。
-- expert_key 为 ExpertIdNormalizer.normalize(trim(expertId))；utf8mb4_bin 保证大小写敏感的身份比较。
CREATE TABLE expert_manual_profile (
    expert_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    talent_code VARCHAR(64) NULL,
    chinese_name VARCHAR(200) NULL,
    birth_date_text VARCHAR(10) NULL,
    document_number VARCHAR(100) NULL,
    source_country VARCHAR(100) NULL,
    graduated_school TEXT NULL,
    school_ranking VARCHAR(500) NULL,
    pre_china_organization TEXT NULL,
    organization_ranking VARCHAR(500) NULL,
    postdoc_organization TEXT NULL,
    postdoc_period VARCHAR(200) NULL,
    professional_title VARCHAR(200) NULL,
    administrative_titles TEXT NULL,
    industry VARCHAR(500) NULL,
    technical_expertise TEXT NULL,
    program_categories VARCHAR(500) NULL,
    research_ability VARCHAR(16) NULL,
    company_match_status VARCHAR(16) NOT NULL DEFAULT 'NOT_MATCHED',
    matched_companies TEXT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (expert_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

```

## src/main/resources/db/migration/V154__create_expert_meeting_video.sql
```text
-- 04（I-3/I-4/I-5/I-10）：会议视频独立私有表。
-- 只由 ExpertMeetingVideoService 读写：不写 manual_expert_material_upload / mail_attachment /
-- expert_document / expert_material_status，也不加 contact 外键（未联系专家同样可上传）。
-- id 是服务端 UUID，磁盘文件只用它命名（客户端文件名永不参与拼路径）；
-- original_filename 不可变，扩展名/内容类型由它派生，不加冗余 storagePath/状态/哈希列。
-- expert_key 为 ExpertIdNormalizer.normalize(trim(expertId))；utf8mb4_bin 保证同一专家的
-- display_filename 大小写敏感唯一（跨专家允许同名），唯一索引是最终裁决。
-- 注意：本文件不得出现 Flyway 占位符语法（${...}），生产未关闭 placeholder-replacement。
CREATE TABLE expert_meeting_video (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expert_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    display_filename VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    byte_length BIGINT NOT NULL,
    uploaded_by VARCHAR(64) NOT NULL,
    uploaded_at DATETIME(3) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_expert_meeting_video_name (expert_key, display_filename),
    KEY idx_expert_meeting_video_list (expert_key, uploaded_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

```

## src/main/resources/db/migration/V15__add_mail_monitoring_columns_and_promotion_audit.sql
```text
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
```text
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
```text
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

## src/main/resources/db/migration/V19__add_operator_status_and_action_log.sql
```text
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

```

## src/main/resources/db/migration/V36__add_mail_attachment_inbound_processing_link.sql
```text
ALTER TABLE mail_attachment
    ADD COLUMN inbound_processing_id BIGINT NULL;

ALTER TABLE mail_attachment
    MODIFY mail_record_id BIGINT NULL;

ALTER TABLE mail_attachment
    ADD KEY idx_mail_attachment_inbound (inbound_processing_id, created_at);

ALTER TABLE mail_attachment
    ADD CONSTRAINT fk_mail_attachment_inbound
        FOREIGN KEY (inbound_processing_id) REFERENCES inbound_mail_processing(id);

ALTER TABLE mail_attachment
    ADD CONSTRAINT chk_mail_attachment_owner
        CHECK ((mail_record_id IS NULL) <> (inbound_processing_id IS NULL));

```

## src/main/resources/db/migration/V53__inbound_mail_tag.sql
```text
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

```

## src/main/resources/db/migration/V5__create_inbound_mail_processing.sql
```text
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

## src/main/resources/db/migration/V7__create_mail_attachment_and_expert_document.sql
```text
CREATE TABLE mail_attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mail_record_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    file_size BIGINT NOT NULL DEFAULT 0,
    storage_path VARCHAR(1024) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_mail_attachment_record (mail_record_id, created_at),
    CONSTRAINT fk_mail_attachment_record
        FOREIGN KEY (mail_record_id) REFERENCES mail_record(id)
);

CREATE TABLE expert_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    mail_attachment_id BIGINT NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    document_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
    review_note TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_expert_document_contact (expert_contact_id, document_type, document_status),
    CONSTRAINT fk_expert_document_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
    CONSTRAINT fk_expert_document_attachment
        FOREIGN KEY (mail_attachment_id) REFERENCES mail_attachment(id)
);

```

## src/main/resources/db/migration/V9__create_meeting_schedule_and_template.sql
```text
CREATE TABLE meeting_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    source_mail_record_id BIGINT,
    expert_available_text VARCHAR(1024),
    expert_timezone VARCHAR(64),
    china_time VARCHAR(255),
    meeting_tool VARCHAR(64),
    meeting_link VARCHAR(1024),
    meeting_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    note TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_meeting_schedule_contact (expert_contact_id),
    CONSTRAINT fk_meeting_schedule_contact FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id),
    CONSTRAINT fk_meeting_schedule_mail FOREIGN KEY (source_mail_record_id) REFERENCES mail_record(id)
);

INSERT INTO mail_template (
    template_code,
    template_name,
    subject,
    body,
    language,
    enabled
) VALUES (
    'MEETING_CONFIRMATION',
    'Meeting Confirmation Email',
    'Meeting Confirmed: Research Collaboration Discussion',
    'Dear Professor,

Thank you for your response and availability.

I am pleased to confirm our meeting details:
Time: ${meetingTime} (China Time)
Meeting Tool: ${meetingTool}
Meeting Link: ${meetingLink}

If you have any issues connecting, please reply to this email. Looking forward to speaking with you soon!

Sincerely,
${senderName}, ${senderTitle}
${teamName}',
    'en',
    1
)
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name),
    subject = VALUES(subject),
    body = VALUES(body),
    language = VALUES(language),
    enabled = VALUES(enabled);

```

## src/main/resources/es/orcid_info_application.json
```text
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 1
  },
  "mappings": {
    "dynamic": false,
    "properties": {
      "orcidId": { "type": "keyword" },
      "orcid": { "type": "keyword" },
      "id": { "type": "keyword" },
      "email": { "type": "keyword" },
      "givenNames": { "type": "text" },
      "familyNames": { "type": "text" },
      "country": { "type": "keyword" },
      "keyword": { "type": "text" },
      "employment": { "type": "text" },
      "age": { "type": "integer" },
      "degree": { "type": "keyword" },
      "nationality": { "type": "keyword" },
      "candidateValidatedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
      "currentConversationStatus": { "type": "keyword" },
      "applicationStatus": { "type": "keyword" },
      "autoReplyEnabled": { "type": "boolean" },
      "firstReplyAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
      "lastReplyAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
      "expertContactId": { "type": "long" },
      "campaignId": { "type": "long" },
      "promotionSource": { "type": "keyword" },
      "applicationPromotedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
      "updatedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
      "latestInboundIntent": { "type": "keyword" },
      "hIndex": { "type": "integer" },
      "citationCount": { "type": "integer" },
      "lastPublicationYear": { "type": "integer" },
      "researchFields": { "type": "keyword" },
      "researchFieldIds": { "type": "keyword" },
      "disciplineCategory": { "type": "keyword" },
      "institution": { "type": "text" },
      "institutionType": { "type": "keyword" },
      "institutionEvidence": { "type": "keyword" },
      "emailSource": { "type": "keyword" },
      "emailVerifiedLevel": { "type": "integer" },
      "dataSource": { "type": "keyword" },
      "identityVerification": { "type": "object", "dynamic": false, "properties": {
        "status": { "type": "keyword" }, "version": { "type": "integer" },
        "email": { "type": "keyword", "index": false }, "givenNames": { "type": "keyword", "index": false },
        "familyNames": { "type": "keyword", "index": false }, "source": { "type": "keyword" },
        "evidenceHash": { "type": "keyword", "index": false }, "orcid": { "type": "keyword", "index": false },
        "openAlexAuthorId": { "type": "keyword", "index": false }
      } },
      "externalIds": { "type": "object", "enabled": false },
      "worksCount": { "type": "integer" },
      "discoveredAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
      "filterResult": { "type": "keyword" },
      "filterRejectReason": { "type": "keyword" },
      "tags": { "type": "keyword" },
      "operatorStatus": { "type": "keyword" },
      "recentWorkTitles": { "type": "keyword" },
      "patentTitles": { "type": "keyword" },
      "enrichedAt": { "type": "keyword" },
      "enrichmentSource": { "type": "keyword" },
      "expertClassification": {
        "type": "object",
        "properties": {
          "type": { "type": "keyword" },
          "sendable": { "type": "boolean" },
          "productionScore": { "type": "integer" },
          "researchScore": { "type": "integer" },
          "positiveEvidence": { "type": "keyword" },
          "negativeEvidence": { "type": "keyword" },
          "version": { "type": "keyword" },
          "sourceFingerprint": { "type": "keyword" },
          "classifiedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" }
        }
      }
    }
  }
}

```

## src/main/resources/es/orcid_info_candidate.json
```text
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 1
  },
  "mappings": {
    "dynamic": false,
    "properties": {
      "orcidId": { "type": "keyword" },
      "orcid": { "type": "keyword" },
      "id": { "type": "keyword" },
      "email": { "type": "keyword" },
      "givenNames": { "type": "keyword" },
      "familyNames": { "type": "keyword" },
      "country": { "type": "keyword" },
      "keyword": { "type": "keyword" },
      "employment": { "type": "keyword" },
      "age": { "type": "integer" },
      "degree": { "type": "keyword" },
      "nationality": { "type": "keyword" },
      "candidateValidatedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
      "hIndex": { "type": "integer" },
      "citationCount": { "type": "integer" },
      "lastPublicationYear": { "type": "integer" },
      "researchFields": { "type": "keyword" },
      "researchFieldIds": { "type": "keyword" },
      "disciplineCategory": { "type": "keyword" },
      "institution": { "type": "text" },
      "institutionType": { "type": "keyword" },
      "institutionEvidence": { "type": "keyword" },
      "emailSource": { "type": "keyword" },
      "emailVerifiedLevel": { "type": "integer" },
      "dataSource": { "type": "keyword" },
      "identityVerification": { "type": "object", "dynamic": false, "properties": {
        "status": { "type": "keyword" }, "version": { "type": "integer" },
        "email": { "type": "keyword", "index": false }, "givenNames": { "type": "keyword", "index": false },
        "familyNames": { "type": "keyword", "index": false }, "source": { "type": "keyword" },
        "evidenceHash": { "type": "keyword", "index": false }, "orcid": { "type": "keyword", "index": false },
        "openAlexAuthorId": { "type": "keyword", "index": false }
      } },
      "externalIds": { "type": "object", "enabled": false },
      "worksCount": { "type": "integer" },
      "discoveredAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
      "updatedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
      "filterResult": { "type": "keyword" },
      "filterRejectReason": { "type": "keyword" },
      "tags": { "type": "keyword" },
      "operatorStatus": { "type": "keyword" },
      "recentWorkTitles": { "type": "keyword" },
      "patentTitles": { "type": "keyword" },
      "enrichedAt": { "type": "keyword" },
      "enrichmentSource": { "type": "keyword" },
      "expertClassification": {
        "type": "object",
        "properties": {
          "type": { "type": "keyword" },
          "sendable": { "type": "boolean" },
          "productionScore": { "type": "integer" },
          "researchScore": { "type": "integer" },
          "positiveEvidence": { "type": "keyword" },
          "negativeEvidence": { "type": "keyword" },
          "version": { "type": "keyword" },
          "sourceFingerprint": { "type": "keyword" },
          "classifiedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" }
        }
      }
    }
  }
}

```

## src/main/resources/es/orcid_info_raw.json
```text
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 1
  },
  "mappings": {
    "dynamic": false,
    "properties": {
      "orcidId": { "type": "keyword" },
      "orcid": { "type": "keyword" },
      "id": { "type": "keyword" },
      "email": { "type": "keyword" },
      "givenNames": { "type": "keyword" },
      "familyNames": { "type": "keyword" },
      "country": { "type": "keyword" },
      "keyword": { "type": "keyword" },
      "employment": { "type": "keyword" },
      "age": { "type": "integer" },
      "degree": { "type": "keyword" },
      "nationality": { "type": "keyword" },
      "hIndex": { "type": "integer" },
      "citationCount": { "type": "integer" },
      "lastPublicationYear": { "type": "integer" },
      "researchFields": { "type": "keyword" },
      "researchFieldIds": { "type": "keyword" },
      "disciplineCategory": { "type": "keyword" },
      "institution": { "type": "text" },
      "institutionType": { "type": "keyword" },
      "institutionEvidence": { "type": "keyword" },
      "emailSource": { "type": "keyword" },
      "emailVerifiedLevel": { "type": "integer" },
      "dataSource": { "type": "keyword" },
      "identityVerification": { "type": "object", "dynamic": false, "properties": {
        "status": { "type": "keyword" }, "version": { "type": "integer" },
        "email": { "type": "keyword", "index": false }, "givenNames": { "type": "keyword", "index": false },
        "familyNames": { "type": "keyword", "index": false }, "source": { "type": "keyword" },
        "evidenceHash": { "type": "keyword", "index": false }, "orcid": { "type": "keyword", "index": false },
        "openAlexAuthorId": { "type": "keyword", "index": false }
      } },
      "externalIds": { "type": "object", "enabled": false },
      "worksCount": { "type": "integer" },
      "discoveredAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
      "filterResult": { "type": "keyword" },
      "filterRejectReason": { "type": "keyword" },
      "tags": { "type": "keyword" },
      "recentWorkTitles": { "type": "keyword" },
      "patentTitles": { "type": "keyword" },
      "enrichedAt": { "type": "keyword" },
      "enrichmentSource": { "type": "keyword" },
      "expertClassification": {
        "type": "object",
        "properties": {
          "type": { "type": "keyword" },
          "sendable": { "type": "boolean" },
          "productionScore": { "type": "integer" },
          "researchScore": { "type": "integer" },
          "positiveEvidence": { "type": "keyword" },
          "negativeEvidence": { "type": "keyword" },
          "version": { "type": "keyword" },
          "sourceFingerprint": { "type": "keyword" },
          "classifiedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" }
        }
      },
      "updatedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" }
    }
  }
}

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

## src/main/resources/db/migration/V146__create_expert_contact_location.sql
```sql
CREATE TABLE expert_contact_location (
    expert_contact_id BIGINT NOT NULL,
    country_code CHAR(2) NOT NULL,
    zone_id VARCHAR(64) NULL,
    PRIMARY KEY (expert_contact_id),
    CONSTRAINT fk_expert_contact_location_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='人工配置的联系人所在地；zone_id为空表示使用国家默认时区';

```

## src/main/resources/db/migration/V150__create_expert_contact_note.sql
```sql
CREATE TABLE expert_contact_note (
    expert_contact_id BIGINT NOT NULL,
    note VARCHAR(2000) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (expert_contact_id),
    CONSTRAINT fk_expert_contact_note_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
