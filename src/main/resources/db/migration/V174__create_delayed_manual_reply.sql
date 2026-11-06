-- fast-p 01 (I-1/I-4/I-10): durable delayed manual reply queue.
-- Independent table: existing attempt/mail_record/draft/review tables gain no columns.
-- All task times are epoch milliseconds; the server reads DB CURRENT_TIMESTAMP(3) and
-- adds the fixed 60000ms business delay inside the queueing transaction.
CREATE TABLE delayed_manual_reply (
    id BIGINT NOT NULL AUTO_INCREMENT,
    send_attempt_id BIGINT NOT NULL,
    expert_contact_id BIGINT NOT NULL,
    account_code VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    actor_username VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    origin_kind VARCHAR(16) NOT NULL,
    origin_id BIGINT NULL,
    origin_version BIGINT NULL,
    revision BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    due_at_ms BIGINT NOT NULL,
    dispatch_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    claimed_at_ms BIGINT NULL,
    subject VARCHAR(255) NOT NULL,
    text_body LONGTEXT NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    public_error_code VARCHAR(64) NULL,
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_delayed_manual_reply_attempt (send_attempt_id),
    KEY idx_delayed_manual_reply_due (status, due_at_ms, id),
    KEY idx_delayed_manual_reply_contact (expert_contact_id, account_code, created_at_ms, id),
    KEY idx_delayed_manual_reply_origin (origin_kind, origin_id, status),
    CONSTRAINT fk_delayed_manual_reply_attempt FOREIGN KEY (send_attempt_id)
        REFERENCES mail_send_attempt (id) ON DELETE RESTRICT,
    CONSTRAINT chk_delayed_manual_reply_status
        CHECK (status IN ('WAITING','SENDING','CANCELLED','SENT','FAILED','UNKNOWN')),
    CONSTRAINT chk_delayed_manual_reply_revision CHECK (revision >= 1),
    CONSTRAINT chk_delayed_manual_reply_due CHECK (due_at_ms >= created_at_ms),
    CONSTRAINT chk_delayed_manual_reply_origin CHECK (
        (origin_kind = 'DRAFT' AND origin_id IS NOT NULL AND origin_version IS NOT NULL)
        OR (origin_kind = 'REVIEW' AND origin_id IS NOT NULL AND origin_version IS NOT NULL)
        OR (origin_kind = 'LEGACY' AND origin_id IS NULL AND origin_version IS NULL)
    ),
    CONSTRAINT chk_delayed_manual_reply_sending CHECK (
        status <> 'SENDING' OR (dispatch_token IS NOT NULL AND claimed_at_ms IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Deliberately no expert_contact FK: deleting a contact must retain the audit row.
