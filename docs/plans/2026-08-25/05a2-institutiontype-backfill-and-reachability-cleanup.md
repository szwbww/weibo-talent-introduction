# 子计划 05A-2：institutionType 存量补采 + 可达性残留清理 + 版本门禁两阶段迁移（阶段一）

> 状态：待评审。三部分互不依赖，可分别发布。
> 前置：05A（`05a-institution-type-collection.md`）已于 2026-08-26 随 `6e928ec` 上线。
> 后续：Part A 产出的真实分布是 05B 的输入；Part C 是 05B 变更 VERSION 的**硬前置**。

---

## 需求描述

**Part A — institutionType 存量补采。** 05A 上线后实测覆盖率仅 618 / 117,546（0.53%），
不足以支撑 05B 的评分定档。原因是 enrichment 的选取口径是「30 天未刷新」，而 84,502 名专家
在最近 30 天内刚被刷新过，要等窗口自然滚动才会重新入池。本部分提供一条一次性补采通路，
把这批人立即拉回来补上 `institutionType`，把「等 30 天」压缩到「跑几小时」。

**Part C — 版本门禁两阶段迁移的阶段一。** 05B 必然要改 `ExpertClassificationService.VERSION`。
一旦改动，全池 117,546 条存量分类的 version 立刻过期，而发信门禁要求 version 严格等于当前 VERSION
——从 05B 上线到回填跑完这段时间里一封邮件都发不出去。两阶段迁移的第一阶段（把「等于一个版本」
改成「属于一个版本集合」）**必须先于** VERSION 变更单独上线，否则两阶段就退化成一阶段，起不到任何作用。
本部分只做机制，不改任何版本号，线上语义逐字不变。

**Part B — 前端可达性残留清理。** `74ec24d`（2026-08-19，`refactor(expert): remove reachability
classification`）删除了可达性的后端全链路，但没有动 `src/main/resources/static/**`。
于是页面上还留着两处筛选 UI 和一枚徽章，它们发出去的值后端完全不消费——是伪功能。

---

## 关键不变量

### Invariant I5a2-1: 不动 30 天常量本身
- Rule: 禁止把 `ExpertDiscoveryService.kt:858` 的 `LocalDateTime.now().minusDays(30)` 改小、
  改成配置项或允许调用方覆盖。补采必须走**另一条独立的过滤器**，不得改变常规刷新节奏。
- 理由: 该常量同时被 `getEnrichmentStats():800` 使用，改它会同时改变页面上「待补充」的口径；
  且一旦参数化，误设一次即全量重跑。
- Applies to: Part A Task A-1、A-2。

### Invariant I5a2-2: 默认行为逐字不变
- Rule: 不传新参数时，`enrichExistingExperts()` 走的过滤器必须仍是 `buildEnrichmentFilters(cutoff)`
  的原始返回值，逐字未变。新增参数一律为**尾部可空默认参数**（同 05A 的 I5a-6）。
- 验证: `ExpertDiscoveryServiceTest` 现有 21 处 `svc.enrichExistingExperts()` 无参调用必须零改动通过。

### Invariant I5a2-3: EMAIL- 前缀排除必须保留
- Rule: 补采过滤器必须原样带上 `must_not: prefix orcidId "EMAIL-"`（现位于 `:830-832`）。
- 理由: 那 22,714 名邮件抽取出的伪 ORCID 专家在 OpenAlex 查不到，放进去只会白烧 API 配额。

### Invariant I5a2-4: 复用同一个任务类型与并发锁
- Rule: 补采必须复用 `taskType = "EXPERT_ENRICHMENT"` 与 `progressStore.tryStartWithToken`
  （`ExpertDiscoveryController.kt:218-226`）。禁止新增可与常规 enrichment 并发的第二个任务。
- 理由: 两者写同一批文档的同一批字段，并发会互相覆盖且进度条互相踩踏。

### Invariant I5a2-5: 补采会刷新 enrichedAt —— 这是既有行为，须显式承认
- Rule: 不得为「避免刷新 enrichedAt」而改动 `updateExpertAcademicFields`（`:1092-1123`）。
- 后果（必须写进验收记录）: 补采成功的约 85,000 人，`enrichedAt` 会被统一刷新到补采当天，
  等于把他们的下一轮 30 天刷新整体推后。这是可接受的——他们本来就刚被刷新过。

