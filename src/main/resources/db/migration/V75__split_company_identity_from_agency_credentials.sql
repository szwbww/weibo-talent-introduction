-- V75: Split company registered identity from Agency credentials.
-- Per V68, id=18 accumulated company registration keywords and body text
-- alongside trust/verification content. This migration:
--   1) Restores id=18 keywords to trust-only (pre-V68).
--   2) Removes the company name paragraph appended by V68 from id=18 reply_body.
--   3) Creates a new rule for company registered identity with facts only.
-- ASCII-only literals per V44/V45/V57 convention.

-- 1. Restore Agency credentials (id=18): trust/verification keywords only.
UPDATE qa_rule
   SET keywords = 'accredited,official agency,prove government,cooperation with government,authorized,how can i trust,trust you,can i trust,commercial,not academic,is this legitimate,legitimate,is this a scam,scam,verify,company website,company site,who are you,real company,are you real'
 WHERE id = 18;

-- 2. Remove company name paragraph appended by V68 from id=18 reply_body.
UPDATE qa_rule
   SET reply_body = REPLACE(
       reply_body COLLATE utf8mb4_unicode_ci,
       CONCAT(_utf8mb4'

Our full registered name is Jiangsu Qingfei Talent Technology Co., Ltd. (' COLLATE utf8mb4_unicode_ci,
              CONVERT(UNHEX('E6B19FE88B8FE6B885E9A39EE4BABAE6898DE7A791E68A80E69C89E99990E585ACE58FB8') USING utf8mb4) COLLATE utf8mb4_unicode_ci,
              _utf8mb4'), registered in Nanjing, China.' COLLATE utf8mb4_unicode_ci),
       _utf8mb4'' COLLATE utf8mb4_unicode_ci
   )
 WHERE id = 18 AND reply_body LIKE '%Jiangsu Qingfei Talent Technology%';

-- 3. New rule: company registered identity and location (facts only).
INSERT INTO qa_rule (
    category_id, keywords, match_mode, priority, reply_subject, reply_body,
    display_name, auto_reply_enabled, handoff_required, enabled, supersedes_children
)
SELECT (SELECT id FROM qa_category WHERE category_code = 'TRUST_AND_COMPLIANCE'),
       'registered location,registered address,company registration,name of your company,your company name,full name and registered,where is your company,where are you based',
       'ANY', 90, 'Company registered identity and location',
       CONCAT(_utf8mb4'Our full registered name is Jiangsu Qingfei Talent Technology Co., Ltd. (' COLLATE utf8mb4_unicode_ci,
              CONVERT(UNHEX('E6B19FE88B8FE6B885E9A39EE4BABAE6898DE7A791E68A80E69C89E99990E585ACE58FB8') USING utf8mb4) COLLATE utf8mb4_unicode_ci,
              _utf8mb4'), registered in Nanjing, China.' COLLATE utf8mb4_unicode_ci),
       CONVERT(UNHEX('E585ACE58FB8E6B3A8E5868CE4BFA1E681AF') USING utf8mb4),
       1, 0, 1, 0
 WHERE NOT EXISTS (SELECT 1 FROM qa_rule WHERE reply_subject = 'Company registered identity and location');
