# 专家可达性（reachability）— 执行顺序与共享审计（主计划）

> 本文件是 6 份子计划的**顺序权威**与**共享证据源**。子计划中凡引用 `(见主计划 R-n)` 的证据，
> 以本文件为准，不得在子计划中重写另一版。
>
> 生成方式：create-p skill。需求方明确要求「不要猜测，所有量化断言与全称判断必须附 grep 命令与输出」，
> 故本文所有计数与全称判断均带 `R-n` 证据编号，命令与输出逐字保留。

## 需求描述

**Observable outcome**

1. 专家列表每行显示一个可达性徽章，四档之一：`可达 高` / `可达 低` / `可达 未知` / `已退订·停发` / `邮箱失效·停发`（后两者同属 BLOCKED 档，文案分开）。
2. 专家列表新增「可达性」筛选下拉，可按档位过滤；可按可达性排序。
3. 批量发送任务配置新增「可达性过滤」项，任务执行时按该项过滤收件人。

**What must NOT change**

- N-1 现有专家列表的默认查询语义：不新增任何默认过滤条件，BLOCKED 专家**仍然显示在列表中**（需求方 2026-08-16 决策：打标但仍显示）。
- N-2 `CandidateEligibilityService` 的候选晋级判定行为。`enableActivityFilter` 默认 `false`（见 R-3），本计划不得改变该默认值，也不得改变 `INACTIVE` 判据表达式。
- N-3 外发邮件正文。`${lastPublicationYear}` 是既有模板变量（见 R-4），本计划会让它从「几乎恒为空串」变为「多数有值」，但不得修改任何模板正文、变量注入点或占位符校验规则。
- N-4 `operatorStatus` 的任何写入/读取/筛选行为。可达性与其**完全独立**，不得复用其字段、不得改动 `syncOperatorStatusBatch`。
- N-5 手动发送脱离每日配额的既有语义（`K-operator-send-quota-paths`）。

**Out of scope（明确后置，不在本 6 份计划内）**

- O-1 意愿评分（按 provider × region 历史回复率给未联系专家打先验分）。`MailMonitoringService.providerDistribution()` / `regionDistribution()` 已在算这两个维度的回复率，反向使用是独立需求。
- O-2 可达性对账服务（仿 `OperatorStatusReconcileService` 的只读三方比对）。属运维工具，不阻塞功能上线。
- O-3 打开率追踪 / 已读回执。全仓无 tracking pixel（见 R-31），且冷发信引入追踪像素对送达率为净负资产，明确不做。
- O-4 修复 `orcid_info_raw.json` 缺失 `operatorStatus` 声明（见 R-20，既有缺陷，本计划仅记录为 observation）。
- O-5 RAW 层的可达性。本计划只写 CANDIDATE + APPLICATION 两层（理由见 I-4）。

## 全局关键不变量（跨计划，子计划按号引用）

### Invariant I-1: BLOCKED 是确定性事实，不是评分
- Rule: `BLOCKED` 只能由两个确定性事实产生——`email_suppression` 命中，或 `bounce_record` 存在 `bounce_type='HARD'` 记录。禁止由任何阈值、权重、评分表达式产生 `BLOCKED`；也禁止把 `BLOCKED` 与 `LOW` 放在同一条可调阈值的连续分上。
- Applies to: `ExpertReachabilityClassifier.classify()`（新增，计划 02）；任何未来的评分扩展。
- Violation consequence: 运营调整阈值即可让已退订专家重新进入投放范围。退订放行是**合规问题**，硬退放行是**发件账号声誉问题**，二者均不可由业务参数控制。
- 来源: original（需求方 2026-08-16 决策）

### Invariant I-2: UNKNOWN 由「字段不存在」表示，不写字符串
- Rule: ES 侧 `reachability` 字段**缺失** = `UNKNOWN`。禁止写入 `"UNKNOWN"` 字符串值。判定 UNKNOWN 的 ES 表达式为 `bool.must_not.exists(reachability)`，内存侧对应 `profile.reachability.isNullOrBlank()`。
- Applies to: 写入侧 `ExpertIndexWriterService.syncReachabilityBatch()`（计划 03）；读取侧 `ExpertSearchService.reachabilityFilter()`（计划 05）、`RecipientScope.matchesExpert()`（计划 05）、前端 `renderContactListItems()`（计划 04）。
- Violation consequence: 双重表示（字段缺失 与 `"UNKNOWN"` 字符串）并存，筛选 UNKNOWN 时两种文档只能命中一种，且无报错。
- 来源: K-operator-status-single-writer（其 I-5：ES 侧「未联系」= `operatorStatus` 字段缺失，`ExpertIndexWriterService:76` 的 `ctx._source.remove('operatorStatus')`）——本不变量为同构复用，先例见 R-6。

