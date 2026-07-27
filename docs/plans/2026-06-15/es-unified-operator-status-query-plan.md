# 专家管理页统一重构：ES 查询一致性 + 性能优化 + 工具栏整合 — 开发计划

> 本计划交给执行 agent 实施。实施前请通读全文；行号基于 2026-06-15 代码，可能有少量漂移，请以符号名定位。
>
> 本计划包含 7 个阶段，建议按顺序实施：
> 1. ES CANDIDATE 索引冗余 operatorStatus + 同步写入
> 2. `/api/experts` 接口扩展
> 3. 前端统一 ES 查询
> 4. 批量发送性能优化
> 5. 清理 CANDIDATE 无邮箱历史数据
> 6. 存量 operatorStatus 回刷
> 7. 工具栏操作按钮整合

---

## 一、需求描述

### 背景

专家管理页当前存在**两套数据源不一致**的问题：

1. **默认列表**走 ES CANDIDATE 索引（`GET /api/experts`），有分页、排序、tag 筛选，返回 country/employment 等丰富字段。
2. **operatorStatus / needsAttention 筛选**走 MySQL `expert_contact` 表（`GET /api/expert-contacts`），无后端分页（前端切片），缺少丰富字段。

关键矛盾：批量发送介绍邮件显示 ~50013 位待发送专家（ES CANDIDATE 中有邮箱且 MySQL 无 contact 记录），但前端筛选"未联系"显示 0（MySQL 中 `operatorStatus=NOT_CONTACTED` 的记录几乎不存在，因为它是创建→发送之间的瞬态）。

### 目标

将所有筛选统一到 ES 查询，在 CANDIDATE 索引中冗余 `operatorStatus` 字段：

- 无 `operatorStatus` 字段（或值为 null）= 从未联系（NOT_CONTACTED）
- 有值 = 与 MySQL `expert_contact.operatorStatus` 保持同步
- 前端筛选"未联系"时能看到 ES 中全部未联系候选人
- 其他 operatorStatus 筛选（已联系、已回复等）也统一走 ES，保留分页、排序、丰富字段

### 约束

- 不改变现有批量发送逻辑的核心流程（已有实时进度面板 + 可取消机制）
- 不改变 MySQL 作为状态 source of truth 的定位，ES 是只读冗余
- 不改变 APPLICATION 索引的结构（APPLICATION 已有 `currentConversationStatus` 等字段，不在本次范围内）

---

## 二、现状分析

### 2.1 ES CANDIDATE 索引当前字段

`ExpertSearchService.sourceFields()` 返回的字段列表（第 216-228 行）：

```
orcidId, displayName, email, country, nationality, age, degree,
hIndex, citationCount, lastPublicationYear, researchFields, institution,
emailSource, emailVerifiedLevel, dataSource, externalIds, worksCount,
tags, updatedAt
```

**不含** `operatorStatus`、`contactId`、`contactStatus` 等联系状态字段。

### 2.2 当前 `/api/experts` 接口的 MySQL 补充查询

`ExpertIndexController.listExperts()`（第 34-57 行）对 ES 返回的每个专家，**逐个查 MySQL** 补充 contactId/contactStatus/needsManualAttention：

```kotlin
val contact = expert.orcidId
    .takeIf { it.isNotBlank() }
    ?.let(expertContactRepository::findFirstByOrcidIdOrderByUpdatedAtDesc)
```

这意味着每页 50 条就要执行 50 次 MySQL 查询。冗余到 ES 后可以消除这些查询。

### 2.3 operatorStatus 变更的全部入口（3 处）

| # | 位置 | 变更 | 文件 |
|---|------|------|------|
| 1 | 批量发送创建 contact | → `NOT_CONTACTED` | `ManualInitialOutreachService.kt:147` |
| 2 | 批量发送成功后 | → `CONTACTED` | `ManualOutreachTxHelper.kt:44` |
| 3 | 运营手动改状态 | → 任意 OperatorStatus | `ExpertOperatorStatusService.changeStatus():28` |
| 4 | 系统自动升级 | → `REPLIED` 等 | `ExpertOperatorStatusService.updateAutomatically():58` |

### 2.4 现有 ES 写入基础设施

`ExpertIndexWriterService` 已有通过 `_update` API 更新 CANDIDATE/APPLICATION 索引文档的能力（如 `syncApplicationStatus()`、`addTag()`）。新增 `operatorStatus` 同步可复用相同模式。

### 2.5 前端 `loadContacts()` 的两分支逻辑

`app.js` 第 1328-1380 行：

