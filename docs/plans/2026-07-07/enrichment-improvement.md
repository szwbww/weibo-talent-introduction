# 补充学术数据（Enrichment）改进计划

## 需求描述

**可观测结果：**
1. 点击"补充学术数据"下拉项后，先弹出 CONFIG 弹窗（展示待补充数量 + 历史执行记录），用户确认后才执行任务
2. 补充任务只扫描真正需要补充的专家（未补充 或 enrichedAt > 30 天），而非全量遍历
3. 更新学术数据时，只写专家实际存在的索引层，不再对 APPLICATION 层产生大量 404 WARN 日志
4. 后端增加并发互斥保护，防止重复提交

**不变的行为：**
- enrichment 数据来源仍为 OpenAlex（`enrichAuthorByOrcid`）
- 补充的字段不变：hIndex, citationCount, worksCount, researchFields, recentWorkTitles, patentTitles, enrichedAt, enrichmentSource
- 30 天内已补充的专家仍跳过
- 任务进度仍通过 `TaskProgressStore` 实时上报，弹窗轮询展示

**不在范围内：**
- 改用 ES `_bulk` API 批量更新（后续优化）
- 修改 enrichment 数据字段或来源
- 前端侧栏 Tab 变更（本计划不涉及视图注册）

## 关键不变量

### Invariant I-1: 只更新文档存在的索引层
- Rule: `updateExpertAcademicFields()` 在更新某一层索引前，必须先 HEAD 检查文档存在性；仅对存在的层执行 `_update`。不存在的层直接跳过，不产生日志。
- Applies to: `ExpertDiscoveryService.updateExpertAcademicFields()`
- Violation consequence: 每个专家产生 1-2 条 404 WARN 日志（当前现状），500 个专家 = 500-1000 条无用日志
- 来源: original（修正 K-enrichment-write-three-layers 的行为）

### Invariant I-2: Enrichment 扫描必须使用过滤查询
- Rule: `enrichExistingExperts()` 必须使用 ES 过滤查询（`must_not exists enrichedAt` OR `range enrichedAt < now-30d`）来精确获取需要补充的专家，不得使用 `match_all` 全量扫描后在内存中逐条判断。
- Applies to: `ExpertDiscoveryService.enrichExistingExperts()`
- Violation consequence: 扫描 5 万条 CANDIDATE 只处理 2000 条，浪费 ES scroll 资源和时间
- 来源: original

### Invariant I-3: 前端点击入口必须先展示 CONFIG 弹窗
- Rule: `handleEnrichExperts()` 必须走 `openTaskLaunchModal("EXPERT_ENRICHMENT")` 路径，不得直接调用 API 执行任务。用户在 CONFIG 弹窗点击"开始执行"后才发 POST 请求。
- Applies to: `app.js` 中 `handleEnrichExperts()`, `taskLaunchConfigs.EXPERT_ENRICHMENT`
- Violation consequence: 用户只想看历史日志时意外触发执行，浪费 API 调用配额
- 来源: original

### Invariant I-4: 后端必须有并发互斥
- Rule: `enrichExperts()` 接口必须使用 `progressStore.tryStartWithToken()` 防止重复提交，与 EXPERT_DISCOVERY 同模式。
- Applies to: `ExpertDiscoveryController.enrichExperts()`
- Violation consequence: 用户连续点击可能触发多个并行 enrichment 任务
- 来源: original

## 现状审计

### CANDIDATE ES 索引（enrichment 主要操作对象）

- **Schema/mapping**: `dynamic: false`，enrichment 相关字段已声明：`hIndex`(integer), `citationCount`(integer), `worksCount`(integer), `researchFields`(text), `recentWorkTitles`(text[]), `patentTitles`(text[]), `enrichedAt`(keyword), `enrichmentSource`(keyword) (来源: K-es-dynamic-false)
- **Write paths（enrichment 相关）:**
  1. `ExpertDiscoveryService.updateExpertAcademicFields()` — partial update (`_update` API)，写 hIndex/citationCount/worksCount/researchFields/recentWorkTitles/patentTitles/enrichedAt/enrichmentSource
  2. `ExpertDiscoveryService.promoteDiscoveredToCandidate()` — 新发现专家晋升时全量写入（不含 enrichment 字段）
  3. `ExpertRevalidationService` — 降级/重新验证，不涉及 enrichment 字段
- **Read paths（enrichment 相关）:**
  1. `ExpertSearchService.scrollExperts()` — 全量遍历，`sourceFields()` 包含 enrichedAt
  2. `ExpertSearchService.scrollExpertsFiltered()` — 支持 filter 参数的遍历
  3. `ExpertSearchService.countExperts()` — 按 filter 计数（`_count` API）
  4. `ExpertSearchService.searchExperts()` — 前端列表查询，支持 hIndexMin/citationCountMin 筛选
  5. `ExpertIndexController` — 前端详情展示 enrichedAt 状态

### RAW / APPLICATION ES 索引

