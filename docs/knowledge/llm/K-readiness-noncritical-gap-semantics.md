---
id: K-readiness-noncritical-gap-semantics
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 5
source: fix-v:ai-reply-05-trust-boundary-readiness-plan:fix-1
severity: P1
---
经验：先识别 critical gap、再以“任意 UNSUPPORTED”覆盖结论，会把已分类非关键缺口错误升级为 BLOCKED。
正确做法：readiness 必须按 blocking、unknown、noncritical 三类互斥排序；noncritical UNSUPPORTED 保持 NEEDS_REVIEW 且自动发送仍拒绝。
反例：AiReplyDraftService.kt:815-817。
