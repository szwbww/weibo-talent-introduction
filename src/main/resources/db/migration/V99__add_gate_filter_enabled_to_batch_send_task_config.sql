-- I4a-1: 存量配置一律回填 FALSE，保证行为零漂移。
-- BOOLEAN 列可带 DEFAULT（与 TEXT 不同），故无需 V93 的两步范式。
ALTER TABLE batch_send_task_config
    ADD COLUMN gate_filter_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER template_id;
