# 专家数据补充 — 后端数据模型与服务扩展

> 计划系列: expert-enrichment (1/2)
> 前置: 无
> 后续: expert-enrichment-frontend (Plan 2)

---

## 需求描述

**可观测结果**: 系统能从 OpenAlex API 补充专家的研究方向（topics）、近期论文标题、专利标题到 ES 索引，并通过扩展后的搜索 API 支持按学术指标（H-Index 区间、引用数区间、近 N 年有发表）和数据完整度（有职位/学历/机构/研究方向/专利）筛选专家；介绍邮件模板可使用全部已补充的变量。

**不可变更的行为**:

- 现有 `enrichExistingExperts()` 跳过已有 hIndex 的专家的逻辑保持不变（改为跳过已有 `enrichedAt` 的专家）
- `updateExpertAcademicFields` 向三层索引 (RAW/CANDIDATE/APPLICATION) 写入的模式不变
- ES `dynamic: false` 设置不变，所有新字段必须在 mapping 中显式声明
- 前端现有 `/api/experts` 响应结构向后兼容（只增字段，不删不改已有字段）
- `IntroductionMailComposer` 的 fallback 语义（`orEmpty()`）不变——新变量缺失时替换为空串

**不在范围内**:

- 前端 UI 改造（Plan 2）
- Semantic Scholar 数据源接入（后续独立计划）
- ORCID `/works`、`/educations`、`/employments` 详情 API 接入（后续独立计划）
- 教育经历、个人简介、基金项目字段（依赖 ORCID API 接入，不在本计划）

---

## 关键不变量

### Invariant I-1: ES mapping 与 ExpertProfile 字段一一对应

- Rule: ES mapping 中声明的每个字段必须在 `ExpertProfile` 数据类中有对应属性；`sourceFields()` 必须包含所有 `ExpertProfile` 属性对应的 ES 字段名；`toExpertProfile()` 必须解析所有 `sourceFields()` 中的字段。三者同步。
- Applies to: `orcid_info_*.json`、`ExpertProfile.kt`、`ExpertSearchService.sourceFields()`、`ExpertSearchService.toExpertProfile()`
- Violation consequence: 新字段存入 ES 但查询不返回，或返回但解析失败
- 来源: original

### Invariant I-2: enrichment 写入覆盖三层索引

- Rule: `updateExpertAcademicFields` 必须对 RAW、CANDIDATE、APPLICATION 三层索引均执行 partial update（`_update` API），写入完全相同的字段集。某层不存在该文档时静默跳过（已有行为）。
- Applies to: `ExpertDiscoveryService.updateExpertAcademicFields()`
- Violation consequence: 层间数据不一致，promotion 后字段丢失
- 来源: original（从现有代码行为提取）

### Invariant I-3: enrichedAt 控制幂等性

- Rule: enrichment 完成后必须写入 `enrichedAt` 时间戳；后续 enrichment 批次跳过 `enrichedAt` 非空且距今不超过 30 天的文档。替代现有的 `hIndex != null` 跳过逻辑。
- Applies to: `ExpertDiscoveryService.enrichExistingExperts()`、`ExpertDiscoveryService.updateExpertAcademicFields()`
- Violation consequence: 反复请求同一专家的 OpenAlex API，浪费配额或导致限流
- 来源: original

### Invariant I-4: ExpertIndexResponse 向后兼容

- Rule: `ExpertIndexResponse` 新增字段全部为可空类型或有默认值。不删除、不重命名、不改变任何已有字段的类型。
- Applies to: `ExpertIndexController.ExpertIndexResponse`
- Violation consequence: 前端旧版本解析失败
- 来源: original

### Invariant I-5: 新增筛选参数全部为可选

- Rule: `searchExperts()` 和 `GET /api/experts` 新增的筛选参数必须有默认值（null 或不传），不传时行为与改动前完全一致。
- Applies to: `ExpertSearchService.searchExperts()`、`ExpertSearchService.buildExpertFilters()`、`ExpertIndexController.listExperts()`
- Violation consequence: 不带新参数的旧请求返回不同结果
- 来源: original

### Invariant I-6: IntroductionMailComposer 变量 map 是模板变量的唯一真实来源

