-- V88: 冷外联退订行文案改写，使 HTML 锚文本版读起来自然。
-- V87 追加的原句以 URL 收尾，HTML 版把 URL 换成锚文本 "Unsubscribe" 后
-- 会读作 "you can unsubscribe here: Unsubscribe"（语义重复）。
-- 只做定点 REPLACE，不整块覆盖，保护运营在后台编辑器里的历史修改（I-6）。
-- LIKE 守卫保证幂等：已改写或运营已自行改写的块跳过。
-- 不写 mail_template：该表已无代码读取方（K-mail-template-table-dead）。

UPDATE mail_compose_template_block b
JOIN mail_compose_template t ON t.id = b.template_id
SET b.custom_text = REPLACE(
        b.custom_text,
        'you can unsubscribe here: ${unsubscribeUrl}',
        'please use this link: ${unsubscribeUrl}'
    )
WHERE t.template_code IN ('INTRODUCTION', 'MATERIAL_REMINDER')
  AND b.block_type = 'CUSTOM_TEXT'
  AND b.custom_text IS NOT NULL
  AND b.custom_text LIKE '%you can unsubscribe here: ${unsubscribeUrl}%';
