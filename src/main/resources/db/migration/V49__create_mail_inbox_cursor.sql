CREATE TABLE mail_inbox_cursor (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_account_code  VARCHAR(64) NOT NULL,
    uid_validity         BIGINT      NOT NULL,
    last_uid             BIGINT      NOT NULL DEFAULT 0,
    updated_at           DATETIME    NOT NULL,
    UNIQUE KEY uk_mail_inbox_cursor_account (sender_account_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
