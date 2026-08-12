# 03 · 收件范围新增「地区」多选（后端）

> 主计划：`batch-send-rhythm-and-filter-00-master.md`
> 覆盖需求：第 3 条（后端部分）
> 依赖：无（与 01/02 并行安全，无共享写路径）
> 后继：04b（前端控件）

## 需求描述

### Observable outcome

1. 定时任务配置新增「地区」筛选维度，**支持多选**。选中多个大区时取并集（专家属于任一选中大区即命中）；不选表示不限制。
2. 地区筛选同时作用于**两条目标来源**：ES 新目标查询与 MySQL `NEW` 重试联系人（K-batch-send-filter-retry-parity），二者口径一致。
3. 地区取值为 `CountryContinentMapping` 的 9 个大区英文常量，非法取值在保存配置时被拒绝（422），不会静默命中 0 条。

### What must NOT change

- 专家列表（`/api/experts`）的**单选** `region` 查询参数与行为不变；`ExpertSearchService.searchExperts()` 的签名与 `buildExpertFilters()` 的 `region: String?` 形参不变。
- `aggregateRegions()` / `aggregateEmailDomains()` / `aggregateTags()` 的行为不变（K-agg-filter-source-of-truth 的互斥联动口径不动）。
- `regionFilter()` 生成的 ES 查询结构逐字不变（含 `REGION_OTHER` 的 `should + minimum_should_match: 1` 双分支特例）。
- 现有 4 个筛选维度（`funnelLevel` / `tags` / `emailDomain` / `discipline`）的语义与组合方式不变：多维度之间仍是 AND。
- `INTRODUCTION + CANDIDATE` 分支仍走 `notContactedWithEmailFilters()`（含 `operatorStatus` 不存在 + 非 `EMAIL_INVALID` 两条），不得改为通用分支。
- 前端不改（→ 04b）。

### Out of scope

- 前端地区多选控件 → 04b
- 地区中文显示 → 05
- 学科 `UNCLASSIFIED` → 05
- 手动执行 tab 的地区筛选（04b 一并处理前端；后端 `BatchExecutionSnapshot.regions` 本计划已就绪）
- 不改专家列表的单选地区筛选为多选

## 关键不变量

### Invariant I-1: 地区取值是英文领域常量，服务端强校验
- Rule: `regions` 列表中每一项必须 `in CountryContinentMapping.allRegions()`（9 个英文常量）。空列表 = 不限制。保存配置时（`BatchSendTaskConfigService.normalizeAndValidate()`）与启动执行时（`BatchSendControlService.validateSnapshotFields()`）**两处**都必须校验，非法值抛 `IllegalArgumentException` → 422。规范化规则：trim、去空串、去重、按 `allRegions()` 的顺序排序后持久化。
- Applies to: `BatchSendTaskConfigService.normalizeAndValidate()`、`BatchSendControlService.validateSnapshotFields()`。
- Violation consequence: 不校验则中文串或拼写错误的大区会进 `countriesForRegion()`，该方法对未知 region 返回 `emptySet()`（`CountryContinentMapping.kt:265`），生成 `terms: {country: []}` → ES 命中 0 条且**无任何报错**，运营只会看到「配了地区就发不出去」。
- 来源: 主计划 G-1（K-region-constant-not-display-label）

### Invariant I-2: 多个地区取并集，与其他维度取交集
- Rule: `regions = [A, B]` 生成的 ES 片段是**一个** `bool.should` 子句（内含 A 与 B 各自的 region 条件）配 `minimum_should_match: 1`，作为**单个元素**加入外层 `bool.filter` 数组。不得把每个地区各自 `filters.add()`（那会变成 AND，永远命中 0 条，因为一个专家不可能同时属于两个大区）。
- Applies to: 新增的 `ExpertSearchService.Companion.regionsFilter(regions)`。
- Violation consequence: 选 2 个及以上地区时静默命中 0 条——这是本计划最可能的实现错误，且单选测试无法暴露。
- 来源: original

