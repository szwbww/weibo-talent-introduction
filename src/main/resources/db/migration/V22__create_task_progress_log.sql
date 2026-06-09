CREATE TABLE task_progress_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_type VARCHAR(64) NOT NULL,
    task_execution_id BIGINT,
    batch_number INT NOT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'RUNNING, COMPLETED, FAILED, CANCELLED',
    processed_count BIGINT NOT NULL DEFAULT 0,
    total_count BIGINT NOT NULL DEFAULT 0,
    batch_processed INT NOT NULL DEFAULT 0 COMMENT '本批次处理数量',
    batch_passed INT NOT NULL DEFAULT 0 COMMENT '本批次通过/成功数量',
    batch_rejected INT NOT NULL DEFAULT 0 COMMENT '本批次拒绝/降级数量',
    message TEXT,
    details_json TEXT COMMENT 'JSON 格式的详细统计',
    errors_json TEXT COMMENT 'JSON 格式的错误列表',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tpl_task_type ON task_progress_log(task_type);
CREATE INDEX idx_tpl_execution_id ON task_progress_log(task_execution_id);
