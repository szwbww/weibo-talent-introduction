-- Existing grants remain READ; NONE is represented by the absence of a row.
ALTER TABLE expert_mail_read_access
    ADD COLUMN access_level VARCHAR(8) NOT NULL DEFAULT 'READ';