### Invariant I5a2-6: Part B 只删不补
- Rule: 不得为了让可达性 UI「有意义」而恢复任何后端能力（分类器、ES 字段、同步任务、快照字段）。
  若业务日后仍要该功能，须完整恢复全链路，不能只补一处。
- Applies to: Part B 全部改动。

### Invariant I5a2-7: 不删 V103 的数据库列
- Rule: 不得新增迁移去 drop `batch_send_task_config.reachability_filter`
  （`V103__add_reachability_filter_to_batch_send_task_config.sql`）。
- 理由: `BatchSendTaskConfig.kt` 已不映射该列，留着零运行成本；删列不可逆。
  只在本计划记录它是孤儿列。

### Invariant I5a2-8: 勾选框禁用条件只摘掉可达性分支
- Rule: `app.js` 中 `${(!contact.contactId || isBlockedReach(contact.reachability)) ? 'disabled' : ''}`
  改为 `${!contact.contactId ? 'disabled' : ''}`——`!contact.contactId` 分支逐字保留。

### Invariant I5a2-9: 发信门禁的版本比较只有一个权威来源
- Rule: 全仓库对 `expertClassification.version` 的**发信门禁**比较必须且只能引用
  `ExpertClassificationService.ACCEPTED_CLASSIFICATION_VERSIONS`。禁止任何一处手写
  `!= ExpertClassificationService.VERSION` 或字面量版本号。
- 现存四处（缺一处，版本切换当天该路径就会静默停发）：

  | # | 位置 | 形态 |
  |---|---|---|
  | 1 | `ExpertSearchService.expertSendableFilter():55-63` | ES 谓词（调用点 2 个：`ManualInitialOutreachService.kt:1326`、`ExpertSearchService.kt:420`） |
  | 2 | `BatchExecutionModels.kt:71` `RecipientScope.matchesExpert` | 内存判定（批量收件范围） |
  | 3 | `ManualInitialOutreachService.kt:609` | 内存判定（发送前最后门禁） |
  | 4 | `InitialOutreachService.kt:44` | 内存判定（发送前最后门禁） |

### Invariant I5a2-10: 集合必须包含当前 VERSION，且日常只含它
- Rule: `ACCEPTED_CLASSIFICATION_VERSIONS` 必须包含 `VERSION`；非迁移窗口期 size 必须为 1。
  单测两条断言钉死。迁移窗口内允许临时为 2，回填完成并确认旧版本计数归零后立即摘除。

### Invariant I5a2-11: 回填的选取口径**不得**改用该集合
- Rule: `ExpertClassificationBackfillService.pendingOnlyFilter():179-197` 与
  `validate():165/172`、`ExpertClassificationAdminController:151/158`
  继续钉死 `VERSION`（目标版本），不得替换成 ACCEPTED 集合。
- 理由: pending 的语义是「还没到新版本」。若改用 ACCEPTED，迁移窗口内旧版本文档会被判为「已达标」，
  回填直接空跑——正好把两阶段迁移最关键的那一步废掉。

### Invariant I5a2-12: 阶段一是纯机制变更，结果集逐字不变
- Rule: 本部分上线前后，ES 查询命中集合与三处内存判定结果必须完全一致（集合只有一个元素，
  `terms:[v]` 与 `term:v` 等价）。任何命中数变化都是缺陷。

---

## 现状审计

### Part A：为什么现在只有 618 条

