-- 通知也按完整物理来源去重，避免 INBOX/Junk 的相同 UID 互相覆盖。
ALTER TABLE expert_inbound_notification_outbox
    ADD COLUMN folder VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'INBOX',
    DROP INDEX uk_expert_inbound_notification_physical,
    ADD UNIQUE KEY uk_expert_inbound_notification_physical (mailbox_owner_code, folder, uid_validity, imap_uid);
