# P3a：专家状态筛选改多值（后端）

主计划：`batch-task-filters-main.md`（共享不变量 M-1…M-5、共享审计 X-1…X-3、验证命令在主计划）
前置计划：**P2a 必须已合并**（`V97` 已占用，本计划用 `V98`；`buildEsFiltersForLevel` 已改为多域形态）
子系统数：2（campaign / expert）  文件数：9

---

## 需求描述

### Observable outcome

配置与快照接受 `operatorStatuses: string[]`；多个状态之间取 **或**；ES 新目标与 MySQL 重试联系人两条来源同口径生效。`GET /api/mail/batch-send/configs` 返回 `operatorStatuses` 数组。

### What must NOT change

- **N3a-1** `ExpertSearchService.operatorStatusFilter(status: String)`（`:144-149`）、`notContactedWithEmailFilters`（`:116`）、`buildExpertFilters`（`:818`）、`searchExperts` 的既有单值签名与行为一行不改（专家列表页在用，主计划 N-1/N-2）。
- **N3a-2** 留空（不限）与「仅选 NOT_CONTACTED」两种情况下，ES 查询产出的 filter 与改动前**逐字相同** —— 这是绝大多数存量任务的形态，不允许有任何行为漂移。
- **N3a-3** `expert_contact.operator_status` 的写入路径不变（M-5 守卫覆盖区）。
- **N3a-4** 旧 typed API `/types/{sendType}/config` 形状不变（它本来就没有 operatorStatus 字段）。
- **N3a-5** 其余五个筛选维度行为不变。

### Out of scope

- 前端 —— 归 P3b。
- 专家列表页状态筛选改多选。
- `OperatorStatus` 枚举本身的增删。
- 删除死代码 `buildMaterialReminderEsFilters`（主计划 X-1）。

---

## 关键不变量

### Invariant I3a-1: `NOT_CONTACTED` = ES 文档**没有** `operatorStatus` 字段，语义唯一
- Rule: 任何地方判定 NOT_CONTACTED，ES 侧一律用 `must_not exists operatorStatus`，内存侧一律用 `profile.operatorStatus.isNullOrBlank()`。**禁止**任何位置出现 `term operatorStatus = "NOT_CONTACTED"`。
- Applies to: 新增的 `operatorStatusesFilter`、`buildEsFiltersForLevel`、`RecipientScope.matchesExpert`。
- Violation consequence: `ExpertIndexWriterService.syncOperatorStatus` 对 NOT_CONTACTED 执行的是 `ctx._source.remove('operatorStatus')`，字段根本不存在，`term` 恒零命中 → 任务静默停发。
- 来源: K-recipient-scope-status-filter / K-operator-status-single-writer

### Invariant I3a-2: should 分支里的状态谓词必须是**纯**谓词
- Rule: `operatorStatusesFilter` 的每个 should 分支只能包含状态本身的判定，**不得**夹带 `exists email`、`must_not term EMAIL_INVALID`、discipline 等非状态条件。NOT_CONTACTED 分支的纯谓词是：
  ```json
  {"bool": {"must_not": [{"exists": {"field": "operatorStatus"}}]}}
  ```
- Applies to: `ExpertSearchService.operatorStatusesFilter`。
- Violation consequence: 现有 `operatorStatusFilter("NOT_CONTACTED")` 返回的是 `notContactedWithEmailFilters(null)`，含 `exists email`（**AND 语义的条件**）。若原样塞进 should 分支，`[NOT_CONTACTED, CONTACTED]` 会变成"（有邮箱 且 无状态）或（状态=CONTACTED）"，**CONTACTED 分支绕过了有邮箱的要求**，会把无邮箱专家纳入目标 → 发送时报 NO_CONTACT 失败。
- 等价性证明（N3a-2 的依据）：`notContactedWithEmailFilters` 的 `must_not: [exists operatorStatus, term operatorStatus=EMAIL_INVALID]` 中，`term operatorStatus=EMAIL_INVALID` 蕴含 `exists operatorStatus`；故 `NOT(exists) AND NOT(term)` ≡ `NOT(exists)`。纯谓词与原表达在 NOT_CONTACTED 情形下**逻辑等价**，`exists email` 由基座另行提供。
- 来源: original（推导自 `ExpertSearchService.kt:116-136` 与 `:144-149` 的逐字实现）

