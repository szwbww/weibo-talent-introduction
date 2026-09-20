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
