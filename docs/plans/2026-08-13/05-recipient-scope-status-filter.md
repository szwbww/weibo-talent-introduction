# P-E：RecipientScope 接入专家状态过滤

优先级 **P1（功能）** ｜ 前置：**P-A + P-B** ｜ 子系统：2 ｜ 文件数：10（已达上限）

> ⚠️ 本计划文件数与子系统数均触及 create-p 上限。执行前若发现任一项超出，
> **必须二次拆分**为「后端过滤语义」与「前端筛选控件」两个计划。

## 需求描述

**Observable outcome**

1. 批量发送的「定时任务配置」与「手动执行」两个面板都出现"专家状态"筛选项。
2. 选定状态后，ES 目标查询与数据库重试目标**两条路径口径一致**地按该状态过滤。
3. 漏斗层级选 APPLICATION（或不限）时，状态过滤同样生效。

**What must NOT change**

- 不选状态（留空）时的行为与现状完全一致。
- `INTRODUCTION + CANDIDATE` 组合下 `notContactedWithEmailFilters` 的既有语义。
- 既有 5 个筛选维度（漏斗层级 / 标签 / 地区 / 邮箱服务商 / 学科）的行为。

**Out of scope**：收件人数量预估（P-F）。

## 前置依赖说明（不可跳过）

**依赖 P-A**：本计划消费 `operator_status` 这列数据。P-A 之前该列不可信
（手动发送路径不写入），在其上做过滤等于把错误数据当成筛选依据。

**依赖 P-B**：APPLICATION 索引**当前没有 `operatorStatus` 的 mapping**
（`orcid_info_application.json` 未声明；线上 `dynamic:false`），
该层的 `term` / `exists` 过滤会静默匹配 **0 条**。P-B 不落地则本计划的 outcome 3 不可能实现。

## 关键不变量

### I-1：状态过滤必须覆盖全部查询旁路
- **Rule**：新增的状态过滤必须同时接入 ES 查询、重试联系人内存过滤、材料提醒查询三条路径。
- **必须打通的 3 条旁路**（结构与 `K-discipline-unclassified-filter-bypasses` 记录的完全同构）：
  1. `ManualInitialOutreachService.buildEsFiltersForLevel()`（`:1211-1224`）
  2. `RecipientScope.matchesExpert()`（`BatchExecutionModels.kt:55-77`）—— **重试联系人路径**
  3. `ManualInitialOutreachService.buildMaterialReminderEsFilters()`（`:1075`）
- **Violation consequence**：只接 ES 不接重试路径，重试联系人会静默绕过配置造成错发——
  这正是 `K-batch-send-filter-retry-parity`（P1，hit_count=8）记录过的原生事故形态。
- **来源**：K-batch-send-filter-retry-parity + K-discipline-unclassified-filter-bypasses

### I-2：APPLICATION 分支必须有状态过滤
- **Rule**：`buildEsFiltersForLevel` 的 else 分支（非 INTRODUCTION+CANDIDATE）必须同样应用状态过滤。
- **证据**（当前 else 分支只有 3 个条件，零状态过滤）：

```kotlin
// ManualInitialOutreachService.kt:1211-1224 逐字
private fun buildEsFiltersForLevel(scope: RecipientScope, level: String): List<Map<String, Any>> {
    val filters = if (scope.mailType == BatchSendType.INTRODUCTION.name && level == "CANDIDATE") {
        ExpertSearchService.notContactedWithEmailFilters(scope.emailDomain, scope.discipline).toMutableList()
    } else {
        val base = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
        scope.emailDomain?.let { base.add(mapOf("wildcard" to mapOf("email" to mapOf("value" to "*@$it")))) }
        scope.discipline?.let { base.add(ExpertSearchService.disciplineFilter(it)) }
        base
    }
    …
}
```

- **Violation consequence**：漏斗层级选 APPLICATION 时，已联系、已回复、
  邮箱无效的专家全部进入目标池。
- **来源**：original

### I-3：NOT_CONTACTED 语义唯一
- **Rule**：状态过滤中 `NOT_CONTACTED` 必须复用 `ExpertSearchService` 已有的
  "字段不存在"表达，不得写成 `term operatorStatus=NOT_CONTACTED`。
- **证据**：`ExpertSearchService.buildExpertFilters:823-830` 已有正确实现——
  `"NOT_CONTACTED" -> filters.addAll(notContactedWithEmailFilters(null))`，其余走 `term`。
  权威实现在 `notContactedWithEmailFilters:116-127` 的 `must_not exists`。
- **来源**：K-agg-filter-source-of-truth（同构：过滤语义单一来源）

### I-4：新增列必须在旧适配器显式保留
- **Rule**：`batch_send_task_config` 新增 `operator_status` 列后，必须在
  `BatchSendTaskConfigService.updateLegacyConfig()` 显式写 `operatorStatus = existing.operatorStatus`。
- **证据**：该适配器（`:165-200`）接收只含旧字段的 `BatchSendConfigUpdateRequest`
  却调用**全量** `BatchSendTaskConfigUpdateCommand`，现有保留集合为
  `configName` / `roundsPerRun` / `funnelLevel` / `tags` / `regions`。
