---
id: K-manual-rich-render-before-send
domain: mail
created: 2026-07-13
last_used: 2026-07-20
hit_count: 15
source: fix-v:ai-reply-06-rich-send-variable-rendering:fix-1
severity: P1
---
经验：manual rich 外发若仅在前端标记为“未编辑的 AI 草稿”时才校验和渲染模板，普通/API 调用或已编辑草稿中的 `${unknownKey}` 会绕过校验而被字面外发，已知变量也不会使用最终 sender/contact 重渲染。
正确做法：`sendManualRichReply` 必须把每次调用的 raw text/html 都作为最终外发模板处理：先双侧校验 placeholder，再用最终账号和 contact 分别做 text 渲染与 HTML 转义渲染，之后才 delivery、mail_record 和日志；前端的 adoption 标记不得决定安全边界。
反例：`PendingMailOperationService.kt:217-239` 用 `hasTemplate` 分支，`templateTextBody/templateHtmlBody` 缺失时直接发送 `textBody/htmlBody`。