### Invariant I-3: UNKNOWN ≠ LOW，信息缺失不得降级为负面判定
- Rule: 当 `enrichedAt` 缺失、或 `lastPublicationYear` 缺失且 `worksCount` 缺失时，结果必须是 `UNKNOWN`，不得落入 `LOW`。`classify()` 中判定 `LOW` 的每个分支都必须建立在**该维度有值**的前提上。
- Applies to: `ExpertReachabilityClassifier.classify()`（计划 02）；前端徽章文案与配色（计划 04，UNKNOWN 用中性灰、不用警告色）。
- Violation consequence: 与 `intro-mail-fallback-renders-as-title` 同类错误——兜底值被当作真值。运营会依据「数据还没到齐」的判断去砍投放量，且无任何报错提示。
- 来源: original（需求方 2026-08-16 决策）

### Invariant I-4: 写入层级集合 = mapping 断言层级集合 = CANDIDATE + APPLICATION
- Rule: `reachability` 只写 CANDIDATE 与 APPLICATION 两层；mapping 前置断言只检查这两层；`orcid_info_raw.json` **不**声明该字段。列表在 `level=RAW` 时该字段恒缺失（= UNKNOWN），这是已知且可接受的表现。
- Applies to: `es/orcid_info_candidate.json`、`es/orcid_info_application.json`（计划 02）；`ExpertIndexService.checkReachabilityMapping()`（计划 02）；`syncReachabilityBatch()` 的层级循环（计划 03）。
- Violation consequence: 若断言层级 ⊃ 写入层级，会重蹈 `checkOperatorStatusMapping()` 的覆辙——该方法循环三层（R-19），而 `orcid_info_raw.json` 从未声明 `operatorStatus`（R-20/R-23），断言在 RAW 层结构上无法通过。
- 来源: original（由 R-19 + R-20 推出）

### Invariant I-5: 增量写入失败不得回传为业务失败
- Rule: 退订登记（`EmailSuppressionService.suppress()`）与硬退落库路径中的可达性单点更新，必须捕获并吞掉全部异常，只记 warn 日志。ES 写失败不得导致退订/退信记录本身失败。
- Applies to: 计划 03 的两个增量挂载点。
- Violation consequence: 退订接口因 ES 不可用而失败 → 用户点了退订却没生效 → 合规风险。ES 侧是冗余，下一轮全量扫描会自愈。
- 来源: original

### Invariant I-6: 单一新字段
- Rule: 本 6 份计划对 ES 索引只新增 `reachability` **一个**字段。判定依据（命中哪条规则、退订时间、退信 DSN）一律不落 ES，只在前端 `title` 属性中由已有字段拼装展示。
- Applies to: 全部 6 份计划。
- Violation consequence: create-p 硬限制「Max 1 new data field per shared store」；每多一个字段，写入路径 × 读取路径的交互面翻倍。
- 来源: create-p Phase 2 scope check

## 共享现状审计（证据）

> 所有命令均在项目根执行。输出为当次实际输出，逐字保留。

### R-1 `lastPublicationYear` 全仓引用（主源码）

```bash
grep -rn "lastPublicationYear" --include=*.kt src/main/kotlin
```

输出 17 行，按角色归类：

- **写入 ExpertProfile 的仅 2 处**：`ExpertDiscoveryService.kt:619`（ORCID 路径，硬编码 `lastPublicationYear = null`）、`:745`（论文全文路径，`lastPublicationYear = paper.pubYear`）
- **写入 ES doc map 仅 1 处**：`ExpertDiscoveryService.kt:759`（`"lastPublicationYear" to profile.lastPublicationYear`）
- 读取：`ExpertSearchService.kt:418`（`_source` → profile）、`:452`（`sourceFields()` 白名单）、`:801`（`hasField` 存在性判定）、`:945`（`recentYears` range 过滤）
- 消费：`CandidateEligibilityService.kt:48`（晋级门槛，见 R-3）、`MailVariableService.kt:142`（邮件模板变量，见 R-4）、`MailPlaceholderService.kt:111/133/156/179`（变量元数据）
- 传输：`ExpertIndexController.kt:396`（响应字段）、`:442`（映射）
- 定义：`ExpertProfile.kt:17`
- 注释：`ManualInitialOutreachService.kt:423`

