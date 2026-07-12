-- V70: Tighten AI reply action boundaries (CTA / materials keywords / style anchors).
-- Baseline: V65 id=23/24 bodies; MATERIALS_LIGHT keywords; V69 STYLE_* turns.
-- Pre-deploy: export live qa_rule id IN (23,24) and ai_training_qa MATERIALS_LIGHT;
-- merge any operator edits before applying (K-qa-rule-runtime-vs-migration-writes).
-- ASCII-only literals.

-- ============================================================
-- 1. Program overview (id=24): keep facts + no-fee/confidentiality; drop CV CTA.
-- ============================================================
UPDATE qa_rule
   SET reply_body = 'Two tracks:

Innovative Talent Scheme -- for senior researchers (PhD + notable institutional experience) to serve as a research consultant to a matched Chinese enterprise. You would guide the company''s R&D for 2-3 years, without leaving your current position. Most participants work remotely and visit China 1-2 times per year; all travel expenses are covered by us.

Entrepreneurial Talent Scheme -- for experts who wish to commercialize their research by establishing a venture in China. Remote involvement is possible, with 1-2 annual visits.

Selected candidates receive dedicated government research funding for the project, with the enterprise providing personal compensation separately -- specifics depend on the track and evaluation, and we are happy to share details.

Your current role is not affected: you would serve as an external research advisor, with no relocation or resignation required.

There are no fees at any stage, and all materials are kept strictly confidential.'
 WHERE id = 24;

-- ============================================================
-- 2. Partner company information (id=23): keep matching facts; drop research-focus ask.
-- ============================================================
UPDATE qa_rule
   SET reply_body = 'Matching is based on your research direction. Once the partner enterprise is confirmed, we will send its full profile, website, and address, along with how its direction aligns with your expertise.'
 WHERE id = 23;

-- ============================================================
-- 3. MATERIALS_LIGHT: drop bare provide; keep explicit materials phrases.
-- ============================================================
UPDATE ai_training_qa
   SET keywords = 'what documents,materials needed,cv,what to send,what do you need,send my documents,what should i send,what should i provide,provide my cv',
       updated_at = CURRENT_TIMESTAMP
 WHERE source = 'MANUAL_IMPORT'
   AND source_ref = 'MATERIALS_LIGHT';

-- ============================================================
-- 4. Style few-shots: remove CV/meeting literal anchors (seed-parity).
-- ============================================================
UPDATE ai_training_dialogue
   SET turns_json = '[{"role":"EXPERT","text":"Before proceeding, could you explain your company registration, programme purpose, selection and matching process, responsibilities, contract and IP arrangements, and next steps?"},{"role":"AGENT","text":"Thank you for setting out the questions clearly. I will address them in the same order and distinguish confirmed information from points that depend on a future enterprise match or written agreement. If the approved information does not support a requested detail, I will mark it for confirmation instead of making an assumption or replacing the answer with requesting unrelated materials."}]',
       updated_at = CURRENT_TIMESTAMP
 WHERE source_ref = 'STYLE_MULTI_DUE_DILIGENCE';

UPDATE ai_training_dialogue
   SET turns_json = '[{"role":"EXPERT","text":"Before I proceed, how can I verify your company identity, registered location, and official channels?"},{"role":"AGENT","text":"That is a reasonable request. Before asking you to proceed, I will provide the legal identity, registered location, and verification channels that are present in our approved information. Any item not available there will be identified for confirmation rather than replaced with a request for unrelated next action."}]',
       updated_at = CURRENT_TIMESTAMP
 WHERE source_ref = 'STYLE_TRUST_VERIFICATION';
