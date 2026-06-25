ALTER TABLE task_progress_log
    ADD COLUMN batch_reject_reasons_json TEXT NULL COMMENT '本批次拒绝原因明细(JSON: reason->count)';
