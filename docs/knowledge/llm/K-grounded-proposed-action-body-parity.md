---
id: K-grounded-proposed-action-body-parity
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 5
source: fix-v:ai-reply-04-grounded-trust-content-plan:fix-2
severity: P1
---
经验：只验证 `proposedAction.text` 会让模型在 claim 正文中写已授权 CTA、却声明 `NONE`；最终 sanitizer 因动作已授权不会删除，导致错误协议仍标为 LLM 成功。
正确做法：frame 组装后用同一 action detector 扫描整个正文；NONE 必须无动作，非 NONE 必须恰有同类型动作且声明文本确实位于正文，否则直接 invalid/fallback。
反例：AiReplyGroundedDraftMaterializer.kt:127-136 仅检测 proposedAction text。
