---
id: K-distinct-contact-order-query
domain: mail
created: 2026-07-01
last_used: 2026-07-18
hit_count: 3
source: fix-v:04-frontend-tab:fix-1
severity: P1
---
经验：从 `mail_record` 取去重 expert_contact_id 且按最近邮件排序时，不能写 `SELECT DISTINCT expert_contact_id ... ORDER BY id DESC`；MySQL 8 对 DISTINCT 查询的 ORDER BY 非 select-list 表达式有运行时报错风险。
正确做法：用 `GROUP BY expert_contact_id ORDER BY MAX(id) DESC` 或等价子查询，并显式过滤 `expert_contact_id IS NOT NULL`；同时用集成/SQL 语义测试覆盖去重与排序。
反例：`MailRecordRepository.kt:42-50` 选择 distinct contact id 但按 `mr.id` 排序，`GET /api/ai-training/simulate/experts` 和 `AiQaExtractionService.extractBatch` 都依赖该查询。
