CREATE TABLE expert_meeting_record (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expert_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    title VARCHAR(200) NOT NULL,
    meeting_time DATETIME NULL,
    participants TEXT NOT NULL,
    minutes MEDIUMTEXT NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_meeting_record_owner (id, expert_key),
    KEY idx_meeting_record_list (expert_key, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE expert_meeting_video
    ADD COLUMN meeting_record_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD KEY idx_meeting_record_video (meeting_record_id, expert_key, uploaded_at, id),
    ADD CONSTRAINT fk_video_meeting_owner FOREIGN KEY (meeting_record_id, expert_key)
        REFERENCES expert_meeting_record (id, expert_key);
