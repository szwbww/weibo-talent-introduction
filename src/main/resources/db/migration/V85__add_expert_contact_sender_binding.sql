-- V85: 专家—发送账号绑定基座。
-- 绑定语义 = 主题发起权归属（回复仍由 mail_record.sender_account_code 决定）。
-- NULL 表示未绑定，禁止空串/哨兵值。
ALTER TABLE expert_contact
    ADD COLUMN bound_sender_account_code VARCHAR(64) NULL
    COMMENT '绑定的发件账号 code；NULL=未绑定。决定新发起主题邮件的发件账号',
    ADD COLUMN sender_account_bound_at DATETIME NULL
    COMMENT '绑定建立时间';

CREATE INDEX idx_expert_contact_bound_sender
    ON expert_contact (bound_sender_account_code);

-- 回填：取每个 contact 最早一封 OUTBOUND INTRODUCTION 的发件账号（IP-1 口径）。
-- 排除 SIMULATOR_NOOP（I-5）。WHERE ... IS NULL 保证重跑幂等（I-3）。
UPDATE expert_contact ec
JOIN (
    SELECT mr.expert_contact_id,
           SUBSTRING_INDEX(GROUP_CONCAT(mr.sender_account_code
                           ORDER BY mr.created_at ASC, mr.id ASC), ',', 1) AS first_code,
           MIN(mr.created_at) AS first_at
      FROM mail_record mr
     WHERE mr.direction = 'OUTBOUND'
       AND mr.mail_type = 'INTRODUCTION'
       AND mr.sender_account_code IS NOT NULL
       AND mr.sender_account_code <> ''
       AND mr.sender_account_code <> 'SIMULATOR_NOOP'
     GROUP BY mr.expert_contact_id
) f ON f.expert_contact_id = ec.id
SET ec.bound_sender_account_code = f.first_code,
    ec.sender_account_bound_at   = f.first_at
WHERE ec.bound_sender_account_code IS NULL;