| 事实 | 代码/数据证据 |
|---|---|
| 待补充口径 = 无 `enrichedAt` 或 `enrichedAt < now-30d` | `buildEnrichmentFilters`（`ExpertDiscoveryService.kt:810-835`）的 should 三分支 |
| 失败**不写** `enrichedAt` | `NotFound`/`ApiError` 分支（`:925-933`）只累加计数，无 ES 写入；`enrichedAt` 只在 `updateExpertAcademicFields:1096` 写 |
| ⇒ 9,643 个 `ORCID_NOT_IN_OPENALEX` 永久滞留 pending | 2026-08-26 实测：scanned 10,330 / enriched 687 / failed 9,643 |
| ⇒ 84,502 个近 30 天已刷新的人本轮进不来 | `enrich/stats` 实测 `enrichedLast30d=84,502` |
| 另有 22,714 人永远进不来 | 117,546 − 84,502 − 10,330；按 `:830` 的 `must_not prefix orcidId "EMAIL-"` 推断为邮件抽取专家（**待一条 count 查询坐实**） |
| OpenAlex 侧成本很低 | `fetch-works-enabled: false`（`application.yml:176`）⇒ 每个作者不额外拉 works；85,000/50（`enrichment-batch-size`，`:173`）≈ 1,700 次请求 |
| ES 侧是瓶颈 | 每个成功文档 3 次 HEAD（`:1079-1090`）+ 最多 3 次 `_update`（`:1110-1121`），逐条无 bulk ⇒ 约 50 万次往返 |

### Part B：可达性后端已被删干净

`74ec24d` 删除清单（`git show --stat 74ec24d`）：`ExpertReachabilityClassifier.kt`(-61)、
`ExpertReachabilitySyncService.kt`(-147)、`ExpertReachability.kt`(-12)、两份 ES mapping 的字段、
`BatchExecutionModels.kt`(-30)、`BatchSendTaskConfig.kt`(-11)、`ExpertIndexController.kt`(-65)、
`ExpertSearchService.kt`(-61)、`BatchSendTaskConfigReachabilityTest.kt`(-252)。
**该提交没有触及任何 `src/main/resources/static/` 文件。**

当前核验：

- `grep -rin reachab src/main/kotlin` 只剩 `BounceCollectionService.kt:109` 一行陈旧注释。
- `ExpertIndexController` 的 `@RequestParam` 列表（`:55-68`）无 `reachability`
  ⇒ 前端 `params.set("reachability", …)` 被 Spring 静默丢弃。
- expert 包内无任何 `reachab` 标识符 ⇒ 响应里没有该字段，`e.reachability` 恒 `undefined`。
- `BatchExecutionSnapshot`（`BatchExecutionModels.kt:10-`）与 `RecipientScope`（`:52-`）均无该字段
  ⇒ 前端 `reachabilityFilter` 在预估与实际发送两条路径上都不消费，改它命中数必然不变。

**隐性行为（值得单独记一笔）**：`isBlockedReach(contact.reachability)` 恒为 `false`，
所以列表里「已退订/硬退不可勾选」的保护早已失效；徽章对所有人恒显示「可达 未知」。
实际拦截由发送时的抑制名单完成，因此**不是功能回归**，但 UI 承诺与实现不符。

### Part C：版本比较散落在四处，且是两种实现

`grep -rn "classification.version\|expertClassification.version" src/main/kotlin` 的结果分两类：

**发信门禁（本部分要改的四处）** —— 见 I5a2-9 的表。ES 侧一处谓词、内存侧三处独立副本，
三处内存副本的表达式逐字相同：

```kotlin
if (classification?.sendable != true || classification.version != ExpertClassificationService.VERSION)
```

**回填与管理面（本部分不改）** —— `ExpertClassificationBackfillService:165/172/191/289/310`、
`ExpertClassificationAdminController:61/151/158/160`、`ExpertClassificationScheduler:52`、
`ExpertIndexWriterService:360`（写入侧）。这些的语义是「目标版本」而非「可接受版本」，见 I5a2-11。

现有守卫：`ExpertSearchServiceTest` 有一条对 `expertSendableFilter()` 返回值的**逐字断言**
（`assertEquals` 硬编码 map）。本部分把 `term` 改成 `terms` 会让它失败——**这正是它该做的事**，
按新形态更新该断言即可，不得删除该测试。

---

## 实现方案

### Part A

#### Task A-1：新增补采过滤器（I5a2-1、I5a2-3）
在 `ExpertDiscoveryService` 新增私有方法，与 `buildEnrichmentFilters` 并列、互不调用：