```javascript
if (operatorStatus || needsAttention) {
    // 走 MySQL: GET /api/expert-contacts?operatorStatus=...
} else {
    // 走 ES: GET /api/experts?level=...&size=...&from=...
}
```

目标是消除这个分支，统一走 ES。

---

## 三、实现方案

### 阶段 1：ES CANDIDATE 索引新增 operatorStatus 字段 + 同步写入

#### Task 1.1：`ExpertIndexWriterService` 新增 `syncCandidateOperatorStatus` 方法

**文件**：`src/main/kotlin/.../expert/service/ExpertIndexWriterService.kt`

新增方法：

```kotlin
fun syncCandidateOperatorStatus(orcidId: String, operatorStatus: String) {
    val candidateIndex = expertIndexService.indexName(ExpertIndexLevel.CANDIDATE)
    val now = LocalDateTime.now().format(dateFormatter)
    try {
        val updateDoc = mapOf(
            "doc" to mapOf(
                "operatorStatus" to operatorStatus,
                "updatedAt" to now
            ),
            "doc_as_upsert" to false
        )
        val updateUrl = "${properties.baseUrl}/$candidateIndex/_update/$orcidId"
        restTemplate.exchange(
            updateUrl, HttpMethod.POST,
            HttpEntity(updateDoc, headers()),
            JsonNode::class.java
        )
    } catch (e: HttpClientErrorException) {
        if (e.statusCode == HttpStatus.NOT_FOUND) {
            log.debug("Candidate doc not found for orcid={}, skip operatorStatus sync", orcidId)
        } else {
            log.warn("Failed to sync operatorStatus for orcid={}", orcidId, e)
        }
    } catch (e: Exception) {
        log.warn("Failed to sync operatorStatus for orcid={}", orcidId, e)
    }
}
```

注意：
- `doc_as_upsert = false`：文档不存在时不创建（已从 CANDIDATE 降级的专家）
- 404 静默处理，不影响 MySQL 主流程
- 同样新增一个批量版本 `syncCandidateOperatorStatusBatch(updates: List<Pair<String, String>>)` 用于存量回刷，使用 ES `_bulk` API

#### Task 1.2：在 4 个 operatorStatus 变更入口注入同步调用

**1) `ManualOutreachTxHelper.persistSendSuccess()`**（第 44 行附近）

发送成功后 `operatorStatus` 从 `NOT_CONTACTED` → `CONTACTED`，在方法末尾追加：

```kotlin
expertIndexWriterService.syncCandidateOperatorStatus(contact.orcidId, "CONTACTED")
```

注意：`NOT_CONTACTED` 的中间态不需要同步到 ES（生命周期太短且 ES 中无值已代表未联系）。

**2) `ExpertOperatorStatusService.changeStatus()`**（第 28 行附近）

运营手动改状态后追加同步：

```kotlin
expertIndexWriterService.syncCandidateOperatorStatus(updated.orcidId, target.name)
```

**3) `ExpertOperatorStatusService.updateAutomatically()`**（第 58 行附近）

系统自动升级后追加同步：

```kotlin
expertIndexWriterService.syncCandidateOperatorStatus(updated.orcidId, targetStatus.name)
```

**4) `ManualInitialOutreachService.runBulkOutreach()`**（第 147 行附近）

创建 contact 时 `NOT_CONTACTED`：**不同步**（ES 中无值已表示未联系，避免批量发送时对 ES 产生 N 次无意义写入）。

#### Task 1.3：`ExpertSearchService.sourceFields()` 新增 `operatorStatus`

在 `sourceFields()` 返回列表中追加 `"operatorStatus"`。

`ExpertProfile` data class 新增 `val operatorStatus: String? = null`，`toExpertProfile()` 解析该字段。

#### Task 1.4：`ExpertSearchService.searchExperts()` 支持 operatorStatus 筛选

新增参数 `operatorStatus: String? = null`，构建 ES query 时：

```kotlin
val filters = mutableListOf<Map<String, Any>>()

if (!tag.isNullOrBlank()) {
    filters.add(mapOf("term" to mapOf("tags" to tag)))
}

when (operatorStatus) {
    "NOT_CONTACTED" -> {
        // 无 operatorStatus 字段 = 从未联系
        filters.add(mapOf("bool" to mapOf(
            "must_not" to listOf(
                mapOf("exists" to mapOf("field" to "operatorStatus"))
            )
        )))
        // 必须有邮箱才算可联系
        filters.add(mapOf("exists" to mapOf("field" to "email")))
    }
    null -> { /* 不筛选 */ }
    else -> {
        filters.add(mapOf("term" to mapOf("operatorStatus" to operatorStatus)))
    }
}

val query = if (filters.isEmpty()) {
    mapOf("match_all" to emptyMap<String, Any>())
} else {
    mapOf("bool" to mapOf("filter" to filters))
}
```

