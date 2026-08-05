---
id: K-ai-reply-evidence-version-deterministic
domain: llm
created: 2026-07-19
last_used: 2026-08-05
hit_count: 5
source: fix-v:ai-reply-06-draft-audit-evidence-preflight-plan:fix-1
severity: P1
---
经验：把观测时间拼入事实集版本会让未变更的事实每次生成都产生新版本，编辑复验必然误报“来源已变化”。
正确做法：evidenceSetVersion 只能由有序 ruleId、可用性、updatedAt/正文 SHA-256 等事实状态确定；observedAt 作为独立审计字段保存。
反例：AiReplyDraftService.kt:1352-1383。
