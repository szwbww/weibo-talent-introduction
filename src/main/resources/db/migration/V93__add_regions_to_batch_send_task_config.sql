ALTER TABLE batch_send_task_config
    ADD COLUMN regions_json TEXT NOT NULL AFTER funnel_level;
UPDATE batch_send_task_config SET regions_json = '[]' WHERE regions_json = '';
