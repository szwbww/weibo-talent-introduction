-- QA material tiering: light materials in auto-reply, heavy documents deferred to human follow-up.
-- Quantify funding (3-12M RMB) in overview/program/funding rules.
-- ASCII-only literals; Chinese display_name uses CONVERT(UNHEX(..) USING utf8mb4) per V44 convention.

-- (a) Program overview: remove heavy document list, add light-material guidance + quantified funding (I-1, I-3, I-5)
UPDATE qa_rule
   SET reply_body = 'Thank you for your interest in our talent program. This is a national-level initiative that connects outstanding overseas experts with Chinese enterprises through two main tracks: Innovative Talent Schemes for high-caliber researchers joining enterprises, and Entrepreneurial Talent Schemes for experts who can convert research into products.

Selected candidates may receive government research funding in the range of 3-12 million RMB (RMB 3,000,000-12,000,000), with enterprises providing personal salary support separately; full-time roles may also include additional housing allowance.

To help us assess your fit at an early stage, a CV, patent certificates, and a publication list would be useful -- we can discuss the details after you learn more about the program.

After you submit materials, our team matches partner enterprises, prepares application documents, and submits them for review; the overall cycle often spans six months or longer, with results commonly announced in late autumn.

We keep all materials strictly confidential, never charge fees, and you may redact sensitive technical details if you prefer.'
 WHERE reply_subject = 'Program overview';

-- (b) About the talent program: quantify funding (I-3)
UPDATE qa_rule
   SET reply_body = 'There are two projects: Innovative Talent Schemes and Entrepreneurial Talent Schemes. Innovative Talent Schemes are intended for individual talents who aim to join an enterprise with an exceptionally high salary.

Entrepreneurial Talent Schemes are designed for talents who can convert ideas into useful products.

Selected candidates may receive government research funding in the range of 3-12 million RMB (RMB 3,000,000-12,000,000), with enterprises providing personal salary support separately; full-time roles may also include additional housing allowance.'
 WHERE reply_subject = 'About the talent program';

-- (c) Application criteria: eligibility only, no document list (I-1)
UPDATE qa_rule
   SET reply_body = 'Applicants should hold the title of associate professor or above, have outstanding research achievements in their field, and be able to contribute to industrial services and scientific and technological innovation.

We can discuss fit first -- no documents needed at this stage.'
 WHERE reply_subject = 'Application criteria';

-- (d) Funding support: quantify funding (I-3)
UPDATE qa_rule
   SET reply_body = 'After a successful application, selected candidates may receive government research funding in the range of 3-12 million RMB (RMB 3,000,000-12,000,000), with enterprises providing personal salary support separately; full-time roles may also include additional housing allowance.

If you are willing to establish a technology company in China, further support may also be provided for start-up capital or subsequent project funding.'
 WHERE reply_subject = 'Funding support';

-- (e) New rule: light materials only at early stage (I-2)
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    display_name, section_title, auto_reply_enabled, handoff_required, enabled, supersedes_children
) VALUES (
    (SELECT id FROM qa_category WHERE category_code = 'PROGRAM_AND_ELIGIBILITY'),
    'what documents,materials needed,cv,what to send,provide,what do you need,send my documents,what should i send',
    'ANY', 35, 'Getting started materials',
    'Thank you for your interest in getting started. At this early stage, we ask for only three items:

Your CV (resume): this helps us match you with the right enterprise and research direction.

Patent certificates: these highlight your innovation track record and can strengthen your application.

A publication list: this substantiates your research level, which is a key review criterion.

All materials are kept strictly confidential, we never charge any fees, and you may redact sensitive technical details if you prefer.',
    CONVERT(UNHEX('E8BDBBE997AEE69D90E69699') USING utf8mb4),
    'Program & eligibility',
    1, 0, 1, 0
);

-- (f) Confirmation video: identity verification handled by operator, not auto-reply (I-1)
UPDATE qa_rule
   SET auto_reply_enabled = 0,
       handoff_required = 1
 WHERE reply_subject = 'Confirmation video requirement';