#### Task 1.5：`ExpertSearchService.searchExperts()` 支持 needsAttention 筛选

类似地，新增 `needsAttention: Boolean? = null` 参数。但 `needsManualAttention` 目前不在 ES 中。

**方案**：同时在 CANDIDATE 索引冗余 `needsManualAttention` 字段，同步逻辑与 operatorStatus 类似——在 `needsManualAttention` 变更时同步到 ES。

变更入口需要额外排查，主要是 `ConversationStateService` 和手动标记需要关注的地方。

**降级方案**：如果 `needsAttention` 筛选使用量不大，可以先保留走 MySQL 的分支仅用于 needsAttention，operatorStatus 先统一。后续迭代再冗余 needsManualAttention。

### 阶段 2：`/api/experts` 接口扩展

#### Task 2.1：`ExpertIndexController.listExperts()` 新增 operatorStatus 参数

```kotlin
@GetMapping
fun listExperts(
    @RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel,
    @RequestParam(defaultValue = "50") size: Int,
    @RequestParam(required = false) tag: String?,
    @RequestParam(required = false) sortBy: String?,
    @RequestParam(defaultValue = "0") from: Int,
    @RequestParam(required = false) operatorStatus: String?  // 新增
): ExpertListResponse
```

传递给 `expertSearchService.searchExperts()`。

#### Task 2.2：`ExpertIndexResponse` 新增 `operatorStatus` 字段

```kotlin
data class ExpertIndexResponse(
    // ... 现有字段 ...
    val operatorStatus: String?,  // 新增：从 ES 读取，null 表示 NOT_CONTACTED
    // ...
)
```

`from()` 方法中：当 ES 返回的 `operatorStatus` 为 null 时，映射为 `"NOT_CONTACTED"`。

#### Task 2.3：减少逐条 MySQL 查询

当前 `listExperts()` 对每个专家逐条查 MySQL 补充 contactId/contactStatus。改造后：

- `operatorStatus` 从 ES 直接读取，不再需要查 MySQL
- `contactId` 和 `contactStatus` 仍然需要（用于详情页跳转和会话状态显示）

**优化方案**：批量查询替代逐条查询：

```kotlin
val orcidIds = result.experts.map { it.orcidId }.filter { it.isNotBlank() }
val contactMap = expertContactRepository
    .findByOrcidIdIn(orcidIds)
    .groupBy { it.orcidId }
    .mapValues { (_, contacts) -> contacts.maxByOrNull { it.updatedAt ?: LocalDateTime.MIN } }
```

需要在 `ExpertContactRepository` 新增 `findByOrcidIdIn(orcidIds: List<String>): List<ExpertContact>` 方法。

### 阶段 3：前端统一查询

#### Task 3.1：`loadContacts()` 消除 MySQL 分支

改造 `app.js` 第 1328-1380 行，**所有筛选条件统一走 `/api/experts`**：

```javascript
async function loadContacts() {
    const level = $("#expertIndexLevel").value;
    const size = Number($("#expertIndexSize").value || "50");
    const operatorStatus = $("#contactStatusFilter")?.value || "";
    const needsAttention = $("#contactNeedsAttentionFilter")?.value || "";
    let tag = $("#expertTagFilter")?.value || "";
    const sortBy = $("#expertSortBy")?.value || "";

    const params = new URLSearchParams();
    params.set("level", level);
    params.set("size", size);
    params.set("from", state.contactsPage * size);
    if (tag) params.set("tag", tag);
    if (sortBy) params.set("sortBy", sortBy);
    if (operatorStatus) params.set("operatorStatus", operatorStatus);
    // needsAttention 暂保留 MySQL 降级（见阶段 1 Task 1.5 降级方案）

    const data = await api(`/api/experts?${params}`);
    // ... 统一映射 ...
}
```

#### Task 3.2：移除 tag 与状态筛选的互斥限制

当前 operatorStatus/needsAttention 筛选时会禁用 tag 筛选（因为 MySQL 接口不支持 tag）。统一到 ES 后，tag 和 operatorStatus 可以同时使用，移除 `app.js` 第 1309-1316 行的互斥逻辑。

#### Task 3.3：确保"未联系"筛选的显示一致性

