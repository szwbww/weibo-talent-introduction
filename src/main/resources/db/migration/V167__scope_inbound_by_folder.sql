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
