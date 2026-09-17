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