前端 `summarizeManualOutreachPending()` 显示的 pending 数量应与"未联系"筛选的 `totalHits` 一致（两者都基于 ES CANDIDATE 索引中无 operatorStatus 且有 email 的文档）。

### 阶段 4：批量发送性能优化（消除无响应卡顿）

#### 问题分析

点击"批量发送介绍邮件"后存在**两段明显卡顿**：

1. **第一段：`pending-count` 接口（点击按钮 → 弹出确认框）**
   - `countPending()` scroll 遍历整个 ES CANDIDATE 索引（5 万+ 条），**对每条逐个查 MySQL `existsByOrcidId()`**
   - 约 5 万次 MySQL 单条查询，耗时可达数十秒

2. **第二段：`start` 接口返回后，进度面板显示"正在初始化发送队列..."**
   - `runBulkOutreach()` → `buildSnapshot()` **再次** scroll 全量 ES + 逐条查 MySQL
   - 构建完整发送列表期间，进度面板 `totalCount=0`，用户感觉无响应

#### 优化方案：利用 ES 冗余 operatorStatus 消除逐条 MySQL 查询

ES 冗余 `operatorStatus` 后，"未联系"= ES 中无 `operatorStatus` 字段。这使得 `countPending` 和 `buildSnapshot` 可以**纯 ES 查询**完成，无需逐条查 MySQL。

##### Task 4.1：重写 `countPending()` —— 纯 ES count 查询

```kotlin
fun countPending(): PendingOutreachSummary {
    var retryable = 0

    // 1. Retryable: MySQL 中 NEW 状态且无 SENT 介绍邮件的 contact（数量极少，保留原逻辑）
    val campaign = campaignRepository.findByCampaignCode("MANUAL_OUTREACH")
    if (campaign != null) {
        val newContacts = expertContactRepository
            .findAllByCampaignIdAndCurrentStatusOrderByUpdatedAtDesc(campaign.id!!, "NEW")
        retryable = newContacts.count { !hasSentIntroduction(it.id!!) }
    }

    // 2. Pending: ES count 查询，operatorStatus 不存在 + 有邮箱
    val pending = expertSearchService.countExperts(
        level = ExpertIndexLevel.CANDIDATE,
        filters = listOf(
            mapOf("exists" to mapOf("field" to "email")),
            mapOf("bool" to mapOf(
                "must_not" to listOf(
                    mapOf("exists" to mapOf("field" to "operatorStatus"))
                )
            ))
        )
    )

    return PendingOutreachSummary(pending = pending.toInt(), retryable = retryable)
}
```

**性能提升**：从 scroll 5 万条 + 5 万次 MySQL → 1 次 ES `_count` 查询，**耗时从数十秒降至毫秒级**。

##### Task 4.2：`ExpertSearchService` 新增 `countExperts()` 方法

```kotlin
fun countExperts(
    level: ExpertIndexLevel,
    filters: List<Map<String, Any>> = emptyList()
): Long {
    val query = if (filters.isEmpty()) {
        mapOf("match_all" to emptyMap<String, Any>())
    } else {
        mapOf("bool" to mapOf("filter" to filters))
    }
    val requestBody = mapOf("query" to query)
    val index = expertIndexService.indexName(level)
    val response = restTemplate.exchange(
        "${properties.baseUrl}/$index/_count",
        HttpMethod.POST,
        HttpEntity(requestBody, headers()),
        JsonNode::class.java
    ).body
    return response?.path("count")?.asLong(0L) ?: 0L
}
```

##### Task 4.3：重写 `buildSnapshot()` —— ES scroll 不再逐条查 MySQL

```kotlin
private fun buildSnapshot(campaignId: Long): List<Pair<ExpertContact?, ExpertProfile>> {
    val seenOrcids = mutableSetOf<String>()
    val snapshot = mutableListOf<Pair<ExpertContact?, ExpertProfile>>()

    // 1. Retryable: 同原逻辑（数量极少，性能无问题）
    // ... 保持不变 ...

    // 2. New candidates: scroll ES，只取 operatorStatus 不存在的文档
    expertSearchService.scrollExpertsFiltered(
        level = ExpertIndexLevel.CANDIDATE,
        filters = listOf(
            mapOf("exists" to mapOf("field" to "email")),
            mapOf("bool" to mapOf(
                "must_not" to listOf(
                    mapOf("exists" to mapOf("field" to "operatorStatus"))
                )
            ))
        )
    ) { batch ->
        for (expert in batch) {
            val normOrcid = normalizeOrcid(expert.orcidId)
            if (seenOrcids.add(normOrcid)) {
                snapshot.add(Pair(null, expert))
            }
        }
        true
    }
    return snapshot
}
```