### Invariant I-3: ES 路径与重试路径口径一致
- Rule: 地区过滤必须同时接入两条目标来源：① **ES 查询**——在 `ManualInitialOutreachService.buildEsFiltersForLevel()` 的 `if/else` 块**之后**加一处（与 `tags` 同位置），使分支 A（`INTRODUCTION+CANDIDATE`，走 `notContactedWithEmailFilters`）与分支 B（其余）同时生效；② **MySQL 重试联系人**——`RecipientScope.matchesExpert()`。二者对同一位专家的判定结果必须一致。
- Applies to: `ManualInitialOutreachService.buildEsFiltersForLevel()`（`:1213-1226`，**唯一 ES 落点**）、`BatchExecutionModels.RecipientScope.matchesExpert()`（`:53-64`）。
- **不适用**：`buildMaterialReminderEsFilters()`（`:1079-1091`）—— grep 实证为死代码（零调用点，见「现状审计」），本计划不动。`ExpertSearchService.notContactedWithEmailFilters()` —— 因地区落点在 `if/else` 之后，该方法签名无需变更。
- Violation consequence: 只接 ES 不接重试路径 → 已建 contact 的 `NEW` 专家绕过地区限制被错发。这正是 K-batch-send-filter-retry-parity 记录的 P1 复发模式。若把地区加进 `if` 或 `else` 分支内部而非之后，则只有一半路径生效。
- 来源: K-batch-send-filter-retry-parity

### Invariant I-4: 重试路径的地区判定必须复用 toRegion 归一
- Rule: `RecipientScope.matchesExpert()` 判定专家地区时，必须用 `CountryContinentMapping.toRegion(profile.country)` 归一后再比对；`country` 为空时 `toRegion` 返回 `REGION_OTHER`（`CountryContinentMapping.kt:259`），故「未填国家的专家」归入 `Other` 大区。**禁止**直接比较 `profile.country` 与 region 字符串。
- Applies to: `RecipientScope.matchesExpert()`。
- Violation consequence: 直接比较国家名与大区名恒为 false，重试路径全量被过滤。
- 来源: original（由 `CountryContinentMapping.kt:254-261` 审计得出）

### Invariant I-5: regionFilter 从私有实例方法提升为公共静态，实现零改动
- Rule: `ExpertSearchService.regionFilter()`（当前 `private fun`，`:811-844`）移入 `companion object` 并改为 `fun regionFilter(region: String)`（与已在 companion 中的 `disciplineFilter` / `notContactedWithEmailFilters` 同列）。方法体**逐字不变**——它只依赖 `CountryContinentMapping`，无实例状态，移动是纯重构。
- Applies to: `ExpertSearchService.kt`。
- Violation consequence: 若顺手「优化」了 `REGION_OTHER` 的双分支逻辑，专家列表的单选地区筛选会跟着变，属越界改动。
- 来源: original

## 现状审计

### 存储：MySQL `batch_send_task_config`

Schema 见 `V72__create_batch_send_task_config.sql:1-31`。现有筛选列：`funnel_level VARCHAR(32) NULL`、`tags_json TEXT NOT NULL`、`email_domain VARCHAR(120) NULL`、`discipline VARCHAR(120) NULL`。

> `tags_json TEXT NOT NULL` 是本计划新列的**形态先例**：多值筛选在本表用 JSON 数组列 + 服务层 `objectMapper` 序列化（`BatchSendTaskConfigService.normalizeTags()` / `parseTags()`，`:315-328`），不建关联表。`regions_json` 应逐字沿用该模式。

写读路径与 01 计划的审计一致（同一张表、同一组方法），不再重复；新增列的接入点与 `tagsJson` **完全平行**。

### ES 查询构造（本计划核心）

**`ExpertSearchService` companion object 现有三个公共过滤器工厂**（`:32-88`）：
- `fieldPresenceFilter(field)` — `:41`
- `disciplineFilter(discipline)` — `:55`，`private`
- `notContactedWithEmailFilters(emailDomain, discipline)` — `:67`，`public`

**`regionFilter(region)`** 目前是**实例私有方法**（`:811-844`），仅被 `buildExpertFilters()`（`:790`）调用。方法体：
- `region == REGION_OTHER` → `bool.should[ {country exists 且 not in 已知值}, {nationality exists 且 not in 已知值} ] + minimum_should_match: 1`
- 其余 → `bool.should[ {terms country in countriesForRegion}, {terms nationality in countriesForRegion} ] + minimum_should_match: 1`

