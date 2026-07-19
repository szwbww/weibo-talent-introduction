---
id: K-readiness-invalid-policy-fail-closed
domain: llm
created: 2026-07-19
last_used: 2026-07-19
hit_count: 1
source: fix-v:ai-reply-05b-grounded-fallback-readiness-plan:fix-1
severity: P1
---
经验：evidence readiness 若直接调用会抛异常的 policy 解析器，历史数据、手工 SQL 或旁路写入留下的非法字符串会令生成失败，而非安全降级为 BLOCKED。
正确做法：readiness 重读 evidence 时，把 policy 解析纳入逐条 fail-closed 验证；解析失败必须返回 BLOCKED，不能让异常逃逸。
反例：AiReplyDraftService.kt:847 调用 QaRule.replyPolicyEnum()；QaRule.kt:17-22 对非法值抛 IllegalArgumentException。
