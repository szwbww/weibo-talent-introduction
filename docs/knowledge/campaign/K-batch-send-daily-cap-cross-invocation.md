---
id: K-batch-send-daily-cap-cross-invocation
domain: campaign
created: 2026-07-13
last_used: 2026-07-14
hit_count: 3
source: fix-v:material-reminder-batch-send:fix-1
severity: P1
---
经验：把 `dailyCap` 计数器初始化为每次 execution 的 0，会让手动补跑、cron 重触发或重启绕过同日上限。
正确做法：自然日限额必须从持久化成功记录或等价持久计数器初始化；失败和取消不计入，重启后仍须保留当天累计值。
反例：`ManualInitialOutreachService.kt:158` 的 per-run `dailySentTotal`。
