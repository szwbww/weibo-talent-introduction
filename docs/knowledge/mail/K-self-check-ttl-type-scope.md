---
id: K-self-check-ttl-type-scope
domain: mail
created: 2026-07-13
last_used: 2026-07-14
hit_count: 3
source: create-p:material-reminder-batch-send
severity: P1
---

`SenderAccountSelfCheckService.checkSendable(account)` 会在内部调用无参 `BatchSendSettingService.getConfig()`，因此天然读取 INTRODUCTION 的 `selfCheckTtlMinutes`。批量发送扩展为多类型配置后，直接复用该方法会让其他类型界面可保存 TTL、运行时却始终使用介绍邮件 TTL。

正确做法：保留无参方法兼容旧介绍邮件调用，同时提供显式 TTL（或显式 sendType）入口；类型化发送循环必须把当前类型配置传入 self-check，测试要使用两种不同 TTL 证明没有串线。

关联位置：`SenderAccountSelfCheckService`、`ManualInitialOutreachService.runRoundGate`、`BatchSendSettingService`。
