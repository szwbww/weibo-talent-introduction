-- I2a-1: email_domains_json 成为唯一事实源；email_domain 单值列在本迁移中删除，避免双事实源。
-- TEXT 列不能带 DEFAULT（MySQL 限制），故照 V93__add_regions_to_batch_send_task_config.sql
-- 的两步范式：先 ADD NOT NULL，再 UPDATE 兜底。
-- I2a-2: 空数组 [] = 不限（与旧 email_domain IS NULL / '' 等价）。
ALTER TABLE batch_send_task_config
    ADD COLUMN email_domains_json TEXT NOT NULL AFTER regions_json;

UPDATE batch_send_task_config
SET email_domains_json = CASE
        WHEN email_domain IS NULL OR email_domain = '' THEN '[]'
        ELSE CONCAT('["', email_domain, '"]')
    END;

ALTER TABLE batch_send_task_config DROP COLUMN email_domain;
