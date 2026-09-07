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
