ALTER TABLE task_execution
    ADD COLUMN batch_config_id BIGINT NULL;

CREATE INDEX idx_task_execution_batch_config_started
    ON task_execution (batch_config_id, started_at);

ALTER TABLE task_execution
    ADD CONSTRAINT fk_task_execution_batch_config
        FOREIGN KEY (batch_config_id) REFERENCES batch_send_task_config(id);