### Invariant I3a-3: 多状态 OR，与其余维度 AND；空集合不产生任何 filter
- Rule: N 个状态产生**一个** filter 项 `{"bool":{"should":[...], "minimum_should_match":1}}`；空集合返回 `null`，调用方不追加。
- Applies to: `operatorStatusesFilter`、`buildEsFiltersForLevel`、`matchesExpert`。
- Violation consequence: 空集合若产出 `should: []` + `minimum_should_match: 1` → 匹配 0 条，所有不限状态的任务静默停发。
- 来源: I2a-2 / I2a-3 的同款结论（P2a 已验证的范式）

### Invariant I3a-4: CANDIDATE 基座切换的判据是「是否含非 NOT_CONTACTED 状态」
- Rule: 在 `buildEsFiltersForLevel` 的 `INTRODUCTION && CANDIDATE` 分支：
  - `statuses` 为空 **或** `statuses == setOf("NOT_CONTACTED")` → 走 `notContactedWithEmailDomainsFilters(...)` 基座，**不再**追加任何状态 filter（保持 N3a-2 的逐字一致）。
  - `statuses` 含**任一**非 NOT_CONTACTED 值 → 换成状态无关基座（`exists email` + 域 + 学科），再追加 `operatorStatusesFilter(statuses)`。
- Applies to: `ManualInitialOutreachService.buildEsFiltersForLevel`。
- Violation consequence: `notContactedWithEmailDomainsFilters` 基座自带 `must_not exists operatorStatus`；若在其上追加 `should [.., term CONTACTED]`，两者 AND 后 CONTACTED 分支恒为空，等于用户白选。这正是既有代码 `:1250-1251` 注释里记的 I-2 陷阱，多值化后判据必须从「等于」改成「包含」。
- 来源: K-recipient-scope-status-filter 的 I-2（本轮已用 `ManualInitialOutreachService.kt:1246-1258` 逐字复核）

### Invariant I3a-5: DB 重试路径与 ES 同口径
- Rule: `matchesExpert` 的状态判定为：`statuses` 为空则不判定；否则
  `statuses.any { if (it == "NOT_CONTACTED") profile.operatorStatus.isNullOrBlank() else profile.operatorStatus == it }`。
- Applies to: `BatchExecutionModels.kt`。
- Violation consequence: M-1 的经典事故。
- 来源: M-1

### Invariant I3a-6: 值域校验引用 `OperatorStatus.entries` 单一权威
- Rule: `normalizeAndValidate` 对每个状态 `require(it in ALLOWED_OPERATOR_STATUSES)`，该集合仍由 `OperatorStatus.entries` 派生（`BatchSendTaskConfigService` 既有常量），**不另抄字符串集合**；并 `require(!it.contains(","))`（I2a-5 同款理由）。
- Applies to: `BatchSendTaskConfigService.normalizeAndValidate`。
- Violation consequence: 枚举演进后校验白名单漂移。
- 来源: K-recipient-scope-status-filter

### Invariant I3a-7: `operator_status` 旧列在同一迁移中删除
- Rule: 同 I2a-1，`operator_statuses_json` 为唯一事实源，`operator_status` 单值列在 V98 中 DROP。
- Applies to: V98。
- 来源: I2a-1 的同款结论

---

## 样式契约

**不适用** —— 本计划零前端文件。

---

## 现状审计

> 表结构与迁移版本见主计划 X-2；两条活体目标来源的证据见主计划 X-1。**下一个可用版本：V98**（P2a 已占 V97）。

