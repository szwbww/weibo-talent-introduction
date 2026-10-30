ALTER TABLE inbound_mail_processing
    ADD COLUMN pending_owner_username VARCHAR(64) NOT NULL DEFAULT 'admin',
    ADD INDEX idx_inbound_pending_owner (pending_owner_username, process_status, expert_contact_id, sender_account_code);
