-- ============================================================================
-- V122 专家会议确认专用模板（fast-p 01）
--
-- code/type 固定为 MANUAL_MEETING_CONFIRMATION（I-2 会议模板独立域）。
-- 该模板的四个 {{...}} 变量由会议弹窗专用生成器解释，通用 ${...} 渲染链路
-- 不认识它们（K-mail-template-table-dead / K-renderText-all-callers），
-- 因此普通单发列表会过滤、直接按 id 单发会被服务端拒绝（IP-1）。
--
-- 幂等与人工配置保护：
--   * 只在 code 不存在时插入头（不覆盖同 code 已有人工配置）；
--   * 只给「本次插入的头」创建唯一 CUSTOM_TEXT 块（禁止为原有模板删块/加块）。
-- V122 重放由 Flyway 管理，不做 UPDATE 覆盖式幂等。
-- 正文 SSOT 是 mail_compose_template_block.custom_text；本文件不写 mail_template。
-- ============================================================================

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
    'MANUAL_MEETING_CONFIRMATION',
    '专家会议确认 · 英文',
    'Meeting confirmation',
    '仅供收发件箱会议确认。{{expert_salutation}} / {{meeting_time}} / {{zoom_url}} / {{sender_signature}} 在会议弹窗生成；通用预览显示原文。',
    'MANUAL_MEETING_CONFIRMATION',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM mail_compose_template
    WHERE template_code = 'MANUAL_MEETING_CONFIRMATION'
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
    'Dear {{expert_salutation}},

Thank you for confirming.

We have noted the meeting time as {{meeting_time}}.

Please join the meeting using the following link:

{{zoom_url}}

We look forward to speaking with you.

Best regards,
{{sender_signature}}'
FROM mail_compose_template t
WHERE t.template_code = 'MANUAL_MEETING_CONFIRMATION'
  AND t.id = LAST_INSERT_ID()
  AND NOT EXISTS (
      SELECT 1
      FROM mail_compose_template_block b
      WHERE b.template_id = t.id
  );
