---
id: K-workbench-state-lazy-expiry
domain: llm
created: 2026-08-20
last_used: 2026-08-20
hit_count: 1
source: create-p:workbench-operator-instruction-authorizes-actions
severity: P1
---

# `trust_reply_workbench_state` 的过期是惰性的，`load()` 会读到过期行

`TrustReplyWorkbenchStateStore.load()`（`TrustReplyWorkbenchStateStore.kt:31-45`）的 SQL **不带 `expires_at` 过滤**：

```sql
SELECT state_version, payload_json, expires_at
  FROM trust_reply_workbench_state
 WHERE source_type = :sourceType AND source_id = :sourceId
```

清理只在两个地方发生：`save()` 内的 `pruneExpired(now)`（`:56`），以及
`TrustReplyWorkbenchService.restoreSavedStateWithFrame()` 命中过期分支时（`TrustReplyWorkbenchService.kt:594`）。
两者都不是定时任务——**一行快照可以在库里过期后长期存在并被 `load()` 正常读出**。
TTL 是 `EXPIRY_DAYS = 30L`（`:190`），即最坏情况能读到 30 天前的锁定集合。

## 任何新增读路径必须自己判过期

既有的唯一读链路（`bootstrap` → `restoreSavedStateWithFrame`）是显式判的：

```kotlin
if (!stored.expiresAt.isAfter(now)) {
    stateStore.pruneExpired(now)
    return RestoreFrameResult(TrustReplySavedState(status = "EXPIRED", stateVersion = 0), ...)
}
```

新读路径照抄这个判据即可（`stored.expiresAt.isAfter(LocalDateTime.now())`），但**只读的路径不要顺手调
`pruneExpired`**——它是写操作，会让一次纯读取产生 DELETE。

## 为什么这件事容易踩

`load()` 返回类型 `TrustReplyStoredState` 带 `expiresAt` 字段，很像"已经过滤过、字段只是顺带返回"。
实际相反：字段返回给调用方**就是**因为过滤责任在调用方。写新读路径时若只 `load()` + `decodePayload()`
就直接用 payload，等于用一份可能三十天前的运营决策去影响今天的判定。

关联：[[K-workbench-lock-replay-needs-dedicated-state-store]]
