ALTER TABLE batch_send_task_config
    ADD COLUMN exclude_verified_unavailable_emails BOOLEAN NOT NULL DEFAULT FALSE;