```kotlin
/** I5a2-3：补采只针对 OpenAlex 认得（有 enrichedAt）且尚无 institutionType 的人。 */
private fun buildInstitutionTypeBackfillFilters(): List<Map<String, Any>> = listOf(
    mapOf("bool" to mapOf(
        "must" to listOf(mapOf("exists" to mapOf("field" to "enrichedAt"))),
        "must_not" to listOf(
            mapOf("exists" to mapOf("field" to "institutionType")),
            mapOf("prefix" to mapOf("orcidId" to "EMAIL-"))
        )
    ))
)
```

#### Task A-2：`enrichExistingExperts` 接受尾部默认参数（I5a2-2）
```kotlin
enum class EnrichmentScope { DEFAULT, INSTITUTION_TYPE_BACKFILL }

fun enrichExistingExperts(scope: EnrichmentScope = EnrichmentScope.DEFAULT): EnrichmentResult {
    ...
    val filters = when (scope) {
        EnrichmentScope.DEFAULT -> buildEnrichmentFilters(cutoff)          // 逐字不变
        EnrichmentScope.INSTITUTION_TYPE_BACKFILL -> buildInstitutionTypeBackfillFilters()
    }
```
其余流程（批次、限流退避、进度、熔断）全部复用，不复制一份。

#### Task A-3：控制器透传（I5a2-4）
`ExpertDiscoveryController.enrichExperts()`（`:216-217`）加一个可选查询参数
`@RequestParam(required = false) scope: EnrichmentScope?`，默认 `DEFAULT`，
在 `:239` 处传下去。任务类型与并发锁一行不改。

#### Task A-4：页面入口
`index.html:608` 的下拉里新增一项「补采机构类型（一次性）」，走同一个 `handleDiscoverOption`
分支、同一个任务弹窗，仅在请求上带 `?scope=INSTITUTION_TYPE_BACKFILL`。
（若不想动 UI，也可只留 API——但那样每次都得手动 curl，且拿不到任务弹窗的进度条。）

#### Task A-5（可选）：`enrich/stats` 增加补采待处理数
`EnrichmentStats` 增加 `institutionTypePending`，用 `countExperts(CANDIDATE, buildInstitutionTypeBackfillFilters())`。
纯新增字段，前端 `app.js:5521` 不改也不会坏。**它的价值是让人知道什么时候跑完了。**

### Part B

按下表逐处删除。共 4 个文件、约 26 处。

| 文件 | 位置 | 动作 |
|---|---|---|
| `index.html` | 专家列表工具栏 `<label>` 含 `#expertReachabilityFilter` | 整块删除 |
| `index.html` | `#editorFieldReachabilityFilter` 整个 `div` | 整块删除 |
| `index.html` | `#manualFieldReachabilityFilter` 整个 `div` | 整块删除 |
| `app.js` | 联系人 mock 分支 `reachability: null` | 删行，并去掉前一行 `enrichedAt: null` 的尾逗号 |
| `app.js` | `loadContacts` 里读 `#expertReachabilityFilter` 与 `params.set("reachability", …)` | 删 2 行 |
| `app.js` | ES 响应映射 `reachability: e.reachability ?? null,` | 删行 |
| `app.js` | `reachabilityMeta` 常量及其上方 T3 注释块 | 整块删除 |
| `app.js` | `isBlockedReach` 定义 | 删行 |
| `app.js` | `expertTypeLabels` 上方注释里「同 reachabilityMeta 的既有约定」 | 改为「详见 child 04 execution.md 偏差说明」，避免悬空引用 |
| `app.js` | `reachMeta` / `reachTitle` / `reachBadge` 三个常量 | 整块删除 |
| `app.js` | 勾选框 `disabled` 表达式 | 按 I5a2-8 改写 |
| `app.js` | 行内 `${reachBadge}${hIndexBadge}…` | 去掉 `${reachBadge}` |
| `app.js` | `gateSummaryParams` 里的两行 | 删 |
| `app.js` | 筛选数字徽章计数数组里的一行 | 删 |
| `app.js` | 列表筛选监听数组末尾的 `"expertReachabilityFilter"` | 删该元素 |
| `app.js` | `BATCH_REACHABILITY_LABELS` + `batchReachabilityFilterLabel` | 整块删除 |
| `app.js` | 定时任务列表的「可达性 · X」pill 块（S-6-2） | 整块删除 |
| `app.js` | 编辑器回填 `setVal("batchConfigEditorReachabilityFilter", …)` | 删行 |
| `app.js` | 编辑器 payload 里 `reachabilityFilter: val(…)`（**2 处**） | 删行 |
| `app.js` | `buildManualExecutionSnapshot` 里的 I-2 注释 + `reachabilityFilter` 行 | 整块删除 |
| `app.js` | 草稿状态 `reachabilityFilter: c.reachabilityFilter \|\| ""` 与默认值 `""` | 删 2 行 |
| `app.js` | 手动面板 `setVal` / `val` / `normalizeManualSnapshot` 三处 | 删 3 行 |
| `app.js` | `formatManualDiffValue` 的 `reachabilityFilter` 分支 | 删行 |
| `app.js` | diff 字段表 `{ key: "reachabilityFilter", label: "可达性过滤" }` | 删行 |
| `app.js` | diff 字段 id 映射 + `clearAllDiffMarkers` 数组里的 `manualFieldReachabilityFilter` | 删 2 处 |
| `app.js` | 编辑器预估监听数组里的 `"batchConfigEditorReachabilityFilter"` | 删该元素 |
| `styles.css` | `/* === 可达性徽章（列表项） === */` 起至 `.reach-blocked{…}` 止 | 整块删除 |
| `batchSendTaskConsoleVisualFix.test.js` | 两个 `it("… reachability …")` 用例 | 整块删除 |