**`ManualInitialOutreachService.buildEsFiltersForLevel()`（`:1213-1226`）—— 两个分支**：
```kotlin
val filters = if (scope.mailType == INTRODUCTION && level == "CANDIDATE") {
    ExpertSearchService.notContactedWithEmailFilters(scope.emailDomain, scope.discipline).toMutableList()   // 分支 A
} else {
    val base = mutableListOf(mapOf("exists" to mapOf("field" to "email")))                                   // 分支 B
    scope.emailDomain?.let { base.add(wildcard...) }
    scope.discipline?.let { base.add(term disciplineCategory...) }
    base
}
if (scope.tags.isNotEmpty()) { filters.add(terms tags...) }
return filters
```
→ 地区过滤必须加在 `if/else` **之后**（与 `tags` 同位置），这样两个分支自动都覆盖。**这是本计划最省事也最不易漏的落点。**

**`ManualInitialOutreachService.buildMaterialReminderEsFilters(config)`（`:1079-1091`）—— 死代码，本计划不动。**

grep 实证（排除定义行后无任何输出）：
```
$ grep -rn "buildMaterialReminderEsFilters" --include=*.kt src/ | grep -v "private fun buildMaterialReminderEsFilters"
（无输出）
```
→ 该方法**零调用点**。发送与统计两条路径实际都走 `buildMaterialReminderSnapshotFromScope()`（`:1120`），其 `:1128` 为 `val filters = buildEsFiltersForLevel(scope, level)`。
→ **结论：`buildEsFiltersForLevel()` 是材料提醒的唯一实际 ES 过滤构造点**，本计划只需接入它一处即可同时覆盖发送与待发统计。死代码的清理属独立任务，不在本计划范围。

**两条调用链的实际形态（`buildMaterialReminderSnapshot` 有两个重载）**：

| 入口 | 重载 | RecipientScope 来源 | 是否携带地区 |
|---|---|---|---|
| 发送路径 `runMaterialFromSnapshot()`（`:181`） | `buildMaterialReminderSnapshot(scope, config)`（`:1098`） | `RecipientScope.fromSnapshot(snapshot)`（`:179`） | ✅ 有（snapshot 携带） |
| 统计路径 `countPending(MATERIAL_REMINDER)`（`:415`） | `buildMaterialReminderSnapshot(config)`（`:1105`） | 方法内手工构造（`:1106-1112`），输入是 `BatchSendConfig`（KV 层，无地区维度） | ❌ 无 |

→ 统计路径的地区缺失是**已知且有界的偏差**（`BatchSendConfig` 本就不承载地区），处理见 A-7 与 A-7 人工验收项。

### 重试路径（MySQL）

`RecipientScope.matchesExpert(profile)`（`BatchExecutionModels.kt:53-64`）现有三项判定：`discipline` 直等、`emailDomain` 后缀、`tags` 交集。被 `ManualInitialOutreachService.buildRetryableTargets()`（`:958`）调用。

`RecipientScope.fromSnapshot()`（`:67-81`）从 snapshot 构造，需同步加 `regions`。

`RecipientScope` 的另外两个构造点：`buildRetryableTargets(campaignId, discipline, emailDomain)` 的 legacy 重载（`:970-983`）与 `buildMaterialReminderSnapshot(config)`（`:1106-1112`）—— 二者手工 `RecipientScope(...)`，**均传 `emptyList()`**：前者是 legacy typed 兼容入口，后者的输入 `BatchSendConfig`（KV 层）本就不承载地区维度。这不是遗漏，是有界偏差，见交互点 X-2 与人工验收 A-7。

### 交互点

| # | 写路径 | 读路径 | 处理 |
|---|---|---|---|
| X-1 | `BatchSendTaskConfigService.create/update` 写 `regions_json` | `toExecutionSnapshot()` → `RecipientScope.fromSnapshot()` → ES + 重试两条路径 | I-3：两条路径都必须接 |
| X-2 | `regions_json` | 材料提醒的**发送**路径（`fromSnapshot` → 携带地区）与**待发统计**路径（`buildMaterialReminderSnapshot(config)` → 手工构造 scope，无地区） | 二者共用 `buildEsFiltersForLevel()`，但 scope 来源不同，故统计数会**大于**实际发送范围。这是 `BatchSendConfig` 无地区维度导致的有界偏差，本计划**接受并记录**，不扩范围去改 KV 层 |
| X-3 | 前端 04b 之前不发 `regions` | `BatchSendTaskConfigCreateCommand.regions` | 默认 `emptyList()` = 不限制，向后兼容；且 `regions_json` 列须 `NOT NULL DEFAULT '[]'` |
| X-4 | `ExpertIndexController` 的 `/api/experts?region=X`（单选） | `buildExpertFilters(region: String?)` → `regionFilter()` | I-5：`regionFilter` 移入 companion 后此路径行为必须逐字不变 |

