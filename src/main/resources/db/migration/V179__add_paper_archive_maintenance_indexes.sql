ALTER TABLE discovery_paper_archive_record
    ADD INDEX idx_paper_protected (protected, id),
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE discovery_paper_archive_attempt
    ADD INDEX idx_paper_attempt_status_updated (status, updated_at, id),
    ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE discovery_paper_archive_blob
    ADD INDEX idx_paper_blob_state (state, sha256),
    ALGORITHM=INPLACE, LOCK=NONE;
