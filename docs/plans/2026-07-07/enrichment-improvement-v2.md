# 补充学术数据（Enrichment）全面改进计划 v2

> 替代 `enrichment-improvement.md`，覆盖全部已讨论的改进项。

## 需求描述

**可观测结果：**
1. 点击"补充学术数据"先弹 CONFIG 弹窗（展示待补充数量 + 历史执行记录），确认后才执行
2. 任务处理全部待补充专家（不限 500），处理完才结束
3. 弹窗内支持"暂停"按钮；暂停后可"继续执行"（利用过滤查询天然断点续跑）
4. 不再对 APPLICATION 层产生 404 WARN 日志
5. 失败原因在进度详情和批次日志中分类展示（如 ORCID_NOT_IN_OPENALEX、API_ERROR 等）
6. 修复 progressStore 状态泄漏导致的"长期初始化中"问题

**不变的行为：**
- enrichment 数据来源仍为 OpenAlex（`enrichAuthorByOrcid`）
- 补充字段不变：hIndex, citationCount, worksCount, researchFields, recentWorkTitles, patentTitles, enrichedAt, enrichmentSource
- 30 天内已补充的专家仍跳过（改为 ES 过滤实现）
- 任务进度通过 `TaskProgressStore` 实时上报，弹窗轮询展示

**不在范围内：**
- 改用 ES `_bulk` API 批量更新（后续优化）
- 修改 enrichment 数据字段或来源
- 前端侧栏 Tab 变更
- 定时调度（本计划只实现手动触发 + 暂停恢复，定时可后续独立加）

## 关键不变量

### Invariant I-1: 只更新文档存在的索引层
- Rule: `updateExpertAcademicFields()` 在更新某一层索引前，必须先 HEAD 检查文档存在性；仅对存在的层执行 `_update`。不存在的层直接跳过且不产生任何日志。
- Applies to: `ExpertDiscoveryService.updateExpertAcademicFields()`
- Violation consequence: 每个专家产生 1-2 条 404 WARN 日志
- 来源: original（修正 K-enrichment-write-three-layers）

### Invariant I-2: Enrichment 扫描使用过滤查询
- Rule: `enrichExistingExperts()` 必须使用 ES 过滤查询（`must_not exists enrichedAt` OR `range enrichedAt < now-30d`）来精确获取需要补充的专家，不得使用 `match_all` 全量扫描。
- Applies to: `ExpertDiscoveryService.enrichExistingExperts()`
- Violation consequence: 全量扫描浪费时间和 ES scroll 资源
- 来源: original

### Invariant I-3: 前端入口走 CONFIG 弹窗
- Rule: `handleEnrichExperts()` 必须走 `openTaskLaunchModal("EXPERT_ENRICHMENT")` 路径。用户在 CONFIG 弹窗点击"开始执行"后才发 POST 请求。
- Applies to: `app.js` 中 `handleEnrichExperts()`, `taskLaunchConfigs.EXPERT_ENRICHMENT`
- Violation consequence: 用户只想看历史日志时意外触发执行
- 来源: original

### Invariant I-4: 后端并发互斥 + 状态保底清理
- Rule: `enrichExperts()` 接口必须使用 `progressStore.tryStartWithToken()` 防重复提交。`finally` 块必须确保在任何退出路径下（包括 OpenAlex 未启用、异常）progressStore 的状态不会遗留为 RUNNING。
- Applies to: `ExpertDiscoveryController.enrichExperts()`
- Violation consequence: 重复提交 / progressStore 遗留 RUNNING 导致永远"初始化中"
- 来源: original（根因一修复）

### Invariant I-5: 失败原因分类追踪
- Rule: `enrichExistingExperts()` 必须对每个处理失败的专家记录失败原因到 `failureReasons: Map<String, Int>`，并将其写入 `progressStore.update()` 的 details 和每批的 `batchRejectReasons`。`enrichAuthorByOrcid()` 的返回值必须区分"未找到"和"API 异常"。
- Applies to: `OpenAlexDataSource.enrichAuthorByOrcid()`, `ExpertDiscoveryService.enrichExistingExperts()`
- Violation consequence: 失败原因不可见，运营无法判断是正常(查不到)还是异常(API 挂了)
- 来源: original

