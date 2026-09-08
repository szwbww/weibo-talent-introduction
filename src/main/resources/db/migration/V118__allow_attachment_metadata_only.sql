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
