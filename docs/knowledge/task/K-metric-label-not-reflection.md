---
id: K-metric-label-not-reflection
domain: task
created: 2026-08-16
last_used: 2026-08-16
hit_count: 0
source: create-p:p1-task-type-catalog-semantics
severity: P1
---

经验：任务记录页"发信统计/成功数"这一列对多数任务显示的是**假数据**，根因是 `TaskExecutionService.kt` 末尾的 `TaskResultSummary.from()` **靠反射猜字段名**：

```kotlin
successCount = firstInt(fields, "sent", "replied", "accepted", "fetched", "dispatched")
failureCount = firstInt(fields, "manualReview", "skipped", "failureCount")
```

- `EXPERT_ENRICHMENT` 的结果字段是 `enriched` / `failed` —— 两侧**全不命中** → 恒显示 `0/0`。这个 0 的含义是"反射没匹配上"，不是"真的处理了 0 个"。
- `AUTO_REPLY_ALL` 显示的 `4/0` 实际是"轮询了 4 个账号"，与列名"发信"毫无关系。

只有实现了 `TaskExecutionSummaryProvider` 的结果类（如 `ManualOutreachResult`）走 provider 分支，不受影响。

**正确做法**：
1. **展示侧**一律以 `TaskTypeCatalog.metricLabel` 为准；`metricLabel = null` 即渲染「— 无统计」，**禁止**为了让每行都有数字而编造语义标签，也禁止回落显示 `successCount/failureCount`。
2. **新写的任务结果类**应直接实现 `TaskExecutionSummaryProvider`，不要依赖反射名单（新结果类的字段名几乎必然不在那 8 个词里）。
3. 反射机制本身**保留**用于写入侧的 status 判定，动它会波及 23 个 `runAndRecord` 调用点。

关联：[[K-task-type-semantics-three-lists]]、[[K-circuit-breaker-terminal-status]]