**性能提升**：ES scroll 时通过 query filter 在 ES 层面排除已联系专家，不再需要逐条 `existsByOrcidId()`。**构建快照从数十秒降至数秒**（仅受 scroll 网络开销限制）。

##### Task 4.4：`ExpertSearchService` 新增 `scrollExpertsFiltered()` 方法

在现有 `scrollExperts()` 基础上新增带自定义 filter 的版本：

```kotlin
fun scrollExpertsFiltered(
    level: ExpertIndexLevel,
    filters: List<Map<String, Any>>,
    batchSize: Int = 500,
    handler: (List<ExpertProfile>) -> Boolean
)
```

逻辑与现有 `scrollExperts()` 相同，只是 scroll 的初始查询从 `match_all` 改为 `bool + filter`。

##### Task 4.5：前端 loading 状态优化

当前点击按钮后 `btn.disabled = true` 但无任何视觉反馈。优化：

```javascript
btn.disabled = true;
btn.textContent = "正在查询待发送数量...";  // 新增 loading 提示
const countRes = await api("/api/mail/manual-outreach/pending-count");
btn.textContent = "批量发送介绍邮件";  // 恢复
```

对于 `start` 后的 snapshot 构建阶段，进度面板已显示"正在初始化发送队列..."，优化后此阶段从数十秒降至数秒，无需额外处理。

### 阶段 5：清理 CANDIDATE 索引中无邮箱的历史数据

#### 问题分析

CANDIDATE 索引有 ~141460 条记录，但仅 ~50013 条有有效邮箱。约 91000+ 条无邮箱数据是**历史遗留**——在 `requireValidEmail` 配置启用之前或为 false 时批量晋升进来的。

当前晋升逻辑（`ExpertRevalidationService.promoteEligibleRawExperts()` 第 148 行）已通过 `evaluateEligibility()` 检查邮箱格式，`requireValidEmail` 配置默认为 `true`，**新数据不会再漏进**。但存量脏数据需要清理。

#### Task 5.1：执行一次"重新验证候选人"清理历史无邮箱数据

现有的 `revalidateCandidates()` 已具备此能力——它遍历 CANDIDATE 索引，对每条记录：
1. 先检查邮箱有效性（`emailValidationService.validate()`），无效则从 CANDIDATE 索引删除
2. 再检查完整资格（`evaluateEligibility()`），不合格也删除

**操作步骤**：在前端点击"重新验证候选人"按钮即可。执行后约 91000 条无邮箱记录会被降级（从 CANDIDATE 索引删除，仍保留在 RAW 索引中）。

**注意**：这是一次性清理操作，可能耗时较长（需 scroll 14 万条 + 逐条 HEAD 检查删除）。建议在低峰期执行。执行后 CANDIDATE 索引数量应降至约 50000+ 条，与批量发送 pending 数基本一致。

#### Task 5.2：确认 `revalidateCandidates` 的邮箱验证逻辑覆盖完整

验证 `EmailValidationService.validate()` 对以下情况返回 invalid：
- email 为 null 或空字符串
- email 格式不合法
- email 为一次性邮箱（disposable）

确保清理后无邮箱的候选人不会残留。

### 阶段 6：存量 operatorStatus 回刷

#### Task 6.1：新增一次性回刷接口

`ExpertIndexController` 新增管理接口：

```kotlin
@PostMapping("/backfill-operator-status")
fun backfillOperatorStatus(): BackfillResult
```

逻辑：
1. 查询 MySQL 所有 `expert_contact` 记录
2. 对每条记录，通过 `_bulk` API 批量更新 CANDIDATE 索引中对应 orcidId 的 `operatorStatus`
3. 返回成功/失败/跳过计数

#### Task 6.2：前端添加回刷按钮（临时）

在后台任务区域添加一个"回刷 ES operatorStatus"按钮，执行完成后可移除。

### 阶段 7：工具栏操作按钮整合

#### 问题分析

当前工具栏按钮存在三个问题：

**1. 布局混乱，业务流水线不清晰**

操作被任意拆分到两个位置——"后台任务"下拉菜单和工具栏直接按钮，用户无法直觉理解操作顺序：

```
当前布局：
┌─────────────────────────────────────────────────────────────┐
│ [刷新] [后台任务 ▾]  [自动回复] [检查回复] [批量发送] [发现专家] │
│           ├─ 轮询日志                                        │
│           ├─ 重新验证候选人                                    │
│           └─ 扫描 RAW 可晋升                                  │
└─────────────────────────────────────────────────────────────┘
```

