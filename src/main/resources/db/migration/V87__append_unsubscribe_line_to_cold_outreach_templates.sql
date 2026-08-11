-- V87: 冷外联邮件（INTRODUCTION / MATERIAL_REMINDER）正文追加退订链接行。
-- 只追加不覆盖：保护运营在后台编辑器里的历史修改（见 plan I-2）。
-- NOT LIKE 守卫保证幂等：已含退订占位符的块跳过。
-- 只覆盖这两个 template_code：它们的渲染路径已注入 unsubscribeUrl 变量（见 plan I-4）。
-- 不写 mail_template：该表已无代码读取方（见 plan I-1）。

UPDATE mail_compose_template_block b
JOIN mail_compose_template t ON t.id = b.template_id
SET b.custom_text = CONCAT(
        b.custom_text,
        '\n\n---\nIf you would prefer not to receive further emails from us, you can unsubscribe here: ${unsubscribeUrl}'
    )
WHERE t.template_code IN ('INTRODUCTION', 'MATERIAL_REMINDER')
  AND b.block_type = 'CUSTOM_TEXT'
  AND b.custom_text IS NOT NULL
  AND b.custom_text NOT LIKE '%unsubscribeUrl%';
