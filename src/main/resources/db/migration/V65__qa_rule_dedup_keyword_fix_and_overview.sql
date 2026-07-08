-- V65: QA rule cleanup based on 2026-07-08 online rule review.
-- Goals: (1) funding figures appear in exactly ONE auto-reply place (Funding support);
--        (2) fix keyword collisions that cause wrong concatenation;
--        (3) enable softened Program overview for "tell me more" replies;
--        (4) trust section composes BEFORE process-action asks;
--        (5) remove disabled duplicate rows.
-- ASCII-only literals per V44/V45/V57 convention. Rows targeted by id (= rule_id in export).

-- ============================================================
-- 1. Program overview (id=24): soften funding, add no-fee + CTA, enable.
--    Requires overview supersede + gap-detection logic (V41) to be live.
-- ============================================================
UPDATE qa_rule
   SET reply_body = 'Two tracks:

Innovative Talent Scheme -- for senior researchers (PhD + notable institutional experience) to serve as a research consultant to a matched Chinese enterprise. You would guide the company''s R&D for 2-3 years, without leaving your current position. Most participants work remotely and visit China 1-2 times per year; all travel expenses are covered by us.

Entrepreneurial Talent Scheme -- for experts who wish to commercialize their research by establishing a venture in China. Remote involvement is possible, with 1-2 annual visits.

Selected candidates receive dedicated government research funding for the project, with the enterprise providing personal compensation separately -- specifics depend on the track and evaluation, and we are happy to share details.

Your current role is not affected: you would serve as an external research advisor, with no relocation or resignation required.

There are no fees at any stage, and all materials are kept strictly confidential. If you are interested, simply reply with your CV or a brief note on your current research focus -- we will then identify matched enterprises for you.',
       enabled = 1
 WHERE id = 24;

-- ============================================================
-- 2. About the talent program (id=1): drop funding paragraph (lives only in
--    Funding support now), narrow over-generic keywords (project/program/scheme).
-- ============================================================
UPDATE qa_rule
   SET reply_body = 'This is a national-level initiative that connects outstanding overseas experts with Chinese enterprises through two main tracks: Innovative Talent Schemes for high-caliber researchers joining enterprises, and Entrepreneurial Talent Schemes for experts who can convert research into products.',
       keywords = 'what is this project,what is the program,about the program,about this project,which program'
 WHERE id = 1;

-- ============================================================
-- 3. Funding support (id=8): keep range once, drop duplicated parenthetical.
-- ============================================================
UPDATE qa_rule
   SET reply_body = 'After a successful application, selected candidates may receive government research funding in the range of 3-12 million RMB, with enterprises providing personal salary support separately; full-time roles may also include additional housing allowance.

If you are willing to establish a technology company in China, further support may also be provided for start-up capital or subsequent project funding.'
 WHERE id = 8;

-- ============================================================
-- 4. Full-time/part-time (id=6) keeps the travel-expense sentence;
--    Workplace arrangement (id=7): remove duplicated travel-expense clause
--    and the bare keyword "china".
-- ============================================================
UPDATE qa_rule
   SET reply_body = 'You may decide whether to come to China, and you may take up to two years to consider the commitment.

It is also possible to visit China several times a year for technical exchanges rather than relocating.',
       keywords = 'workplace,relocate,come to china,move to china,live in china'
 WHERE id = 7;

-- ============================================================
-- 5. Getting started materials (id=33): add confidentiality reassurance
--    (per staged-materials conclusion in docs/qa提炼).
-- ============================================================
UPDATE qa_rule
   SET reply_body = CONCAT(reply_body, '

No original documents or IDs are needed at this stage, and nothing you send is used beyond enterprise matching and application preparation.')
 WHERE id = 33
   AND reply_body NOT LIKE '%No original documents or IDs%';

-- ============================================================
-- 6. Application deadline (id=10): give the typical window instead of nothing.
-- ============================================================
UPDATE qa_rule
   SET reply_body = 'Materials are usually submitted around March-May, with results announced in November-December.

The exact submission deadline should be confirmed according to the latest project notice before application.'
 WHERE id = 10;

-- ============================================================
-- 7. Keyword collision fixes.
-- ============================================================
-- 7a. Application format (id=2): "team" also matches "Teams" (meeting software).
UPDATE qa_rule
   SET keywords = 'apply individually,apply jointly,as a team,with my team,jointly,research partner'
 WHERE id = 2;

-- 7b. Responsibilities and benefits (id=5): bare "right" matches "right now" etc.
UPDATE qa_rule
   SET keywords = 'duty,my rights,responsibility,benefit,benefits,what will i get,what do i get'
 WHERE id = 5;

-- 7c. Our support (id=11): "experience"/"support" too generic ("funding support" hits).
UPDATE qa_rule
   SET keywords = 'why you,your advantage,why choose you,track record,what makes you different'
 WHERE id = 11;

-- 7d. Agency credentials (id=18): bare "linkedin" collides with the
--     "email-only / not on LinkedIn" rule (id=22 keeps its phrases).
UPDATE qa_rule
   SET keywords = 'accredited,official agency,prove government,cooperation with government,authorized,how can i trust,trust you,can i trust,commercial,not academic,is this legitimate,legitimate,is this a scam,scam,verify,company website,company site,who are you,real company,are you real'
 WHERE id = 18;

-- ============================================================
-- 8. Body upgrades for thin / non-answers.
-- ============================================================
-- 8a. Project sensitivity (id=20): offer email pace + verifiable credentials.
UPDATE qa_rule
   SET reply_body = 'The project is legitimate and information is kept confidential.

We are glad to proceed entirely by email at your pace, and can share verifiable credentials before you decide anything.'
 WHERE id = 20;

-- 8b. Partner company information (id=23): answer with a next step instead of a promise.
UPDATE qa_rule
   SET reply_body = 'Matching is based on your research direction. Once the partner enterprise is confirmed, we will send its full profile, website, and address, along with how its direction aligns with your expertise.

To match precisely, could you confirm your current research focus?'
 WHERE id = 23;

-- ============================================================
-- 9. Compose order: trust reassurance must precede process-action asks
--    (VCR / passport video) in concatenated replies.
-- ============================================================
UPDATE qa_category SET compose_order = 40 WHERE category_code = 'TRUST_AND_COMPLIANCE';
UPDATE qa_category SET compose_order = 50 WHERE category_code = 'PROCESS_ACTIONS';

-- ============================================================
-- 10. Remove disabled duplicate rows (id=30 dup of 32, id=31 dup of 29).
--     If mail_record references block the delete, keep them disabled instead.
-- ============================================================
DELETE FROM qa_rule WHERE id IN (30, 31) AND enabled = 0;