- **Violation consequence**：漏写会命中 Kotlin 默认值，运营从旧界面改任意字段
  就把新配置项**静默重置**，无任何报错。
- **来源**：K-batch-config-legacy-adapter-field-preservation（P1）

### I-5：三类映射区分清楚
- **Rule**：新列须加入 `toView()`（`:368`，前端要读）与三个 `*Fields()`
  （`:497` / `:514` / `:531`，走校验）；**不要**加入 `toLegacyConfig()`（`:208`）
  与 `updateLegacyConfig` 的返回值构造——否则把 KV 兼容层拖进变更范围。
- **来源**：K-batch-config-legacy-adapter-field-preservation

## 现状审计

### RecipientScope 当前字段（`BatchExecutionModels.kt:45-52`）

```kotlin
data class RecipientScope(
    val mailType: String,
    val funnelLevels: Set<String>,
    val tags: List<String>,
    val regions: List<String>,
    val emailDomain: String?,
    val discipline: String?
)   // ← 无 operatorStatus
```

`matchesExpert()`（`:55-77`）逐项检查 discipline / emailDomain / tags / regions，**不检查状态**。

### 配置实体链路（新增列须同步的全部位置）

```
grep -n "ALLOWED_DISCIPLINES|toView|Fields\(|normalizeAndValidate" BatchSendTaskConfigService.kt
  :55   normalizeAndValidate(cmd.toFields(), excludeId = null)      ← create
  :87   normalizeAndValidate(cmd.toFields(), excludeId = id)        ← update
  :125  normalizeAndValidate(existing.toFields().copy(autoEnabled = true), excludeId = id)
  :254  require(discipline in ALLOWED_DISCIPLINES)                  ← 校验模式参考
  :368  toView()                                                    ← 要加
  :462  data class ConfigFields                                     ← 要加
  :497  BatchSendTaskConfigCreateCommand.toFields()                 ← 要加
  :514  BatchSendTaskConfigUpdateCommand.toFields()                 ← 要加
  :531  BatchSendTaskConfig.toFields()                              ← 要加
  :561  ALLOWED_DISCIPLINES = ExpertSearchService.ALLOWED_DISCIPLINES  ← 白名单单一来源范式
```

> `:561` 值得照抄：学科白名单已收敛为"引用 `ExpertSearchService` 的单一权威"，
> 状态白名单应同样引用 `OperatorStatus.entries` 而非另抄一份字符串集合。

### 前端注册点（`K-expert-filter-registration-sites`，hit_count=5）

该条目记录专家漏斗视图新增筛选控件须注册**五处**。**但本计划的控件在批量发送面板，
不是漏斗视图**，注册点不同——须以本计划实测为准：

配置编辑器（`batchConfigEditor*`）：`buildBatchConfigEditorPayload`（`app.js:13662`）、
`fillBatchConfigEditor`（`:13233`）。
手动执行面板（`batchManual*`）：P-0 已梳理的 7 个函数。

> ⚠️ 该知识条目自身注明"行号漂移幅度已达千行量级——本条目的行号只能当作存在性提示，
> 改前必须 grep 复核全集"。执行时须重新 grep。

### Interaction points

| # | 写 | 读 | 验收 |
|---|---|---|---|
| IP-1 | 配置保存 operator_status | `toExecutionSnapshot` → `RecipientScope` | A-2 |
| IP-2 | RecipientScope | `buildEsFiltersForLevel` ES 路径 | A-2 |
| IP-3 | RecipientScope | `matchesExpert` 重试路径 | A-3 |
| IP-4 | 旧 typed API 保存 | `updateLegacyConfig` 字段保留 | A-5 |

## 实现方案

### T-1 数据库列【I-4】
新增：`resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql`
（P-A 已占用 V94）。可空，默认 NULL 表示"不限状态"。

### T-2 实体与命令【I-4, I-5】
文件：`campaign/domain/BatchSendTaskConfig.kt` ——
`BatchSendTaskConfig` / `BatchSendTaskConfigView` / `...CreateCommand` / `...UpdateCommand`
四个 data class 各加一个 `operatorStatus: String? = null`。

### T-3 服务层映射【I-4, I-5】
文件：`campaign/service/BatchSendTaskConfigService.kt` ——
按「现状审计」清单加入 `toView()`、`ConfigFields`、三个 `toFields()`；
在 `updateLegacyConfig`（`:165-200`）显式写 `operatorStatus = existing.operatorStatus`；
校验白名单引用 `OperatorStatus.entries`（照 `:561` 的范式）。

### T-4 Scope 与快照【I-1, I-3】
文件：`campaign/domain/BatchExecutionModels.kt` ——
`BatchExecutionSnapshot` 加 `operatorStatus: String? = null`；
`RecipientScope` 加同名字段并在 `fromSnapshot` 传递；
`matchesExpert()` 增加状态判断（**重试路径，I-1 的第 2 条旁路**）；
`toExecutionSnapshot()` 传递该字段。