### Invariant I-6: 不限数量，全部完成才结束
- Rule: `enrichExistingExperts()` 不再有 `maxExperts` 上限参数。任务遍历全部待补充专家直到 scroll 结束或被暂停/取消。`totalCount` 使用 `countExperts(CANDIDATE, filters)` 的真实值。
- Applies to: `ExpertDiscoveryService.enrichExistingExperts()`, `ExpertDiscoveryController.enrichExperts()`
- Violation consequence: 只处理 500 个就停止，待补充积压越来越大
- 来源: original

### Invariant I-7: 暂停 = 取消，恢复 = 重启（幂等）
- Rule: 暂停通过已有的 `progressStore.requestCancel()` / `isCancelled()` 机制实现，任务在当前批次结束后以 CANCELLED 状态退出。恢复时重新调 POST 接口，由于 I-2 的过滤查询天然跳过已补充的专家，等价于从断点续跑。前端取消按钮文案改为"暂停"，终态后显示"继续执行"。
- Applies to: `app.js` 弹窗 UI, `ExpertDiscoveryService.enrichExistingExperts()`
- Violation consequence: 需要引入 PAUSED 状态和 scroll 位置持久化（复杂度不必要）
- 来源: original

## 现状审计

### CANDIDATE ES 索引（enrichment 主要读写对象）

- **Schema/mapping**: `dynamic: false`，enrichment 字段已声明：`enrichedAt`(keyword), `enrichmentSource`(keyword), `hIndex`(integer), `citationCount`(integer), `worksCount`(integer), `researchFields`(text), `recentWorkTitles`(text[]), `patentTitles`(text[]) (来源: K-es-dynamic-false)
- **Write paths (enrichment):**
  1. `ExpertDiscoveryService.updateExpertAcademicFields()` L841-868 — partial update (`_update`)，写 hIndex/citationCount/worksCount/researchFields/recentWorkTitles/patentTitles/enrichedAt/enrichmentSource (来源: K-enrichment-write-three-layers)
  2. `ExpertDiscoveryService.promoteDiscoveredToCandidate()` L770-787 — 新专家晋升全量写入（不含 enrichment 字段）
- **Read paths (enrichment):**
  1. `ExpertSearchService.scrollExperts()` L142-200 — `match_all` + scroll，`sourceFields()` 含 enrichedAt
  2. `ExpertSearchService.scrollExpertsFiltered()` L380-444 — 支持 filter 参数的 scroll
  3. `ExpertSearchService.countExperts()` L360-378 — `_count` API 按 filter 计数
  4. `ExpertSearchService.searchExperts()` — 前端列表，支持 hIndexMin/citationCountMin 筛选
- **Interaction points:**
  - `enrichExistingExperts()` 的 scroll 方式选择（read path 1 vs 2）直接影响性能
  - `updateExpertAcademicFields()` 写三层 → APPLICATION 404 → 日志

### RAW / APPLICATION ES 索引

- `updateExpertAcademicFields()` 循环写 RAW → CANDIDATE → APPLICATION，APPLICATION 层大多数专家不存在 → 404 WARN
- RAW 层始终有对应文档

### TaskProgressStore（内存 + DB 日志）

- `store: ConcurrentHashMap` 内存缓存
- `clearExecutionContext()` L57-74 — 只清 executionId，**不清 status**。若 `enrichExistingExperts` 未显式设 COMPLETED 就退出，store 遗留 RUNNING
- `restoreFromLog()` L204-246 — 从 DB 恢复时把 RUNNING 映射为 INTERRUPTED
- `tryStartWithToken()` L145-156 — EXPERT_DISCOVERY 用此防并发，EXPERT_ENRICHMENT 当前未用

### TaskProgressController

- `allowedTaskTypes` L33 = `{"EXPERT_REVALIDATION", "RAW_PROMOTION_SCAN", "EXPERT_DISCOVERY", "MANUAL_INITIAL_OUTREACH", "CHECK_REPLIES"}` — **不含 EXPERT_ENRICHMENT**
- `GET /executions` 和 `parseResultSummary` 均基于此白名单过滤
- **结果：执行历史接口返回 400，前端永远看不到执行记录**

### OpenAlexDataSource.enrichAuthorByOrcid() L163-176

- 返回 `AuthorEnrichment?`，null 时无法区分"未找到"和"API 异常"
- 内部已能区分：`searchResponse.results[0]` 为空 = 未找到；catch 异常 = API 错误。只是统一吞掉返回 null

### ExpertDiscoveryController.enrichExperts() L205-226

- 同步阻塞 HTTP 线程
- 无 `tryStartWithToken()` 互斥
- `finally` 用 `clearExecutionContext`（只清 executionId 不清 status）

