-- Existing FULL grants also take ownership of unresolved real incoming mail.
-- Processed/resolved mail, READ grants and simulator messages are unchanged.
UPDATE inbound_mail_processing p
JOIN expert_mail_read_access a ON a.expert_contact_id = p.expert_contact_id
    AND a.username = 'user1' AND a.access_level = 'FULL'
SET p.pending_owner_username = 'user1'
WHERE p.process_status = 'MANUAL_REVIEW' AND p.pending_owner_username = 'admin'
  AND p.sender_account_code <> 'SIMULATOR_NOOP';