### Part C

#### Task C-1：声明唯一权威集合（I5a2-9、I5a2-10）
放在 `ExpertClassificationService` 的 companion 里，**紧挨 `VERSION`**（`:220`）——
让任何人改 VERSION 时必然看见它：

```kotlin
const val VERSION = "rnd-v2-2026"

/**
 * I5a2-9：发信门禁接受的策略版本。日常只含 VERSION；
 * 仅在 VERSION 切换的迁移窗口内临时并列旧版本，回填完成且旧版本计数归零后立即摘除。
 * 顺序即 ES `terms` 数组顺序，必须稳定（逐字断言依赖它）。
 */
val ACCEPTED_CLASSIFICATION_VERSIONS: List<String> = listOf(VERSION)
```

#### Task C-2：ES 谓词改 terms（I5a2-12）
`ExpertSearchService.expertSendableFilter():55-63`：

```kotlin
mapOf("terms" to mapOf(
    "expertClassification.version" to ExpertClassificationService.ACCEPTED_CLASSIFICATION_VERSIONS
))
```
`sendable` 那一行逐字不动。两个调用点（`ManualInitialOutreachService.kt:1326`、
`ExpertSearchService.kt:420`）一行不改。

#### Task C-3：三处内存判定改集合成员判定（I5a2-9）
`BatchExecutionModels.kt:71`、`ManualInitialOutreachService.kt:609`、`InitialOutreachService.kt:44`
一律改为：

```kotlin
if (classification?.sendable != true ||
    classification.version !in ExpertClassificationService.ACCEPTED_CLASSIFICATION_VERSIONS)
```
三处的 `sendable != true` 分支、以及各自的 skip 记账逻辑逐字不动。

#### Task C-4：测试
1. 更新 `ExpertSearchServiceTest` 中 `expertSendableFilter()` 的逐字断言为 `terms` 形态
   （数组含且仅含当前 VERSION）。
2. 新增：`ACCEPTED_CLASSIFICATION_VERSIONS` 包含 `VERSION`、无重复、且当前 size == 1（I5a2-10）。
3. 新增：给定 `version = "rnd-v1-legacy"` 的 profile，三处内存判定**全部**拒绝；
   给定当前 VERSION 且 `sendable=true` 的 profile，三处**全部**放行。
4. 新增源码守卫测试（仿 `OperatorStatusWriteSeamGuardTest` 的写法）：扫描 `src/main/kotlin`，
   凡出现 `classification.version` 与 `VERSION` 直接比较的位置，必须恰好等于白名单
   ——白名单在阶段一之后应为**空集**。这条守卫的作用是防止 05B 或以后有人新增第五处漏改。

