# 计划 05 — 四处筛选落点与列表筛选控件

> 依赖：计划 03。与计划 04 并行。后继：计划 06。共享证据见主计划。

## 需求描述

**Observable outcome**

1. `ExpertSearchService` 提供唯一权威的 `reachabilityFilter()` 静态方法。
2. 专家列表新增「可达性」筛选下拉，选项：全部 / 仅高可达 / 高+低（排除已失效）/ 仅未知 / 仅已失效。
3. 批量发送的 ES 目标查询与 MySQL 重试路径按同一口径过滤可达性（本计划先接通表达式，配置项由计划 06 提供）。

**What must NOT change**

- N-1 四处筛选构造点既有的过滤项与顺序（tags / operatorStatus / emailDomain / region / discipline / gateEsFields / hIndex / citation / recentYears / hasField）。
- N-2 `notContactedWithEmailFilters` / `notContactedWithEmailDomainsFilters` / `operatorStatusesFilter` / `disciplineFilter` / `regionFilter` / `fieldPresenceFilters` 的实现。
- N-3 `ALLOWED_HAS_FIELDS` 白名单内容（可达性**不**加入该白名单——它是独立筛选维度，不是「字段存在性」门禁）。

**Out of scope**

- O-1 批量任务配置列与前端配置 UI（属计划 06）。
- O-2 ES 侧按可达性排序（`missing` 参数处理）。第一版只做筛选；排序留待运营反馈后单独评估。

## 关键不变量

### Invariant I-5-1: 表达式唯一权威
- Rule: 可达性的 ES query 表达式只允许存在于 `ExpertSearchService.reachabilityFilter()` 一处；四处构造点一律委托调用，禁止自持 `term` / `terms` / `must_not exists` 表达式。
- Applies to: `ExpertSearchService.buildExpertFilters()`（`:905`）、`ManualInitialOutreachService.buildEsFiltersForLevel()`（`:1272`）、`ManualInitialOutreachService.buildMaterialReminderEsFilters()`（`:1129`）、`RecipientScope.matchesExpert()`（`BatchExecutionModels.kt:60`）。
- Violation consequence: 与 `K-discipline-unclassified-filter-bypasses` 记载的历史缺陷同构——旁路写 `term` 而权威实现是 `must_not exists`，UNKNOWN 档筛出 0 条且无报错。
- 来源: K-discipline-unclassified-filter-bypasses（行号已过期，更正见主计划 R-6）

### Invariant I-5-2: UNKNOWN 用 `must_not exists`，内存侧用 `isNullOrBlank()`
- Rule: 筛选 UNKNOWN 的 ES 表达式为 `{"bool":{"must_not":[{"exists":{"field":"reachability"}}]}}`；`matchesExpert` 中对应 `profile.reachability.isNullOrBlank()`。禁止 `term: {reachability: "UNKNOWN"}`。
- Applies to: `reachabilityFilter()`、`matchesExpert()`。
- Violation consequence: 违反主计划 I-2。
- 来源: 主计划 I-2；范本见主计划 R-5（`operatorStatusesFilter` + `matchesExpert` 的 NOT_CONTACTED 双侧写法）

### Invariant I-5-3: ES 侧与内存侧口径逐档等价
- Rule: 对同一份 `ExpertProfile` 数据，`reachabilityFilter()` 与 `matchesExpert()` 的可达性判定结果必须一致，四档 × 五个筛选选项全组合均成立。
- Applies to: `ReachabilityFilterSeamTest`（新增，必须以参数化用例覆盖 4×5 全组合）。
- Violation consequence: ES 路径（首封发送）与 MySQL 重试路径（重发失败者）目标集不一致，表现为「同一配置下重试轮发给了首轮明确排除的人」。
- 来源: K-batch-send-filter-retry-parity

