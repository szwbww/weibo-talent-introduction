---
id: K-backfill-readonly-inbound
domain: mail
created: 2026-06-26
last_used: 2026-06-29
hit_count: 5
source: fix-v:inbound-selfcheck-bounce-visibility:fix-1
severity: P1
---
经验：历史回填任务如果声明只读某个事实表，就不能顺手清理或改写同一表的历史行；混合“重分类生成新事实”和“修正旧事实状态”会破坏复验边界。
正确做法：退信回填只读 `inbound_mail_processing` 并只写 `bounce_record`；self-check 历史清理必须拆成独立计划或先修正原计划不变量。
反例：`BounceBackfillService.kt:22` 在回填入口调用 `cleanupHistoricalSelfCheckProbes()`，`BounceBackfillService.kt:79-87` 保存修改后的 inbound 行。