- `updateExpertAcademicFields()` 循环写三层（RAW, CANDIDATE, APPLICATION），APPLICATION 层大多数专家不存在文档 → 404 (来源: K-enrichment-write-three-layers)
- RAW 层始终有对应文档（专家先入 RAW 再晋升 CANDIDATE）

### ExpertDiscoveryService.enrichExistingExperts()

- 当前使用 `scrollExperts(ExpertIndexLevel.CANDIDATE)` = `match_all` 查询
- 内存中逐条判断 `enrichedAt` 是否为 null 或超过 30 天
- `maxExperts` 默认 500，计数 `enriched + failed >= maxExperts` 时停止
- scanned 计数包含所有遍历过的专家（含跳过的）
- 只处理有 ORCID ID 且不以 `EMAIL-` 开头的专家

### ExpertDiscoveryController.enrichExperts()

- 同步执行（阻塞 HTTP 线程）
- 无 `tryStartWithToken()` 互斥保护（与 EXPERT_DISCOVERY 不同）
- `progressStore.setCurrentExecutionId()` 直接设置，无检查

### 前端 handleEnrichExperts()（app.js L3786-3829）

- 仅检查 `isTaskRunning` 判断是否正在运行
- 未运行时直接 `openTaskModal(... { launchRequested: true })` + `POST /api/expert-discovery/enrich`
- 未注册到 `taskLaunchConfigs`，跳过了 CONFIG 弹窗阶段
- 对比其他任务（EXPERT_DISCOVERY/RAW_PROMOTION_SCAN/EXPERT_REVALIDATION）均走 `openTaskLaunchModal` → CONFIG → 用户点击"开始执行" → `execute*()` 的标准流程

### 交互点

1. `enrichExistingExperts()` 读 CANDIDATE 层 → `scrollExperts` / `scrollExpertsFiltered` 选择影响性能
2. `updateExpertAcademicFields()` 写三层索引 → APPLICATION 层 404 → 日志
3. `taskLaunchConfigs` 注册 → `openTaskLaunchModal` → `preload` → stats 接口 → CONFIG 弹窗展示
4. 后端 `tryStartWithToken` → 前端 409 处理

## 实现方案

### 阶段一：后端 — 消除 404 日志 + 精确查询 + 互斥保护

#### Task 1: 修改 `updateExpertAcademicFields()` — 只更新存在的索引层 [I-1]

**文件**: `ExpertDiscoveryService.kt`

将当前的三层循环：
```kotlin
for (level in listOf(RAW, CANDIDATE, APPLICATION)) {
    try { restTemplate._update ... }
    catch (e) { log.warn(...) }  // 产生 404 WARN
}
```

改为：先 HEAD 检查文档是否存在，存在才 `_update`：
```kotlin
for (level in listOf(RAW, CANDIDATE, APPLICATION)) {
    val index = expertIndexService.indexName(level)
    val exists = try {
        restTemplate.exchange("$baseUrl/$index/_doc/$orcidId", HEAD, ...)
        true
    } catch (e: HttpClientErrorException) {
        e.statusCode == HttpStatus.NOT_FOUND -> false
    } catch (e) { false }
    if (!exists) continue
    // _update ...
}
```

#### Task 2: 修改 `enrichExistingExperts()` — 使用过滤查询 + 暴露统计 [I-2]

**文件**: `ExpertDiscoveryService.kt`

2a. 新增 `buildEnrichmentFilters()` 私有方法，构建过滤条件：
```kotlin
private fun buildEnrichmentFilters(): List<Map<String, Any>> {
    val thirtyDaysAgo = LocalDateTime.now().minusDays(30).format(dateFormatter)
    return listOf(
        mapOf("bool" to mapOf("should" to listOf(
            mapOf("bool" to mapOf("must_not" to listOf(mapOf("exists" to mapOf("field" to "enrichedAt"))))),
            mapOf("range" to mapOf("enrichedAt" to mapOf("lt" to thirtyDaysAgo)))
        ), "minimum_should_match" to 1))
    )
}
```

2b. 新增 `getEnrichmentStats()` 公开方法：
```kotlin
fun getEnrichmentStats(): EnrichmentStats {
    val total = expertSearchService.countExperts(ExpertIndexLevel.CANDIDATE)
    val pending = expertSearchService.countExperts(ExpertIndexLevel.CANDIDATE, buildEnrichmentFilters())
    val enrichedRecently = total - pending
    return EnrichmentStats(pending, enrichedRecently, total)
}

data class EnrichmentStats(val pending: Long, val enrichedLast30d: Long, val total: Long)
```

2c. 将 `enrichExistingExperts()` 中的 `scrollExperts(ExpertIndexLevel.CANDIDATE)` 替换为 `scrollExpertsFiltered(ExpertIndexLevel.CANDIDATE, buildEnrichmentFilters())`。同时移除内存中的 `enrichedAt` 过期检查逻辑（已由 ES 过滤完成）。

2d. 在调用 `scrollExpertsFiltered` 前先 `countExperts(CANDIDATE, buildEnrichmentFilters())` 拿到真实 pending 数，用于 `progressStore.update` 的 `totalCount`。

