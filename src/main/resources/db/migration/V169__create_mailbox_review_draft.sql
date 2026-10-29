CREATE TABLE mailbox_review_draft (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_username VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    expert_contact_id BIGINT NOT NULL,
    client_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_kind VARCHAR(8) NOT NULL,
    target_id BIGINT NOT NULL,
    account_code VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    version BIGINT NOT NULL,
    state VARCHAR(16) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    html_body LONGTEXT NOT NULL,
    text_body LONGTEXT NOT NULL,
    submitted_at DATETIME(3) NULL,
    return_reason VARCHAR(2000) NULL,
    reviewed_by VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    reviewed_at DATETIME(3) NULL,
    approved_context_token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    send_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    send_attempt_id BIGINT NULL,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_draft_request (owner_username, client_request_id),
    KEY idx_review_draft_owner (owner_username, expert_contact_id, deleted_at, updated_at, id),
    KEY idx_review_draft_pending (state, deleted_at, account_code, expert_contact_id),
    CONSTRAINT chk_review_draft_state CHECK (state IN ('DRAFT','SUBMITTED','RETURNED','APPROVED')),
    CONSTRAINT chk_review_draft_version CHECK (version >= 1),
    CONSTRAINT chk_review_draft_target CHECK (target_kind IN ('INBOUND','OUTBOUND')),
    CONSTRAINT chk_review_draft_approval CHECK (
        (state = 'APPROVED' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND approved_context_token IS NOT NULL AND send_request_id IS NOT NULL)
        OR (state <> 'APPROVED' AND reviewed_by IS NULL AND reviewed_at IS NULL AND approved_context_token IS NULL AND send_request_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Deliberately no contact/account FK: deleting existing business data must retain audit history.
CREATE TABLE mailbox_review_draft_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    draft_id BIGINT NOT NULL,
    draft_version BIGINT NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    actor_username VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    payload_json LONGTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_review_draft_event_cursor (draft_id, id),
    CONSTRAINT fk_review_draft_event FOREIGN KEY (draft_id) REFERENCES mailbox_review_draft(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
