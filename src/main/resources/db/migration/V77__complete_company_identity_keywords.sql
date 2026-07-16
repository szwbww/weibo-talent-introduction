-- V77: Keep company-identity QA keywords aligned with the intent catalog.
-- Preserve runtime/operator keywords and append only missing aliases.

UPDATE qa_rule
   SET keywords = CONCAT(keywords, ',full legal name')
 WHERE reply_subject = 'Company registered identity and location'
   AND CONCAT(',', keywords, ',') NOT LIKE '%,full legal name,%';

UPDATE qa_rule
   SET keywords = CONCAT(keywords, ',legal name')
 WHERE reply_subject = 'Company registered identity and location'
   AND CONCAT(',', keywords, ',') NOT LIKE '%,legal name,%';

UPDATE qa_rule
   SET keywords = CONCAT(keywords, ',full name')
 WHERE reply_subject = 'Company registered identity and location'
   AND CONCAT(',', keywords, ',') NOT LIKE '%,full name,%';

UPDATE qa_rule
   SET keywords = CONCAT(keywords, ',company name')
 WHERE reply_subject = 'Company registered identity and location'
   AND CONCAT(',', keywords, ',') NOT LIKE '%,company name,%';
