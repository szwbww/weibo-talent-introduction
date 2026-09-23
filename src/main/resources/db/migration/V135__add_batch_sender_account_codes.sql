-- I-1: 批量任务的发件账号白名单（逻辑 mail_sender_account.account_code 的 JSON 数组）。
-- 空数组 [] = 不限制（旧任务与未传字段同义），非空 = 严格白名单；绝不按 inbound_mailbox_code
-- 合并共享 IMAP 的兄弟别名（LuKai / LuKai_QF 是两个独立 code）。
-- TEXT 列不能带 DEFAULT（MySQL 限制），故照 V93/V97/V98 的两步范式，另显式 MODIFY 收紧 NOT NULL：
-- 先加可空列，再把存量行回填 '[]'，最后收紧为 NOT NULL —— 存量任务行为零漂移。
ALTER TABLE batch_send_task_config
    ADD COLUMN sender_account_codes_json TEXT NULL AFTER expert_types_json;

UPDATE batch_send_task_config
SET sender_account_codes_json = '[]'
WHERE sender_account_codes_json IS NULL;

ALTER TABLE batch_send_task_config
    MODIFY COLUMN sender_account_codes_json TEXT NOT NULL;
