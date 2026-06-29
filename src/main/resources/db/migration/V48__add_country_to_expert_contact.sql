ALTER TABLE expert_contact ADD COLUMN country VARCHAR(128) NULL;
CREATE INDEX idx_expert_contact_country ON expert_contact (country);
