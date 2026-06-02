ALTER TABLE expert_contact
  ADD COLUMN first_reply_at DATETIME NULL,
  ADD COLUMN application_indexed TINYINT(1) NOT NULL DEFAULT 0;