- Rule: 模板变量 API（template-variables endpoint）必须通过调用 `IntroductionMailComposer` 相同的变量构建逻辑来生成变量列表，不可硬编码变量名。
- Applies to: `IntroductionMailComposer.compose()`、新增 template-variables API
- Violation consequence: API 返回的变量列表与实际发送邮件使用的变量不一致
- 来源: original

---

## 现状审计

### ES 索引 (RAW / CANDIDATE / APPLICATION)

- **Schema**: 三层索引均为 `dynamic: false`。字段集基本一致，APPLICATION 额外含会话状态字段。
- **当前 enrichment 相关字段**: `hIndex`(integer)、`citationCount`(integer)、`worksCount`(integer)、`lastPublicationYear`(integer)、`researchFields`(keyword)、`institution`(text)。
- **Write paths**:
  1. `ExpertIndexWriterService.indexToRaw()` — 初始入库到 RAW，写入全量 profile map
  2. `ExpertIndexWriterService.promoteToCandidate()` — RAW→CANDIDATE 晋升，复制文档
  3. `ExpertIndexWriterService.promoteToApplication()` — CANDIDATE→APPLICATION 晋升，复制文档
  4. `ExpertIndexWriterService.syncCandidateOperatorStatus()` — 更新 CANDIDATE 的 operatorStatus
  5. `ExpertIndexWriterService.syncApplicationStatus()` — 更新 APPLICATION 的会话状态
  6. `ExpertIndexWriterService.addTag()/removeTag()` — 标签增删
  7. `ExpertDiscoveryService.updateExpertAcademicFields()` — enrichment partial update 到三层
  8. Flyway migrations (V1-V10) — 不涉及 ES
- **Read paths**:
  1. `ExpertSearchService.searchExperts()` — 带筛选的分页查询，返回 `sourceFields()` 定义的字段
  2. `ExpertSearchService.scrollExperts()` — scroll 遍历，返回同样字段集
  3. `ExpertSearchService.findByOrcidId()` — 单个专家查询
  4. `IntroductionMailComposer.compose()` — 消费 `ExpertProfile` 的 researchFields、institution、keyword、country 等
- **Interaction points**:
  - Write path 7 (enrichment) × Read path 1-3 (search): 新字段写入后必须被搜索返回 → 受 I-1 约束
  - Write path 7 (enrichment) × Read path 4 (mail composer): 新字段写入后必须被模板变量使用 → 受 I-6 约束

### ExpertProfile 数据类

- **文件**: `expert/domain/ExpertProfile.kt`
- **当前字段**: 24 个属性 + 1 个计算属性 `displayName`
- **Write paths**: 无直接写入（不可变 data class），由 `ExpertSearchService.toExpertProfile()` 构造
- **Read paths**: `IntroductionMailComposer`、`MeetingInvitationMailComposer`、`ExpertDiscoveryService.enrichExistingExperts()`、`ExpertIndexController.listExperts()`、`ExpertContactManagementService`

### ExpertIndexResponse

- **文件**: `expert/controller/ExpertIndexController.kt` (line 326-381)
- **当前字段**: 18 个字段。**缺失但 ExpertProfile 有的**: hIndex、citationCount、lastPublicationYear、researchFields、institution、worksCount、emailSource、emailVerifiedLevel、dataSource
- **Read paths**: 前端 `loadContacts()` → `/api/experts` → 列表渲染

### IntroductionMailComposer

- **文件**: `mail/service/IntroductionMailComposer.kt`
- **当前变量 map**: senderEmail, senderName, senderTitle, teamName, countryName, expertName, expertFamilyName, researchFields, institution, keyword, expertCountry (11 个)
- **未使用的 ExpertProfile 字段**: employment, hIndex, citationCount, worksCount, lastPublicationYear, degree

### OpenAlexDataSource / AuthorEnrichment

- **文件**: `discovery/service/OpenAlexDataSource.kt`
- **AuthorEnrichment 当前字段**: hIndex, citationCount, worksCount (3 个)
- **OpenAlex author API 可用但未获取的数据**:
  - `topics[].display_name` — 研究方向（带 score 排序）
  - `works_api_url` → 可请求近期论文标题
  - 论文的 `type=patent` 条目 — 专利
- **enrichAuthor() 当前逻辑**: 只解析 summary_stats.h_index、cited_by_count、works_count

---

## 实现方案

### Stage 1: ES mapping 扩展 (I-1, I-2)

**Task 1.1**: 三层 ES mapping 新增以下字段：

