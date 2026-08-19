-- V106: Append 'remuneration' to Funding support keywords.
-- Repair 00-grounded-coverage-master V-1: the approved P1 orthopaedic trigger
-- letter asks about "the general arrangements regarding remuneration and
-- intellectual property". V3+V81 keywords (salary,subsidy,funding,compensation,
-- advisory role compensated,is the advisory role compensated) contain no
-- substring of that letter, so Funding support cannot bind finance.arrangements
-- on it. Conditional append preserves runtime-added keywords and does not
-- refresh updated_at (K-qa-rule-runtime-vs-migration-writes).

UPDATE qa_rule
SET keywords = CONCAT(keywords,
    CASE WHEN LOWER(keywords) NOT LIKE '%remuneration%'
         THEN ',remuneration' ELSE '' END
),
updated_at = updated_at
WHERE reply_subject = 'Funding support'
  AND LOWER(keywords) NOT LIKE '%remuneration%';
