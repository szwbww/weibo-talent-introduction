ALTER TABLE mail_sender_account
    ADD COLUMN warmup_enabled TINYINT(1) NULL DEFAULT NULL,
    ADD COLUMN warmup_started_at DATETIME NULL DEFAULT NULL,
    ADD COLUMN warmup_steps_json TEXT NULL DEFAULT NULL;
