ALTER TABLE mail_sender_account
    ADD COLUMN auto_send_paused TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN auto_send_paused_reason VARCHAR(255) NULL,
    ADD COLUMN auto_send_paused_at DATETIME NULL;
