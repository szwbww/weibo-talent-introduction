-- 模拟器专用 sender account（按自然键 account_code 匹配，不会误改其它行）
INSERT INTO mail_sender_account (account_code, sender_email, sender_name, sender_title, sender_display_name,
                                 team_name, country_name,
                                 smtp_host, smtp_port, smtp_username, smtp_password,
                                 imap_host, imap_port, imap_username, imap_password,
                                 strategy_weight, daily_send_limit, today_sent_count, enabled, created_at, updated_at)
SELECT 'SIMULATOR_NOOP', 'sim+sender@simulator.local', 'Simulator Sender', NULL, 'Simulator', NULL, NULL,
       'localhost', 25, 'noop', 'noop',
       'localhost', 143, 'noop', 'noop',
       0, 99999, 0, TRUE, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM mail_sender_account WHERE account_code = 'SIMULATOR_NOOP');

-- 如果已存在则只确保 enabled
UPDATE mail_sender_account SET enabled = TRUE WHERE account_code = 'SIMULATOR_NOOP' AND enabled = FALSE;

-- 模拟器专用 campaign（按自然键 campaign_code 匹配，不会误改其它行）
INSERT INTO campaign (campaign_code, campaign_name, description, status, sender_account_id, created_at, updated_at)
SELECT 'SIMULATOR', '自动回复模拟器', '仅供测试页面使用，不要给真实专家发邮件', 'DRAFT', msa.id, NOW(), NOW()
FROM mail_sender_account msa
WHERE msa.account_code = 'SIMULATOR_NOOP'
  AND NOT EXISTS (SELECT 1 FROM campaign WHERE campaign_code = 'SIMULATOR');

-- 如果已存在则只更新描述
UPDATE campaign c
   JOIN mail_sender_account msa ON msa.account_code = 'SIMULATOR_NOOP'
   SET c.description = '仅供测试页面使用，不要给真实专家发邮件',
       c.sender_account_id = msa.id
 WHERE c.campaign_code = 'SIMULATOR';
