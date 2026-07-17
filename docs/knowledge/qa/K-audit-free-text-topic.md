---
id: K-audit-free-text-topic
domain: qa
created: 2026-06-26
last_used: 2026-07-17
hit_count: 7
source: fix-v:qa-rules-phase3:fix-1
severity: P1
---
经验：人工改写审计若只统计 edited 次数，无法指导规则优化；计划要求“高频自由文本主题”时，发送日志或报表 DTO 必须保留可聚合的自由文本摘要。
正确做法：发送组装回复时记录最小自由文本/override 摘要字段，报表按规范化摘要聚合并在 UI 展示 topic/count；不要用 LLM 做审计基础能力。
反例：`QaRuleUsageAuditReport` 只有 total/edited/removed/added，`app.js:1456-1463` 只渲染这些字段，`PendingMailOperationService.kt:341-348` 日志 after 没有自由文本主题字段。