## 实现方案

### A-1 迁移 `V93__add_regions_to_batch_send_task_config.sql`

```sql
ALTER TABLE batch_send_task_config
    ADD COLUMN regions_json TEXT NOT NULL AFTER funnel_level;
UPDATE batch_send_task_config SET regions_json = '[]' WHERE regions_json = '';
```

> MySQL 的 `TEXT` 不支持 `DEFAULT`，故沿用 `tags_json TEXT NOT NULL` 的既有形态（`V72:13`），并用紧随的 UPDATE 兜底为 `'[]'`。文件不得含 `${...}`（K-flyway-placeholder-replacement）。

### A-2 `ExpertSearchService.kt`：提升 regionFilter + 新增 regionsFilter（I-1、I-2、I-5）

- 将 `regionFilter(region: String)`（`:811-844`）整体移入 `companion object`，置于 `disciplineFilter` 之后，可见性改为 `fun`（public）。**方法体一字不改**。
- `buildExpertFilters()` 的调用点（`:790`）不变（companion 成员在实例方法中可直接调用）。
- companion object 新增：
  ```kotlin
  /** 多选地区：并集（should + minimum_should_match 1）。空列表返回 null 表示不限制。 */
  fun regionsFilter(regions: List<String>): Map<String, Any>? {
      if (regions.isEmpty()) return null
      regions.forEach { require(it in CountryContinentMapping.allRegions()) { "Invalid region: $it" } }
      if (regions.size == 1) return regionFilter(regions.first())
      return mapOf(
          "bool" to mapOf(
              "should" to regions.map { regionFilter(it) },
              "minimum_should_match" to 1
          )
      )
  }
  ```
- `notContactedWithEmailFilters()` 新增第三个形参 `regions: List<String> = emptyList()`，但**本计划不使用该形参**——地区过滤统一加在 `buildEsFiltersForLevel()` 的 if/else 之后（见 A-5）。**因此本计划不改该方法签名**，此条作废，保持 `notContactedWithEmailFilters` 原样。

### A-3 `BatchSendTaskConfig.kt`（与 tagsJson 平行）

- `BatchSendTaskConfig`：`val regionsJson: String = "[]"`（默认值必需，理由同 01 的 11 个构造点）
- `BatchSendTaskConfigView`：`val regions: List<String> = emptyList()`
- `BatchSendTaskConfigCreateCommand` / `UpdateCommand`：`val regions: List<String> = emptyList()`

### A-4 `BatchSendTaskConfigService.kt`（I-1）

- `ConfigFields` / `NormalizedConfig` 加 `val regions: List<String>` / `val regionsJson: String`
- 新增 `private fun normalizeRegions(regions: List<String>): List<String>`：trim → 过滤空串 → 去重 → 校验 `in CountryContinentMapping.allRegions()`（失败抛 `IllegalArgumentException("region must be one of ...")`）→ 按 `allRegions()` 的顺序排序
- `normalizeAndValidate()` 调用它并写入 `regionsJson = objectMapper.writeValueAsString(normalized)`
- `create()` / `update()` 实体构造加 `regionsJson = normalized.regionsJson`
- `toView()`（`:330-351`）加 `regions = parseRegions(row.regionsJson)`；新增 `parseRegions` 复用 `parseTags` 的 try/catch 形态（`:322-328`）
- `updateLegacyConfig()`（`:161-175`）加 `regions = parseRegions(existing.regionsJson)`（K-batch-config-legacy-adapter-field-preservation）
- 三个 `*Fields()`（`:423`/`:439`/`:455`）加 `regions = ...`（entity 侧用 `parseRegions(regionsJson)`）

### A-5 `BatchExecutionModels.kt`（I-3、I-4）