### 存储

`operator_status VARCHAR(32) NULL`（`V95__add_operator_status_to_batch_send_task_config.sql`）。该迁移注释已明确：**NULL = 不限**，与 `expert_contact.operator_status` 的 `NOT NULL DEFAULT 'NOT_CONTACTED'` 语义相反。

### 关键既有实现（逐字，改动前基线）

`ExpertSearchService.kt:138-149`：

```kotlin
        /**
         * I-3: NOT_CONTACTED 语义唯一 —— 复用 [notContactedWithEmailFilters] 的 must_not exists
         * 表达（= ES 文档无 operatorStatus 字段），其余状态走 term。三处批量发送旁路
         * （buildEsFiltersForLevel / buildMaterialReminderEsFilters / matchesExpert）共用此实现，
         * 禁止在别处另写 `term operatorStatus=NOT_CONTACTED`。
         */
        fun operatorStatusFilter(status: String): List<Map<String, Any>> {
            if (status == "NOT_CONTACTED") {
                return notContactedWithEmailFilters(null)
            }
            return listOf(mapOf("term" to mapOf("operatorStatus" to status)))
        }
```

⚠️ 该注释里的「三处旁路」已被主计划 X-1 证伪（`buildMaterialReminderEsFilters` 零调用方）。本计划**顺带修正这段注释文字**，但不改函数体（N3a-1）。

`ManualInitialOutreachService.kt:1245-1274`（P2a 改造后的形态，本计划在其基础上继续改）：

```kotlin
    private fun buildEsFiltersForLevel(scope: RecipientScope, level: String): List<Map<String, Any>> {
        val filters = if (scope.mailType == BatchSendType.INTRODUCTION.name && level == "CANDIDATE") {
            if (scope.operatorStatus == null || scope.operatorStatus == "NOT_CONTACTED") {
                ExpertSearchService.notContactedWithEmailFilters(scope.emailDomain, scope.discipline).toMutableList()
            } else {
                val base = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
                scope.emailDomain?.let { base.add(mapOf("wildcard" to mapOf("email" to mapOf("value" to "*@$it")))) }
                scope.discipline?.let { base.add(ExpertSearchService.disciplineFilter(it)) }
                base.addAll(ExpertSearchService.operatorStatusFilter(scope.operatorStatus))
                base
            }
        } else {
            val base = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
            scope.emailDomain?.let { base.add(mapOf("wildcard" to mapOf("email" to mapOf("value" to "*@$it")))) }
            scope.discipline?.let { base.add(ExpertSearchService.disciplineFilter(it)) }
            scope.operatorStatus?.let { base.addAll(ExpertSearchService.operatorStatusFilter(it)) }
            base
        }
        if (scope.tags.isNotEmpty()) {
            filters.add(mapOf("terms" to mapOf("tags" to scope.tags)))
        }
        ExpertSearchService.regionsFilter(scope.regions)?.let { filters.add(it) }
        return filters
    }
```

`BatchExecutionModels.kt:59-67`：

```kotlin
        if (!operatorStatus.isNullOrBlank()) {
            val matched = if (operatorStatus == "NOT_CONTACTED") {
                profile.operatorStatus.isNullOrBlank()
            } else {
                profile.operatorStatus == operatorStatus
            }
            if (!matched) return false
        }
```

### 写路径 / 读路径

与 P2a 的 `emailDomain` **同构**（同样 5 个写点、同样 14 个读点，同样的行号邻域）。逐点清单见 P2a「现状审计」，把 `emailDomain` 替换为 `operatorStatus` 即为本计划的对应点；差异只有两处：

