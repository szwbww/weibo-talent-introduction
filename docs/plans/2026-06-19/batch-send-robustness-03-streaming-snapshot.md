# 子计划 03：流式快照（分页拉取替代全量内存）

> 主计划：`2026-06-19-batch-send-robustness-00-master.md`。共享不变量见主计划。

## 需求描述

- 可观察结果：`ManualInitialOutreachService` 不再一次性将全部待发专家加载到内存。改为按轮按需拉取，内存占用与 `roundSize` 成正比而非与候选池总量成正比。5 万候选池场景下内存占用从 ~200MB 降至 ~5MB。
- 不可改变：防重语义（R-3 / I-7）完全保持。发送顺序可变（不保证与原全量快照一致，但不影响业务）。
- 不做：ES scroll 游标的断点续传（中断后重头开始）。

## 关键不变量（引用 + 专属）

- 引用 R-3（流式快照与防重等价）。
- Invariant L3-1：retryable 优先。与原实现一致，先处理 retryable 联系人（NEW 状态且无 SENT 记录），再处理 ES 新候选。retryable 列表较小（通常 < 100），允许一次性加载。
- Invariant L3-2：`seenOrcids` 集合跨页持续。该集合只存 ORCID 字符串（~20 字节/条），即使 10 万条也仅 ~2MB，可接受。
- Invariant L3-3：`totalCount` 预估。由于不再预加载全量，`totalCount` 改为 retryable 数量 + ES count 查询的估算值。进度百分比变为近似值，不影响功能。

## 现状审计

`ManualInitialOutreachService.buildSnapshot()`（L376-413）：
1. 加载 retryable（NEW 状态联系人） → 按 ORCID 从 ES 查 profile → 加入 snapshot。
2. 调用 `expertSearchService.scrollExpertsFiltered()` 全量滚动 → 逐条加入 snapshot。
3. 返回 `List<Pair<ExpertContact?, ExpertProfile>>`，调用方在 `while (index < snapshot.size)` 循环中逐条发送。

问题：步骤 2 将全部 ES 候选（可能数万条）加载到 `snapshot` 列表。

## 实现方案

### 设计思路

将 `buildSnapshot()` 拆为两阶段：
1. **Retryable 阶段**：保持不变，一次性加载（通常 < 100 条）。
2. **ES 新候选阶段**：改为延迟迭代器模式。每轮开始时按 `roundSize` 从 ES 拉取一页，处理完后再拉下一页。

核心抽象：引入 `OutreachTargetIterator`，封装两阶段的顺序迭代。

### 任务 1：定义 `OutreachTargetIterator`

新文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIterator.kt`

```kotlin
/**
 * 延迟迭代器：先返回 retryable 联系人，再按页从 ES 拉取新候选。
 * seenOrcids 跨整个迭代生命周期保持，保证 R-3 防重等价。
 */
