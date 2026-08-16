-- 保留清理按 created_at 删除（不按 task_execution_id 关联，见计划 I3-1：
-- tryStartWithToken 落的初始化行 task_execution_id 为负值 pendingToken，
-- 关联删除会漏掉这些孤儿行）。V22 建表时无此索引。
CREATE INDEX idx_tpl_created_at ON task_progress_log (created_at);