### 前端 handleEnrichExperts() (app.js L3786-3829)

- 未注册到 `taskLaunchConfigs`，跳过 CONFIG 阶段
- 直接 `openTaskModal + POST`，无确认步骤
- 进度轮询的 `updateTaskModalFromProgress()` L758-766 已支持 `details.bySource` 表格和 L768-773 的 `filterReasons`/`demotionReasons` 表格渲染 — **enrichment 的 `failureReasons` 可复用此机制**

### 前端 renderBatchTable() (app.js L853-897)

- 每行展示 `batchRejectReasonsJson` 的 top 3 原因 — enrichment 的批次失败原因可直接走此通道

## 实现方案

### 阶段一：后端核心修复

#### Task 1: OpenAlexDataSource — enrichAuthorByOrcid 返回失败原因 [I-5]

**文件**: `src/main/kotlin/.../discovery/service/OpenAlexDataSource.kt`

1a. 在文件末尾（`AuthorEnrichment` data class 旁边）新增结果密封类：
```kotlin
sealed class EnrichmentOutcome {
    data class Success(val data: AuthorEnrichment) : EnrichmentOutcome()
    object NotFound : EnrichmentOutcome()
    data class ApiError(val message: String) : EnrichmentOutcome()
}
```

1b. 新增 `enrichAuthorByOrcidWithReason(orcid: String): EnrichmentOutcome` 方法，逻辑与 `enrichAuthorByOrcid` 相同但返回 `EnrichmentOutcome`：
- 搜索响应中 `results[0]` 不存在 → `NotFound`
- `enrichAuthor(authorId)` 返回 null → `NotFound`
- `enrichAuthor(authorId)` 返回非 null → `Success(data)`
- catch 异常 → `ApiError(e.message)`

1c. 保留原 `enrichAuthorByOrcid` 不删（其他调用点可能用到），但 `enrichExistingExperts` 改用新方法。

**读取此数据的路径**: `ExpertDiscoveryService.enrichExistingExperts()` (Task 2)

#### Task 2: ExpertDiscoveryService — 过滤查询 + 去掉上限 + 失败原因追踪 + 暂停支持 [I-2, I-5, I-6, I-7]

**文件**: `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt`

2a. 新增 `buildEnrichmentFilters()` 私有方法：
```kotlin
private fun buildEnrichmentFilters(): List<Map<String, Any>> {
    val thirtyDaysAgo = LocalDateTime.now().minusDays(30).format(dateFormatter)
    return listOf(
        mapOf("bool" to mapOf(
            "should" to listOf(
                mapOf("bool" to mapOf("must_not" to listOf(mapOf("exists" to mapOf("field" to "enrichedAt"))))),
                mapOf("range" to mapOf("enrichedAt" to mapOf("lt" to thirtyDaysAgo)))
            ),
            "minimum_should_match" to 1
        ))
    )
}
```

2b. 新增 `getEnrichmentStats()` 公开方法：
```kotlin
data class EnrichmentStats(val pending: Long, val enrichedLast30d: Long, val total: Long)

fun getEnrichmentStats(): EnrichmentStats {
    val total = expertSearchService.countExperts(ExpertIndexLevel.CANDIDATE)
    val pending = expertSearchService.countExperts(ExpertIndexLevel.CANDIDATE, buildEnrichmentFilters())
    return EnrichmentStats(pending, total - pending, total)
}
```

2c. 重写 `enrichExistingExperts()`：
- 去掉 `maxExperts` 参数（或改为 `maxExperts: Int = 0` 表示不限）
- 先 `countExperts(CANDIDATE, buildEnrichmentFilters())` 获取 pending 总数作为 `totalCount`
- 用 `scrollExpertsFiltered(ExpertIndexLevel.CANDIDATE, buildEnrichmentFilters())` 替代 `scrollExperts`
- 移除内存中的 `enrichedAt` 过期检查（已由 ES 过滤完成）
- 新增 `failureReasons: MutableMap<String, Int>` 追踪失败原因
- 在 scroll 回调中使用 `enrichAuthorByOrcidWithReason()` 并按 `EnrichmentOutcome` 分支计数：
  - `NO_ORCID_ID` — esDocId 以 EMAIL- 开头
  - `ORCID_NOT_IN_OPENALEX` — `NotFound`
  - `OPENALEX_API_ERROR` — `ApiError`
  - `ES_UPDATE_FAILED` — `updateExpertAcademicFields` 返回 false
