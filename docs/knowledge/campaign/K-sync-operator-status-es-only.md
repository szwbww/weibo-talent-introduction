---
id: K-sync-operator-status-es-only
domain: campaign
created: 2026-08-18
last_used: 2026-08-18
hit_count: 0
source: create-p:bounce-dsn-classification-and-email-invalid-writeback
severity: P1
---
经验：`ExpertIndexWriterService.syncOperatorStatus()`（`:67-107`）与
`syncOperatorStatusBatch()`（`:114`）**只更新 RAW/CANDIDATE/APPLICATION 三层 ES 索引，
不写 MySQL `expert_contact`**。
证据：`grep -n "expertContactRepository" ExpertIndexWriterService.kt` 零输出。

方法名带 "sync" 极易被误读为"两边同步"。任何"状态要在**批量发送过滤**中生效"的路径
**必须双写**：`expertContactRepository.save(contact.copy(operatorStatus = ...))` **加**
`syncOperatorStatus(...)`——因为过滤读的是 MySQL：
`ManualInitialOutreachService.buildRetryableTargets():995` 的
`it.operatorStatus != "EMAIL_INVALID"` 走 `expertContactRepository`。

线上事故（2026-08-18 实测）：`BounceCollectionService.ingest():109` 硬退信只调
`syncOperatorStatus(...)`（ES-only），漏了 MySQL 那一半 → ES/MySQL 脑裂 →
`expert_contact` 中 `EMAIL_INVALID` 计数为 **0**（2112 封 INTRODUCTION 已发送），
死地址永远不被重试队列排除。正确参照系是发送侧
`ManualInitialOutreachService:761-764`，那里是标准两写形状。

关联：K-operator-status-single-writer（写出口收敛）、K-operator-status-write-seam-guard（守卫白名单）。
