-- Refresh MATERIAL_REMINDER content so mail_template and compose preview/send share one body.
UPDATE mail_template
SET template_name = 'Material Reminder Email',
    subject = 'Gentle Follow-up on the Requested Materials',
    body = 'Dear Professor,

I hope you are doing well.

I am writing to gently follow up on the materials you previously mentioned you would share with us. We understand that you may have a busy schedule, so please feel free to send them whenever convenient.

If you need more time, have any questions, or encounter difficulty preparing any of the documents, please let us know. We will be happy to assist.

If you have already sent the materials, please disregard this reminder.

Thank you again for your interest and support.

Best regards,
${senderName}
${senderTitle}
${teamName}',
    enabled = 1
WHERE template_code = 'MATERIAL_REMINDER';

UPDATE mail_compose_template
SET template_name = 'Material Reminder Email',
    subject = 'Gentle Follow-up on the Requested Materials',
    mail_type = 'MATERIAL_REMINDER',
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE template_code = 'MATERIAL_REMINDER';

DELETE b
FROM mail_compose_template_block b
JOIN mail_compose_template t ON t.id = b.template_id
WHERE t.template_code = 'MATERIAL_REMINDER';

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
WHERE t.template_code = 'MATERIAL_REMINDER';
