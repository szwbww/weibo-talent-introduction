-- 为 V3 种子 QA 规则补中文显示名（运营手动发送邮件下拉框使用）
-- 按 category_id + reply_subject 精确限定为 V3 种子行，避免误标运营自定义规则
UPDATE qa_rule SET display_name = '项目内容介绍'              WHERE category_id = 1  AND reply_subject = 'About the talent program'      AND display_name IS NULL;
UPDATE qa_rule SET display_name = '申报方式（个人/联合）'      WHERE category_id = 2  AND reply_subject = 'Application format'           AND display_name IS NULL;
UPDATE qa_rule SET display_name = '申报条件与材料'            WHERE category_id = 3  AND reply_subject = 'Application criteria'         AND display_name IS NULL;
UPDATE qa_rule SET display_name = '可担任的角色'              WHERE category_id = 4  AND reply_subject = 'Possible role'              AND display_name IS NULL;
UPDATE qa_rule SET display_name = '职责与权益'               WHERE category_id = 5  AND reply_subject = 'Responsibilities and benefits' AND display_name IS NULL;
UPDATE qa_rule SET display_name = '全职 / 兼职安排'           WHERE category_id = 6  AND reply_subject = 'Full-time and part-time options' AND display_name IS NULL;
UPDATE qa_rule SET display_name = '工作地点安排'             WHERE category_id = 7  AND reply_subject = 'Workplace arrangement'        AND display_name IS NULL;
UPDATE qa_rule SET display_name = '薪资与资金支持'            WHERE category_id = 8  AND reply_subject = 'Funding support'              AND display_name IS NULL;
UPDATE qa_rule SET display_name = '申报流程'                 WHERE category_id = 9  AND reply_subject = 'Application process'          AND display_name IS NULL;
UPDATE qa_rule SET display_name = '申报截止时间'              WHERE category_id = 10 AND reply_subject = 'Application deadline'          AND display_name IS NULL;
UPDATE qa_rule SET display_name = '我们的优势'              WHERE category_id = 11 AND reply_subject = 'Our support'                 AND display_name IS NULL;
UPDATE qa_rule SET display_name = '退休专家答复'             WHERE category_id = 12 AND reply_subject = 'Thank you for your reply'      AND display_name IS NULL;