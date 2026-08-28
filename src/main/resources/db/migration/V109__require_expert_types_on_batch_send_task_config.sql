-- I3-3: 三个值与 ExpertClassification.SENDABLE_TYPES（枚举前三值）逐字等价，
--       迁移后线上发信人群零变化。
-- I3-4: 只覆盖当前为空的行，不抹掉运营已手工勾选的配置。
UPDATE batch_send_task_config
SET expert_types_json = '["PRODUCTION_RND","ACADEMIC_RND","HYBRID_RND"]'
WHERE expert_types_json IS NULL
   OR expert_types_json = ''
   OR expert_types_json = '[]';