**结论（全称判断，有证据）**：OpenAlex enrichment 路径 **完全没有** 写入 `lastPublicationYear`——
`ExpertDiscoveryService.updateExpertAcademicFields()`（`:1082-1096`）的 doc map 中不含该键，
上述 grep 在该函数范围内零命中。故当前只有「论文全文发现」路径产出的专家有该值。

### R-2 `fetchRecentWorks` 已取回年份却丢弃

```bash
grep -n "publication_year\|pubYear" src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt
```

```
66:        parts += "publication_year:${criteria.publicationYearFrom}-${criteria.publicationYearTo}"
100:                    title = node.path("title").asText(""), pubYear = node.path("publication_year").asInt(0),
127:        val url = "$worksUrl?sort=publication_year:desc&per_page=$limit&select=title,publication_year" +
147:        val url = "$worksUrl?filter=type:patent&per_page=$limit&select=title,publication_year" +
```

`:127` 的请求已经 `sort=publication_year:desc` 且 `select` 含 `publication_year`，但函数体
（`:131-133`）只 `mapNotNull { it.path("title") }`，年份被丢弃。**故补齐 `lastPublicationYear` 不需要任何新增 OpenAlex 请求。**

### R-3 `enableActivityFilter` 默认关闭

```bash
grep -rn "enableActivityFilter\|recentYearsThreshold" src/main/resources/application.yml src/main/kotlin/com/weibo/talentintroduction/config/*.kt
```

```
src/main/resources/application.yml:121:    enable-activity-filter: ${ACADEMIC_ENABLE_ACTIVITY_FILTER:false}
src/main/kotlin/com/weibo/talentintroduction/config/AcademicFilterProperties.kt:13:    val enableActivityFilter: Boolean = false,
src/main/kotlin/com/weibo/talentintroduction/config/AcademicFilterProperties.kt:14:    val recentYearsThreshold: Int = 5
```

消费点 `CandidateEligibilityService.kt:47-50`：

```kotlin
if (academicProperties.enableActivityFilter) {
    val cutoff = Year.now().value - academicProperties.recentYearsThreshold
    if ((expert.lastPublicationYear ?: 0) < cutoff)
        reasons += "INACTIVE"
}
```

**结论**：该过滤器默认关闭（yml 与 Kotlin 默认值双重证据），故计划 01 补齐 `lastPublicationYear`
在默认配置下**不改变晋级行为**。但若线上设置了 `ACADEMIC_ENABLE_ACTIVITY_FILTER=true`，
行为会从「`null ?: 0 < cutoff` 恒真 → 全部 INACTIVE」变为「按真实年份判定」。
该环境变量的线上取值**不可从代码证明**，列为计划 01 的人工前置核对项 A-1。

### R-4 `lastPublicationYear` 是既有邮件模板变量

```bash
grep -rn "lastPublicationYear" src/main/resources/db/migration/*.sql
```

输出：（无匹配）

```bash
sed -n 140,145p src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt
```

```
                "lastPublicationYear" to (expert.lastPublicationYear?.toString()).orEmpty(),
```

**结论**：该变量在 `MailPlaceholderService.EXPERT_KEYS`（`:111`）中已注册，`MailVariableService:142`
会注入。种子迁移中无任何模板正文使用它（上述 grep 零命中）。但**运营运行时可能已在
`mail_compose_template_block.custom_text` 中加入该占位符**——这不可从代码证明，
列为计划 01 的人工前置核对项 A-2（上线前对生产库执行 `SELECT` 核对）。

### R-5 「字段不存在 = 某状态」的既有先例（I-2 的实现范本）

```bash
sed -n 226,236p src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt
```

