-- QA rule optimization driven by real inbound-reply analysis (37 replies, all previously unmatched).
-- 1) Trust/credentials: add verifiable company website + LinkedIn and expand distrust keywords.
-- 2) New rule: "how did you find me" (previously uncovered).
-- 3) New rule: passport/document refusal -> route to human (handoff, no auto-reply).
-- 4) Expand Program overview keywords for the "give me more info before CV" cluster.
-- 5) Expand role keywords for generic "what is this / what will I get" phrasings.
-- Literals are ASCII-only; Chinese display_name uses CONVERT(UNHEX(..) USING utf8mb4) per V44 convention.

-- 1) Trust: credentials + verifiable links + broadened keywords
UPDATE qa_rule
   SET keywords = 'accredited,official agency,prove government,cooperation with government,authorized,how can i trust,trust you,can i trust,commercial,not academic,is this legitimate,legitimate,is this a scam,scam,verify,company website,company site,linkedin,who are you,real company,are you real',
       reply_body = 'We completely understand your caution, and we are happy to share information you can verify independently. Our agency is a legitimately registered company, though the talent program itself is confidential and has no public project website.

You can review our company website and our representative''s profile directly:
Company website: http://www.qingfeitalent.com/
LinkedIn: https://www.linkedin.com/in/yuyun-chou-48899a392

Our cooperation with the government is further documented through talent-office certificates and participation in official talent summits, which we are glad to share as supporting evidence. Please feel free to verify these before proceeding; we would like to build trust step by step at your pace.'
 WHERE reply_subject = 'Agency credentials and government cooperation';

-- 2) New rule: how did you find / contact me
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    display_name, auto_reply_enabled, handoff_required, enabled
) VALUES (
    (SELECT id FROM qa_category WHERE category_code = 'COMMUNICATION_AND_OTHER'),
    'how did you find,how did you get my,where did you find,how did you contact,found my details,found my information,get my contact,how did you know',
    'ANY', 120, 'How we found you',
    'Thank you for asking. We identify potential candidates from public academic sources such as ORCID, published papers, and public researcher profiles.

Your contact details were obtained from these public records, and we reached out because your research expertise aligns closely with the needs of our partner enterprises.',
    CONVERT(UNHEX('E4BFA1E681AFE69DA5E6BA90') USING utf8mb4),
    1, 0, 1
);

-- 3) New rule: passport / document refusal -> route to human (no auto-reply)
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    display_name, auto_reply_enabled, handoff_required, enabled
) VALUES (
    (SELECT id FROM qa_category WHERE category_code = 'PROCESS_ACTIONS'),
    'can''t share passport,cannot share passport,won''t share passport,not comfortable sharing,share my passport,not share passport,privacy of my documents',
    'ANY', 120, 'Passport and document reluctance',
    'We fully respect your concerns about sharing personal documents. The passport and short confirmation video are used only to verify identity and to prevent AI-forged or duplicate applications; all materials are kept strictly confidential and are never used beyond the application.

If you prefer, we can walk you through the safeguards personally and discuss which materials you are comfortable providing before you decide.',
    CONVERT(UNHEX('E68AA4E785A7E69687E4BBB6E9A1BEE89991') USING utf8mb4),
    0, 1, 1
);

-- 4) Program overview: cover more "info before CV" phrasings
UPDATE qa_rule
   SET keywords = 'learn more,more information,name and background,objectives and scope,before sharing,understand the program,additional information,about the initiative,participating institution,participating organization,why was i selected,why did you choose me,why did you contact me,official website,program objectives,tell me more'
 WHERE reply_subject = 'Program overview';

-- 5) Role/benefits: generic "what is this / what will I get" phrasings
UPDATE qa_rule
   SET keywords = 'role,position,what would i do,what is collaboration,what is this collaboration,focus area,what will i be doing'
 WHERE reply_subject = 'Possible role';

UPDATE qa_rule
   SET keywords = 'duty,right,responsibility,benefit,what will i get,what do i get'
 WHERE reply_subject = 'Responsibilities and benefits';
