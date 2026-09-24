-- I-1: 发送前邮箱验证开关。存量任务一律回填 FALSE（不暗中开启已验证策略），
-- 新列 NOT NULL 且带默认值，旧客户端不传该字段时写入默认关闭。
ALTER TABLE batch_send_task_config
    ADD COLUMN email_verification_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER research_direction_filter;