```kotlin
fun operatorStatusesFilter(statuses: List<String>): Map<String, Any>? {
    val values = statuses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (values.isEmpty()) return null
    return mapOf(
        "bool" to mapOf(
            "should" to values.map { operatorStatusPredicate(it) },
            "minimum_should_match" to 1
        )
    )
}
```

内存侧同口径实现，`BatchExecutionModels.kt:63-68`：

```kotlin
if (operatorStatuses.isNotEmpty()) {
    val matched = operatorStatuses.any {
        if (it == "NOT_CONTACTED") profile.operatorStatus.isNullOrBlank()
        else profile.operatorStatus == it
    }
    if (!matched) return false
}
```

### R-6 ES 筛选构造点：**恰 4 处**

```bash
grep -rn "fun buildExpertFilters\|fun buildEsFiltersForLevel\|fun matchesExpert\|fun buildMaterialReminderEsFilters" --include=*.kt src/main/kotlin
```

```
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1129:    private fun buildMaterialReminderEsFilters(
src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt:1272:    private fun buildEsFiltersForLevel(scope: RecipientScope, level: String): List<Map<String, Any>> {
src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt:60:    fun matchesExpert(profile: ...): Boolean {
src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt:905:    private fun buildExpertFilters(
```

**知识条目 `K-discipline-unclassified-filter-bypasses` 已过期，必须就地更正**（create-p Phase 6 要求）：
该条目称三处旁路「直接 `term`，未复用 `ExpertSearchService.disciplineFilter()`」，并给出行号
`:1219` / `BatchExecutionModels.kt:54` / `:1086`。实测三处均已改为委托：

- `ManualInitialOutreachService.kt:1284`：`scope.discipline?.let { base.add(ExpertSearchService.disciplineFilter(it)) }`
- `ManualInitialOutreachService.kt:1141`：`filters.add(ExpertSearchService.disciplineFilter(config.discipline))`
- `BatchExecutionModels.kt:70-76`：含 `if (discipline == "UNCLASSIFIED") profile.disciplineCategory.isNullOrBlank()` 分支

行号亦全部漂移。**推论：可达性筛选应沿用同一架构——在 `ExpertSearchService` companion 内新增
`reachabilityFilter()` 静态方法作为唯一权威实现，4 处构造点各自委托调用，不自持表达式。**

### R-7 `scrollExperts` 签名（计划 03 的全量遍历原语）

```bash
sed -n 330,342p src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt
```

```kotlin
fun scrollExperts(
    level: ExpertIndexLevel,
    batchSize: Int = 500,
    handler: (List<ExpertProfile>) -> Boolean
) {
    scrollExperts(level, batchSize) { batch, _, _ -> handler(batch) }
}

fun scrollExperts(
    level: ExpertIndexLevel,
    batchSize: Int = 500,
    handler: (batch: List<ExpertProfile>, batchNumber: Int, totalHits: Long) -> Boolean
) {
```

三参重载提供 `batchNumber` 与 `totalHits`，正是 `TaskProgress` 上报所需，计划 03 用三参版本。

### R-8 `syncOperatorStatusBatch` 与 `resolveOrcidToDocIds`（计划 03 的复制源）

```bash
grep -n "fun syncOperatorStatusBatch\|private fun resolveOrcidToDocIds" src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt
```

```
113:    fun syncOperatorStatusBatch(updates: List<Pair<String, String>>): BulkSyncResult {
213:    private fun resolveOrcidToDocIds(index: String, orcidIds: List<String>): Map<String, String> {
```

`:132-139` 是「NOT_CONTACTED → script 删字段」的实现，即 I-2 的范本：

```kotlin
val data = if (operatorStatus == "NOT_CONTACTED") {
    mapOf("script" to mapOf(
        "source" to "if (ctx._source.containsKey('operatorStatus')) { ctx._source.remove('operatorStatus'); ctx._source.updatedAt = params.updatedAt; }",
        "params" to mapOf("updatedAt" to now)))
} else { ... }
```

返回值 `BulkSyncResult`（`:673-688`）已实现 `TaskExecutionSummaryProvider`，含
`PARTIAL_SUCCESS / FAILED / SUCCESS` 推导，计划 03 直接复用，不新建结果类型。

### R-9 mapping 断言先例与 RAW 层缺口（I-4 的依据）

```bash
grep -n "fun checkOperatorStatusMapping" -A5 src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexService.kt
```

