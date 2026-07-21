-- V81: Idempotent keyword additions for due-diligence intent parity.
-- Only appends keywords whose lower-cased form is not already present in the
-- existing keywords column. Does NOT modify answer_body, display_name,
-- section_title, coverage_keys, reply_policy, enabled, priority, or updated_at.
UPDATE qa_rule
SET keywords = CONCAT(keywords,
    CASE WHEN LOWER(keywords) NOT LIKE '%advisory role compensated%'
         THEN ',advisory role compensated' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%is the advisory role compensated%'
         THEN ',is the advisory role compensated' ELSE '' END
),
updated_at = updated_at
WHERE reply_subject = 'Funding support'
  AND (
    LOWER(keywords) NOT LIKE '%advisory role compensated%'
    OR LOWER(keywords) NOT LIKE '%is the advisory role compensated%'
  );

UPDATE qa_rule
SET keywords = CONCAT(keywords,
    CASE WHEN LOWER(keywords) NOT LIKE '%typical duration%'
         THEN ',typical duration' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%duration of advisory projects%'
         THEN ',duration of advisory projects' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%advisory project duration%'
         THEN ',advisory project duration' ELSE '' END
),
updated_at = updated_at
WHERE reply_subject = 'Program overview'
  AND (
    LOWER(keywords) NOT LIKE '%typical duration%'
    OR LOWER(keywords) NOT LIKE '%duration of advisory projects%'
    OR LOWER(keywords) NOT LIKE '%advisory project duration%'
  );

UPDATE qa_rule
SET keywords = CONCAT(keywords,
    CASE WHEN LOWER(keywords) NOT LIKE '%formal agreement%'
         THEN ',formal agreement' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%formal contract%'
         THEN ',formal contract' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%before any collaboration begins%'
         THEN ',before any collaboration begins' ELSE '' END
),
updated_at = updated_at
WHERE reply_subject = 'Contract and IP arrangements'
  AND (
    LOWER(keywords) NOT LIKE '%formal agreement%'
    OR LOWER(keywords) NOT LIKE '%formal contract%'
    OR LOWER(keywords) NOT LIKE '%before any collaboration begins%'
  );
