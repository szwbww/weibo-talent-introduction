-- I-1/I-3: 批量任务研究方向三态（ANY/PRESENT/ABSENT）。
-- 存量任务一律回填默认值 ANY（不限），升级不改动旧任务收件范围（M-3）。
-- VARCHAR 列可带 DEFAULT（无需 V98/V108 的两步范式）；三态白名单的权威在
-- BatchSendTaskConfigService（ResearchDirectionFilters.ALLOWED），此处不建 CHECK 约束。
ALTER TABLE batch_send_task_config
    ADD COLUMN research_direction_filter VARCHAR(16) NOT NULL DEFAULT 'ANY' AFTER gate_filter_enabled;
