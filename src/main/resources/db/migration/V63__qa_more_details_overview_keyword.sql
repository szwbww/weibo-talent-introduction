UPDATE qa_rule
   SET keywords = CONCAT(keywords, ',more details,more detail,know more details,want to know more details')
 WHERE reply_subject = 'Program overview'
   AND keywords NOT LIKE '%more details%';
