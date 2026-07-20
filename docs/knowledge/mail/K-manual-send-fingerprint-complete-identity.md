---
id: K-manual-send-fingerprint-complete-identity
domain: mail
created: 2026-07-20
last_used: 2026-07-20
hit_count: 4
source: fix-v:ai-reply-07-final-send-integrity-plan:fix-1
last_source: create-p:ai-reply-final-send-identity-scope-repair
severity: P1
---
经验：人工回复投递指纹若省略 inbound identity 或排序语义列表，会把不同会话或不同 QA 事实顺序误折叠成同一发送尝试。
正确做法：长度前缀 payload 必须逐字段包含计划声明的 inbound/contact/account/recipient/最终正文/有序 canonical IDs；不要排序有业务顺序的列表。
反例：ManualReplySendAttemptService.kt:68-80。