### Invariant I-5-4: 空/未指定 = 不追加任何 filter
- Rule: 筛选参数为 null / 空串 / 空集合时，`reachabilityFilter()` 返回 `null`，调用方不追加任何 filter 项；`matchesExpert` 不做该维度判定。
- Applies to: 全部四处落点。
- Violation consequence: 违反 N-1（默认查询语义变化）。既有先例：`operatorStatusesFilter` 空集合返回 null（主计划 R-5，`:228`），`buildEsFiltersForLevel:1287` 注释「I3a-3: 空集合返回 null，不追加任何状态 filter」。
- 来源: 主计划 N-1；先例 `ExpertSearchService:228`

## 现状审计

### 四处构造点（主计划 R-6，逐字复述结论）

```bash
grep -rn "fun buildExpertFilters\|fun buildEsFiltersForLevel\|fun matchesExpert\|fun buildMaterialReminderEsFilters" --include=*.kt src/main/kotlin
```
```
ManualInitialOutreachService.kt:1129:    private fun buildMaterialReminderEsFilters(
ManualInitialOutreachService.kt:1272:    private fun buildEsFiltersForLevel(scope: RecipientScope, level: String): List<Map<String, Any>> {
BatchExecutionModels.kt:60:    fun matchesExpert(profile: ...): Boolean {
ExpertSearchService.kt:905:    private fun buildExpertFilters(
```

**恰 4 处**。三处 ES 构造点当前均已委托 `ExpertSearchService` 的静态方法
（`:1284` `disciplineFilter`、`:1141` `disciplineFilter`、`:1286` `operatorStatusesFilter`），
本计划沿用该架构。

### `RecipientScope` 需新增字段

```bash
sed -n 49,59p src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt
```
```kotlin
data class RecipientScope(
    val mailType: String,
    val funnelLevels: Set<String>,
    val tags: List<String>,
    val regions: List<String>,
    val emailDomains: List<String>,
    val discipline: String?,
    val operatorStatuses: List<String> = emptyList(),
    /** I4a-4: 已解析的门禁 ES 字段（ALLOWED_HAS_FIELDS 交集）；解析只发生在 resolveScope。 */
    val gateEsFields: List<String> = emptyList()
)
```
末尾追加 `val reachabilityFilter: String? = null`（带默认值，避免破坏既有构造点——
`RecipientScope(` 的构造点见下）。

```bash
grep -rn "RecipientScope(" --include=*.kt src/main | wc -l
```
执行此命令确认构造点数量，并在实现时逐一核对是否需要传值（带默认值时不需要，
但 `resolveScope` 相关路径必须传，否则配置项永远不生效——这是计划 06 的接线点）。

### 列表筛选控件位置（主计划 R-14）

`index.html:545` 是 `expertRecentYearsFilter`，新控件紧随其后。
`app.js:4669-4677` 是筛选值读取区块；`:11420-11421` 与 `:11435` 是「筛选是否激活」的判定与重置列表，
新控件必须同步登记，否则「清除筛选」不会清掉它。

```bash
grep -n "expertRecentYearsFilter" src/main/resources/static/app.js
```
```
4669:        const recentYears = $("#expertRecentYearsFilter")?.value || "";
11420:            ($("#expertRecentYearsFilter")?.value || "") !== "",
11435:        "expertRecentYearsFilter"].forEach((id) => {
11685:        const recentYears = $("#expertRecentYearsFilter")?.value || "";
```

**4 处**：读取（`:4669`）、激活判定（`:11420`）、重置列表（`:11435`）、另一处读取（`:11685`，
需实现时确认其所属函数）。新控件必须同步这 4 处，缺一则「筛选未生效」或「清不掉」。

### Interaction points

| # | 写入 | 读取 | 处置 |
|---|------|------|------|
| IP-1 | 计划 03 写 ES | `buildExpertFilters` | 本计划实现，A-1 验收 |
| IP-2 | 计划 03 写 ES | `buildEsFiltersForLevel` | 本计划接通表达式，实际生效由计划 06 传值 |
| IP-3 | 计划 03 写 ES | `buildMaterialReminderEsFilters` | 同 IP-2 |
| IP-4 | 计划 03 写 ES → `mapToProfile` | `matchesExpert` | I-5-3 保证与 IP-2 等价 |

