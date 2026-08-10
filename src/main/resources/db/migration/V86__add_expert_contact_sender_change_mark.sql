-- V86: 发送账号「主动变更」标记。
-- 只由运营单专家主动换绑置位；账号被禁用后的批量迁移不置位（决策 ④）。
ALTER TABLE expert_contact
    ADD COLUMN sender_account_changed TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '发送账号是否被运营主动变更过；批量迁移不置位',
    ADD COLUMN sender_account_changed_at DATETIME NULL
    COMMENT '最近一次主动变更时间';