- `BatchExecutionSnapshot` 加 `val regions: List<String> = emptyList()`
- `toExecutionSnapshot()` 加 `regions = parseRegionsJson(regionsJson)`（本文件内用 `objectMapper` 解析，与既有 `tags` 的解析逻辑 `:187-193` 同形态）
- `RecipientScope` 加 `val regions: List<String>`
- `RecipientScope.fromSnapshot()` 加 `regions = snapshot.regions`
- `RecipientScope.matchesExpert()` 在 `discipline` 判定之后加：
  ```kotlin
  if (regions.isNotEmpty()) {
      val expertRegion = com.weibo.talentintroduction.expert.domain
          .CountryContinentMapping.toRegion(profile.country)
      if (expertRegion !in regions) return false
  }
  ```
  （I-4：必须走 `toRegion` 归一）

### A-6 `BatchSendControlService.kt`（I-1）

- `validateSnapshotFields()`（`:448-465`）加：
  ```kotlin
  snapshot.regions.forEach { region ->
      require(region in CountryContinentMapping.allRegions()) { "Invalid region: $region" }
  }
  ```
- `toLegacySnapshot()`（`:593-607`）加 `regions = emptyList()`（legacy KV 无地区维度）

### A-7 `ManualInitialOutreachService.kt`（I-2、I-3、X-2）

- `buildEsFiltersForLevel()`（`:1213-1226`）：在 `if (scope.tags.isNotEmpty())` 之后、`return filters` 之前加
  ```kotlin
  ExpertSearchService.regionsFilter(scope.regions)?.let { filters.add(it) }
  ```
  （单次 `add`，I-2）
- `buildMaterialReminderEsFilters(config)`（`:1079-1091`）：**不动**。grep 实证其为死代码（零调用点），改它无任何运行时效果，且会虚增 diff。
- `RecipientScope` 的两个手工构造点加 `regions = emptyList()`：
  - `:975-981`（`buildRetryableTargets` 的 legacy 重载）
  - `:1106-1112`（`buildMaterialReminderSnapshot(config)`，即 `countPending` 统计路径）——同时在该处加注释：`// 统计路径输入为 BatchSendConfig（KV 层，无地区维度），故不携带地区；发送路径经 fromSnapshot 携带`

### A-8 测试

**`BatchSendTaskConfigServiceTest.kt`** — +5 用例：
- 合法多选 `["China", "Europe"]` 创建 → `getById()` 返回同样两项（顺序按 `allRegions()`）
- 非法值 `["中国"]` → `IllegalArgumentException`，消息含 `region must be one of`（I-1，**这条直接锁住主计划 G-1**）
- 重复值 `["China", "China", " Europe "]` → 规范化为 `["China", "Europe"]`
- 空列表 → `regionsJson == "[]"`，View 返回 `emptyList()`
- `updateLegacyConfig()` 保留实体现值（K-batch-config-legacy-adapter-field-preservation）

**`ExpertSearchServiceTest.kt`** — +4 用例：
- `regionsFilter(emptyList())` 返回 `null`
- `regionsFilter(listOf("China"))` 与 `regionFilter("China")` 结果**逐字相等**（I-2 的单选退化）
- `regionsFilter(listOf("China", "Europe"))` 返回**一个** map，其 `bool.should` 长度为 2 且 `minimum_should_match == 1`（I-2）
- `regionsFilter(listOf("Mars"))` 抛 `IllegalArgumentException`
- 回归：`searchExperts(region = "Other")` 生成的 filter 与本计划前逐字相同（I-5、X-4）

**`ManualInitialOutreachServiceTest.kt`** — +4 用例：
- `regions = ["Europe"]` 时，`buildEsFiltersForLevel` 的 INTRODUCTION+CANDIDATE 分支（分支 A）产出的 filters 中含 1 个地区子句（I-3 分支 A）
- 同上，MATERIAL_REMINDER 分支（分支 B）同样含 1 个地区子句（I-3 分支 B）
- 重试路径：一个 `country = "Germany"` 的 `NEW` 联系人在 `regions = ["Europe"]` 下被保留、在 `regions = ["China"]` 下被过滤（I-3、I-4）
- 重试路径：一个 `country = null` 的 `NEW` 联系人在 `regions = ["Other"]` 下被保留（I-4 的空值归一）

## 变更文件清单

