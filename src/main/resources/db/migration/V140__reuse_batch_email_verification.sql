-- 跨执行按邮箱复用一年内的原始结果；旧原始结果无需回填。
-- 关联用于审计，不设自引用FK：原始结果到期清理后，本次明细仍保留已复制的结果与时间。
ALTER TABLE batch_email_verification
    ADD COLUMN reused_from_id BIGINT NULL COMMENT '复用的原始验证明细ID，实际HTTP验证为空',
    ADD INDEX idx_batch_email_verification_email_time (email, checked_at, id);