```
170:    fun checkOperatorStatusMapping(): Boolean {
171-        // IP-3: operatorStatus is written to all three layers (RAW/CANDIDATE/APPLICATION),
172-        // so the mapping precondition must hold on all three.
173-        for (level in listOf(ExpertIndexLevel.RAW, ExpertIndexLevel.CANDIDATE, ExpertIndexLevel.APPLICATION)) {
```

```bash
for f in raw candidate application; do echo "--- orcid_info_$f.json"; grep -n '"dynamic"\|"operatorStatus"' src/main/resources/es/orcid_info_$f.json; done
```

```
--- orcid_info_raw.json
7:    "dynamic": false,
--- orcid_info_candidate.json
7:    "dynamic": false,
38:      "operatorStatus": { "type": "keyword" },
--- orcid_info_application.json
7:    "dynamic": false,
48:      "operatorStatus": { "type": "keyword" },
```

**Observation（既有缺陷，不在本计划范围，见 O-4）**：`orcid_info_raw.json` 声明 `dynamic: false`
却**未声明** `operatorStatus`。`checkOperatorStatusMapping()` 要求三层皆有 keyword mapping，
故仓库声明与断言不自洽。本计划据此定下 I-4：可达性的写入层级与断言层级严格一致，只取
CANDIDATE + APPLICATION。

### R-10 mapping 唯一声明源已收敛（新增字段可直接生效）

```bash
grep -n "loadMappingProperties" -A4 src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexService.kt
```

```
149:    private fun loadMappingProperties(resource: String): Map<String, Any>? {
150-        // I-1: the es/*.json file is the single declaration source for index mappings.
151-        // No field-name whitelist lives in Kotlin; every property declared in JSON is pushed.
```

确认 `K-es-mapping-single-declaration-source` 记载的 `phase5NewFields` 白名单**已移除**（该条目内容有效，
只需把「曾有」的表述保留即可，无需更正）。故计划 02 在 JSON 中加一行即可让字段推送到既有索引，
无需 Kotlin 侧配套改动。

同条目的 I-4 提示「新增 mapping 不追溯存量：`_source` 已有值不会自动进倒排索引，须 `_update_by_query`」
——对本计划**不适用**：`reachability` 是全新字段，值由计划 03 的 bulk update 首次写入，写入即索引。

### R-11 迁移最大版本号

```bash
ls src/main/resources/db/migration/ | sed 's/V\([0-9]*\)__.*/\1/' | sort -n | tail -3
```

```
97
98
99
```

计划 06 的新迁移为 **V100**。

### R-12 `updateLegacyConfig` 的字段保留契约

```bash
sed -n 173,198p src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt
```

关键行（逐字）：

```kotlin
                // M-2: 旧 typed API 不传该字段，必须显式保留现有多值状态（漏写会命中默认值静默重置）。
                operatorStatuses = parseOperatorStatuses(existing.operatorStatusesJson),
                templateId = request.templateId,
                // I4a-6 (M-2): 旧 typed API 不传门禁开关，必须显式保留存量值（漏写会命中默认值静默重置为 false）。
                gateFilterEnabled = existing.gateFilterEnabled
```

计划 06 必须在此处新增第三条同款保留行。证据链与 `K-batch-config-legacy-adapter-field-preservation` 一致，
且该知识条目指出的三类映射（`toView()` / `toLegacyConfig()` / 三个 `*Fields()`）在计划 06 中逐一处理。

### R-13 `gateFilterEnabled` 的前端触点规模（计划 06 工作量依据）

```bash
grep -n "gateFilterEnabled" src/main/resources/static/app.js
```

输出 12 行：`13389, 13550, 14281, 14301, 14337, 14419, 14503, 14524, 14555, 14631, 14649, 14659`。

**结论**：批量任务配置项在前端是 12 处触点的规模，故计划 06 必须独立成计划，不可与计划 05 合并。

### R-14 专家列表前端锚点

```bash
grep -n "async function loadContacts\|function renderContactListItems\|const hIndexBadge\|const enrichedBadge\|function badge(" src/main/resources/static/app.js
```

```
1463:function badge(value, type) {
4507:async function loadContacts() {
4739:function renderContactListItems() {
4760:        const hIndexBadge = contact.hIndex != null
4763:        const enrichedBadge = contact.enrichedAt
```

