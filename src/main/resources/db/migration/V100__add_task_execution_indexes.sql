-- 任务记录列表：ORDER BY started_at DESC 以及按类型/状态筛选，
-- 在 V4 建表时均无索引可用，13k+ 行已导致全表扫 + filesort。
CREATE INDEX idx_te_started ON task_execution (started_at);
CREATE INDEX idx_te_type_started ON task_execution (task_type, started_at);
CREATE INDEX idx_te_status_started ON task_execution (status, started_at);