#### Task C-5：把阶段二的操作顺序写进本计划（不实现）
05B 变更 VERSION 时必须按此顺序，每步独立发布：

| 步 | 动作 | 此刻 ACCEPTED | 发信状态 |
|---|---|---|---|
| 0 | 阶段一（本计划 Part C）已上线 | `[v2]` | 正常 |
| 1 | 05B 上线：`VERSION = v3`，ACCEPTED 改为 `[v2, v3]` | `[v2, v3]` | **正常**（旧分类仍被接受） |
| 2 | 跑分类回填（`onlyPending=true`，选的是 version != v3 的人） | `[v2, v3]` | 正常 |
| 3 | 确认 `count(expertClassification.version == v2) == 0` | `[v2, v3]` | 正常 |
| 4 | 摘除旧版本：ACCEPTED 改回 `[v3]`，单独发一次 | `[v3]` | 正常 |

第 4 步不得与第 1 步合并。若跳过阶段一直接做第 1 步，第 1 步与第 2 步之间全池停发。

---

## 变更文件清单

**Part A**：`ExpertDiscoveryService.kt`、`ExpertDiscoveryController.kt`、`index.html`、`app.js`
（Task A-4）、`ExpertDiscoveryServiceTest.kt`、`ExpertDiscoveryControllerTest.kt`。

**Part B**：`index.html`、`app.js`、`styles.css`、`batchSendTaskConsoleVisualFix.test.js`。

**Part C**：`ExpertClassificationService.kt`（只加常量，分类逻辑一行不改）、
`ExpertSearchService.kt`、`BatchExecutionModels.kt`、`ManualInitialOutreachService.kt`、
`InitialOutreachService.kt`、`ExpertSearchServiceTest.kt`、
`InitialOutreachServiceTest.kt`、`ManualInitialOutreachServiceTest.kt`
（`RecipientScope.matchesExpert` 的现有覆盖在此类与 `BatchSendTaskRuntimeIntegrationTest.kt`，
仓库无 `BatchExecutionModelsTest`）、新增一个版本门禁源码守卫测试类。

---

## 验证命令

```
# 先在同一台机器上跑一次 HEAD 基线，取差值比对（不同机器的 node 计时表现不同）
git stash push -- src/main/resources/static src/test/js && node --test src/test/js/*.test.js | tail -8 && git stash pop

# 语法
node --check src/main/resources/static/app.js

# 前端用例：Part B 删掉 2 条 ⇒ 总数应由 744 降为 742，fail 计数与基线相同
node --test src/test/js/*.test.js | tail -8

# 残留归零（Part B 唯一硬判据）
grep -rn "reachab\|可达性\|reach-badge\|reach-high\|reach-low\|reach-unknown\|reach-blocked" \
  src/main/resources/static src/test/js
# 期望：无输出

# 后端（Part A）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='ExpertDiscoveryServiceTest,ExpertDiscoveryControllerTest' -q

# 后端（Part C）：门禁四处 + 分类零变化
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest='ExpertSearchServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ExpertClassificationServiceTest,BatchSendTaskRuntimeIntegrationTest' -q

# Part C 硬判据：发信门禁的版本比较不得有第五处
grep -rn "classification\.version" src/main/kotlin
# 期望：只出现在 ACCEPTED_CLASSIFICATION_VERSIONS 的四个引用点，以及回填/管理面的目标版本比较（I5a2-11）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
```

⚠️ 本仓库挂载在 Cowork 侧时 `rm`/`unlink` 被禁止，`git stash` 会留下 `.git/index.lock`。
上面这条基线比对命令请在 Mac 本机（omp）执行。

---

## 验收标准

**Part A**
1. 不传 `scope` 时，`enrich/stats` 的 `pending` 与补采前逐字一致；`ExpertDiscoveryServiceTest`
   的 21 处无参调用零改动通过（I5a2-2）。
2. 传 `scope=INSTITUTION_TYPE_BACKFILL` 跑完后，CANDIDATE 层
   `exists institutionType` 的计数从 618 升至 **≥ 60,000**（保守下限；理论上限约 85,000）。