| # | 文件 | 类型 | 改动摘要 |
|---|---|---|---|
| 1 | `src/main/resources/db/migration/V93__add_regions_to_batch_send_task_config.sql` | 新增 | `ADD COLUMN regions_json TEXT NOT NULL` + 兜底 UPDATE |
| 2 | `src/main/kotlin/.../campaign/domain/BatchSendTaskConfig.kt` | 修改 | 4 个 data class 加地区字段 |
| 3 | `src/main/kotlin/.../campaign/domain/BatchExecutionModels.kt` | 修改 | Snapshot + RecipientScope 加字段；`matchesExpert` 加地区判定；`toExecutionSnapshot` 解析 |
| 4 | `src/main/kotlin/.../campaign/service/BatchSendTaskConfigService.kt` | 修改 | `normalizeRegions` + `parseRegions` + 8 处接入 |
| 5 | `src/main/kotlin/.../campaign/service/BatchSendControlService.kt` | 修改 | `validateSnapshotFields` 加校验、`toLegacySnapshot` 加空列表 |
| 6 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | 修改 | `buildEsFiltersForLevel` / `buildMaterialReminderEsFilters` 接入、2 处 `RecipientScope` 构造补参 |
| 7 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | 修改 | `regionFilter` 移入 companion 并公开、新增 `regionsFilter` |
| 8 | `src/test/kotlin/.../campaign/service/BatchSendTaskConfigServiceTest.kt` | 修改 | +5 用例 |
| 9 | `src/test/kotlin/.../expert/service/ExpertSearchServiceTest.kt` | 修改 | +5 用例 |
| 10 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 | +4 用例 |

**文件数 10 ≤ 10 ✅　独立子系统 2（批量发送配置 / 专家 ES 检索）≤ 2 ✅　新增字段 1（`regions_json`）✅**

> **不得**修改：`app.js`、`index.html`、`ExpertIndexController.kt`、`CountryContinentMapping.kt`、`BatchSendSettingService.kt`。

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类（Surefire 逗号分隔）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=BatchSendTaskConfigServiceTest,ExpertSearchServiceTest,ManualInitialOutreachServiceTest

# 单个测试方法
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest#methodName

# Flyway 迁移集成测试（验证 V93 可应用；需本地 Docker）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

**通过判据**：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。`git diff --check` 无输出。
**来源**：`CLAUDE.md` 的「Commands」章节 + 项目元信息。

> 本计划不改 `.js` / `.html`，无需 `node --test` 门禁。

## 验收标准

- **I-1**：`BatchSendTaskConfigServiceTest` 的非法值用例通过；grep `CountryContinentMapping.allRegions()` 在 `BatchSendTaskConfigService.kt` 与 `BatchSendControlService.kt` 各命中 ≥1 处。
- **I-2**：`ExpertSearchServiceTest` 的三个 `regionsFilter` 用例通过；grep `ManualInitialOutreachService.kt` 中 `regionsFilter` 的调用形态为 `?.let { filters.add(it) }`（单次 add），无 `regions.forEach { filters.add(` 类写法。
- **I-3**：`ManualInitialOutreachServiceTest` 的 4 个新增用例通过；grep 确认 `buildEsFiltersForLevel` 中地区行位于 `if/else` 块**之后**（对两个分支同时生效）；`git diff` 确认 `buildMaterialReminderEsFilters()` 零改动（死代码不动）。
- **I-4**：grep `matchesExpert` 方法体含 `CountryContinentMapping.toRegion(`；`country = null` 归 `Other` 的用例通过。
- **I-5**：`git diff` 确认 `regionFilter` 方法体零改动（仅位置与可见性修饰符变化）；`ExpertSearchServiceTest` 的 `region = "Other"` 回归用例通过。
- **回归**：执行「验证命令」节的全量测试命令通过；构建命令通过；Flyway 迁移集成测试命令通过。

## 人工验收清单

### A-1：单选地区生效，两条目标来源口径一致
- 前置条件：ES CANDIDATE 层有 `country = "Germany"` 的未联系专家 ≥ 3、`country = "Japan"` 的 ≥ 3，均有邮箱；MySQL 有一个 `country = "Germany"` 的 `NEW` 状态联系人（无 SENT 记录）和一个 `country = "Japan"` 的同类联系人；用 SQL 建配置 `regions_json = '["Europe"]'`、`round_size = 20`、`rounds_per_run = 1`；账号容量充足。
- 操作步骤：
  1. 手动执行该配置，等待结束
  2. 查 `SELECT expert_email, country FROM expert_contact WHERE id IN (SELECT expert_contact_id FROM mail_record WHERE mail_type='INTRODUCTION' AND send_status='SENT' AND created_at > NOW() - INTERVAL 10 MINUTE);`