- `operatorStatus` **不在** `BatchSendConfig`（KV 兼容 data class，`BatchSendSettingService.kt:249/262`）里 —— 故 `toLegacyConfig()` / `updateLegacyConfig` 返回值**无需**做降级映射，只需按 M-2 做 `operatorStatusesJson = existing.operatorStatusesJson` 的保留。
- `ManualInitialOutreachService.kt:1102-1128` 的死代码 `buildMaterialReminderEsFilters` 带一个 `operatorStatus: String? = null` 形参 —— 零调用方，本计划**不改也不删**。

### 交互点

| IP | 写 → 读 | 说明 |
|---|---|---|
| IP-1 | 配置保存 → `buildEsFiltersForLevel` CANDIDATE 分支 | I3a-4 的基座切换判据，最易错 |
| IP-2 | 配置保存 → `matchesExpert` | M-1 两条来源同口径（I3a-5） |
| IP-3 | 预估 `countEsTargets` → 实发 `fetchEsPage` | 同一函数，天然同源（M-4） |
| IP-4 | 本计划的映射行 → `OperatorStatusWriteSeamGuardTest` 扫描 | M-5：守卫红了要 HUMAN 授权登记噪声，**不得**自行改守卫 |

---

## 实现方案

### T3a-1 迁移 V98（I3a-7）

新建 `src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql`：

```sql
-- I3a-7: operator_statuses_json 成为唯一事实源；operator_status 单值列在本迁移中删除。
-- 照 V93 的两步范式（TEXT 不能带 DEFAULT）。空数组 [] = 不限（与旧 operator_status IS NULL 等价）。
ALTER TABLE batch_send_task_config
    ADD COLUMN operator_statuses_json TEXT NOT NULL AFTER discipline;

UPDATE batch_send_task_config
SET operator_statuses_json = CASE
        WHEN operator_status IS NULL OR operator_status = '' THEN '[]'
        ELSE CONCAT('["', operator_status, '"]')
    END;

ALTER TABLE batch_send_task_config DROP COLUMN operator_status;
```

⚠️ 不得含 `${` 字面量（主计划 X-2）；不得编辑已应用迁移。

### T3a-2 ES filter 助手（I3a-1 / I3a-2 / I3a-3 / N3a-1）

文件：`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt`

在 companion object 内、`operatorStatusFilter` 之后**新增**（逐字）：

```kotlin
        /**
         * I3a-2: 单个状态的**纯谓词** —— 只判定状态本身，不夹带 exists email /
         * EMAIL_INVALID 排除等 AND 语义条件，因此可安全放进 bool.should 分支。
         *
         * NOT_CONTACTED = ES 文档无 operatorStatus 字段（I3a-1）。与
         * [notContactedWithEmailFilters] 的 `must_not [exists, term EMAIL_INVALID]` 逻辑等价：
         * `term operatorStatus=EMAIL_INVALID` 蕴含 `exists operatorStatus`，
         * 故 NOT(exists) AND NOT(term) ≡ NOT(exists)。
         */
        fun operatorStatusPredicate(status: String): Map<String, Any> =
            if (status == "NOT_CONTACTED") {
                mapOf("bool" to mapOf(
                    "must_not" to listOf(mapOf("exists" to mapOf("field" to "operatorStatus")))
                ))
            } else {
                mapOf("term" to mapOf("operatorStatus" to status))
            }

        /**
         * I3a-3: N 个状态取 OR，产出**单个** filter 项；空集合返回 null（调用方不得追加）。
         * 照 [regionsFilter] / [emailDomainsFilter] 的 should + minimum_should_match 范式。
         */
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

同文件 `:138-143` 的 KDoc 注释按主计划 X-1 更正为「两条活体旁路（buildEsFiltersForLevel / matchesExpert）」，**函数体不动**（N3a-1）。

### T3a-3 ES 目标查询接入（I3a-4 / M-1）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

`buildEsFiltersForLevel` 的两个分支改为：

```kotlin
    private fun buildEsFiltersForLevel(scope: RecipientScope, level: String): List<Map<String, Any>> {
        // I3a-4: 判据从「等于 NOT_CONTACTED」变为「是否含非 NOT_CONTACTED 值」。
        // 空集合 或 仅含 NOT_CONTACTED  → 保持 notContacted 基座（N3a-2 逐字不变）。
        val statuses = scope.operatorStatuses
        val onlyNotContacted = statuses.isEmpty() || statuses.all { it == "NOT_CONTACTED" }
        val filters = if (scope.mailType == BatchSendType.INTRODUCTION.name && level == "CANDIDATE" && onlyNotContacted) {
            ExpertSearchService.notContactedWithEmailDomainsFilters(scope.emailDomains, scope.discipline).toMutableList()
        } else {
            // I3a-4: 含任一非 NOT_CONTACTED 状态时必须换成状态无关基座 —— notContacted 基座
            // 自带 must_not exists operatorStatus，与 term 状态并存恒为空。
            val base = mutableListOf<Map<String, Any>>(mapOf("exists" to mapOf("field" to "email")))
            ExpertSearchService.emailDomainsFilter(scope.emailDomains)?.let { base.add(it) }
            scope.discipline?.let { base.add(ExpertSearchService.disciplineFilter(it)) }
            // I3a-3: 空集合返回 null，不追加任何状态 filter。
            ExpertSearchService.operatorStatusesFilter(statuses)?.let { base.add(it) }
            base
        }
        if (scope.tags.isNotEmpty()) {
            filters.add(mapOf("terms" to mapOf("tags" to scope.tags)))
        }
        ExpertSearchService.regionsFilter(scope.regions)?.let { filters.add(it) }
        return filters
    }