#### Task 3: 修改 `enrichExperts()` 接口 — 增加互斥 + stats 端点 [I-4]

**文件**: `ExpertDiscoveryController.kt`

3a. 新增 `GET /api/expert-discovery/enrich/stats` 端点：
```kotlin
@GetMapping("/enrich/stats")
fun getEnrichmentStats(): EnrichmentStats {
    return discoveryService.getEnrichmentStats()
}
```

3b. 改造 `POST /api/expert-discovery/enrich`，加入 `tryStartWithToken` 互斥保护（参照 `triggerDiscovery` 的模式）：
```kotlin
val (started, token) = progressStore.tryStartWithToken("EXPERT_ENRICHMENT", ...)
if (!started) return 409 Conflict
```

### 阶段二：前端 — CONFIG 弹窗 + 确认执行

#### Task 4: 注册 `EXPERT_ENRICHMENT` 到 `taskLaunchConfigs` [I-3]

**文件**: `app.js`

4a. 在 `taskLaunchConfigs` 对象中新增：
```javascript
EXPERT_ENRICHMENT: {
    title: "补充学术数据（OpenAlex）",
    desc: "正在加载统计信息...",
    btnId: "discoverBtn",
    showKeyword: false,
    showMaxPromotions: false,
    preload: async () => {
        const stats = await api("/api/expert-discovery/enrich/stats");
        const desc = `CANDIDATE 层共 ${stats.total} 人，其中 ${stats.pending} 人待补充学术数据` +
            (stats.enrichedLast30d > 0 ? `（${stats.enrichedLast30d} 人已在 30 天内补充）` : '') + '。';
        return { desc, canRun: stats.pending > 0 };
    },
    run: executeEnrichExperts
}
```

4b. 重写 `handleEnrichExperts()` 为标准模式：
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

4c. 提取原 `handleEnrichExperts` 中的执行逻辑到新函数 `executeEnrichExperts()`：
```javascript
async function executeEnrichExperts() {
    const taskType = "EXPERT_ENRICHMENT";
    const hasRunning = await progressStoreHasRunningTask();
    if (hasRunning) {
        showStatus("已有其他任务正在执行中，请等待完成后再启动新任务", "warn");
        return;
    }
    openTaskModal(taskType, "补充学术数据（OpenAlex）", "discoverBtn", { launchRequested: true });
    const capturedGeneration = currentTaskModal?.generation;
    try {
        const response = await api("/api/expert-discovery/enrich", { method: "POST" });
        // ... 与原逻辑一致：bindTaskModalExecution, markTaskWatcherLaunchSucceeded, notifyTaskCompletionOnce
    } catch (e) {
        if (e.message.includes("正在执行中")) {
            showStatus(e.message, "warn");
            stopTaskWatcher(taskType, true);
            return;
        }
        showStatus(`补充学术数据失败: ${e.message}`, "error");
        showTaskErrorLog(e.message);
        stopTaskModalPolling();
        stopTaskWatcher(taskType, true);
        hideProgressBar();
    }
}
```

## 变更文件清单

| # | 文件 | 变更内容 |
|---|------|----------|
| 1 | `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt` | Task 1: HEAD 检查后再 _update; Task 2: 过滤查询 + getEnrichmentStats() + EnrichmentStats |
| 2 | `src/main/kotlin/.../discovery/controller/ExpertDiscoveryController.kt` | Task 3: GET /enrich/stats 端点 + tryStartWithToken 互斥 |
| 3 | `src/main/resources/static/app.js` | Task 4: taskLaunchConfigs 注册 + handleEnrichExperts 重写 + executeEnrichExperts 新增 |

共 **3 个文件**，涉及 **1 个子系统**（enrichment 流程）。

## 验收标准

- **I-1**: 对一个只存在于 RAW + CANDIDATE 的专家执行 enrichment，日志中不出现 APPLICATION 层的 404 WARN。可通过搜索日志 `Failed to update academic fields.*APPLICATION` 验证为零。
- **I-2**: 启动 enrichment 前调用 `GET /api/expert-discovery/enrich/stats`，返回 `pending` 数量；启动后观察 `scrollExpertsFiltered` 实际扫描数量应接近 `pending` 而非全量 CANDIDATE。对比改进前后的 scanned 计数。
- **I-3**: 点击"补充学术数据"下拉项 → 弹出 CONFIG 弹窗，展示待补充数量 + 历史执行记录。不点"开始执行"则不发 POST 请求（可通过浏览器 Network 面板验证）。关闭弹窗后无副作用。
- **I-4**: 快速连续两次点击"开始执行"，第二次返回 409 + "任务正在执行中" 提示，不产生并行任务。
- **集成场景**: 完整流程 — 点击入口 → 看到 CONFIG（"共 N 人待补充"）→ 点击执行 → 进度条展示真实 pending 总数 → 完成后日志中无 404 WARN → 再次点击入口可看到刚完成的执行记录。