- 每个专家处理后检查 `progressStore.isCancelled("EXPERT_ENRICHMENT")`，true 时退出 scroll
- `progressStore.update()` 的 details 包含 `failureReasons`
- 每批上报 `batchRejectReasons`（本批的增量失败原因）
- `EnrichmentResult` 增加 `failureReasons` 字段

2d. 修改 `updateExpertAcademicFields()` [I-1]：
- 循环中在 `_update` 前 HEAD 检查文档存在性
- 不存在直接 continue，不 log

#### Task 3: ExpertDiscoveryController — 互斥 + 状态清理 + stats 端点 [I-4, I-6]

**文件**: `src/main/kotlin/.../discovery/controller/ExpertDiscoveryController.kt`

3a. 新增 `GET /api/expert-discovery/enrich/stats` 端点，调用 `discoveryService.getEnrichmentStats()`

3b. 重写 `POST /api/expert-discovery/enrich`：
- 加入 `tryStartWithToken("EXPERT_ENRICHMENT", ...)` 互斥，未获得则返回 409
- `onStarted` 改用 `bindExecutionId`（与 EXPERT_DISCOVERY 同模式）
- `finally` 块：
  - 如果 enrichExistingExperts 正常完成 → progressStore 已被设为 COMPLETED/CANCELLED（在 Task 2c 中保证），只清 executionContext
  - 如果异常退出 → 显式 `progressStore.update(..., status="FAILED")`，然后清 executionContext
  - 最终兜底：如果 store 中仍然是 RUNNING → 强制 `progressStore.clear("EXPERT_ENRICHMENT")`

#### Task 4: TaskProgressController — 白名单补齐 + ENRICHMENT 结果解析 [I-4 相关]

**文件**: `src/main/kotlin/.../task/controller/TaskProgressController.kt`

4a. `allowedTaskTypes` 加入 `"EXPERT_ENRICHMENT"`

4b. `parseResultSummary` 增加 `"EXPERT_ENRICHMENT"` 分支：
```kotlin
"EXPERT_ENRICHMENT" -> {
    val enriched = root.path("enriched").asLong(0)
    val failed = root.path("failed").asLong(0)
    ExecutionTotals(
        totalProcessed = enriched + failed,
        totalPassed = enriched,
        totalRejected = failed
    )
}
```

4c. `fallbackFromLog` 增加 `"EXPERT_ENRICHMENT"` 分支（同样从 details 中读 enriched/failed）

### 阶段二：前端

#### Task 5: app.js — CONFIG 弹窗 + 暂停恢复 + 失败原因展示 [I-3, I-5, I-7]

**文件**: `src/main/resources/static/app.js`

5a. `filterReasonLabels` 对象新增 enrichment 失败原因的中文映射：
```javascript
NO_ORCID_ID: "无 ORCID ID",
ORCID_NOT_IN_OPENALEX: "OpenAlex 未收录此 ORCID",
OPENALEX_API_ERROR: "OpenAlex API 错误",
ES_UPDATE_FAILED: "ES 更新失败"
```

5b. `taskLaunchConfigs` 新增 `EXPERT_ENRICHMENT`：
```javascript
EXPERT_ENRICHMENT: {
    title: "补充学术数据（OpenAlex）",
    desc: "正在加载...",
    btnId: "discoverBtn",
    showKeyword: false,
    showMaxPromotions: false,
    preload: async () => {
        const stats = await api("/api/expert-discovery/enrich/stats");
        const desc = `CANDIDATE 层共 ${stats.total.toLocaleString()} 人，` +
            `其中 ${stats.pending.toLocaleString()} 人待补充学术数据` +
            (stats.enrichedLast30d > 0 ? `（${stats.enrichedLast30d.toLocaleString()} 人已在 30 天内补充）` : '') + '。';
        return { desc, canRun: stats.pending > 0 };
    },
    run: executeEnrichExperts
}
```

5c. 重写 `handleEnrichExperts()`：
```javascript
async function handleEnrichExperts() {
    const taskType = "EXPERT_ENRICHMENT";
    const running = await isTaskRunning(taskType);
    if (running) {
        openTaskModal(taskType, "补充学术数据（OpenAlex）", "discoverBtn", { knownActiveAtOpen: true });
        return;
    }
    openTaskLaunchModal(taskType);
}
```

