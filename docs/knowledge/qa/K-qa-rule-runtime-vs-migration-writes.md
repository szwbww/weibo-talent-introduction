---
id: K-qa-rule-runtime-vs-migration-writes
domain: qa
created: 2026-07-11
last_used: 2026-07-11
hit_count: 0
source: create-p:qa-keyword-gap-v68
---
经验：`qa_rule` 有两类写路径——Flyway 迁移（V3/V38/V52/V57/V63/V65/V68…批量修订）和 `QaRuleManagementService`（运营 UI 运行时改 keywords/reply_body/enabled）。迁移中的 `UPDATE qa_rule SET keywords/reply_body` 会无条件覆盖运营的运行时改动。
正确做法：出关键词/正文修订迁移的计划必须包含"上线前基线核对"验收项——导出线上目标规则的 keywords 与计划书写时的基线比对，有运营改动先并入迁移再上线；对 reply_body 的 CONCAT 追加带 `NOT LIKE` 防重条件，INSERT 带 `NOT EXISTS`（按 reply_subject）。