```

⚠️ **行为等价性核对（N3a-2）**：原代码 CANDIDATE 分支在 `operatorStatus == null` 或 `== "NOT_CONTACTED"` 时走 notContacted 基座、**不追加**状态 filter；新代码在 `statuses` 为空或全为 NOT_CONTACTED 时走同一基座、同样不追加。原 else 分支（APPLICATION/MATERIAL_REMINDER）在 `operatorStatus == null` 时不追加状态 filter；新代码由 `operatorStatusesFilter` 返回 null 达到同样效果。**唯一行为变化**：APPLICATION/MATERIAL_REMINDER 层选 NOT_CONTACTED 时，从 `must_not [exists, term EMAIL_INVALID]` 变成 `must_not [exists]` —— 已由 I3a-2 的等价性证明覆盖，且须由 T3a-6 的等价用例断言。

### T3a-4 实体、快照与重试路径（M-3 / I3a-5）

文件：`BatchSendTaskConfig.kt` —— 4 个 data class 的 `operatorStatus: String?` → `operatorStatuses: List<String>`（实体侧为 `operatorStatusesJson: String = "[]"`），位置放在 `discipline` 之后（与迁移列序一致）。

文件：`BatchExecutionModels.kt`
- `BatchExecutionSnapshot`（`:20`）：`operatorStatuses: List<String> = emptyList()`
- `RecipientScope`（`:54`）：`operatorStatuses: List<String>`
- `fromSnapshot`（`:106`）：`operatorStatuses = snapshot.operatorStatuses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()`
- `matchesExpert`（`:59-67`）替换为（I3a-5）：
  ```kotlin
          // I3a-5：与 ES 的 operatorStatusesFilter 同口径 —— 多状态取 OR；
          // NOT_CONTACTED = ES 文档无该字段（I3a-1）；空集合不判定（I3a-3）。
          if (operatorStatuses.isNotEmpty()) {
              val matched = operatorStatuses.any {
                  if (it == "NOT_CONTACTED") profile.operatorStatus.isNullOrBlank()
                  else profile.operatorStatus == it
              }
              if (!matched) return false
          }
  ```
- `toExecutionSnapshot`（`:242` 附近）：透传改名

文件：`BatchSendControlService.kt` —— 快照构造处透传改名。

### T3a-5 配置服务映射全集（M-2 / M-3 / I3a-6）

文件：`BatchSendTaskConfigService.kt`。改点与 P2a 的 T2a-5 **同构**（`create` / `update` / `toView` / `ConfigFields` / `NormalizedConfig` / 三个 `*Fields()` / `updateLegacyConfig`），把 `emailDomain(s)` 换成 `operatorStatus(es)`；差异：

- `updateLegacyConfig`（`:185` 邻域）：`operatorStatuses = parseOperatorStatuses(existing.operatorStatusesJson)`（M-2 保留；旧 API 本来就不传该字段，漏写必被默认值抹掉）。
- `toLegacyConfig()` / `updateLegacyConfig` 返回值：**不加**该字段（`BatchSendConfig` KV data class 本来就没有它，M-3）。
- `normalizeAndValidate`（`:263-269`）替换为：
  ```kotlin
          // I3a-6：白名单仍引用 OperatorStatus.entries 派生的 ALLOWED_OPERATOR_STATUSES（单一权威）。
          // 逗号是前端 picker 的分隔符（K-batch-picker-comma-delimited-contract）。
          val operatorStatuses = fields.operatorStatuses
              .map { it.trim() }
              .filter { it.isNotEmpty() }
              .distinct()
          operatorStatuses.forEach {
              require(it in ALLOWED_OPERATOR_STATUSES) {
                  "operatorStatus must be one of $ALLOWED_OPERATOR_STATUSES: $it"
              }
              require(!it.contains(",")) { "operatorStatus must not contain a comma: $it" }
          }
          val operatorStatusesJson = objectMapper.writeValueAsString(operatorStatuses)
  ```
- 新增 `parseOperatorStatuses(json: String?)`，实现照 P2a 的 `parseEmailDomains`。

### T3a-6 测试

文件：`BatchSendTaskConfigServiceTest.kt`
- `["NOT_CONTACTED","CONTACTED"]` 保存 → `toView().operatorStatuses` 顺序一致
- 去空白 / 去重 / 逗号 / 非法枚举值（如 `"BOGUS"`）四条校验用例（I3a-6）
- `null` / `[]` → `"[]"`（I3a-3）
- **`updateLegacyConfig` 保留用例（M-2）**：先设 `["CONTACTED"]` → 旧 API 只改 cron → 断言仍是 `["CONTACTED"]`

文件：`ManualInitialOutreachServiceTest.kt`
- **等价性用例（N3a-2，本计划最关键）**：`operatorStatuses = []` 与 `["NOT_CONTACTED"]` 在 `INTRODUCTION + CANDIDATE` 下产出的 filter 列表，与改动前基线**逐字相等**（把改动前的期望 filter 结构硬编码进断言）
- **基座切换用例（I3a-4）**：`["CONTACTED"]` → 产出的 filter 列表中**不含** `must_not exists operatorStatus`（即已换成状态无关基座），且含一个 `bool.should` 长度 1
- **混合用例（I3a-4）**：`["NOT_CONTACTED","CONTACTED"]` → 走状态无关基座；`bool.should` 长度 2，其中一支是 `{"bool":{"must_not":[{"exists":{"field":"operatorStatus"}}]}}`（I3a-1 / I3a-2）
- **纯谓词用例（I3a-2）**：`operatorStatusPredicate("NOT_CONTACTED")` 的返回值中**不含** `exists`→`email`、也不含 `term operatorStatus=EMAIL_INVALID`
- **ES/DB 同口径（I3a-5）**：构造 profile 集（`operatorStatus` 分别为 null / ""/ "CONTACTED" / "EMAIL_INVALID"），对 `[]`、`["NOT_CONTACTED"]`、`["CONTACTED"]`、`["NOT_CONTACTED","EMAIL_INVALID"]` 四组，`matchesExpert` 的判定与 `operatorStatusesFilter` 语义逐条相等
- **无 `term NOT_CONTACTED` 断言（I3a-1）**：把产出的 filter 序列化为 JSON 字符串，断言不含子串 `"NOT_CONTACTED"`

---

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V98__add_operator_statuses_to_batch_send_task_config.sql` | 新建 |
| 2 | `src/main/kotlin/.../campaign/domain/BatchSendTaskConfig.kt` | 修改 |
| 3 | `src/main/kotlin/.../campaign/domain/BatchExecutionModels.kt` | 修改 |
| 4 | `src/main/kotlin/.../campaign/service/BatchSendTaskConfigService.kt` | 修改 |
| 5 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | 修改 |
| 6 | `src/main/kotlin/.../campaign/service/BatchSendControlService.kt` | 修改（快照构造改名） |
| 7 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | 修改（新增 2 函数 + 更正 1 段注释） |
| 8 | `src/test/kotlin/.../campaign/service/BatchSendTaskConfigServiceTest.kt` | 修改 |
| 9 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 |
| 10 | `src/test/kotlin/.../campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | 修改（`:218/:225/:237/:260` 的 `RecipientScope.fromSnapshot` 与 `baseSnapshot` 适配。**执行前先 `grep -n "operatorStatus" src/test/kotlin/.../BatchSendTaskRuntimeIntegrationTest.kt` 确认该文件是否真的引用了该字段；若无引用且编译通过，本行从清单中划掉**） |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 修改（**A6 授权**：`EXCLUDED_NOISE_SITES` 维护 —— ExpertSearchService 386→419 行号刷新；删除 10 条因 `operatorStatus`→`operatorStatuses` 改名而失效的配置映射排除项（BatchExecutionModels 110/255、BatchSendTaskConfigService 77/110/190/304/423/551/569/587）。M-5：守卫判定逻辑一行不改） |

文件数：**11**（原 10 上限 + A6 授权放宽 1 个测试文件；执行中若发现仍需改第 12 个文件，**停下来报告**）  子系统数：**2** ✅

---

## 验证命令

见主计划。本计划专用：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OperatorStatusWriteSeamGuardTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
```

