-- P-E T-1: operator_status 可空，默认 NULL = 不限（与 expert_contact.operator_status 的
-- NOT NULL DEFAULT 'NOT_CONTACTED' 语义相反——这里是无筛选的"全部"，不是事实状态）。
-- 列宽沿用 V19 的 VARCHAR(32) 约定；值域由 OperatorStatus.entries 校验（service 层），
-- 不加 CHECK 约束以便枚举演进（与 expert_contact 同款立场）。
ALTER TABLE batch_send_task_config
    ADD COLUMN operator_status VARCHAR(32) NULL AFTER discipline;
