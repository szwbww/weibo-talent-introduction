ALTER TABLE mail_attachment
    ADD COLUMN inbound_processing_id BIGINT NULL;

ALTER TABLE mail_attachment
    MODIFY mail_record_id BIGINT NULL;

ALTER TABLE mail_attachment
    ADD KEY idx_mail_attachment_inbound (inbound_processing_id, created_at);

ALTER TABLE mail_attachment
    ADD CONSTRAINT fk_mail_attachment_inbound
        FOREIGN KEY (inbound_processing_id) REFERENCES inbound_mail_processing(id);

ALTER TABLE mail_attachment
    ADD CONSTRAINT chk_mail_attachment_owner
        CHECK ((mail_record_id IS NULL) <> (inbound_processing_id IS NULL));
