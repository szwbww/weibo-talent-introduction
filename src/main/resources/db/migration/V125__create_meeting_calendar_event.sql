CREATE TABLE meeting_calendar_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expert_contact_id BIGINT NOT NULL,
    source_mail_record_id BIGINT NULL,
    starts_at_utc DATETIME(6) NOT NULL,
    ends_at_utc DATETIME(6) NOT NULL,
    meeting_link VARCHAR(1024) NULL,
    note VARCHAR(200) NULL,
    cancel_reason VARCHAR(200) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_meeting_calendar_status_start_id (status, starts_at_utc, id),
    KEY idx_meeting_calendar_contact_status_start_id (expert_contact_id, status, starts_at_utc, id),
    UNIQUE KEY uk_meeting_calendar_source_mail (source_mail_record_id),
    CONSTRAINT fk_meeting_calendar_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE RESTRICT,
    CONSTRAINT fk_meeting_calendar_source_mail
        FOREIGN KEY (source_mail_record_id) REFERENCES mail_record(id) ON DELETE RESTRICT
);
