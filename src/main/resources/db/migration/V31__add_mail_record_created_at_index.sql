ALTER TABLE mail_record
    ADD INDEX idx_mail_record_status_created (direction, send_status, created_at);