class OutreachTargetIterator(
    retryableTargets: List<Pair<ExpertContact?, ExpertProfile>>,
    private val pageSize: Int,
    private val seenOrcids: MutableSet<String>,
    private val fetchNextPage: (offset: Int, size: Int) -> List<ExpertProfile>
) : Iterator<Pair<ExpertContact?, ExpertProfile>> {

    private val retryableIterator = retryableTargets.iterator()
    private var esBuffer: MutableList<Pair<ExpertContact?, ExpertProfile>> = mutableListOf()
    private var esBufferIndex = 0
    private var esOffset = 0
    private var esExhausted = false

    override fun hasNext(): Boolean {
        if (retryableIterator.hasNext()) return true
        if (esBufferIndex < esBuffer.size) return true
        if (esExhausted) return false
        loadNextEsPage()
        return esBufferIndex < esBuffer.size
    }

    override fun next(): Pair<ExpertContact?, ExpertProfile> {
        if (retryableIterator.hasNext()) return retryableIterator.next()
        if (esBufferIndex >= esBuffer.size && !esExhausted) loadNextEsPage()
        if (esBufferIndex >= esBuffer.size) throw NoSuchElementException()
        return esBuffer[esBufferIndex++]
    }

    private fun loadNextEsPage() {
        val page = fetchNextPage(esOffset, pageSize)
        esOffset += page.size
        if (page.size < pageSize) esExhausted = true
        if (page.isEmpty()) return

        esBuffer = mutableListOf()
        esBufferIndex = 0
        for (expert in page) {
            val normOrcid = expert.orcidId.trim().uppercase()
            if (seenOrcids.add(normOrcid)) {
                esBuffer.add(Pair(null, expert))
            }
        }
        // 如果整页都被 seenOrcids 过滤掉了，递归加载下一页
        if (esBuffer.isEmpty() && !esExhausted) loadNextEsPage()
    }
}
```

### 任务 2：`ExpertSearchService` 新增分页查询方法

文件：`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt`

新增方法（与 `scrollExpertsFiltered` 并存，不删旧方法）：

```kotlin
fun searchExpertsFiltered(
    level: ExpertIndexLevel,
    filters: Map<String, Any>,
    from: Int,
    size: Int
): List<ExpertProfile>
```

底层用 ES `from+size` 分页（而非 scroll），适用于顺序拉取场景。`from+size` 对于 < 10 万的数据集性能可接受（ES 默认 `index.max_result_window=10000`，需确认配置或改用 `search_after`）。

> **备选方案**：如果候选池 > 10000，改用 `search_after` 分页，避免 ES 深分页性能问题。`fetchNextPage` lambda 内部维护 `search_after` 游标。

### 任务 3：改造 `ManualInitialOutreachService.runScheduledBatch()`

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

核心变更：

1. **删除** `buildSnapshot()` 方法。

2. **新增** `buildRetryableTargets(campaignId)` 方法（原 `buildSnapshot()` 的第一阶段，不变）：
```kotlin
private fun buildRetryableTargets(campaignId: Long): Pair<List<Pair<ExpertContact?, ExpertProfile>>, MutableSet<String>> {
    val seenOrcids = mutableSetOf<String>()
    val targets = mutableListOf<Pair<ExpertContact?, ExpertProfile>>()
    // ... 原 buildSnapshot() L381-394 的逻辑，不变
    return Pair(targets, seenOrcids)
}
```

3. **改造** `runScheduledBatch()` 入口：
```kotlin
// 原: val snapshot = buildSnapshot(campaignId); val totalCount = snapshot.size
// 新:
val (retryableTargets, seenOrcids) = buildRetryableTargets(campaignId)
val esEstimate = expertSearchService.countExperts(
    level = ExpertIndexLevel.CANDIDATE,
    filters = ExpertSearchService.notContactedWithEmailFilters()
).toInt()
val totalEstimate = retryableTargets.size + esEstimate

val targetIterator = OutreachTargetIterator(
    retryableTargets = retryableTargets,
    pageSize = config.roundSize * 2,  // 预取 2 轮的量，减少 ES 请求次数
    seenOrcids = seenOrcids,
    fetchNextPage = { offset, size ->
        expertSearchService.searchExpertsFiltered(
            level = ExpertIndexLevel.CANDIDATE,
            filters = ExpertSearchService.notContactedWithEmailFilters(),
            from = offset, size = size
        )
    }
)
```

4. **改造主循环**：从 `while (index < snapshot.size)` 改为 `while (targetIterator.hasNext())`。每轮取 `roundQuota` 个目标：
```kotlin
while (targetIterator.hasNext()) {
    // 取消检查、round gate、quota 计算（不变）...
    var roundSent = 0
    while (roundSent < roundQuota && targetIterator.hasNext()) {
        val (existingContact, expert) = targetIterator.next()
        // ... 发送逻辑不变
        roundSent++
    }
    // round 结束处理（不变）...
}
```

5. **进度更新**：`totalCount` 使用 `totalEstimate`，`remaining` 使用 `totalEstimate - processedTotal`（近似值，L3-3）。

### 任务 4：清理

- 删除 `buildSnapshot()` 方法。
- `scrollExpertsFiltered()` 方法保留（其他地方可能用到），但 `runScheduledBatch` 不再调用。

### 任务 5：测试

文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/OutreachTargetIteratorTest.kt`

- retryable 优先返回（L3-1）。
- ES 分页：第一页 5 条，第二页 3 条（< pageSize），`hasNext()` 正确终止。
- `seenOrcids` 跨 retryable 和 ES 页去重（R-3）。
- 整页被过滤后自动加载下一页。
- 空候选池 → `hasNext()` 立即返回 false。

文件：补充 `ManualInitialOutreachServiceTest` 集成级测试：
- 3 个 retryable + 7 个 ES 候选，`roundSize=5` → 第一轮 5 条（3 retryable + 2 ES），第二轮 5 条（5 ES）。