```bash
grep -n "expertSortBy\|expertRegionFilter\|expertDisciplineFilter\|expertHIndexMinFilter\|expertRecentYearsFilter" src/main/resources/static/index.html
```

```
458:                        <select id="expertSortBy">
522:                        <select id="expertRegionFilter">
528:                        <select id="expertDisciplineFilter">
537:                        <input type="number" id="expertHIndexMinFilter" ...>
545:                        <select id="expertRecentYearsFilter">
```

### R-15 `runAndRecordWithResult` 签名（计划 03 端点）

```bash
sed -n 87,94p src/main/kotlin/com/weibo/talentintroduction/task/service/TaskExecutionService.kt
```

```kotlin
fun <T : Any?> runAndRecordWithResult(
    taskType: String,
    triggerType: String,
    request: Any,
    onStarted: ((executionId: Long) -> Unit)? = null,
    batchConfigId: Long? = null,
    block: () -> T
): Pair<TaskExecution, T> {
```

### R-31 全仓无打开率追踪（O-3 的依据）

```bash
grep -rln "trackingPixel\|openTracking\|/track/open\|readReceipt\|Disposition-Notification" --include=*.kt --include=*.sql --include=*.js src
```

输出：（无匹配）

## 计划分解与执行顺序

create-p Phase 2 硬限制：单计划 ≤10 文件、≤2 子系统、每个共享存储 ≤1 新字段。
6 个步骤合计远超上限（仅 ES + 后端服务 + 前端 + 迁移已跨 4 个子系统），故必须分解。

| 序 | 计划文件 | 交付 | 文件数 | 依赖 |
|----|---------|------|--------|------|
| ~~1~~ | ~~`expert-reachability-01-last-publication-year.md`~~ | **已作废**（2026-08-16 需求方决策，见「修正记录」A-1） | — | — |
| 2 | `expert-reachability-02-classifier-and-mapping.md` | `classify()` 纯函数 + 2 份 ES JSON + mapping 断言 | 8 | 无（首个可执行计划） |
| 3 | `expert-reachability-03-sync-and-backfill.md` | 写入方法 + sync 服务 + 端点 + 2 个增量点 | 8 | 02 |
| 4 | `expert-reachability-04-list-badge.md` | 列表徽章展示（含样式契约） | 5 | 03 |
| 5 | `expert-reachability-05-filter-seams.md` | 4 处筛选落点 + 列表筛选控件 | 7 | 03（04 非前置） |
| 6 | `expert-reachability-06-batch-config.md` | V100 迁移 + 配置列 + 前端 12 触点 | 9 | 05 |

**顺序约束（强）**

- ~~01 → 02~~：计划 01 已作废，02 成为首个可执行计划，无前置依赖。
- 02 → 03：mapping 未声明时 03 的前置断言必然抛异常，无法联调。
- 03 → 04/05：无数据时前端与筛选均无法人工验收。
- 05 → 06：`reachabilityFilter()` 权威实现在 05 落地；06 只接线配置项，不重复实现表达式。
- 04 与 05 相互独立，可并行。

**上线节奏**

修正后的口径**不依赖 enrichment**：两个判据（`emailSource`、邮箱域名）在两条发现路径上
均于建档时写入（`ExpertDiscoveryService:620` / `:746`），回填当天即 100% 覆盖，
不存在「数据逐步到齐」的混合期。故原「先展示、暂缓开过滤、等 30 天 enrichment 轮完」
的约束**已随计划 01 一并作废**，06 可在 05 完成后正常上线。

唯一仍需留意的是：`UNKNOWN` 档现在只覆盖「`emailSource` 缺失」的早期存量文档，
预期占比很低；若回填后 `UNKNOWN` 占比异常高（>10%），说明存量文档缺字段的规模超预期，
应先排查再开 06 的过滤。

## 验证命令

> 本项目**必须**使用 JDK 11（zulu-11）；裸 `mvn` 在更高版本 JDK 下构建失败。
> 全部 6 份子计划的「跑测试 / 构建通过」一律引用本节，不得就地重写简化版。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 本计划族新增测试类（逐计划，单独运行）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertReachabilityClassifierTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertReachabilitySyncServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ReachabilityFilterSeamTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigReachabilityTest

