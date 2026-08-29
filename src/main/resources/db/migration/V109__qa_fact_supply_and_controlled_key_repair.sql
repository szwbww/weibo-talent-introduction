-- V109: QA fact supply for reachable-but-unowned coverage keys + controlled-key
-- deadlock repair. Plan docs/plans/2026-08-28/11-fact-supply.md.
--
-- I-1: every UPDATE below is guarded on the four frozen rule ids — those rows were
--      hand-adjusted by the requester and must not be touched by this migration.
--      (guard literal appears exactly once per UPDATE statement, nowhere else)
-- I-2: every coverage key below is already referenced by an intent in
--      AiReplyIntentCatalog; this migration adds no catalog key and no intent.
-- I-3: no new rule's coverage set equals a V82 controlled group.
-- I-6: every category_code below exists (V38:5-11 and V41:7).
-- G-3: INSERTs are guarded by WHERE NOT EXISTS on reply_subject; the keyword
--      append is NOT LIKE guarded; every UPDATE preserves updated_at.

-- ============================================================
-- 1. Repair: Project sensitivity concerns is stuck on the G1 controlled group.
--    V76:73-75 gave it the single key 'confidentiality.materials', which equals
--    the G1 group exactly, while its body has never matched the G1 canonical
--    body. Any create/update/enable therefore returns HTTP 400. Same class of
--    defect as id=24, which V107 fixed and this one was missed.
--    Blanking coverage_keys returns it to legacy reachability (isCoverageEligible
--    returns true for every non-high-risk intent on empty coverage) — I-4.
--    Body is deliberately untouched.
-- ============================================================
UPDATE qa_rule
   SET coverage_keys = '',
       updated_at = updated_at
 WHERE reply_subject = 'Project sensitivity concerns'
   AND coverage_keys = 'confidentiality.materials'
   AND id NOT IN (1, 3, 21, 24);

-- ============================================================
-- 2. Keyword parity: Pre-contract IP boundary (V82) cannot be reached by the
--    real-world phrasing "the ownership of IP arising from advisory input".
--    Keywords only; the body is the G4 canonical text and must stay byte-identical
--    (I-5).
-- ============================================================
UPDATE qa_rule
SET keywords = CONCAT(keywords,
    CASE WHEN LOWER(keywords) NOT LIKE '%ip arising%'
         THEN ',ip arising' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%advisory input%'
         THEN ',advisory input' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%ownership of ip%'
         THEN ',ownership of ip' ELSE '' END,
    CASE WHEN LOWER(keywords) NOT LIKE '%ip ownership%'
         THEN ',ip ownership' ELSE '' END
),
updated_at = updated_at
WHERE reply_subject = 'Pre-contract IP boundary'
  AND id NOT IN (1, 3, 21, 24)
  AND (
    LOWER(keywords) NOT LIKE '%ip arising%'
    OR LOWER(keywords) NOT LIKE '%advisory input%'
    OR LOWER(keywords) NOT LIKE '%ownership of ip%'
    OR LOWER(keywords) NOT LIKE '%ip ownership%'
  );

-- ============================================================
-- 3. Five new atomic facts, one per reachable-but-unowned coverage key.
--    Priority 120 matches the V82/V105 atomic-fact convention (priority ASC =
--    higher precedence, so 120 keeps these below the overview rules).
-- ============================================================

-- 3.1 enterprise.project_types — company types AND the R&D needs question are
--     answered by one fact on purpose, so that no new coverage key is required.
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'COMMUNICATION_AND_OTHER'),
    'types of companies,companies typically work with,enterprise types,partner types,r&d gaps,research gaps,technical needs,common problems',
    'ANY', 120, 'Partner enterprise types and R&D needs',
    'We do not work from a fixed public list of companies. The relevant company type and industry depend on the expert''s research direction and the availability of a genuinely suitable partner. Potential enterprise needs may involve technical problem-solving, product development, research guidance or technology commercialisation. The specific need cannot be confirmed before an enterprise and project are identified.',
    'We do not work from a fixed public list of companies. The relevant company type and industry depend on the expert''s research direction and the availability of a genuinely suitable partner. Potential enterprise needs may involve technical problem-solving, product development, research guidance or technology commercialisation. The specific need cannot be confirmed before an enterprise and project are identified.',
    'Partner enterprise types and R&D needs', 'Communication and other', 'AUTO', 1, 0, 0, 1,
    'enterprise.project_types'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Partner enterprise types and R&D needs');

-- 3.2 finance.compensation_structure
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'FUNDING_AND_TIMELINE'),
    'compensation structure,retainer,hourly,project-based,payment method,payment schedule',
    'ANY', 120, 'Compensation structure',
    'There is no universal compensation model for all advisers. Compensation may be structured according to the agreed workload and project arrangement. The exact amount, payment method, deliverables and payment schedule are negotiated and included in the written agreement before any commitment is made.',
    'There is no universal compensation model for all advisers. Compensation may be structured according to the agreed workload and project arrangement. The exact amount, payment method, deliverables and payment schedule are negotiated and included in the written agreement before any commitment is made.',
    'Compensation structure', 'Funding and timeline', 'AUTO', 1, 0, 0, 1,
    'finance.compensation_structure'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Compensation structure');

-- 3.3 role.deliverables
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'ROLE_AND_WORKSTYLE'),
    'deliverables,outputs,milestones,expected work',
    'ANY', 120, 'Advisory deliverables',
    'There is no universal deliverables list. Expected outputs, milestones, reports or other deliverables are negotiated with the matched enterprise and recorded in the written agreement.',
    'There is no universal deliverables list. Expected outputs, milestones, reports or other deliverables are negotiated with the matched enterprise and recorded in the written agreement.',
    'Advisory deliverables', 'Role and work style', 'AUTO', 1, 0, 0, 1,
    'role.deliverables'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Advisory deliverables');

-- 3.4 confidentiality.research
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'TRUST_AND_COMPLIANCE'),
    'research confidentiality,confidential research,use of research data,nda',
    'ANY', 120, 'Project research confidentiality',
    'Project-specific confidentiality obligations and permitted use of research information are defined in the written agreement with the matched enterprise.',
    'Project-specific confidentiality obligations and permitted use of research information are defined in the written agreement with the matched enterprise.',
    'Project research confidentiality', 'Trust and compliance', 'AUTO', 1, 0, 0, 1,
    'confidentiality.research'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Project research confidentiality');

-- 3.5 work.advisory_duration + work.time_commitment. Two keys on one rule: the
--     set {work.advisory_duration, work.time_commitment} is not a controlled
--     group (I-3), and both keys are intent-referenced (AiReplyIntentCatalog
--     :294 and :305), so the rule is reachable through either intent (I-2).
--     Key order follows catalog declaration order (G-6): work.time_commitment
--     is declared before work.advisory_duration in QaCoverageKeyCatalog.
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT id FROM qa_category WHERE category_code = 'ROLE_AND_WORKSTYLE'),
    'project duration,time commitment,weekly hours,monthly hours,how involved',
    'ANY', 120, 'Advisory duration and time commitment',
    'A research advisory project commonly runs for approximately two to three years. The exact weekly or monthly workload is flexible and must be agreed with the matched enterprise.',
    'A research advisory project commonly runs for approximately two to three years. The exact weekly or monthly workload is flexible and must be agreed with the matched enterprise.',
    'Advisory duration and time commitment', 'Role and work style', 'AUTO', 1, 0, 0, 1,
    'work.time_commitment,work.advisory_duration'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Advisory duration and time commitment');
