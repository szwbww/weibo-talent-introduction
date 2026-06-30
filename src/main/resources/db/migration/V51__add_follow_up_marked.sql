ALTER TABLE expert_contact
    ADD COLUMN follow_up_marked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN follow_up_marked_at DATETIME NULL;
