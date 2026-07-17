ALTER TABLE qa_rule
    ADD COLUMN reply_policy VARCHAR(16) NOT NULL DEFAULT 'REVIEW' AFTER handoff_required;

UPDATE qa_rule
SET reply_policy = CASE
    WHEN handoff_required = 1 OR auto_reply_enabled = 0 THEN 'REVIEW'
    ELSE 'AUTO'
END,
    updated_at = updated_at;

UPDATE qa_rule
SET auto_reply_enabled = CASE WHEN reply_policy = 'AUTO' THEN 1 ELSE 0 END,
    handoff_required = CASE WHEN reply_policy IN ('REVIEW', 'NEVER') THEN 1 ELSE 0 END,
    updated_at = updated_at;
