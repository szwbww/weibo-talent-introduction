ALTER TABLE qa_rule
    ADD COLUMN answer_body TEXT NULL AFTER reply_body;

UPDATE qa_rule
SET answer_body = reply_body,
    updated_at = updated_at
WHERE answer_body IS NULL;

ALTER TABLE qa_rule
    MODIFY answer_body TEXT NOT NULL;
