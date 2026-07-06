ALTER TABLE mail_compose_template ADD COLUMN subject_variants TEXT NULL COMMENT 'JSON 数组: subject 变体列表，为空时使用 subject 字段';
ALTER TABLE reply_snippet ADD COLUMN variant_group VARCHAR(64) NULL COMMENT '变体组标识，同组 snippet 按确定性规则选一个';
