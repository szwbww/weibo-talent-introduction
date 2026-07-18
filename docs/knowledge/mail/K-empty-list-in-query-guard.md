---
id: K-empty-list-in-query-guard
domain: mail
created: 2026-07-02
last_used: 2026-07-18
hit_count: 1
source: fix-v:ai-training-redesign:fix-1
severity: P1
---
经验：Spring JDBC named parameter 查询里只要 SQL 含 `IN (:ids)`，即使外层有 `:unrestricted = true OR ...` 兜底，空集合仍会展开成 `IN ()`，MySQL 下直接语法错误。
正确做法：进入 repository 前保证集合非空（哨兵值可行但要受 OR/AND 语义保护），或拆成无 `IN` 的 unrestricted 专用查询；mock controller 测试不足以证明真实 SQL 可执行。
反例：`AiTrainingController.kt:205` 无专家标签时返回 `contactIds=emptyList()`，随后 `MailRecordRepository.kt:65` 展开为 `expert_contact_id IN ()`。
