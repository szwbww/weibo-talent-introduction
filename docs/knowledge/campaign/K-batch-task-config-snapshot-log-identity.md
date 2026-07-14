---
id: K-batch-task-config-snapshot-log-identity
domain: campaign
created: 2026-07-14
last_used: 2026-07-14
hit_count: 5
source: create-p:batch-send-task-console
severity: P1
---

经验：当批量发送从“每类型一份配置”扩展为可增删改查的配置列表时，继续使用 KV 会丢失稳定身份，无法可靠支持名称唯一、软删除、调度 future 和配置级日志。

正确做法：配置实体化并使用稳定 configId；删除采用软删除；execution 保存 nullable configId 外键和完整启动快照。运行中只能消费启动快照，不能重新读取可变配置。这样配置编辑不污染在途任务，配置删除不破坏历史日志，独立手动执行可用 null configId 与配置日志隔离。

避免：硬删除配置、execution 只存类型、运行循环中反复读取最新配置、用任务名称作为日志归属键。
