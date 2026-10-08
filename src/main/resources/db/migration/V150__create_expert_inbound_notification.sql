CREATE TABLE expert_inbound_notification_setting (
    id TINYINT NOT NULL PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    generation BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NULL,
    updated_by VARCHAR(100) NULL,
    worker_owner VARCHAR(64) NULL,
    worker_lease_until DATETIME(3) NULL,
    next_send_at DATETIME(3) NULL,
    CONSTRAINT ck_expert_inbound_notification_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO expert_inbound_notification_setting (id, enabled, generation) VALUES (1, FALSE, 0);

CREATE TABLE expert_inbound_notification_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inbound_processing_id BIGINT NOT NULL,
    expert_contact_id BIGINT NOT NULL,
    mailbox_owner_code VARCHAR(64) NOT NULL,
    uid_validity BIGINT NOT NULL,
    imap_uid BIGINT NOT NULL,
    generation BIGINT NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    lease_token VARCHAR(64) NULL,
    lease_until DATETIME(3) NULL,
    sent_at DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    UNIQUE KEY uk_expert_inbound_notification_physical (mailbox_owner_code, uid_validity, imap_uid),
    KEY idx_expert_inbound_notification_ready (status, next_attempt_at, id),
    KEY idx_expert_inbound_notification_lease (status, lease_until),
    CONSTRAINT ck_expert_inbound_notification_status CHECK (status IN ('PENDING','SENDING','SENT','FAILED','CANCELLED')),
    CONSTRAINT ck_expert_inbound_notification_attempts CHECK (attempts BETWEEN 0 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