- 预期结果：结果集中**只有** `country = 'Germany'` 的记录，**不含任何** `country = 'Japan'` 的记录；且 Germany 的 `NEW` 重试联系人也在其中（证明重试路径同样受限）。
- 覆盖：Observable outcome 1、2；I-3、I-4

### A-2：多选地区取并集
- 前置条件：同 A-1，但配置改为 `regions_json = '["Europe","Asia (Japan & Korea)"]'`；重置相关联系人为可再发状态（清空 `mail_record` 中的 SENT 记录或换一批专家）。
- 操作步骤：同 A-1。
- 预期结果：`Germany` 与 `Japan` 的专家**都被发送**（并集），发送总数 = 两组之和；**不是** 0 条（若为 0 条即 I-2 被违反，实现写成了 AND）。
- 覆盖：Observable outcome 1；I-2

### A-3：非法地区被拒绝，不静默失败
- 前置条件：无。
- 操作步骤：
  1. 调用 `POST /api/mail/batch-send/configs`，请求体中 `"regions": ["中国"]`，其余字段填合法值
  2. 换成 `"regions": ["Chna"]`（拼写错误）重试
  3. 换成 `"regions": ["China"]` 重试
- 预期结果：前两次均返回 **422**，响应 message 含 `region must be one of` 并列出 9 个合法值；第三次返回 **201** 且响应体 `regions` 为 `["China"]`。
- 覆盖：Observable outcome 3；I-1；主计划 G-1

### A-4：Other 大区覆盖未填国家的专家
- 前置条件：ES CANDIDATE 层有 ≥ 2 位 `country` 字段缺失或为空的未联系专家（有邮箱）；MySQL 有一个 `country IS NULL` 的 `NEW` 联系人；配置 `regions_json = '["Other"]'`。
- 操作步骤：手动执行该配置，查看发送出的专家。
- 预期结果：`country` 缺失的专家**被包含在发送范围内**（`toRegion(null)` 归 `Other`）；`country = 'Germany'` 的专家不在其中。
- 覆盖：I-4

### A-5【回归】不选地区 = 不限制
- 前置条件：配置 `regions_json = '[]'`；ES 有分布在多个大区的目标 ≥ 20。
- 操作步骤：手动执行，统计发送出的专家国家分布。
- 预期结果：发送范围横跨多个大区，与本计划上线前的行为一致；未因新列引入而减少。
- 覆盖：交互点 X-3

### A-6【回归】专家列表的单选地区筛选不变
- 前置条件：专家列表页可访问。
- 操作步骤：
  1. 打开「专家」视图，地区下拉依次选择 `China`、`Europe`、`Other`
  2. 记录每次的命中总数
  3. 与本计划上线前的同样操作结果对比
- 预期结果：三次的命中数与上线前**逐条相同**；下拉选项仍为 9 项且每项带 `(N)` 计数。
- 覆盖：must-NOT-change 第 1、3 条；I-5；交互点 X-4

### A-7【已知偏差确认】待发统计不含地区筛选
- 前置条件：`材料提醒任务` 配置 `regions_json = '["Europe"]'`；APPLICATION 层带 `承诺回复材料` 标签、有邮箱、已有 contact 且未发过材料提醒的联系人分布在多个大区（欧洲 ≥ 3、非欧洲 ≥ 5）。
- 操作步骤：
  1. 调用 `GET /api/mail/batch-send/types/MATERIAL_REMINDER/pending-count`，记录返回数 P
  2. 手动执行该任务（轮次与每轮数量设足够大，账号容量充足），记录实际发送数 S
  3. 核对 S 中每位收件人的 `country` 所属大区
- 预期结果：**S 全部为欧洲专家**（发送路径地区筛选生效，这是本条的核心）；**P 明显大于 S 且包含非欧洲专家的计数**。这是本计划**有意接受的已知偏差**——统计路径的 `RecipientScope` 由 `BatchSendConfig`（KV 层，无地区维度）手工构造。验收人确认「发送范围正确、统计偏大」即通过本条；若发现 S 中含非欧洲专家，则为 I-3 缺陷，验收不通过。
- 覆盖：交互点 X-2（有界偏差的显式确认）