5d. 新增 `executeEnrichExperts()` — 参照 `executeRevalidate` 模式：
- `progressStoreHasRunningTask()` 检查
- `openTaskModal(... { launchRequested: true })`
- `POST /api/expert-discovery/enrich`
- 成功后 `bindTaskModalExecution` + `markTaskWatcherLaunchSucceeded` + `notifyTaskCompletionOnce`
- 409 错误 → "任务正在执行中"

5e. `updateTaskModalFromProgress()` 增加 `details.failureReasons` 渲染：
- 在现有 `details.filterReasons` / `details.demotionReasons` 判断之后，增加：
```javascript
if (progress.details && progress.details.failureReasons != null) {
    messageEl.innerHTML = escapeHtml(progress.message || "") +
        renderFilterReasonsTable(progress.details.failureReasons);
}
```
- `renderFilterReasonsTable` 的表头 caption 当前硬编码为"过滤原因分布"，可改为接受参数或为 enrichment 专门渲染。**简单方案**：直接复用，因为 `filterReasonLabels` 已包含 enrichment 的 key 映射，表格内容能正确中文化。

5f. 暂停/恢复的 UI 交互：
- 取消按钮文案：在 `updateTaskModalFromProgress` 中，当 `taskType === "EXPERT_ENRICHMENT"` 且 `status === "RUNNING"` 时，把取消按钮文案从"取消任务"改为"暂停"
- 终态后处理：当 enrichment 以 CANCELLED 状态完成时，在 `openTaskLaunchModal` 的 CONFIG 视图中，`preload` 返回的 `canRun` 反映还有未完成的 pending → 用户可以点"开始执行"继续

## 变更文件清单

| # | 文件 | 变更内容 |
|---|------|----------|
| 1 | `src/main/kotlin/.../discovery/service/OpenAlexDataSource.kt` | Task 1: 新增 `EnrichmentOutcome` 密封类 + `enrichAuthorByOrcidWithReason()` |
| 2 | `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt` | Task 2: 过滤查询 + 去掉上限 + failureReasons + HEAD 检查 + getEnrichmentStats() |
| 3 | `src/main/kotlin/.../discovery/controller/ExpertDiscoveryController.kt` | Task 3: tryStartWithToken + 状态清理 + GET /enrich/stats |
| 4 | `src/main/kotlin/.../task/controller/TaskProgressController.kt` | Task 4: allowedTaskTypes + parseResultSummary + fallbackFromLog |
| 5 | `src/main/resources/static/app.js` | Task 5: taskLaunchConfigs + handleEnrichExperts + executeEnrichExperts + failureReasons 渲染 + 暂停文案 |

共 **5 个文件**，涉及 **2 个子系统**（后端 enrichment 流程 + 前端弹窗）。

## 验收标准

- **I-1**: 对一个只存在于 RAW + CANDIDATE 的专家执行 enrichment，日志中无 APPLICATION 层 404 WARN。`grep "Failed to update academic fields.*APPLICATION" logs` 为空。
- **I-2**: 启动 enrichment 后，观察 ES 请求日志或 `scanned` 计数 ≈ `pending` 数（而非全量 CANDIDATE 数）。
- **I-3**: 点击"补充学术数据" → CONFIG 弹窗弹出，展示"共 N 人待补充"；不点"开始执行"则浏览器 Network 面板中无 POST 请求。弹窗底部可见历史执行记录列表。
- **I-4**: 快速两次点击"开始执行"→ 第二次返回"任务正在执行中"提示。另外：OpenAlex 未启用时，enrichment 应以 COMPLETED(0,0) 结束而非遗留 RUNNING。
- **I-5**: 执行 enrichment 后，进度弹窗中 message 下方展示失败原因分布表（如"OpenAlex 未收录此 ORCID: 312"）。展开批次日志行，rejectReasons 列显示该批次的失败原因 top 3。
- **I-6**: 有 2000 人待补充时，任务不在 500 处停止，继续处理直到全部完成。`totalCount` 和百分比反映真实的 pending 总数。
- **I-7**: 运行中点击"暂停"→ 当前批次结束后状态变为 CANCELLED → 取消按钮消失 → 再次点击入口 → CONFIG 弹窗显示剩余待补充数 → 点击"开始执行" → 任务从剩余部分继续（已补充的被 ES 过滤跳过）。
- **集成场景**: 点击入口 → CONFIG 弹窗显示"2000 人待补充" → 点击执行 → 进度实时更新 → 暂停(处理到 800) → 再次进入 CONFIG 显示"1200 人待补充" → 继续执行 → 完成 → 进度 100% + 失败原因表 → 日志无 404 WARN。
