ALTER TABLE task_execution
    ADD COLUMN owner_token VARCHAR(64) NULL,
    ADD COLUMN heartbeat_at DATETIME NULL,
    ADD COLUMN interruption_reason_code VARCHAR(64) NULL,
    ADD COLUMN interruption_reason_detail VARCHAR(1000) NULL,
    ADD COLUMN handled_by VARCHAR(128) NULL,
    ADD COLUMN handled_at DATETIME NULL;

CREATE INDEX idx_te_active_heartbeat ON task_execution (status, heartbeat_at);