## 实现方案

### T1 — `reachabilityFilter()` 权威实现（遵 I-5-1、I-5-2、I-5-4）

`ExpertSearchService` companion 新增：

```
fun reachabilityFilter(mode: String?): Map<String, Any>?
  null/空 → null
  "HIGH_ONLY"      → term reachability = HIGH
  "EXCLUDE_BLOCKED"→ bool.must_not.terms reachability in [BLOCKED_UNSUBSCRIBED, BLOCKED_BOUNCED]
                      AND bool.must.exists(reachability)   // 排除 UNKNOWN
  "UNKNOWN_ONLY"   → bool.must_not.exists(reachability)
  "BLOCKED_ONLY"   → terms reachability in [BLOCKED_UNSUBSCRIBED, BLOCKED_BOUNCED]
```
模式常量集中定义为 `ALLOWED_REACHABILITY_MODES`，非法值 `require(...)` 抛
`IllegalArgumentException`（`GlobalExceptionHandler` 映射为 400，见 `K-custom-exception-http-status-mapping`）。

### T2 — 内存侧同口径（遵 I-5-2、I-5-3、I-5-4）
`RecipientScope` 加字段 + `matchesExpert` 加判定段，位置紧随 `operatorStatuses` 段之后。

### T3 — 三处 ES 构造点接线（遵 I-5-1、I-5-4）
- `buildExpertFilters`：新增 `reachability: String? = null` 参数，`reachabilityFilter(it)?.let { filters.add(it) }`；
  `searchExperts` 签名同步加参数并透传；`ExpertIndexController.listExperts` 加 `@RequestParam`。
- `buildEsFiltersForLevel`：`ExpertSearchService.reachabilityFilter(scope.reachabilityFilter)?.let { filters.add(it) }`
- `buildMaterialReminderEsFilters`：同款一行

### T4 — 前端控件（遵 N-1）
`index.html` 在 `:545` 之后加 `<select id="expertReachabilityFilter">`，5 个 option
（value 分别为空串 / `HIGH_ONLY` / `EXCLUDE_BLOCKED` / `UNKNOWN_ONLY` / `BLOCKED_ONLY`）。
`app.js` 同步 4 处（读取 `:4669` 区块、激活判定 `:11420`、重置列表 `:11435`、`:11685` 区块）。

### T5 — 等价性测试（遵 I-5-3）
`ReachabilityFilterSeamTest`：参数化 4 档 × 5 模式 = 20 组，断言
`matchesExpert` 结果与「按 `reachabilityFilter` 的语义手写的期望值」一致；
另断言 `reachabilityFilter(null)` 与 `reachabilityFilter("")` 均返回 null。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | T1/T3 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | T2 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | T3 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | T3 |
| 5 | `src/main/resources/static/index.html` | T4 |
| 6 | `src/main/resources/static/app.js` | T4 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ReachabilityFilterSeamTest.kt` | 新增（T5） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | **修正记录 A3（授权）：** `EXCLUDED_NOISE_SITES` 三条 pin 行号同步 —— `ExpertIndexController.kt` 94→95、484→485，`ExpertSearchService.kt` 431→476（context 不变），遵该 guard 自带维护规程（`:130`）与 A1/A2 同机制 |

文件数 8 ≤ 10。子系统 2（筛选表达式 / 前端控件）。新增 ES 字段 0。

## 验证命令

见主计划「验证命令」节。本计划专属：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ReachabilityFilterSeamTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
```

## 验收标准

