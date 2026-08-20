---
id: K-workbench-bootstrap-failure-has-no-escape
domain: llm
created: 2026-08-20
last_used: 2026-08-20
hit_count: 0
source: create-p:P0-sse-error-code-and-state-reset
severity: P1
---

# 回复台 bootstrap 一失败，UI 就没有任何自救路径（最长锁死 30 天）

四条约束合起来构成死锁：

1. bootstrap 的 `catch` 只做 `setStatus` + `renderShell`，`renderShell` 渲染的失败界面里**没有任何按钮**。
2. `deleteSavedState()` 首行 `if (state.savedStateVersion <= 0) return true;`，
   而 `state.savedStateVersion` 只在 bootstrap 成功后由 `applySavedState` 填入。
3. `deleteSavedState()` 全仓**只有一个调用点**（`changeRequestFacts` 内），而它本身要求工作台已加载。
4. 手工调 `PUT /state` 传 `lockedItems: []` 也不行：`saveState` 会先跑
   `requireCurrentSourceVersion` 与 `requireCurrentEvidenceVersion`，这两个值同样只有 bootstrap 成功才有。

出路只剩：等 `EXPIRY_DAYS = 30L` 的 TTL，或直接改库。

## `stateStore.delete` 解不开这个锁

```kotlin
fun delete(sourceType: String, sourceId: Long, expectedStateVersion: Long): Boolean {
    if (expectedStateVersion == 0L) return false          // 传 0 是 no-op
    ...
    if (deleted == 1) return true
    throwStateConflict(...)                                // 传错版本抛冲突
}
```
死锁场景下前端**恰恰拿不到当前版本号**。要自救必须新增一个按 `(source_type, source_id)`
无条件删行的方法，而不是给 `delete` 传 0。

## 顺带记一个静默谎言

`saveState` 的 `lockedItems.isEmpty()` 分支丢弃了 `stateStore.delete` 的返回值，
`expectedStateVersion == 0` 时它 `return false`（什么都没删），调用方仍返回 `status = "DELETED"`。
**这条路径永远不报错，也永远不删东西。**

## 哪些失败会走到这里

bootstrap 的 fallback（`resolveCanonicalSelection` 抛 `TrustReplyWorkbenchException` 时退回自动匹配）
**只在 callerSelections == null 时生效**，而且**不覆盖** `restoreSavedStateWithFrame` → `validateLockedSubset`。
所以：
- **事实矩阵类**（`FACT_*` / `REQUEST_KEY_INVALID`）→ 硬刷新页面可恢复（冷启动不发 selections，fallback 生效）。
- **锁定快照类**（`LOCKED_ITEM_INVALID` / `ACKNOWLEDGEMENT_INVALID` / `CLAIM_INVALID` / `ITEM_VERSION_INVALID`）
  → **刷新无效**，因为快照在库里，每次 bootstrap 都重新校验它。这一类才是真死锁。

`validateLockedSubset` 的策略是"证据版本对不上就丢弃并计数，其余校验失败一律抛"——
所以**过严的锁定校验不只让汇总失败，还会让工作台加载不出来**。

关联：[[K-workbench-lock-replay-needs-dedicated-state-store]]、[[K-workbench-state-lazy-expiry]]