---

## 验收标准

- **I3a-1**：「无 `term NOT_CONTACTED`」用例绿；`grep -rn '"NOT_CONTACTED"' src/main/kotlin/com/weibo/talentintroduction/campaign/ src/main/kotlin/com/weibo/talentintroduction/expert/` 的每一处命中都能指向 `must_not exists` 或 `isNullOrBlank()` 判定，无一处是 `term`。**贴 grep 输出**（`K-plan-quantified-claims-need-grep-receipts`）。
- **I3a-2**：纯谓词用例绿。
- **I3a-3**：空集合用例绿；`operatorStatusesFilter(emptyList())` 返回 `null`。
- **I3a-4**：基座切换用例 + 混合用例绿。
- **I3a-5**：ES/DB 同口径用例绿。
- **I3a-6**：四条校验用例绿；`grep -n "ALLOWED_OPERATOR_STATUSES" src/main/kotlin/.../BatchSendTaskConfigService.kt` 显示该常量仍由 `OperatorStatus.entries` 派生，未被字符串字面量集合替代。
- **I3a-7**：`FlywayMigrationIntegrationTest` 绿；V98 含 DROP 语句。
- **N3a-1**：`git diff src/main/kotlin/.../ExpertSearchService.kt` 中，`operatorStatusFilter` / `notContactedWithEmailFilters` / `buildExpertFilters` / `searchExperts` 的**函数体**无改动行（只允许 KDoc 注释行变更）。
- **N3a-2**：等价性用例绿（硬编码基线断言）。
- **M-3**：`grep -rn "operatorStatuses" src/main/kotlin | wc -l` 的输出贴进复验报告并逐点核对。
- **M-5**：`OperatorStatusWriteSeamGuardTest` 绿；若红，检查是否已按 IP-4 获 HUMAN 授权登记噪声站点 —— **未获授权就修改守卫判定逻辑视为 P1**。
- 回归：主计划全量测试命令通过。

