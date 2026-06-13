-- Preflight must complete before any persistent DDL because MySQL ALTER TABLE
-- implicitly commits and cannot be rolled back by Flyway.
CREATE TEMPORARY TABLE v24_mail_record_ambiguous_attempts AS
SELECT msa.id AS mail_send_attempt_id
  FROM mail_send_attempt msa
  JOIN mail_record mr
    ON mr.message_id = msa.message_id
   AND mr.direction = 'OUTBOUND'
   AND mr.mail_type = 'INTRODUCTION'
 GROUP BY msa.id
HAVING COUNT(*) > 1;

CREATE TEMPORARY TABLE v24_mail_record_ambiguous_guard (id BIGINT PRIMARY KEY);
INSERT INTO v24_mail_record_ambiguous_guard (id) VALUES (1);
INSERT INTO v24_mail_record_ambiguous_guard (id)
SELECT 1 FROM v24_mail_record_ambiguous_attempts LIMIT 1;

ALTER TABLE mail_send_attempt
    ADD COLUMN recipient VARCHAR(255) DEFAULT NULL,
    ADD COLUMN subject VARCHAR(255) DEFAULT NULL,
    ADD COLUMN body LONGTEXT DEFAULT NULL,
    ADD COLUMN content_type VARCHAR(64) DEFAULT NULL,
    ADD COLUMN quota_counted TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN account_counted_at DATETIME DEFAULT NULL;

UPDATE mail_record mr
JOIN mail_send_attempt msa
  ON mr.message_id = msa.message_id
 AND mr.direction = 'OUTBOUND'
 AND mr.mail_type = 'INTRODUCTION'
SET mr.mail_send_attempt_id = msa.id
WHERE mr.mail_send_attempt_id IS NULL;

-- Drop the ordinary index from V23 since it's replaced by a UNIQUE constraint
DROP INDEX idx_mr_mail_send_attempt_id ON mail_record;

ALTER TABLE mail_record
    ADD CONSTRAINT uq_mail_record_send_attempt UNIQUE (mail_send_attempt_id),
    ADD CONSTRAINT fk_mail_record_send_attempt
        FOREIGN KEY (mail_send_attempt_id) REFERENCES mail_send_attempt(id);

UPDATE mail_send_attempt msa
JOIN mail_record mr
  ON mr.mail_send_attempt_id = msa.id
 AND mr.direction = 'OUTBOUND'
 AND mr.mail_type = 'INTRODUCTION'
 AND mr.send_status = 'SENT'
SET msa.quota_counted = 1,
    msa.account_counted_at = COALESCE(mr.sent_at, msa.updated_at)
WHERE msa.quota_counted = 0;