# 迁移集成测试（计划 06，需本地 Docker）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，且输出含 `Tests run: N, Failures: 0, Errors: 0`。
来源：`CLAUDE.md` 「Commands」章节（`:11/:14/:17/:20/:24`）与项目元信息 `test_command:` / `build_command:`（`:137/:139`）。

## 已知既有缺陷（observations，本计划族不修）

1. **`orcid_info_raw.json` 缺 `operatorStatus` 声明**（R-9）。`checkOperatorStatusMapping()` 三层断言与仓库声明不自洽，`/api/experts/backfill-operator-status` 在纯净环境下会 400。建议独立修复计划。
2. **知识条目 `K-discipline-unclassified-filter-bypasses` 已过期**（R-6）：其记载的三处 `term` 旁路均已改为委托 `ExpertSearchService.disciplineFilter()`，行号亦全部漂移。已按 create-p Phase 6 要求就地更正。
3. **`buildEnrichmentFilters` 的第三个 should 分支存在永久滞留风险**：`有 enrichedAt + 有 researchFields + 无 disciplineCategory`（`ExpertDiscoveryService:807-816`）对「topics 判不出 STEM/HUMANITIES 的专家」恒真，该批文档每轮被重复 enrich、反复消耗 OpenAlex 配额。本计划族不触碰该分支；建议独立排查。
4. **`emailVerifiedLevel` 不可作为邮箱质量信号**（见 R-44）。入库文档的取值只有 2 与 3，二者差别仅反映发现该专家时 MX 检查开没开，与邮箱本身无关。任何后续需求若想用它做质量分档，须先修 `EmailValidationService` 的取值语义。

## 新增证据（2026-08-16 口径修正后补充）

### R-44 `emailVerifiedLevel` 不可作为邮箱质量信号

```bash
grep -n "level\s*=\|reject(\|cacheAndReturn(" src/main/kotlin/com/weibo/talentintroduction/expert/service/EmailValidationService.kt
```
```
29:        if (normalized.isBlank()) return reject(0, "EMPTY_EMAIL")
47:                cacheAndReturn(normalized, 2, "NO_MX_RECORD")
48:                return reject(2, "NO_MX_RECORD")
50:            cacheAndReturn(normalized, 3, null)
55:            cacheAndReturn(normalized, 0, "INVALID_FORMAT")
56:            return reject(0, "INVALID_FORMAT")
61:            cacheAndReturn(normalized, 1, "DISPOSABLE_EMAIL")
62:            return reject(1, "DISPOSABLE_EMAIL")
67:                cacheAndReturn(normalized, 2, "NO_MX_RECORD")
68:                return reject(2, "NO_MX_RECORD")
72:        val level = if (mxEnabled) 3 else 2
73:        cacheAndReturn(normalized, level, null)
```

调用侧（`ExpertDiscoveryService:527-528` 与 `:695-696`）在 `!emailResult.valid` 时 `continue`，
故 **level 0 / 1 / 2(NO_MX) 的邮箱根本不入库**。入库文档的 `emailVerifiedLevel` 取值只有：

- `3` = 格式合法且 MX 记录存在（`:50` / `:72` 当 `mxEnabled=true`）
- `2` = 格式合法但 **MX 检查未启用**（`:72` 当 `mxEnabled=false`）

**结论（全称判断，有证据）**：2 与 3 的差别**只反映发现该专家时 MX 检查开没开**，
与该邮箱本身的质量无关。故 `emailVerifiedLevel` **从可达性口径中移除**，
不作为 HIGH/LOW 的判据。

### R-45 `ProviderResolver` 可作为「消费级邮箱」判据（现成实现，零新增数据源）

```bash
find src/main/kotlin -name "ProviderResolver.kt" -exec cat {} \;
```
```kotlin
@Service
class ProviderResolver {
    fun resolve(email: String?): String {
        val domain = email?.substringAfterLast('@', "")?.lowercase()?.trim().orEmpty()
        if (domain.isBlank()) return "other"
        return when {
            domain.endsWith(".edu") || domain.contains(".edu.") || domain.endsWith(".ac.uk") -> "edu"
            domain in GMAIL -> "gmail"
            domain in OUTLOOK -> "outlook"
            domain in YAHOO -> "yahoo"
            domain in TENCENT -> "tencent"
            domain in NETEASE -> "netease"
            else -> "other"
        }
    }
    companion object {
        private val GMAIL = setOf("gmail.com", "googlemail.com")
        private val OUTLOOK = setOf("outlook.com", "hotmail.com", "live.com", "msn.com")
        private val YAHOO = setOf("yahoo.com", "ymail.com")
        private val TENCENT = setOf("qq.com", "foxmail.com")
        private val NETEASE = setOf("163.com", "126.com", "yeah.net")
    }
}
```

