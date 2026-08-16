-- 关联邮件到产生它的任务执行。刻意不加外键：task_execution 有 90 天硬删除保留策略
-- （见 V102 / TaskAuditRetentionScheduler），加 FK 会阻塞清理。悬垂值由读取侧兜底。
ALTER TABLE mail_record
    ADD COLUMN task_execution_id BIGINT NULL;

CREATE INDEX idx_mail_record_task_execution
    ON mail_record (task_execution_id, id);