```json
"recentWorkTitles": { "type": "keyword" },
"patentTitles": { "type": "keyword" },
"enrichedAt": { "type": "date", "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis" },
"enrichmentSource": { "type": "keyword" }
```

- 文件: `src/main/resources/es/orcid_info_raw.json`、`orcid_info_candidate.json`、`orcid_info_application.json`
- 三个文件新增相同的 4 个字段定义
- `recentWorkTitles` 和 `patentTitles` 类型为 `keyword`（数组），与现有 `tags` 字段模式一致
- **注意**: `dynamic: false` 意味着必须在 mapping 中显式声明才能被索引和查询。未声明的字段可以存入但不可搜索、聚合。
- **线上迁移**: 需通过 ES PUT mapping API 添加字段，或重建索引。ES 允许对已有索引添加新字段（`PUT /<index>/_mapping`），不需要重建。

### Stage 2: ExpertProfile + ExpertSearchService 扩展 (I-1)

**Task 2.1**: `ExpertProfile` 新增 4 个字段：

```kotlin
val recentWorkTitles: List<String>? = null,
val patentTitles: List<String>? = null,
val enrichedAt: String? = null,
val enrichmentSource: String? = null
```

- 文件: `src/main/kotlin/.../expert/domain/ExpertProfile.kt`
- 全部可空 + 默认 null，向后兼容

**Task 2.2**: `ExpertSearchService.sourceFields()` 追加 4 个字段名：

```kotlin
"recentWorkTitles", "patentTitles", "enrichedAt", "enrichmentSource"
```

- 文件: `src/main/kotlin/.../expert/service/ExpertSearchService.kt`

**Task 2.3**: `ExpertSearchService.toExpertProfile()` 解析新字段：

```kotlin
recentWorkTitles = source.path("recentWorkTitles").takeIf { it.isArray }
    ?.map { it.asText() }?.filter { it.isNotBlank() },
patentTitles = source.path("patentTitles").takeIf { it.isArray }
    ?.map { it.asText() }?.filter { it.isNotBlank() },
enrichedAt = source.nullableText("enrichedAt"),
enrichmentSource = source.nullableText("enrichmentSource")
```

- 文件: 同 Task 2.2

**Task 2.4**: `ExpertSearchService.searchExperts()` 新增筛选参数 + `buildExpertFilters()` 扩展 (I-5)：

新增参数:
- `hIndexMin: Int?` — H-Index 下限（range filter: `gte`）
- `citationCountMin: Int?` — 引用数下限
- `recentYears: Int?` — 最近 N 年有发表（`lastPublicationYear >= currentYear - N`）
- `hasField: List<String>?` — 数据完整度筛选（`exists` filter），可选值: `employment`, `degree`, `institution`, `researchFields`, `patentTitles`

`buildExpertFilters()` 内添加:
```kotlin
hIndexMin?.let { filters.add(mapOf("range" to mapOf("hIndex" to mapOf("gte" to it)))) }
citationCountMin?.let { filters.add(mapOf("range" to mapOf("citationCount" to mapOf("gte" to it)))) }
recentYears?.let {
    val cutoff = java.time.Year.now().value - it
    filters.add(mapOf("range" to mapOf("lastPublicationYear" to mapOf("gte" to cutoff))))
}
hasField?.forEach { field ->
    require(field in ALLOWED_HAS_FIELDS) { "Invalid hasField: $field" }
    filters.add(mapOf("exists" to mapOf("field" to field)))
}
```

- 文件: 同 Task 2.2

### Stage 3: OpenAlex enrichment 扩展 (I-2, I-3)

**Task 3.1**: 扩展 `AuthorEnrichment` 数据类：

```kotlin
data class AuthorEnrichment(
    val hIndex: Int?,
    val citationCount: Int?,
    val worksCount: Int?,
    val topics: List<String>? = null,
    val recentWorkTitles: List<String>? = null,
    val patentTitles: List<String>? = null
)
```

- 文件: `src/main/kotlin/.../discovery/service/OpenAlexDataSource.kt`

**Task 3.2**: `enrichAuthor()` 扩展 — 解析 topics + 获取近期论文：