3. 补采期间 `failureReasons` 里 `ORCID_NOT_IN_OPENALEX` 占比应**显著低于**常规轮的 93%
   ——这批人上次都成功过。若仍是 90%+，说明过滤器写错了，立即停止。
4. 分类结果与发信行为零变化（沿用 05A 的 A5a-4 / A5a-5 做法复核）。

**Part B**
5. 上面那条 grep 无输出。
6. 定时任务列表、配置编辑器、手动发送面板、专家列表页四处目视无「可达性」字样，
   且各自其余筛选项功能正常。
7. 专家列表行内不再出现「可达 未知」徽章；无邮箱联系人（`!contact.contactId`）的勾选框仍为禁用。

**Part C**
8. `ACCEPTED_CLASSIFICATION_VERSIONS` 的单测三条断言（含 VERSION、无重复、size == 1）通过。
9. 上线前后，同一套筛选条件下的收件人预估命中数**逐字相同**（I5a2-12）。
   这是本部分唯一的线上判据——机制换了，结果不许变。
10. 源码守卫测试通过：发信门禁不存在第五处手写版本比较。

---

## 人工验收清单

### A5a2-1: 补采跑完后重跑分布盘点
- 前置条件: Part A 已上线并完整跑完一轮。
- 操作步骤: 按 `docs/runbooks/institution-type-distribution.md` 重跑，跳过其中的第 3 步
  （改跑补采），直接执行第 5 步盘点脚本。
- 预期结果: 产出覆盖率 ≥ 50%（相对全池 117,546）的真实分布表。
  **这张表是 05B 定分值的唯一依据；在它产出之前不要写 05B。**

### A5a2-2: 补采的幂等性
- 操作步骤: 第一轮跑完后立即再跑一轮。
- 预期结果: 第二轮的 `totalCount` 应大幅缩小（只剩 OpenAlex 有作者但无
  `last_known_institutions` 的那约 10%，以及新增专家）。若两轮 `totalCount` 相同，
  说明写入没生效，立即停止排查。

### A5a2-3: 补采不改机构名
- 操作步骤: 补采前后各记录同一批专家的 `institution` 字段。
- 预期结果: 逐字未变（沿用 05A 的 I5a-7：两条路径写的是不同机构的类型）。

---

### A5a2-4: 阶段一上线前后预估命中数不变（Part C）
- 前置条件: 记录一个定时任务配置（漏斗层级 + 地区 + 邮箱服务商 + 专家状态的组合）在上线前的预估命中数。
- 操作步骤: 上线 Part C 后，用**完全相同**的配置再预估一次。
- 预期结果: 两次数字逐字相同。任何差异都说明 `terms` 改写引入了语义变化，立即回滚。
- 覆盖: I5a2-12

### A5a2-5: 旧版本分类确实被拒（Part C 的反向验证）
- 前置条件: 在测试环境（**不要在生产做**）把某条 CANDIDATE 文档的
  `expertClassification.version` 改成 `rnd-v1-legacy`。
- 操作步骤: 用包含该专家的条件做一次预估，并在批量发送里跑一轮。
- 预期结果: 预估不计入该专家；发送侧记 `EXPERT_NOT_SENDABLE`。
  说明 `terms` 集合确实在收窄，而不是变成了「什么版本都放行」。
- 覆盖: I5a2-9、I5a2-12

---

## 遗留与不做

- `batch_send_task_config.reachability_filter` 列保留为孤儿列（I5a2-7）。
- `BounceCollectionService.kt:109` 那行提到 reachability 的陈旧注释：属于后端，
  不在本计划改动面内，留待下次碰该文件时顺手清理。
- 22,714 名 `EMAIL-` 前缀专家永远拿不到 `institutionType`，只能靠 works 路径在新发现时采集。
  ⇒ 05B 的「null = 无信号，绝不扣分」是硬性不变量，不是可选项。
- ES 侧逐条 HEAD + `_update` 改 bulk：能把往返数砍掉约 6 倍，但属于优化，不阻塞本计划。
