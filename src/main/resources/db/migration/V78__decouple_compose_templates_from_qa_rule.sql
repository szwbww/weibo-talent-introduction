-- Snapshot live qa_rule.reply_body into template blocks and decouple INTRODUCTION (and any
-- other templates) from runtime QA updates. Preserves block id, block_order, template_id.

UPDATE mail_compose_template_block b
INNER JOIN qa_rule q ON q.id = b.ref_id
SET
    b.custom_text = q.reply_body,
    b.block_type = 'CUSTOM_TEXT',
    b.ref_id = NULL
WHERE b.block_type = 'QA_RULE';
