-- V83: Durable locked snapshot store for the trust-reply workbench.
--
-- One row per (source_type, source_id). The payload_json holds only the
-- validated resolved snapshot (schema version, source/evidence versions,
-- requested fact ids, selected model and canonical-order locked items);
-- never active-only versions, assembly preview, translations or DOM state.
-- Optimistic concurrency: insert requires expected state version 0, update
-- and delete require the current state version. payload_json is capped at
-- 256 KiB and rows expire 30 days after the last update; expired rows are
-- pruned opportunistically. This table is NOT a replay store: operator
-- action log semantics are untouched.

CREATE TABLE trust_reply_workbench_state (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT NOT NULL,
    state_version BIGINT NOT NULL,
    payload_json LONGTEXT NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_trust_reply_state_source (source_type, source_id),
    KEY idx_trust_reply_state_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