## 修正记录

（暂无）

---

## 全局约束（主计划 00 共享，本批所有子计划必须复述并各自验证）

### G-1 地区常量是领域值，不可中文化
`CountryContinentMapping` 的 9 个大区英文串（`China` / `Asia (Japan & Korea)` / `Asia (Other)` / `Europe` / `North America` / `South America` / `Africa` / `Oceania` / `Other`）是领域常量，参与 ES term 查询构造（`countriesForRegion` → `esTermVariants`）。需求 4 的「改为中文」只能作用于显示标签；API 传值、DB 存值、ES 查询值必须保持英文原串。

### G-2 服务端始终存在至少一道单次调度发送量硬闸门
从 01 提交开始到 02 提交完成，`ManualInitialOutreachService` 的轮次循环必须始终受一个服务端配置字段约束（先是 `dailyCap`，01 后新增 `roundsPerRun`，02 后仅剩 `roundsPerRun` + 账号容量）。

### G-3 UNCLASSIFIED 学科的过滤实现必须同源
`ExpertSearchService.disciplineFilter()` 已正确实现 `UNCLASSIFIED` = `must_not exists disciplineCategory`，且 `ALLOWED_DISCIPLINES` 已含该值。已知缺陷点：#1 `ManualInitialOutreachService.buildEsFiltersForLevel()` else 分支（:1219）直接写 `term disciplineCategory = it`（活跃旁路）；#2 `RecipientScope.matchesExpert()`（BatchExecutionModels.kt:54）直接写 `profile.disciplineCategory != discipline`（活跃缺陷）；#3 `BatchSendTaskConfigService.ALLOWED_DISCIPLINES`（:473）= `setOf("STEM","HUMANITIES")`（白名单缺项）；#4 `BatchSendSettingService.ALLOWED_DISCIPLINES`（:236）有意不改；#5 `buildMaterialReminderEsFilters()`（:1088）是死代码；#6 前端 `index.html:1199-1201`、`:1336-1338` 缺 option。

### G-4 运行中只消费启动快照
任何新增配置字段（`roundsPerRun`、`regions`）都必须经 `BatchExecutionSnapshot` 传入执行循环，禁止在循环内重新读 `batch_send_task_config`。

### G-5 调度重排的触发条件是 cron ∪ autoEnabled
`BatchSendScheduler.reload()` 目前仅在 `scheduledCrons[configId] != cron` 时重排；04 引入自定义 cron 后必须确认「沿用原 cron、仅把 autoEnabled 由 false 改 true」的场景仍会重排。

### 全批约束
- 迁移文件禁止包含 `${...}`（生产 application.yml 未关 Flyway placeholder-replacement）。
- 新建迁移前必须先跑 `ls src/main/resources/db/migration/ | sort -V | tail -3` 与 `grep -rn "V9[0-9]__" docs/plans/` 确认版本号未被占用；本批计划编号 V91/V92/V93，若实际落地顺序不同则按实际重编号并同步本计划与主计划引用。已应用的迁移一律不得编辑。
- `BatchSendTaskConfig` 等 data class 的新增字段必须带默认值（全仓 11 个构造点，10 个在测试里）。
- 不在本批范围：账号侧 `dailySendLimit` / warmup ramp 语义与配置入口、`AccountRateLimiter` 动态间隔算法、`oneRoundOnly` 手动单轮语义、`batch_send_setting` KV 兼容表迁移、跨执行自然日发送量统计替代品（`TaskExecutionService.sumSuccessCountTodayByBatchConfigId()` 保留方法与其测试）。

## 执行契约（fast-p 实施者）
- 使用 execute-p 技能；本 brief 是完整批准的契约。
- 只修改「变更文件清单」列出的授权文件；不引入新文件（除计划明示的迁移/测试文件）。
- 保留全部关键不变量与下游接口；data class 新增字段带默认值。
- 运行「验证命令」中全部命令；记录命令与退出码。
- 禁止修改 docs/plans/fast/ 下的任何 fast-p 工件；实现提交不得包含它们。
- 实现提交信息：`feat(fast-p): implement 03`；只提交授权文件。

