-- V68: QA keyword gap fix for overview multi-question mail + Contract/IP rule.
-- ASCII-only literals per V44/V45/V57. Rows targeted by id (= rule_id in export).

-- ============================================================
-- 1. Program overview (id=24): catch "further information" / purpose-structure asks
-- ============================================================
UPDATE qa_rule
   SET keywords = CONCAT(keywords, ',further information,purpose and structure,structure of the program,more about the program,know more about')
 WHERE id = 24 AND keywords NOT LIKE '%further information%';

-- ============================================================
-- 2. Agency credentials (id=18): company registration asks + registered name body
-- ============================================================
UPDATE qa_rule
   SET keywords = CONCAT(keywords, ',registered location,registered address,company registration,name of your company,your company name,full name and registered,where is your company,where are you based')
 WHERE id = 18 AND keywords NOT LIKE '%registered location%';

UPDATE qa_rule
   SET reply_body = CONCAT(reply_body, '

Our full registered name is Jiangsu Qingfei Talent Technology Co., Ltd. (', CONVERT(UNHEX('E6B19FE88B8FE6B885E9A39EE4BABAE6898DE7A791E68A80E69C89E99990E585ACE58FB8') USING utf8mb4), '), registered in Nanjing, China.')
 WHERE id = 18 AND reply_body NOT LIKE '%Jiangsu Qingfei Talent Technology%';

-- ============================================================
-- 3. Responsibilities and benefits (id=5): plural / deliverables phrases
-- ============================================================
UPDATE qa_rule
   SET keywords = 'duty,my rights,responsibility,responsibilities,benefit,benefits,what will i get,what do i get,deliverables,my duties,expected responsibilities'
 WHERE id = 5;

-- ============================================================
-- 4. Partner company information (id=23): matching / scope asks
-- ============================================================
UPDATE qa_rule
   SET keywords = CONCAT(keywords, ',within the scope,selected and matched,how do you match,matching process,enterprise projects')
 WHERE id = 23 AND keywords NOT LIKE '%within the scope%';

-- ============================================================
-- 5. Application process (id=9): next-stages phrases; drop bare "process"
-- ============================================================
UPDATE qa_rule
   SET keywords = 'application process,the process,procedure,timeline,next stages,next steps,what happens next,stages of the application,selection process,how are researchers selected'
 WHERE id = 9;

-- ============================================================
-- 6. Getting started materials (id=33): drop bare "provide"
-- ============================================================
UPDATE qa_rule
   SET keywords = 'what documents,materials needed,cv,what to send,what do you need,send my documents,what should i send,provide my cv,what should i provide'
 WHERE id = 33;

-- ============================================================
-- 7. New rule: Contract and IP arrangements
-- ============================================================
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    display_name, section_title, auto_reply_enabled, handoff_required, enabled, supersedes_children
)
SELECT (SELECT id FROM qa_category WHERE category_code = 'FUNDING_AND_TIMELINE'),
       'intellectual property,ip rights,ip arrangements,contractual,contract terms,patent ownership,who owns the',
       'ANY', 120, 'Contract and IP arrangements',
       'After selection, you will sign a labor contract directly with the matched enterprise; intellectual-property and compensation terms are set out in that agreement, and you may review the full terms before making any commitment.

Until then, nothing you share with us transfers any rights -- your materials are used only for enterprise matching and application preparation.',
       CONVERT(UNHEX('E59088E5908CE4B88EE79FA5E8AF86E4BAA7E69D83') USING utf8mb4),
       'Funding & timeline', 1, 0, 1, 0
 WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Contract and IP arrangements');
