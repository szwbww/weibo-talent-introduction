-- V105: Programme identity facts + keyword parity (P1, plan 01-fact-and-catalog).
-- Two new grounded facts (coverage programme.name / governance.sponsor_level),
-- plus idempotent keyword appends to id=6 (Full-time and part-time options) and
-- id=18 (Agency credentials and government cooperation).
--
-- I-2: no keyword below contains "programme" (canonicalize rewrites
-- programme -> program, which would break alias-side substring matching).
-- I-4: new facts carry their coverage_keys inline in the INSERT column list.
-- I-5: existing-rule updates are CONCAT + NOT LIKE guarded and preserve
-- updated_at; new INSERTs are guarded by WHERE NOT EXISTS.

-- Statement 1: new fact — programme name and public visibility (coverage programme.name)
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'OVERVIEW'),
    'official name,name of the scheme,what is it called',
    'ANY', 120, 'Programme name and public visibility',
    'The programme runs as three schemes: the Innovative Talent Project, the Entrepreneurial Talent Project and the Young Talent Project. The programme itself is not publicly listed and has no public project website; in formal documents it is identified by the scheme name together with the applying agency and the application year.',
    'The programme runs as three schemes: the Innovative Talent Project, the Entrepreneurial Talent Project and the Young Talent Project. The programme itself is not publicly listed and has no public project website; in formal documents it is identified by the scheme name together with the applying agency and the application year.',
    'Programme name and public visibility', 'Programme identity', 'AUTO', 1, 0, 0, 1,
    'programme.name'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Programme name and public visibility');

-- Statement 2: new fact — programme sponsorship and organising level (coverage governance.sponsor_level)
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'TRUST_AND_COMPLIANCE'),
    'government body,government institution,government agency,institution supporting,supporting body',
    'ANY', 120, 'Programme sponsorship and organising level',
    'It is a national-level talent scheme, and applications are organised locally through municipal governments and their talent offices. Jiangsu Qingfei Talent Technology Co., Ltd. maintains standing cooperation with the local governments of Shanghai, Hangzhou, Suzhou, Wuxi, Nantong and Ningbo. Which talent office handles an application depends on the location of the matched enterprise and is therefore not determined before matching.',
    'It is a national-level talent scheme, and applications are organised locally through municipal governments and their talent offices. Jiangsu Qingfei Talent Technology Co., Ltd. maintains standing cooperation with the local governments of Shanghai, Hangzhou, Suzhou, Wuxi, Nantong and Ningbo. Which talent office handles an application depends on the location of the matched enterprise and is therefore not determined before matching.',
    'Programme sponsorship and organising level', 'Trust and compliance', 'AUTO', 1, 0, 0, 1,
    'governance.sponsor_level'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Programme sponsorship and organising level');

-- Statement 3: id=6 — append collaboration-form keywords (dedupe-guarded, keep updated_at)
UPDATE qa_rule
SET keywords = CONCAT(keywords,
    CASE WHEN LOWER(keywords) NOT LIKE '%form of collaboration%'
         THEN ',form of collaboration' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%forms of collaboration%'
         THEN ',forms of collaboration' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%how the collaboration works%'
         THEN ',how the collaboration works' ELSE '' END
),
updated_at = updated_at
WHERE reply_subject = 'Full-time and part-time options'
  AND (
    LOWER(keywords) NOT LIKE '%form of collaboration%'
    OR LOWER(keywords) NOT LIKE '%forms of collaboration%'
    OR LOWER(keywords) NOT LIKE '%how the collaboration works%'
  );

-- Statement 4: id=18 — append sponsor keywords (IP-6 fix; dedupe-guarded, keep updated_at)
UPDATE qa_rule
SET keywords = CONCAT(keywords,
    CASE WHEN LOWER(keywords) NOT LIKE '%government body%'
         THEN ',government body' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%institution supporting%'
         THEN ',institution supporting' ELSE '' END
),
updated_at = updated_at
WHERE reply_subject = 'Agency credentials and government cooperation'
  AND (
    LOWER(keywords) NOT LIKE '%government body%'
    OR LOWER(keywords) NOT LIKE '%institution supporting%'
  );