### T-5 三条查询旁路【I-1, I-2, I-3】
文件：`campaign/service/ManualInitialOutreachService.kt` ——
`buildEsFiltersForLevel`（`:1211`）**两个分支都要加**（I-2）；
`buildMaterialReminderEsFilters`（`:1075`）加同款。

文件：`expert/service/ExpertSearchService.kt` ——
抽出 `operatorStatusFilter(status: String): List<Map<String, Any>>`，
内部复用 `buildExpertFilters:823-830` 已有的 NOT_CONTACTED 特判逻辑（I-3），
供上述三处调用，避免第四次重复实现。

### T-6 前端
文件：`src/main/resources/static/index.html`、`app.js` ——
两个面板各加一个"专家状态"下拉，option 取自既有
`operatorStatusOptions`（`app.js:618-624`）+ 一个"全部"空值。
样式复用 `.batch-config-field` + `.bsc-input`（与 P-0 的 S-2 同款，`styles.css` 零改动）。

### T-7 测试
`ManualInitialOutreachServiceTest` 补：ES 路径按状态过滤 / 重试路径按状态过滤 /
APPLICATION 层生效 / 留空时行为不变。

## 变更文件清单（10 个，= 上限）

| # | 文件 | 类型 |
|---|---|---|
| 1 | `resources/db/migration/V95__add_operator_status_to_batch_send_task_config.sql` | 新增 |
| 2 | `campaign/domain/BatchSendTaskConfig.kt` | 改 |
| 3 | `campaign/domain/BatchExecutionModels.kt` | 改 |
| 4 | `campaign/service/BatchSendTaskConfigService.kt` | 改 |
| 5 | `campaign/service/ManualInitialOutreachService.kt` | 改 |
| 6 | `expert/service/ExpertSearchService.kt` | 改 |
| 7 | `src/main/resources/static/index.html` | 改 |
| 8 | `src/main/resources/static/app.js` | 改 |
| 9 | `test/…/campaign/service/ManualInitialOutreachServiceTest.kt` | 改 |
| 10 | `docs/knowledge/campaign/K-recipient-scope-status-filter.md` | 新增 |

## 验证命令

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。
来源：CLAUDE.md「Commands」章节。

## 验收标准

- **I-1**：三条旁路各有单测；`grep -n "operatorStatus" ManualInitialOutreachService.kt`
  在 `buildEsFiltersForLevel` 与 `buildMaterialReminderEsFilters` 内均有命中；
  `matchesExpert` 内有命中。
- **I-2**：`buildEsFiltersForLevel` 的 else 分支单测断言含状态过滤条件。
- **I-3**：断言 `NOT_CONTACTED` 生成的是 `must_not exists`，不是 `term`。
- **I-4**：`updateLegacyConfig` 单测——调用旧 API 只改 cron，断言 `operatorStatus` 保持原值。
- **回归**：执行『验证命令』节全部通过。

## 人工验收清单

### A-1：两个面板都有状态筛选【outcome 1】
- 步骤：分别打开「定时任务配置」编辑器与「手动执行」面板。
- 预期：两处均出现"专家状态"下拉，选项为
  全部 / 未联系 / 已联系 / 已回复 / 已回复材料 / 已邀约 / 已完成。

### A-2：ES 路径按状态过滤【outcome 2 / IP-1, IP-2】
- 前置：库中同时存在「未联系」与「已联系」的专家。
- 步骤：配置状态=「未联系」，执行，看日志目标数与实际收件人。
- 预期：目标数等于该状态的专家数；抽查收件人状态均为「未联系」。

### A-3：重试路径同口径【outcome 2 / IP-3 / I-1】
- 前置：造一位 `current_status='NEW'`、无 SENT 介绍信、但 `operator_status='REPLIED'` 的联系人
  （即会进入重试目标集合的形态）。
- 步骤：配置状态=「未联系」，执行。
- 预期：该联系人**不在**目标中。
  **（这是 K-batch-send-filter-retry-parity 记录过的事故点，必验）**

### A-4：APPLICATION 层生效【outcome 3 / I-2】
- 前置：P-B 已落地并完成 `_update_by_query`；存在已晋升到 APPLICATION 的专家。
- 步骤：配置漏斗层级=APPLICATION、状态=「已联系」，执行。
- 预期：目标数 > 0 且全部为「已联系」。
  **若目标数为 0，先回到 P-B 的 A-3 确认 mapping 与 reindex 是否完成。**

### A-5：旧界面不清空新字段【must-NOT-change / IP-4 / I-4】
- 前置：某任务配置的状态筛选设为「未联系」。
- 步骤：用旧 typed API（`PUT /api/mail/batch-send/types/INTRODUCTION/config`）只改 cron。
- 预期：改完后查该配置，状态筛选**仍为「未联系」**。

### A-6：留空 = 不限【must-NOT-change】
- 步骤：状态留空，执行。
- 预期：目标数与升级前同条件执行**完全一致**。
