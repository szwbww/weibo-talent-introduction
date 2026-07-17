---
id: K-audit-selected-source
domain: qa
created: 2026-06-26
last_used: 2026-07-17
hit_count: 16
source: fix-v:ai-reply-review-authority-fail-closed:fix-1
severity: P1
---
经验：审计报表的“人工选用规则集”必须以实际外发关联表为准；日志字段适合记录操作上下文，但不能替代业务事实表。
正确做法：从 `operator_action_log` 取建议集和 `mailRecordId`，再从 `mail_record_qa_rule` 按 ordinal 读取 selected；历史数据缺关联表时才 fallback 到日志里的 `qaRuleIds`。
反例：`QaRuleAuditService.kt:15-24` 只查日志，`QaRuleAuditService.kt:32-33` 直接用 `after.qaRuleIds` 作为 selected，完全未调用 `MailRecordQaRuleRepository.findByMailRecordIdOrderByOrdinalAsc`。