---

## 人工验收清单

### A3a-1: 多状态预估命中数 = 各单状态之和
- 前置条件：ES 中 CANDIDATE 层同时存在无 `operatorStatus` 字段（未联系）与 `operatorStatus=CONTACTED` 的专家。
- 操作步骤：`POST {BASE}/api/mail/batch-send/recipients/preview`，`operatorStatuses` 依次为 `["NOT_CONTACTED"]`（记 P1）、`["CONTACTED"]`（记 P2）、`["NOT_CONTACTED","CONTACTED"]`（记 P3）。
- 预期结果：`P3 == P1 + P2`，且三者均 > 0。若 P3 == 0 而 P1/P2 > 0 → I3a-4 的基座未切换（notContacted 基座与 term 并存恒空）。
- 覆盖：O-3、I3a-3、I3a-4、IP-1

### A3a-2: 回归 —— 留空与仅选 NOT_CONTACTED 的命中数不变
- 前置条件：**改动前先记录基线** —— 用旧版本对同一批筛选条件（`operatorStatus` 留空、以及 `= NOT_CONTACTED`）各调一次预估，记下数字 B0、B1。
- 操作步骤：新版本上 `operatorStatuses: []` 与 `["NOT_CONTACTED"]` 各调一次预估。
- 预期结果：两个数字与 B0、B1 **完全相等**。
- 覆盖：N3a-2

