---
id: K-batch-send-scheduler-reschedule-on-enable
domain: campaign
created: 2026-07-13
last_used: 2026-07-14
hit_count: 4
source: create-p:material-reminder-batch-send
severity: P1
---

动态批量发送 scheduler 若只在 cron 字符串变化时重排 future，会漏掉“沿用原 cron、仅把 autoEnabled 从 false 改为 true”的首次启用场景，导致配置显示已启用但进程内没有对应定时任务。

正确做法：cron 或 autoEnabled 任一变化都发布重排事件；scheduler 必须按稳定调度键维护 future（单例配置可用发送类型，配置列表必须用 configId），取消旧 future 后为当前 enabled 配置重新注册。新增、软删除同样触发重排；取消使用 `cancel(false)`，不得中断已运行任务。

关联位置：配置 service 的 create/update/setEnabled/softDelete、`BatchSendScheduler`。