```kotlin
// topics: 取 score 最高的 top 5
val topics = response.path("topics")
    .takeIf { it.isArray }
    ?.sortedByDescending { it.path("count").asInt(0) }
    ?.take(5)
    ?.mapNotNull { it.path("display_name").asText(null) }

// 近期论文: 请求 works_api_url 取最近 3 篇
val worksUrl = response.path("works_api_url").asText(null)
val recentWorks = if (worksUrl != null) {
    fetchRecentWorks(worksUrl, limit = 3)
} else null

// 专利: 请求 works 中 type=patent
val patents = if (worksUrl != null) {
    fetchPatents(worksUrl, limit = 3)
} else null
```

新增两个 private 辅助方法:
- `fetchRecentWorks(worksUrl, limit)` — `GET {worksUrl}?sort=publication_year:desc&per_page={limit}&select=title,publication_year`
- `fetchPatents(worksUrl, limit)` — `GET {worksUrl}?filter=type:patent&per_page={limit}&select=title,publication_year`

- 文件: 同 Task 3.1

**Task 3.3**: `ExpertDiscoveryService.updateExpertAcademicFields()` 写入新字段 (I-2, I-3)：

```kotlin
enrichment.topics?.takeIf { it.isNotEmpty() }?.let { doc["researchFields"] = it.joinToString(", ") }
enrichment.recentWorkTitles?.takeIf { it.isNotEmpty() }?.let { doc["recentWorkTitles"] = it }
enrichment.patentTitles?.takeIf { it.isNotEmpty() }?.let { doc["patentTitles"] = it }
doc["enrichedAt"] = now
doc["enrichmentSource"] = "OPENALEX"
```

- 文件: `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt`

**Task 3.4**: `enrichExistingExperts()` 跳过逻辑改用 `enrichedAt` (I-3)：

替换 `if (profile.hIndex != null) continue` 为:

```kotlin
if (profile.enrichedAt != null) {
    val enrichedDate = LocalDate.parse(profile.enrichedAt.take(10))
    if (ChronoUnit.DAYS.between(enrichedDate, LocalDate.now()) < 30) continue
}
```

- 文件: 同 Task 3.3

### Stage 4: API 层扩展 (I-4, I-5, I-6)

**Task 4.1**: `ExpertIndexResponse` 新增字段 (I-4)：

```kotlin
val hIndex: Int? = null,
val citationCount: Int? = null,
val lastPublicationYear: Int? = null,
val researchFields: String? = null,
val institution: String? = null,
val worksCount: Int? = null,
val enrichedAt: String? = null
```

`from()` 工厂方法中赋值:
```kotlin
hIndex = expert.hIndex,
citationCount = expert.citationCount,
lastPublicationYear = expert.lastPublicationYear,
researchFields = expert.researchFields,
institution = expert.institution,
worksCount = expert.worksCount,
enrichedAt = expert.enrichedAt
```

- 文件: `src/main/kotlin/.../expert/controller/ExpertIndexController.kt`

**Task 4.2**: `listExperts()` 新增筛选参数 (I-5)：

```kotlin
@RequestParam(required = false) hIndexMin: Int?,
@RequestParam(required = false) citationCountMin: Int?,
@RequestParam(required = false) recentYears: Int?,
@RequestParam(required = false) hasField: List<String>?
```

传递给 `searchExperts()`。

- 文件: 同 Task 4.1

**Task 4.3**: 新增模板变量预览 API (I-6)：

```kotlin
@GetMapping("/template-variables")
fun getTemplateVariables(
    @RequestParam orcidId: String,
    @RequestParam(defaultValue = "CANDIDATE") level: ExpertIndexLevel,
    @RequestParam(required = false) accountCode: String?
): List<TemplateVariableItem>
```

逻辑: 查询 ExpertProfile → 使用 `IntroductionMailComposer` 相同的变量构建逻辑 → 返回 `[{key, label, value, filled}]`。

为此需将 `IntroductionMailComposer.compose()` 中的变量 map 构建逻辑抽取为 `fun buildVariables(account, expert): Map<String, String>`，compose 调用 buildVariables，API 也调用同一方法。

- 文件: `ExpertIndexController.kt` (API endpoint) + `IntroductionMailComposer.kt` (抽取方法)

### Stage 5: IntroductionMailComposer 扩展 (I-6)

**Task 5.1**: 在 `buildVariables()` 中追加已有但未用的字段:

```kotlin
"employment" to expert.employment.orEmpty(),
"hIndex" to (expert.hIndex?.toString()).orEmpty(),
"worksCount" to (expert.worksCount?.toString()).orEmpty(),
"lastPublicationYear" to (expert.lastPublicationYear?.toString()).orEmpty(),
"degree" to expert.degree.orEmpty(),
"recentWorkTitle" to (expert.recentWorkTitles?.firstOrNull()).orEmpty(),
"patentTitle" to (expert.patentTitles?.firstOrNull()).orEmpty()
```

- 文件: `src/main/kotlin/.../mail/service/IntroductionMailComposer.kt`

---

## 变更文件清单

| # | 文件 | 改动类型 | 涉及不变量 |
|---|------|---------|-----------|
| 1 | `src/main/resources/es/orcid_info_raw.json` | 新增 4 字段 | I-1 |
| 2 | `src/main/resources/es/orcid_info_candidate.json` | 新增 4 字段 | I-1 |
| 3 | `src/main/resources/es/orcid_info_application.json` | 新增 4 字段 | I-1 |
| 4 | `src/main/kotlin/.../expert/domain/ExpertProfile.kt` | 新增 4 属性 | I-1 |
| 5 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | sourceFields + toExpertProfile + searchExperts 参数 + buildExpertFilters | I-1, I-5 |
| 6 | `src/main/kotlin/.../discovery/service/OpenAlexDataSource.kt` | AuthorEnrichment 扩展 + enrichAuthor 扩展 + 2 个辅助方法 | I-2 |
| 7 | `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt` | updateExpertAcademicFields 写入新字段 + enrichExistingExperts 跳过逻辑 | I-2, I-3 |
| 8 | `src/main/kotlin/.../expert/controller/ExpertIndexController.kt` | ExpertIndexResponse 扩展 + listExperts 参数 + template-variables API | I-4, I-5, I-6 |
| 9 | `src/main/kotlin/.../mail/service/IntroductionMailComposer.kt` | buildVariables 抽取 + 新变量 | I-6 |

**共 9 文件**，涉及 2 个子系统（expert 模块 + discovery 模块），均通过 ES 索引交互。

---

## 验收标准

### 不变量验证

- **I-1**: 对每个新增 ES 字段，确认：mapping JSON 中有声明 → `ExpertProfile` 有同名属性 → `sourceFields()` 含该字段名 → `toExpertProfile()` 有解析逻辑。共 4 字段 × 4 检查点 = 16 项。
- **I-2**: 在测试中 mock enrichment 数据，验证 `updateExpertAcademicFields` 对 RAW/CANDIDATE/APPLICATION 三层均调用 `_update`，且 doc body 包含 recentWorkTitles、patentTitles、enrichedAt、enrichmentSource。
- **I-3**: 连续执行两次 `enrichExistingExperts()`：第一次应补充数据，第二次同一专家应被跳过（因 enrichedAt 距今 < 30 天）。
- **I-4**: 对比改动前后 `/api/experts` 的 JSON 响应结构，确认旧字段名、类型不变，新字段为 nullable。
- **I-5**: 不带任何新参数调用 `/api/experts?level=CANDIDATE&size=10`，结果与改动前一致。带新参数 `hIndexMin=20` 调用，结果只含 hIndex ≥ 20 的专家。
- **I-6**: 调用 `/api/experts/template-variables?orcidId=xxx`，返回的变量列表与 `IntroductionMailComposer.buildVariables()` 生成的 map keys 完全一致。

### 集成场景

1. **端到端 enrichment**: 触发 `POST /api/expert-discovery/enrich?maxExperts=5`，完成后查询被 enrich 的专家，验证 researchFields、recentWorkTitles、enrichedAt 均有值。
2. **筛选联动**: 先 enrich 若干专家使其有 researchFields，再调用 `GET /api/experts?hasField=researchFields`，验证只返回有该字段的专家。
3. **模板变量完整性**: 对一个已 enrich 的专家调用 template-variables API，确认 recentWorkTitle、employment、hIndex 等新变量出现且 filled=true。

---

## 后续计划 (Plan 2: expert-enrichment-frontend)

前端改造独立部署，依赖 Plan 1 的 API 完成后实施：

- 文件: `index.html`、`app.js`、`styles.css`
- 内容: 详情面板 sub-tab（学术档案/联系详情/模板预览）、学术指标卡片、数据完整度筛选 chip、enrichment 触发 UI 与进度条、模板变量覆盖展示、列表项 H-Index 与补充状态标签
