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
