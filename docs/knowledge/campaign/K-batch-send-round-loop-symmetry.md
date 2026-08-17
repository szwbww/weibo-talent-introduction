---
id: K-batch-send-round-loop-symmetry
domain: campaign
created: 2026-08-12
last_used: 2026-08-16
hit_count: 1
source: create-p:batch-send-rhythm-01-rounds-per-run
severity: P1
---

`ManualInitialOutreachService` 有**两个结构同构但完全独立**的轮次循环：
`runIntroductionFromSnapshot()`（`:499-819`）与 `runMaterialFromSnapshot()`（`:209-392`）。
二者各自实现取消检查、`roundNumber++`、`runRoundGate()`、`roundQuota = minOf(...)`、
内层发送、`oneRoundOnly` break、轮间 `Thread.sleep(perRoundIntervalMs)`。

任何「改发送节奏」的需求（新增配额维度、改停止条件、改进度字段）**必须对称落到两处**；
只改介绍邮件那一处，材料提醒会静默沿用旧节奏，且因两条路径共用同一 `TASK_TYPE`
单槽位与同一进度存储，症状表现为「偶尔不生效」而非稳定失败，极难定位。

配套的两个陷阱：

1. **进度写入也是两个方法**：`updateProgress()`（`:1030`）与
   `updateProgressWithAccumulator()`（`:1292`）各自维护 `details` map。轮次循环实际
   调用的是后者，前者只服务材料提醒的空快照分支（`:190`）。往 `details` 加字段必须两处都加。
2. **停止时别忘了守卫轮间 sleep**：新增的 break 条件若只加在循环首，循环尾的
   `Thread.sleep(perRoundIntervalMs)` 仍会先睡满（介绍邮件默认 60s、材料提醒默认 120s）
   再回到循环首 break，表现为「任务结束前莫名卡住一到两分钟」。

关联：[[K-batch-task-config-snapshot-log-identity]]、[[K-batch-send-daily-cap-cross-invocation]]
