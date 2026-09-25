CREATE TABLE mail_open_tracking (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    token CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    first_open_at DATETIME(6) NULL,
    last_open_at DATETIME(6) NULL,
    UNIQUE KEY uq_mail_open_tracking_token (token)
) ENGINE=InnoDB;

ALTER TABLE mail_record
    ADD COLUMN open_tracking_id BIGINT NULL,
    ADD UNIQUE KEY uq_mail_record_open_tracking (open_tracking_id),
    ADD CONSTRAINT fk_mail_record_open_tracking
        FOREIGN KEY (open_tracking_id) REFERENCES mail_open_tracking(id);
