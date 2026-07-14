CREATE TABLE batch_send_task_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(120) NOT NULL,
    mail_type VARCHAR(32) NOT NULL,
    auto_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    cron VARCHAR(64) NOT NULL,
    daily_cap INT NOT NULL,
    round_size INT NOT NULL,
    per_mail_interval_ms BIGINT NOT NULL,
    per_round_interval_ms BIGINT NOT NULL,
    self_check_ttl_minutes INT NOT NULL,
    funnel_level VARCHAR(32) NULL,
    tags_json TEXT NOT NULL,
    email_domain VARCHAR(120) NULL,
    discipline VARCHAR(120) NULL,
    template_id BIGINT NULL,
    legacy_code VARCHAR(64) NULL,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_send_task_config_legacy_code (legacy_code),
    KEY idx_batch_send_task_config_deleted_updated (deleted_at, updated_at),
    KEY idx_batch_send_task_config_auto_deleted (auto_enabled, deleted_at),
    KEY idx_batch_send_task_config_template (template_id),
    CONSTRAINT fk_batch_send_task_config_template
        FOREIGN KEY (template_id) REFERENCES mail_compose_template(id)
);

-- Seed INTRODUCTION default from old KV (idempotent via legacy_code).
INSERT INTO batch_send_task_config (
    config_name,
    mail_type,
    auto_enabled,
    cron,
    daily_cap,
    round_size,
    per_mail_interval_ms,
    per_round_interval_ms,
    self_check_ttl_minutes,
    funnel_level,
    tags_json,
    email_domain,
    discipline,
    template_id,
    legacy_code,
    deleted_at,
    created_at,
    updated_at
)
SELECT
    '默认介绍邮件任务',
    'INTRODUCTION',
    COALESCE((SELECT CASE WHEN setting_value = 'true' THEN 1 ELSE 0 END FROM batch_send_setting WHERE setting_key = 'batchSend.autoEnabled' LIMIT 1), 0),
    COALESCE((SELECT setting_value FROM batch_send_setting WHERE setting_key = 'batchSend.cron' LIMIT 1), '0 0 0 * * ?'),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.dailyCap' LIMIT 1), 1000),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.roundSize' LIMIT 1), 50),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.perMailIntervalMs' LIMIT 1), 1000),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.perRoundIntervalMs' LIMIT 1), 60000),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.selfCheckTtlMinutes' LIMIT 1), 30),
    NULL,
    '[]',
    NULLIF(COALESCE((SELECT setting_value FROM batch_send_setting WHERE setting_key = 'batchSend.emailDomain' LIMIT 1), ''), ''),
    NULLIF(COALESCE((SELECT setting_value FROM batch_send_setting WHERE setting_key = 'batchSend.discipline' LIMIT 1), ''), ''),
    (
        SELECT CASE
            WHEN setting_value IS NULL OR setting_value = '' THEN NULL
            ELSE CAST(setting_value AS UNSIGNED)
        END
        FROM batch_send_setting
        WHERE setting_key = 'batchSend.templateId'
        LIMIT 1
    ),
    'INTRODUCTION',
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM batch_send_task_config WHERE legacy_code = 'INTRODUCTION'
);

-- Seed MATERIAL_REMINDER default from old KV (idempotent via legacy_code).
INSERT INTO batch_send_task_config (
    config_name,
    mail_type,
    auto_enabled,
    cron,
    daily_cap,
    round_size,
    per_mail_interval_ms,
    per_round_interval_ms,
    self_check_ttl_minutes,
    funnel_level,
    tags_json,
    email_domain,
    discipline,
    template_id,
    legacy_code,
    deleted_at,
    created_at,
    updated_at
)
SELECT
    '材料提醒任务',
    'MATERIAL_REMINDER',
    COALESCE((SELECT CASE WHEN setting_value = 'true' THEN 1 ELSE 0 END FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.autoEnabled' LIMIT 1), 0),
    COALESCE((SELECT setting_value FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.cron' LIMIT 1), '0 0 8 * * ?'),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.dailyCap' LIMIT 1), 60),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.roundSize' LIMIT 1), 30),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.perMailIntervalMs' LIMIT 1), 3000),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.perRoundIntervalMs' LIMIT 1), 120000),
    COALESCE((SELECT CAST(setting_value AS UNSIGNED) FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.selfCheckTtlMinutes' LIMIT 1), 30),
    NULL,
    '[]',
    NULLIF(COALESCE((SELECT setting_value FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.emailDomain' LIMIT 1), ''), ''),
    NULLIF(COALESCE((SELECT setting_value FROM batch_send_setting WHERE setting_key = 'batchSend.materialReminder.discipline' LIMIT 1), ''), ''),
    COALESCE(
        (
            SELECT CASE
                WHEN setting_value IS NULL OR setting_value = '' THEN NULL
                ELSE CAST(setting_value AS UNSIGNED)
            END
            FROM batch_send_setting
            WHERE setting_key = 'batchSend.materialReminder.templateId'
            LIMIT 1
        ),
        (SELECT id FROM mail_compose_template WHERE template_code = 'MATERIAL_REMINDER' LIMIT 1)
    ),
    'MATERIAL_REMINDER',
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM batch_send_task_config WHERE legacy_code = 'MATERIAL_REMINDER'
);
