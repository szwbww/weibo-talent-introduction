-- 审计触发来源既包含 SYSTEM / OPERATOR，也包含 MATERIAL_ATTACHED（17 字符）。
-- 不能因审计字段截断阻断来信主事务。
ALTER TABLE expert_application_promotion
    MODIFY COLUMN triggered_by VARCHAR(32) NOT NULL COMMENT 'SYSTEM / OPERATOR / MATERIAL_ATTACHED';
