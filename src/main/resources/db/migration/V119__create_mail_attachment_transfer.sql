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