**2. 进度/交互模式不统一**

| 操作 | 当前模式 | 问题 |
|------|---------|------|
| 重新验证 / RAW 扫描 / 发现专家 | taskModal 弹窗 + TaskProgress 轮询 + 可取消 | ✅ 统一 |
| 批量发送介绍邮件 | 独立 outreachProgressPanel + 按钮变红可取消 | ❌ 独立实现，与其他任务体验不一致 |
| 检查回复 | **同步 `await api(...)`**，按钮 disabled 等待返回 | ❌ 阻塞 UI，无进度，无法取消 |
| 自动回复开关 | 同步 toggle | ✅ 合理（瞬时操作，无需异步） |

**3. "发现专家"与"扫描 RAW 可晋升"功能重叠**

两者目标相同（扩充 CANDIDATE 索引），区别仅在数据来源（外部平台 vs 本地 RAW 索引）。用户需要理解内部存储层级才能区分，认知负担不必要。

#### 目标布局

按业务流水线从左到右排列，所有异步操作统一使用 taskModal + TaskProgress 模式：

```
目标布局：
┌──────────────────────────────────────────────────────────────────┐
│ [刷新] [发现专家 ▾] [重新验证] [批量发送] [检查回复] [自动回复开关] │
│         ├─ 快速扫描（本地）                                       │
│         └─ 深度发现（含外部平台）                                  │
└──────────────────────────────────────────────────────────────────┘
```

流水线语义：**发现获取 → 验证筛选 → 发送触达 → 收取回复 → 自动回复管理**

#### Task 7.1：合并"发现专家"与"扫描 RAW 可晋升"

**前端**：

- 删除"后台任务"下拉菜单
- "发现专家"按钮改为带下拉的分体按钮（split button）：
  - 按钮主体点击 = 默认执行"深度发现"
  - 下拉箭头展开两个选项：
    - **快速扫描**：纯本地，遍历 RAW 索引做资格检查+邮箱验证，符合条件晋升 CANDIDATE。对应现有 `RAW_PROMOTION_SCAN`
    - **深度发现**：先跑快速扫描，再对 RAW 中无邮箱专家调外部平台补邮箱，最后搜索外部平台发现全新专家。对应现有 `EXPERT_DISCOVERY`（扩展）

**后端**：

`ExpertDiscoveryService.discover()` 新增参数 `includeRawScan: Boolean = true`：
- 当 `includeRawScan = true` 时，在外部平台发现之前先执行 `promoteEligibleRawExperts()` 逻辑
- 新增 RAW 邮箱补全阶段：遍历 RAW 中无邮箱但其他条件合格的专家，用 orcidId 调 ORCID/OpenAlex API 查邮箱，查到后更新 RAW 文档并晋升

taskLaunchConfigs 中删除 `RAW_PROMOTION_SCAN`，`EXPERT_DISCOVERY` 的配置弹窗新增"扫描模式"选择（快速/深度）。

**taskType 映射**：
- 快速扫描仍使用 `RAW_PROMOTION_SCAN` taskType（保持执行历史连续性）
- 深度发现仍使用 `EXPERT_DISCOVERY` taskType

#### Task 7.2："重新验证候选人"从下拉菜单提升为工具栏按钮

- 从"后台任务"下拉菜单移出，放到"发现专家"按钮右侧
- 交互模式不变（已使用 taskModal，✅ 无需改造）

#### Task 7.3："批量发送介绍邮件"迁移到统一 taskModal 模式

当前"批量发送"有独立的 `outreachProgressPanel` + 按钮变红取消逻辑。改造为与其他任务统一的模式：

- 删除 `outreachProgressPanel` 相关 HTML 和 JS
- 在 `taskLaunchConfigs` 中新增 `MANUAL_INITIAL_OUTREACH` 配置
- 点击按钮 → 打开 taskModal 弹窗（先显示 pending count，确认后启动）
- 进度通过 taskModal 内的 TaskProgress 轮询展示
- 取消通过 taskModal 的标准取消按钮

```javascript
const taskLaunchConfigs = {
    // ...
    MANUAL_INITIAL_OUTREACH: {
        title: "批量发送介绍邮件",
        desc: "", // 动态填充 pending count
        btnId: "bulkOutreachBtn",
        showKeyword: false,
        showMaxPromotions: false,
        preload: async () => {
            const countRes = await api("/api/mail/manual-outreach/pending-count");
            const summary = summarizeManualOutreachPending(countRes);
            return { desc: summary.confirmMessage, canRun: summary.total > 0 };
        },
        run: executeManualOutreach
    }
};
```

