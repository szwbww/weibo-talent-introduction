-- Repair display names inserted through a mis-decoded client connection.
-- The literals are ASCII-only UTF-8 hex to avoid another client encoding issue.
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E9A1B9E79BAEE58685E5AEB9E4BB8BE7BB8D') USING utf8mb4) WHERE category_id = 1  AND reply_subject = 'About the talent program';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E794B3E68AA5E696B9E5BC8FEFBC88E4B8AAE4BABA2FE88194E59088EFBC89') USING utf8mb4) WHERE category_id = 2  AND reply_subject = 'Application format';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E794B3E68AA5E69DA1E4BBB6E4B88EE69D90E69699') USING utf8mb4) WHERE category_id = 3  AND reply_subject = 'Application criteria';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E58FAFE68B85E4BBBBE79A84E8A792E889B2') USING utf8mb4) WHERE category_id = 4  AND reply_subject = 'Possible role';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E8818CE8B4A3E4B88EE69D83E79B8A') USING utf8mb4) WHERE category_id = 5  AND reply_subject = 'Responsibilities and benefits';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E585A8E8818C202F20E585BCE8818CE5AE89E68E92') USING utf8mb4) WHERE category_id = 6  AND reply_subject = 'Full-time and part-time options';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E5B7A5E4BD9CE59CB0E782B9E5AE89E68E92') USING utf8mb4) WHERE category_id = 7  AND reply_subject = 'Workplace arrangement';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E896AAE8B584E4B88EE8B584E98791E694AFE68C81') USING utf8mb4) WHERE category_id = 8  AND reply_subject = 'Funding support';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E794B3E68AA5E6B581E7A88B') USING utf8mb4) WHERE category_id = 9  AND reply_subject = 'Application process';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E794B3E68AA5E688AAE6ADA2E697B6E997B4') USING utf8mb4) WHERE category_id = 10 AND reply_subject = 'Application deadline';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E68891E4BBACE79A84E4BC98E58ABF') USING utf8mb4) WHERE category_id = 11 AND reply_subject = 'Our support';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E98080E4BC91E4B893E5AEB6E7AD94E5A48D') USING utf8mb4) WHERE category_id = 12 AND reply_subject = 'Thank you for your reply';
