ALTER TABLE bounce_record
    ADD COLUMN failed_recipient VARCHAR(255) NULL AFTER original_expert_contact_id;