#### Task 7.4："检查回复"改为异步 + TaskProgress 模式

当前 `executeCheckReplies()` 是同步 `await api(...)` 阻塞 UI。改造：

**后端**：`/api/mail/auto-reply/check-replies` 改为异步：
- POST 立即返回（类似 `manual-outreach/start`）
- 后台线程执行邮件检查，通过 `TaskProgressStore` 更新进度
- 新增 taskType `CHECK_REPLIES`

**前端**：
- 在 `taskLaunchConfigs` 中新增 `CHECK_REPLIES` 配置
- 点击按钮 → 打开 taskModal → 显示进度（已检查 N/M 个账号）
- 支持取消

```javascript
CHECK_REPLIES: {
    title: "检查回复",
    desc: "检查所有已联系专家的邮箱回复。",
    btnId: "checkRepliesBtn",
    showKeyword: false,
    showMaxPromotions: false,
    run: executeCheckReplies
}
```

#### Task 7.5：删除"后台任务"下拉菜单

所有内容已迁出：
- "重新验证候选人" → 工具栏按钮（Task 7.2）
- "扫描 RAW 可晋升" → 合并到"发现专家"（Task 7.1）
- "轮询日志" → 移到页面底部状态栏或设置区域（调试用途，非业务操作）

删除 `#taskMenuDropdown` 相关 HTML、CSS 和 JS。

#### Task 7.6：按钮排列按流水线顺序

HTML 中 toolbar-actions 区域的按钮顺序调整为：

```html
<div class="toolbar-group toolbar-actions">
    <button id="loadContactsBtn">刷新</button>
    <!-- 阶段 1: 获取 -->
    <div class="split-button" id="discoverBtnGroup">
        <button class="button primary" id="discoverBtn">发现专家</button>
        <button class="button primary split-arrow" id="discoverModeToggle">▾</button>
        <div class="dropdown-menu" id="discoverModeMenu" hidden>
            <button class="dropdown-item" data-mode="quick">快速扫描（本地 RAW）</button>
            <button class="dropdown-item" data-mode="deep">深度发现（含外部平台）</button>
        </div>
    </div>
    <!-- 阶段 2: 验证 -->
    <button id="revalidateBtn">重新验证</button>
    <!-- 阶段 3: 发送 -->
    <button class="button primary" id="bulkOutreachBtn">批量发送</button>
    <!-- 阶段 4: 收取 -->
    <button id="checkRepliesBtn">检查回复</button>
    <!-- 阶段 5: 回复管理 -->
    <button id="bulkAutoReplyBtn">自动回复：加载中...</button>
</div>
```

#### Task 7.7：统一 taskButtonMapping

更新 `taskButtonMapping` 以覆盖所有异步任务，确保刷新页面后能恢复任何正在运行的任务的进度面板：

```javascript
const taskButtonMapping = {
    EXPERT_REVALIDATION:      { label: "重新验证候选人",   btnId: "revalidateBtn" },
    EXPERT_DISCOVERY:         { label: "发现专家（深度）", btnId: "discoverBtn" },
    RAW_PROMOTION_SCAN:       { label: "发现专家（快速）", btnId: "discoverBtn" },
    MANUAL_INITIAL_OUTREACH:  { label: "批量发送介绍邮件", btnId: "bulkOutreachBtn" },
    CHECK_REPLIES:            { label: "检查回复",         btnId: "checkRepliesBtn" }
};
```

---

## 四、测试计划

### 4.1 后端单元测试

| 测试 | 覆盖点 |
|------|--------|
| `ExpertIndexWriterServiceTest.syncCandidateOperatorStatus` | 正常更新、404 静默、异常处理 |
| `ExpertSearchServiceTest.searchWithOperatorStatus` | NOT_CONTACTED（must_not exists）、CONTACTED（term）、组合 tag+operatorStatus |
| `ExpertSearchServiceTest.countExperts` | count 查询正确构建 filter、空 filter 时 match_all |
| `ManualInitialOutreachServiceTest.countPending` | 改造后使用 ES count 而非 scroll+逐条查 MySQL |
| `ManualInitialOutreachServiceTest.buildSnapshot` | 改造后使用 scrollExpertsFiltered 而非逐条 existsByOrcidId |
| `ExpertOperatorStatusServiceTest` | 手动/自动变更后 ES 同步被调用 |
| `ManualOutreachTxHelperTest` | 发送成功后 ES 同步被调用 |

### 4.2 集成验证

