---
id: K-batch-send-scheduler-reschedule-on-enable
domain: campaign
created: 2026-07-13
last_used: 2026-07-14
hit_count: 2
source: create-p:material-reminder-batch-send
severity: P1
---

动态批量发送 scheduler 若只在 cron 字符串变化时重排 future，会漏掉“沿用原 cron、仅把 autoEnabled 从 false 改为 true”的首次启用场景，导致配置显示已启用但进程内没有对应定时任务。

正确做法：cron 或 autoEnabled 任一变化都发布重排事件；scheduler 重排时按每个发送类型取消旧 future，并为当前 enabled 类型重新注册。禁用时同样触发重排并取消该类型 future，使用 `cancel(false)`，不得中断已运行任务。

关联位置：`BatchSendSettingService.updateConfig/setAutoEnabled`、`BatchSendScheduler`。
