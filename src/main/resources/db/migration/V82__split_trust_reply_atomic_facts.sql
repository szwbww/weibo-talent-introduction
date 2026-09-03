-- V82: Split composite trust-reply rules into atomic facts.
--
-- Atomic audited baseline gate (I-6): the migration aborts BEFORE any qa_rule
-- write when either audited legacy rule drifted from the 2026-08-04 baseline --
-- reply_subject, exact keywords, enabled and SHA-256 of answer_body. A mismatch
-- means operator edits landed after the plan; best-effort skip or partial
-- application is prohibited -- deployment stops and the drift is merged
-- manually. On a matching baseline the two composite rules are disabled and the
-- four atomic rules below are inserted idempotently (NOT EXISTS on
-- reply_subject) with non-overlapping keywords and one semantic family per rule
-- (I-1/I-3/I-4). reply_body = answer_body.
--
-- Identity note: the audited rules are matched by reply_subject + content
-- signature, NOT by id / updated_at. Production history pins them at id 17/34
-- with operator-edit timestamps, but a fresh migration chain assigns different
-- ids (contract rule lands at 28) and migration-run timestamps; both worlds
-- carry byte-identical content (verified 2026-09-02 against the V81 chain).
-- id/updated_at are deployment-history artifacts and cannot be reproduced on a
-- fresh database, so requiring them makes the gate unpassable there. Content
-- signature still catches every operator edit that matters for the split.

-- ============================================================
-- 1. Audited baseline gate: abort on drift (I-6)
-- ============================================================
DELIMITER //

DROP PROCEDURE IF EXISTS v82_trust_reply_baseline_gate//

CREATE PROCEDURE v82_trust_reply_baseline_gate()
BEGIN
    DECLARE v_documentation_count INT DEFAULT 0;
    DECLARE v_contract_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_documentation_count FROM qa_rule
     WHERE reply_subject = 'Document confidentiality and no fees'
       AND keywords = 'confidential,keep my documents,never charge,any fee,money transfer'
       AND enabled = 1
       AND SHA2(answer_body, 256) = '04027e0b2046f72f4bcc736a7436299f7880bdef74e321744c61bafebcbb0a37';

    SELECT COUNT(*) INTO v_contract_count FROM qa_rule
     WHERE reply_subject = 'Contract and IP arrangements'
       AND keywords = 'intellectual property,ip rights,ip arrangements,contractual,contract terms,patent ownership,who owns the,formal agreement,formal contract,before any collaboration begins'
       AND enabled = 1
       AND SHA2(answer_body, 256) = '3f142b13e0274db4d5b218f522ffe7071de7a501f6b5ab6324ccade424448f16';

    IF v_documentation_count <> 1 OR v_contract_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V82 baseline drift: audited legacy QA rules changed; stop deployment and merge manually';
    END IF;
END//

DELIMITER ;

CALL v82_trust_reply_baseline_gate();
DROP PROCEDURE v82_trust_reply_baseline_gate;

UPDATE qa_rule
SET enabled = 0,
    updated_at = updated_at
WHERE reply_subject = 'Document confidentiality and no fees'
  AND keywords = 'confidential,keep my documents,never charge,any fee,money transfer'
  AND enabled = 1
  AND SHA2(answer_body, 256) = '04027e0b2046f72f4bcc736a7436299f7880bdef74e321744c61bafebcbb0a37';

UPDATE qa_rule
SET enabled = 0,
    updated_at = updated_at
WHERE reply_subject = 'Contract and IP arrangements'
  AND keywords = 'intellectual property,ip rights,ip arrangements,contractual,contract terms,patent ownership,who owns the,formal agreement,formal contract,before any collaboration begins'
  AND enabled = 1
  AND SHA2(answer_body, 256) = '3f142b13e0274db4d5b218f522ffe7071de7a501f6b5ab6324ccade424448f16';

-- Application material confidentiality: material handling, usage limitation and
-- redaction only. No fee or contract content.
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT category_id FROM qa_rule WHERE reply_subject = 'Document confidentiality and no fees'),
    'confidential,keep my documents,application materials,materials confidentiality,redaction,redacted',
    'ANY', 120, 'Application material confidentiality',
    'Your materials are kept strictly confidential and used only for application purposes. Technical details you prefer not to disclose can be handled with appropriate redaction.',
    'Your materials are kept strictly confidential and used only for application purposes. Technical details you prefer not to disclose can be handled with appropriate redaction.',
    'Application material confidentiality', 'Trust and compliance', 'AUTO', 1, 0, 0, 1,
    'confidentiality.materials'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Application material confidentiality');

-- Participant fee policy: no fees for participants at any stage only.
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT category_id FROM qa_rule WHERE reply_subject = 'Document confidentiality and no fees'),
    'never charge,any fee,any fees,any costs,money transfer,charge,charges,cost,costs',
    'ANY', 120, 'Participant fee policy',
    'We never charge any fees throughout the entire process.',
    'We never charge any fees throughout the entire process.',
    'Participant fee policy', 'Trust and compliance', 'AUTO', 1, 0, 0, 1,
    'fees.policy'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Participant fee policy');

-- Contract arrangements: contracting party, written contract and pre-signature
-- review only. No IP boundary or fee content.
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT category_id FROM qa_rule WHERE reply_subject = 'Contract and IP arrangements'),
    'contract terms,contractual,formal agreement,formal contract,before any collaboration begins,before collaboration,contract signing,written contract,labor contract,sign a contract',
    'ANY', 120, 'Contract arrangements',
    'After selection, you will sign a labor contract directly with the matched enterprise, and you may review the full terms before making any commitment.',
    'After selection, you will sign a labor contract directly with the matched enterprise, and you may review the full terms before making any commitment.',
    'Contract arrangements', 'Funding & timeline', 'AUTO', 1, 0, 0, 1,
    'contract.party,contract.terms'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Contract arrangements');

-- Pre-contract IP boundary: no rights transfer before signing and final IP
-- terms governed by the future written agreement only.
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    answer_body, display_name, section_title, reply_policy,
    auto_reply_enabled, handoff_required, supersedes_children, enabled, coverage_keys
)
SELECT
    (SELECT category_id FROM qa_rule WHERE reply_subject = 'Contract and IP arrangements'),
    'intellectual property,ip rights,ip arrangements,patent ownership,who owns the,ip terms',
    'ANY', 120, 'Pre-contract IP boundary',
    'Until a contract is signed, nothing you share with us transfers any rights; any final intellectual-property arrangements will be set out in the future written agreement.',
    'Until a contract is signed, nothing you share with us transfers any rights; any final intellectual-property arrangements will be set out in the future written agreement.',
    'Pre-contract IP boundary', 'Funding & timeline', 'AUTO', 1, 0, 0, 1,
    'ip.arrangements'
WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Pre-contract IP boundary');
