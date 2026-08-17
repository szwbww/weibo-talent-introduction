-- I-6-5: 新列默认「不过滤」（NULL），存量配置升级后投放范围零漂移。
-- VARCHAR 可带 NULL 默认值，无需两步范式；存量行自动为 NULL = 不过滤。
ALTER TABLE batch_send_task_config
    ADD COLUMN reachability_filter VARCHAR(32) NULL COMMENT '可达性过滤模式：NULL=不过滤';
