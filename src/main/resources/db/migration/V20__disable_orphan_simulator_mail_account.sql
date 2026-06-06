-- 已移除的自动回复模拟器遗留账号不能参与真实 SMTP/IMAP 任务。
UPDATE mail_sender_account
SET enabled = FALSE,
    updated_at = NOW()
WHERE account_code = 'SIMULATOR_NOOP'
  AND enabled = TRUE;