- I-5-1：`grep -rn '"reachability"' --include=*.kt src/main/kotlin` 的命中中，构造 ES query 的只有 `ExpertSearchService.reachabilityFilter` 一处（其余为 `sourceFields` 白名单、`mapToProfile` 与 writer 的字段名）。
- I-5-2：`grep -rn 'reachability" to "UNKNOWN"\|term.*reachability.*UNKNOWN' --include=*.kt src/main/kotlin` 零命中。
- I-5-3：`ReachabilityFilterSeamTest` 的 20 组参数化用例全绿。
- I-5-4：单测断言 `reachabilityFilter(null)` 与 `reachabilityFilter("")` 返回 null；断言未传该参数时 `buildExpertFilters` 的返回列表长度与改动前一致。
- N-3：`git diff` 中 `ALLOWED_HAS_FIELDS` 零改动行。
- 前端 4 处同步：`grep -c "expertReachabilityFilter" src/main/resources/static/app.js` ≥ 4。
- 回归：执行主计划「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 五个筛选选项各自生效
- 前置条件: 计划 03 回填完成，四档专家均有样本。
- 操作步骤: 依次选择 5 个选项，每次记录「筛选结果: N 位专家」的 N 值与随机抽查 3 行的徽章。
- 预期结果: 「全部」的 N 最大；「仅高可达」结果全为绿色徽章；「高+低」结果不含任何红色与灰色徽章；「仅未知」全为灰色；「仅已失效」全为红色；五个选项的 N 值满足 `高 + 低 + 未知 + 已失效 = 全部`。
- 覆盖: Observable outcome 2 / I-5-2

### A-2: 清除筛选能清掉该项
- 前置条件: 已选中「仅高可达」。
- 操作步骤: 1) 点击列表的「清除筛选」。2) 观察可达性下拉。
- 预期结果: 下拉回到「全部」；列表条数回到无筛选状态；「筛选已激活」的提示消失。
- 覆盖: 现状审计「列表筛选控件位置」的 4 处同步

### A-3: 批量任务 ES 路径与重试路径口径一致
- 前置条件: 计划 06 未上线时，可用 dry-run 或日志核对；计划 06 上线后用真实配置。
- 操作步骤: 1) 构造一个 `RecipientScope`，`reachabilityFilter = EXCLUDE_BLOCKED`。2) 观察 ES 目标查询命中数。3) 观察重试路径经 `matchesExpert` 过滤后的目标数。4) 抽查两者的交集与差集。
- 预期结果: 两条路径均不含任何 BLOCKED 专家；差集只应由「层级/状态」等其他维度造成，不应由可达性造成。
- 覆盖: I-5-3 / IP-2 / IP-4

### A-4: 回归 —— 既有筛选组合未受影响
- 前置条件: 无。
- 操作步骤: 1) 不选可达性，依次使用地区 / 学科 / 邮箱域名 / 状态 / 近 N 年 / H 指数筛选。2) 记录每次条数。3) 与改动前基线对比。
- 预期结果: 每项条数与改动前完全一致（未选可达性时不应追加任何 filter）。
- 覆盖: N-1 / I-5-4

### A-5: 回归 —— 材料提醒批次目标未变
- 前置条件: 存在带「承诺回复材料」标签的 APPLICATION 层专家。
- 操作步骤: 在未配置可达性过滤的情况下，触发一次材料提醒的收件人预估。
- 预期结果: 预估人数与改动前一致。
- 覆盖: N-1 / IP-3

## 修正记录

### A3（2026-08-16，fast-p 运行期）授权第 8 个文件：guard 测试行号 pin 同步

- **决策方**：需求方（fast-p 运行期授权，ask 选项「Approve amendment A3」）。
- **触发证据**：T3 对 `listExperts` 的 `@RequestParam` 追加使 `ExpertIndexController.kt` 行号 +1
  （guard pin 94→95、484→485）；T1 在 `ExpertSearchService` companion 新增 `reachabilityFilter()` 块
  （约 +45 行）使该文件 pin 431→476。`OperatorStatusWriteSeamGuardTest.EXCLUDED_NOISE_SITES` 三条 pin
  全部过期，guard 自检（`staleExclusions`）必然失败，且任何 T1/T3 实现都无法避免该漂移。
- **影响**：变更文件清单新增第 8 项 `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
  （仅行号同步 94→95、484→485、431→476，context 不变，零断言语义变更）。文件数 7→8，仍 ≤10。