1. 存量回刷后，前端"未联系"筛选数 ≈ 批量发送的 pending 数
2. 发送一封介绍邮件后，刷新列表该专家 operatorStatus 从"未联系"变为"已联系"
3. 运营手动改状态后，刷新列表状态正确更新
4. tag 与 operatorStatus 可同时筛选
5. "发现专家"下拉：快速扫描走 `RAW_PROMOTION_SCAN`，深度发现走 `EXPERT_DISCOVERY`，两者进度面板+取消行为一致
6. "批量发送"点击后打开 taskModal 弹窗，进度展示与取消行为与其他任务一致
7. "检查回复"改异步后，进度面板正确展示已检查账号数，可取消
8. 刷新页面后，任何正在运行的任务的进度面板能正确恢复

---

## 五、风险与注意事项

1. **ES 与 MySQL 一致性**：ES 是异步冗余，短暂不一致可接受。同步失败只 warn 日志，不影响主流程。
2. **批量发送性能**：批量发送时不同步 `NOT_CONTACTED` 瞬态，只在发送成功后同步 `CONTACTED`，每次发送多一次 ES `_update` 调用，影响可忽略（本身已有 `sendIntervalMs` 间隔）。
3. **CANDIDATE 索引 mapping**：ES 动态 mapping 会自动识别 `operatorStatus` 为 keyword 类型（首次写入时）。如需严格控制，可在回刷前手动 PUT mapping，但非必须。
4. **needsManualAttention 延后**：本次计划先不冗余此字段，needsAttention 筛选暂保留走 MySQL 降级路径，后续迭代补齐。

---

## 六、文件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `expert/service/ExpertIndexWriterService.kt` | 修改 | 新增 `syncCandidateOperatorStatus()` + `backfillOperatorStatus()` |
| `expert/service/ExpertSearchService.kt` | 修改 | `sourceFields` 加 `operatorStatus`；`searchExperts` 加 operatorStatus 筛选参数；新增 `countExperts()` + `scrollExpertsFiltered()` |
| `expert/domain/ExpertProfile.kt` | 修改 | 新增 `operatorStatus: String?` 字段 |
| `expert/controller/ExpertIndexController.kt` | 修改 | `listExperts` 加 operatorStatus 参数；新增回刷接口 |
| `campaign/service/ExpertOperatorStatusService.kt` | 修改 | 两个方法末尾追加 ES 同步调用 |
| `campaign/service/ManualOutreachTxHelper.kt` | 修改 | `persistSendSuccess` 末尾追加 ES 同步调用 |
| `campaign/service/ManualInitialOutreachService.kt` | 修改 | `countPending()` 改用 ES count；`buildSnapshot()` 改用 `scrollExpertsFiltered` 消除逐条 MySQL 查询 |
| `campaign/repository/ExpertContactRepository.kt` | 修改 | 新增 `findByOrcidIdIn()` 批量查询方法 |
| `src/main/resources/static/app.js` | 修改 | `loadContacts()` 统一走 `/api/experts`；工具栏按钮整合；批量发送迁移到 taskModal；检查回复改异步；删除后台任务下拉菜单 |
| `src/main/resources/static/index.html` | 修改 | 工具栏 HTML 重构：删除 `#taskMenuDropdown`，按流水线顺序排列按钮，新增"发现专家"分体按钮 |
| `src/main/resources/static/styles.css` | 修改 | 新增 split-button 样式；删除 outreachProgressPanel 相关样式 |
| `mail/controller/MailAutomationController.kt` | 修改 | `check-replies` 接口改为异步（线程池 + TaskProgress） |
| `discovery/service/ExpertDiscoveryService.kt` | 修改 | `discover()` 新增 `includeRawScan` 参数；新增 RAW 邮箱补全阶段 |
| 测试文件（多个） | 新增/修改 | 见测试计划 |

---

## 修正记录

| 原要求 | 修正后要求 | 原因 | 参考 |
|---|---|---|---|
| ES 动态 mapping 会自动识别 `operatorStatus` 为 keyword，回刷前无需显式 PUT mapping | CANDIDATE mapping 为 `dynamic:false`，必须在索引模板中显式声明 `operatorStatus: keyword`，并在存量回刷前更新现有索引 mapping；mapping 更新失败时禁止回刷 | 当前 `src/main/resources/es/orcid_info_candidate.json` 明确关闭动态 mapping，未声明字段时写入值不可用于 `exists`/`term` 查询 | `docs/plans/fix/2026-06-15-es-unified-operator-status-query-reverification-fix-plan.md` P1-1 |
