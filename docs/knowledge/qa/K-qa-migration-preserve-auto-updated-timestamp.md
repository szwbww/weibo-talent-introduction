---
id: K-qa-migration-preserve-auto-updated-timestamp
domain: qa
created: 2026-07-17
last_used: 2026-07-17
hit_count: 3
source: fix-v:qa-refactor-02-fact-card-foundation:fix-1
severity: P1
---
经验：对带 `ON UPDATE CURRENT_TIMESTAMP` 的业务表做仅回填新列的迁移时，未显式保留时间戳会把全量历史记录误标为运营更新。
正确做法：迁移更新新列时显式保持 `updated_at=updated_at`，并在发布前确认迁移尚未执行；已执行迁移不得伪造旧时间。
反例：`V79__add_qa_answer_body.sql:4-6` 仅设置 `answer_body`，触发 `qa_rule.updated_at` 自动刷新。
