-- I2-4: expert_types_json 是唯一事实源；空数组 [] = 不限（与「不追加 filter」等价）。
-- 照 V98 两步范式：TEXT 列不能带 DEFAULT。
ALTER TABLE batch_send_task_config
    ADD COLUMN expert_types_json TEXT NOT NULL AFTER operator_statuses_json;

UPDATE batch_send_task_config SET expert_types_json = '[]';