### A3a-3: NOT_CONTACTED 语义 —— 不是字符串值
- 前置条件：手工准备两个 CANDIDATE 专家：X 的 ES 文档**没有** `operatorStatus` 字段；Y 的 `operatorStatus` 显式为字符串 `"NOT_CONTACTED"`（用 ES `_update` 强行写入，模拟脏数据）。
- 操作步骤：`operatorStatuses: ["NOT_CONTACTED"]` 调预估，并用 `POST .../manual-executions` 的 dry 观察目标（或直接看命中数变化）。
- 预期结果：命中 X，**不**命中 Y。（若 Y 也被命中，说明某处写了 `term operatorStatus=NOT_CONTACTED`，违反 I3a-1。）
- 覆盖：I3a-1

### A3a-4: 重试联系人与 ES 同口径
- 前置条件：`MANUAL_OUTREACH` campaign 下存在可重试联系人 A（其 ES profile 无 `operatorStatus`）与 B（`operatorStatus=CONTACTED`）。
- 操作步骤：`operatorStatuses: ["CONTACTED"]` 调预估，观察 `retryable`。
- 预期结果：计入 B、**不**计入 A。
- 覆盖：I3a-5、M-1、IP-2

### A3a-5: 非法状态值被拒
- 前置条件：无。
- 操作步骤：`POST .../configs`，`operatorStatuses: ["BOGUS_STATUS"]`。
- 预期结果：HTTP 4xx（非 500），错误信息含合法值列表。
- 覆盖：I3a-6

### A3a-6: 回归 —— 旧 typed API 不清空多值状态
- 前置条件：`legacy_code='INTRODUCTION'` 的配置，先用新接口设 `operatorStatuses = ["CONTACTED","REPLIED"]`（用真实枚举值）。
- 操作步骤：`PUT .../types/INTRODUCTION/config` 只改 cron → 再 `GET .../configs` 查该条。
- 预期结果：`operatorStatuses` 仍是两个值。
- 覆盖：M-2

### A3a-7: 回归 —— 专家列表页状态筛选未受影响
- 前置条件：无。
- 操作步骤：专家列表页用「状态」下拉分别选「未联系」与另一状态。
- 预期结果：过滤正常；请求参数仍为 `operatorStatus=<单值>`；「未联系」的结果集与改动前一致。
- 覆盖：N3a-1、主计划 N-1
