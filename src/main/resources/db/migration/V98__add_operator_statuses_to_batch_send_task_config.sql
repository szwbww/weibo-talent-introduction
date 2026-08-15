-- I3a-7: operator_statuses_json 成为唯一事实源；operator_status 单值列在本迁移中删除。
-- 照 V93 的两步范式（TEXT 不能带 DEFAULT）。空数组 [] = 不限（与旧 operator_status IS NULL 等价）。
ALTER TABLE batch_send_task_config
    ADD COLUMN operator_statuses_json TEXT NOT NULL AFTER discipline;

UPDATE batch_send_task_config
SET operator_statuses_json = CASE
        WHEN operator_status IS NULL OR operator_status = '' THEN '[]'
        ELSE CONCAT('["', operator_status, '"]')
    END;

ALTER TABLE batch_send_task_config DROP COLUMN operator_status;