既有消费者：`MailMonitoringService.resolveProviderFromDomain()`（`:337-341`），
用于服务商维度的送达/回复统计。

**只能反向使用**：`edu` 规则仅覆盖 `.edu` / `.edu.` / `.ac.uk`，遗漏 `.ac.jp` / `.edu.cn` /
欧陆高校域名等，故 `other` 是「机构 + 未识别」的混合桶，**不可正向断言 `other` 即机构**。
可达性口径只用它做负向判据：落在 5 个消费级 provider 之一 → 减分。

### R-46 `emailSource` 在两条发现路径均于建档时写入（UNKNOWN 判据改用它的依据）

```bash
grep -n "emailSource = " src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt
```
```
620:            emailSource = "ORCID_PUBLIC", emailVerifiedLevel = emailVerifiedLevel, dataSource = "ORCID"
746:            emailVerifiedLevel = emailVerifiedLevel, dataSource = paper.source,
```
（`:746` 所在的 `buildProfile` 于 `:745` 行写 `emailSource = "PAPER_FULLTEXT"`。）

**结论**：`emailSource` 由发现流程在建档时无条件写入，**不依赖 enrichment**。
故新口径下 `UNKNOWN` = `emailSource` 缺失，只会命中早期存量文档，
回填当天即可覆盖绝大多数专家 —— 这是删除原「30 天混合期」上线节奏约束的依据。

## 修正记录

> 依据项目 `CLAUDE.md` 的「Decision Log Protocol」：以下为**已关闭决策**。
> 后续验证轮次不得将这些条目重新报告为开放问题。

### A-1（2026-08-16）删除「近年仍在发论文」维度，计划 01 整份作废

- **决策方**：需求方，原话「可以忽略 近年仍在发论文」。
- **触发证据**：主计划 R-2 + `K-openalex-fetch-works-gated` —— `OPENALEX_FETCH_WORKS_ENABLED` 默认 false，批量 enrichment 路径不取 works，计划 01 在默认配置下对批量场景零产出。
- **影响**：计划 01 作废（文件保留为决策日志）；计划 02 口径删除 `lastPublicationYear` 条件；主计划「上线节奏」的 30 天混合期约束删除；验证命令删除 `OpenAlexLastPublicationYearTest`。

### A-2（2026-08-16）`emailVerifiedLevel` 移出口径

- **触发证据**：R-44。入库文档取值只有 2 与 3，差别只反映 MX 检查开关状态，非邮箱质量。
- **影响**：计划 02 的 HIGH 判据删除 `emailVerifiedLevel >= HIGH_VERIFIED_LEVEL` 分支与 `HIGH_VERIFIED_LEVEL` 常量。

### A-3（2026-08-16）新增「消费级邮箱域名」作为第二判据

- **决策方**：需求方，在「emailSource + 消费级域名 / 只用 emailSource / 只做停发标识」三选一中选定第一项。
- **触发证据**：R-45。`ProviderResolver` 已实现且已被 `MailMonitoringService` 消费，零新增数据源。
- **约束**：只能反向使用（消费级 → 减分），不得正向断言 `other` 即机构域名。
- **影响**：计划 02 的 `classify()` 新增 `ProviderResolver` 依赖（唯一外部依赖，纯函数性质保持——`resolve()` 无 IO）。

### A-4（2026-08-16）`UNKNOWN` 判据由 `enrichedAt` 缺失改为 `emailSource` 缺失

- **触发证据**：R-46。`emailSource` 建档即写、不依赖 enrichment；`enrichedAt` 依赖 enrichment 轮次。
- **影响**：计划 02 的 I-2-5 与口径表；计划 03 的人工验收 A-3；计划 04 的 UNKNOWN 悬停文案。
