ALTER TABLE mail_compose_template
    ADD COLUMN template_code VARCHAR(64) NULL,
    ADD COLUMN mail_type VARCHAR(64) NULL,
    ADD UNIQUE KEY uk_mail_compose_template_code (template_code);

INSERT INTO mail_compose_template (
    template_code,
    template_name,
    subject,
    description,
    mail_type,
    enabled,
    created_at,
    updated_at
)
SELECT
    template_code,
    template_name,
    COALESCE(subject, template_name),
    NULL,
    template_code,
    enabled,
    COALESCE(created_at, CURRENT_TIMESTAMP),
    COALESCE(updated_at, CURRENT_TIMESTAMP)
FROM mail_template
WHERE template_code IN (
    'INTRODUCTION',
    'MEETING_INVITATION',
    'MATERIAL_REMINDER',
    'MEETING_CONFIRMATION'
)
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name),
    subject = VALUES(subject),
    mail_type = VALUES(mail_type),
    enabled = VALUES(enabled),
    updated_at = VALUES(updated_at);

DELETE b
FROM mail_compose_template_block b
JOIN mail_compose_template t ON t.id = b.template_id
WHERE t.template_code IN (
    'INTRODUCTION',
    'MEETING_INVITATION',
    'MATERIAL_REMINDER',
    'MEETING_CONFIRMATION'
);

INSERT INTO mail_compose_template_block (
    template_id,
    block_order,
    block_type,
    ref_id,
    custom_text
)
SELECT
    t.id,
    0,
    'CUSTOM_TEXT',
    NULL,
    mt.body
FROM mail_compose_template t
JOIN mail_template mt ON mt.template_code = t.template_code
WHERE t.template_code IN (
    'INTRODUCTION',
    'MEETING_INVITATION',
    'MATERIAL_REMINDER',
    'MEETING_CONFIRMATION'
);
