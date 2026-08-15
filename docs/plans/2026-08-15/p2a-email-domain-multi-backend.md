# P2a：邮箱服务商筛选改多值（后端）

主计划：`batch-task-filters-main.md`（共享不变量 M-1…M-5、共享审计 X-1…X-3、验证命令均在主计划，本文不重复）
子系统数：2（campaign / expert）  文件数：8
依赖：无。可在 P1 之后立即执行。

---

## 需求描述

### Observable outcome

`POST /api/mail/batch-send/configs`、`PUT /api/mail/batch-send/configs/{id}`、`POST /api/mail/batch-send/recipients/preview`、`POST /api/mail/batch-send/manual-executions` 接受 `emailDomains: string[]`；多个域名之间取 **或**；ES 新目标与 MySQL 重试联系人两条来源同口径生效。`GET /api/mail/batch-send/configs` 返回 `emailDomains` 数组。

### What must NOT change

- **N2a-1** `ExpertSearchService.searchExperts` / `aggregateTags` / `aggregateRegions` / `aggregateEmailDomains` 的 `emailDomain: String?` 形参与语义不变（专家列表页在用，见主计划 N-1/N-2）。
- **N2a-2** `notContactedWithEmailFilters(emailDomain: String?, discipline: String?)` 的**现有签名与行为保留**（`ExpertSearchService.kt:116`）—— 它被 `buildEsFiltersForLevel` 以外的路径依赖时不能被破坏。多值走**新增**重载，不改旧的。
- **N2a-3** 旧 typed API `/api/mail/batch-send/types/{sendType}/config` 的请求/响应形状不变（仍是单值 `emailDomain: String`）。
- **N2a-4** `discipline`、`funnelLevel`、`tags`、`regions`、`operatorStatus` 五个筛选维度的行为不变。
- **N2a-5** 收件预估零副作用契约不变（M-4）。

### Out of scope

- 前端（`app.js` / `index.html`）—— 归 P2b。
- `operatorStatus` 多值 —— 归 P3a。
- 专家列表页 `emailDomain` 多选。
- 删除死代码 `buildMaterialReminderEsFilters`（主计划 X-1 观察项）。

---

## 关键不变量

### Invariant I2a-1: `email_domains_json` 是唯一事实源，`email_domain` 列在同一迁移中删除
- Rule: V97 迁移完成后，`batch_send_task_config` **不再有** `email_domain` 列。所有读写走 `email_domains_json`（JSON 字符串数组，空数组 `[]` = 不限）。
- Applies to: V97 迁移；`BatchSendTaskConfig` 实体；`BatchSendTaskConfigService` 全部映射点。
- Violation consequence: 保留旧列 = 双事实源。运营从旧 typed API 改一次，新旧列分叉，定时任务按哪个执行取决于代码路径，无法排查（同 `K-batch-send-legacy-routes-entity-ssot` 的事故形态）。
- 先例: `V92__drop_daily_cap_from_batch_send_task_config.sql` 已有在迁移中 DROP 列的先例。
- 来源: original + K-batch-send-legacy-routes-entity-ssot

### Invariant I2a-2: 空数组 = 不限，与 `null` 等价，不产生任何 ES filter
- Rule: `emailDomains` 为 `null`、`[]`、或全为空白字符串时，一律归一化为 `emptyList()`，且**不向 ES 查询追加任何 email 相关 filter**，也不在 `matchesExpert` 中做任何判定。
- Applies to: `normalizeAndValidate`、`RecipientScope.fromSnapshot`、`buildEsFiltersForLevel`、`matchesExpert`、`emailDomainsFilter`。
- Violation consequence: 空数组若生成 `bool.should []` + `minimum_should_match 1`，ES 会**匹配 0 条**，所有不限服务商的任务静默停发。
- 来源: original（`regionsFilter` 已是此范式，`ExpertSearchService.kt:105-113` 对空集合返回 null）

### Invariant I2a-3: 多域之间是 OR，与其余维度是 AND
- Rule: N 个域名产生**一个** filter 项：
  ```
  {"bool": {"should": [ {"wildcard":{"email":{"value":"*@d1"}}}, ... ], "minimum_should_match": 1}}
  ```
  该项与 tags / regions / discipline / operatorStatus / exists-email 等其余 filter 一起放进 `bool.filter` 数组（AND）。
- Applies to: `ExpertSearchService.emailDomainsFilter`、`buildEsFiltersForLevel`。
- Violation consequence: 若把 N 个 wildcard 平铺进 `bool.filter`，语义变成"邮箱同时属于 d1 且属于 d2"，恒为空 —— 与 `K-batch-send-filter-retry-parity` 同类的静默零命中事故。
- 来源: original（照 `ExpertSearchService.regionsFilter` 的 should + minimum_should_match 范式，`:105-113`）

### Invariant I2a-4: DB 重试路径与 ES 同口径
- Rule: `RecipientScope.matchesExpert` 的域名判定必须是 `emailDomains.any { email.endsWith("@$it") }`，即 **any**（OR），且 `emailDomains` 为空时不判定。ES 侧用 `wildcard *@d`，两者对同一专家必须给出相同结论。
- Applies to: `BatchExecutionModels.kt` 的 `matchesExpert`。
- Violation consequence: M-1 的经典事故 —— 重试联系人静默绕过配置错发。
- 来源: M-1 / K-batch-send-filter-retry-parity

### Invariant I2a-5: 域名值不得含逗号，且须去空白去重
- Rule: `normalizeAndValidate` 对每个域名 `trim()`，丢弃空串，**去重**（保序），并 `require(!it.contains(","))`。
- Applies to: `BatchSendTaskConfigService.normalizeAndValidate`。
- Violation consequence: 前端 picker 以逗号分隔存值（主计划 X-3），含逗号的域名会在回显时被拆成两个不存在的域名，筛选静默命中 0 条。
- 来源: K-batch-picker-comma-delimited-contract（本轮新增）

### Invariant I2a-6: 旧 typed API 的双向适配
- Rule:
  - **读** `toLegacyConfig()` / `updateLegacyConfig()` 返回值：`emailDomain = row.emailDomains.firstOrNull().orEmpty()`（取首个，多值降级）。
  - **写** `updateLegacyConfig()`：`emailDomainsJson` 必须 **显式保留 `existing.emailDomainsJson`**，绝不从 `request.emailDomain` 重建 —— 旧 API 传的是降级后的单值，用它重建会把运营在新界面配的其余域名抹掉。
- Applies to: `BatchSendTaskConfigService.updateLegacyConfig()`（`:156-190`）、`toLegacyConfig()`（`:208` 附近）。
- Violation consequence: 运营从旧界面改一次 cron，多选的邮箱服务商被静默裁成 1 个。
- 来源: M-2 / K-batch-config-legacy-adapter-field-preservation

---

## 样式契约

**不适用** —— 本计划零前端文件。

---

## 现状审计

> 表结构、迁移版本号、Flyway 占位符约束见主计划 X-2；两条活体目标来源的证据见主计划 X-1。

### 存储：`batch_send_task_config.email_domain`

- Schema：`email_domain VARCHAR(120) NULL`（`V72__create_batch_send_task_config.sql:14`）。NULL 或空串 = 不限。
- **下一个可用迁移版本：V97**（已核对 `src/main/resources/db/migration/` 最高为 V96，V96 已被 `V96__add_name_to_reply_snippet.sql` 占用）。

### 写路径（全量 grep 取证）

```
$ grep -rn "emailDomain" src/main/kotlin | grep -v "^.*ExpertIndexController\|^.*ExpertSearchService"
```

| # | 位置 | 写什么 |
|---|---|---|
| 1 | `BatchSendTaskConfigService.kt:72` | `create()` 组装实体 `emailDomain = normalized.emailDomain` |
| 2 | `BatchSendTaskConfigService.kt:105` | `update()` 组装实体 |
| 3 | `BatchSendTaskConfigService.kt:185` | `updateLegacyConfig()` —— **旧 typed API 适配器**，`emailDomain = request.emailDomain.ifBlank { null }` |
| 4 | `BatchSendTaskConfigService.kt:255` | `normalizeAndValidate` 归一化 `normalizeOptionalFilter(fields.emailDomain)` |
| 5 | `BatchSendTaskConfigService.kt:290` | `NormalizedConfig` 构造 |

### 读路径（全量 grep 取证）

| # | 位置 | 读什么 / 依赖 |
|---|---|---|
| 1 | `BatchSendTaskConfigService.kt:200` | `updateLegacyConfig` 返回值 → `BatchSendConfig`（KV 兼容 data class），`view.emailDomain.orEmpty()` |
| 2 | `BatchSendTaskConfigService.kt:227` | `toLegacyConfig()` → 同上 |
| 3 | `BatchSendTaskConfigService.kt:397` | `toView()` → `BatchSendTaskConfigView`（**前端读这个**） |
| 4 | `BatchSendTaskConfigService.kt:525/543/561` | 三个 `*Fields()` → `ConfigFields`（走校验） |
| 5 | `BatchExecutionModels.kt:105` | `RecipientScope.fromSnapshot` 归一化 |
| 6 | `BatchExecutionModels.kt:76-79` | **`matchesExpert`** —— DB 重试路径判定：`!email.endsWith("@$emailDomain") → false` |
| 7 | `BatchExecutionModels.kt:241` | `toExecutionSnapshot` 透传 |
| 8 | `ManualInitialOutreachService.kt:1254` / `:1261` | **`buildEsFiltersForLevel`** 两个分支各追加一次 `wildcard *@domain` |
| 9 | `ManualInitialOutreachService.kt:1249` | `buildEsFiltersForLevel` 的 CANDIDATE 基座 `notContactedWithEmailFilters(scope.emailDomain, scope.discipline)` |
| 10 | `ManualInitialOutreachService.kt:1110-1111` | `buildMaterialReminderEsFilters` —— **死代码，零调用方（主计划 X-1）**，本计划不改也不删 |
| 11 | `ManualInitialOutreachService.kt:1130` / `:1146` | `scopeDescription` 日志串拼接 |
| 12 | `ManualInitialOutreachService.kt:102/111/1142` | 材料提醒 scope 构造 |
| 13 | `ManualInitialOutreachService.kt:1284/1300` | `toSnapshot` / `toBatchSendConfig` 两个 KV 桥接 |
| 14 | `BatchSendControlService.kt:575` | 快照构造 |

### 交互点

| IP | 写 → 读 | 说明 |
|---|---|---|
| IP-1 | 配置保存（写 #1/#2）→ ES 目标查询（读 #8/#9） | 多值必须在这里变成 should-OR，否则恒零命中（I2a-3） |
| IP-2 | 配置保存 → DB 重试判定（读 #6） | M-1 的两条来源，必须同口径（I2a-4） |
| IP-3 | 旧 typed API（写 #3）→ 新界面读（读 #3 `toView`） | 旧 API 一次调用不得裁剪多值（I2a-6） |
| IP-4 | 收件预估（`countBySnapshot:423` → `countEsTargets:1211` → 读 #8）→ 实际发送（`fetchEsPage:1220` → 读 #8） | 同一个 `buildEsFiltersForLevel`，天然同源；改动不得拆成两份（M-4） |

### 现有 ES filter 组合范式（照抄对象，逐字）

`ExpertSearchService.kt:105-113` —— 多值 OR 的既有权威写法：

```kotlin
        fun regionsFilter(regions: List<String>): Map<String, Any>? {
            if (regions.isEmpty()) return null
            return mapOf(
                "bool" to mapOf(
                    "should" to regions.map { regionFilter(it) },
                    "minimum_should_match" to 1
                )
            )
        }
```

`ExpertSearchService.kt:116-136` —— 待新增重载的原函数：

```kotlin
        fun notContactedWithEmailFilters(
            emailDomain: String? = null,
            discipline: String? = null
        ): List<Map<String, Any>> {
            val filters = mutableListOf<Map<String, Any>>(
                mapOf("exists" to mapOf("field" to "email")),
                mapOf("bool" to mapOf(
                    "must_not" to listOf(
                        mapOf("exists" to mapOf("field" to "operatorStatus")),
                        mapOf("term" to mapOf("operatorStatus" to "EMAIL_INVALID"))
                    )
                ))
            )
            if (!emailDomain.isNullOrBlank()) {
                filters.add(mapOf("wildcard" to mapOf("email" to mapOf("value" to "*@$emailDomain"))))
            }
            if (!discipline.isNullOrBlank()) {
                filters.add(disciplineFilter(discipline))
            }
            return filters
        }
```

### 既有测试

- `src/test/kotlin/.../campaign/service/BatchSendTaskConfigServiceTest.kt` —— 配置 CRUD / 归一化 / 校验
- `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` —— 发送与目标计算
- `src/test/kotlin/.../campaign/OperatorStatusWriteSeamGuardTest.kt` —— 见 M-5（本计划不碰 `operatorStatus`，理论上不受影响，但仍须绿）

---

## 实现方案

### T2a-1 迁移 V97（I2a-1）

新建 `src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql`：

```sql
-- I2a-1: email_domains_json 成为唯一事实源；email_domain 单值列在本迁移中删除，避免双事实源。
-- TEXT 列不能带 DEFAULT（MySQL 限制），故照 V93__add_regions_to_batch_send_task_config.sql
-- 的两步范式：先 ADD NOT NULL，再 UPDATE 兜底。
-- I2a-2: 空数组 [] = 不限（与旧 email_domain IS NULL / '' 等价）。
ALTER TABLE batch_send_task_config
    ADD COLUMN email_domains_json TEXT NOT NULL AFTER regions_json;

UPDATE batch_send_task_config
SET email_domains_json = CASE
        WHEN email_domain IS NULL OR email_domain = '' THEN '[]'
        ELSE CONCAT('["', email_domain, '"]')
    END;

ALTER TABLE batch_send_task_config DROP COLUMN email_domain;
```

⚠️ 约束：
- **不得**出现 `${` 字面量（主计划 X-2 的 Flyway 占位符约束）。上面的 SQL 已满足。
- **不得**编辑任何已应用的迁移。
- DROP 列的先例是 `V92`。

### T2a-2 实体与快照（M-3 / I2a-2）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`

- `BatchSendTaskConfig`（`:8`）：`val emailDomain: String? = null` → `val emailDomainsJson: String = "[]"`，位置放在 `regionsJson` 之后（与迁移列序一致）。
- `BatchSendTaskConfigView`（`:34`）：`val emailDomain: String?` → `val emailDomains: List<String> = emptyList()`。
- `BatchSendTaskConfigCreateCommand`（`:59`）/ `BatchSendTaskConfigUpdateCommand`（`:76`）：`val emailDomain: String? = null` → `val emailDomains: List<String> = emptyList()`。

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`

- `BatchExecutionSnapshot`（`:18`）：`val emailDomain: String? = null` → `val emailDomains: List<String> = emptyList()`。
- `RecipientScope`（`:53`）：`val emailDomain: String?` → `val emailDomains: List<String>`。
- `RecipientScope.fromSnapshot`（`:105`）：
  ```kotlin
  // I2a-2 / I2a-5：trim、丢空、去重保序；空集合 = 不限。
  emailDomains = snapshot.emailDomains.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
  ```
- `matchesExpert`（`:76-79`）替换为（I2a-4）：
  ```kotlin
          // I2a-4：与 ES 的 emailDomainsFilter 同口径 —— 多域取 OR；空集合不判定（I2a-2）。
          if (emailDomains.isNotEmpty()) {
              val email = profile.email
              if (email.isNullOrBlank()) return false
              if (emailDomains.none { email.endsWith("@$it") }) return false
          }
  ```
- `toExecutionSnapshot`（`:241`）：透传改名。

### T2a-3 ES filter 助手（I2a-3 / N2a-2）

文件：`src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt`

在 companion object 内、`regionsFilter` 之后新增（**逐字**）：

```kotlin
        /**
         * I2a-3: N 个邮箱域取 OR，产出**单个** filter 项；空集合返回 null（I2a-2，
         * 调用方不得追加）。照 [regionsFilter] 的 should + minimum_should_match 范式。
         */
        fun emailDomainsFilter(emailDomains: List<String>): Map<String, Any>? {
            val domains = emailDomains.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (domains.isEmpty()) return null
            return mapOf(
                "bool" to mapOf(
                    "should" to domains.map {
                        mapOf("wildcard" to mapOf("email" to mapOf("value" to "*@$it")))
                    },
                    "minimum_should_match" to 1
                )
            )
        }

        /**
         * 多域版 [notContactedWithEmailFilters]。**旧单值重载保持原样不动**（N2a-2）——
         * 专家列表等路径仍在用它。
         */
        fun notContactedWithEmailDomainsFilters(
            emailDomains: List<String> = emptyList(),
            discipline: String? = null
        ): List<Map<String, Any>> {
            val filters = mutableListOf<Map<String, Any>>(
                mapOf("exists" to mapOf("field" to "email")),
                mapOf("bool" to mapOf(
                    "must_not" to listOf(
                        mapOf("exists" to mapOf("field" to "operatorStatus")),
                        mapOf("term" to mapOf("operatorStatus" to "EMAIL_INVALID"))
                    )
                ))
            )
            emailDomainsFilter(emailDomains)?.let { filters.add(it) }
            if (!discipline.isNullOrBlank()) {
                filters.add(disciplineFilter(discipline))
            }
            return filters
        }
```

⚠️ **不得**修改 `notContactedWithEmailFilters`、`buildExpertFilters`、`searchExperts`、`aggregate*` 的任何一行（N2a-1 / N2a-2）。

### T2a-4 ES 目标查询接入（I2a-3 / M-1）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

`buildEsFiltersForLevel`（`:1245-1274`）：
- `:1249` → `ExpertSearchService.notContactedWithEmailDomainsFilters(scope.emailDomains, scope.discipline).toMutableList()`
- `:1254` → `ExpertSearchService.emailDomainsFilter(scope.emailDomains)?.let { base.add(it) }`
- `:1261` → 同上

其余读点（`:102`、`:111`、`:1142` 的 scope 构造；`:1130`、`:1146` 的日志串；`:1284`、`:1300` 的 KV 桥接；`BatchSendControlService.kt:575`）按新字段名改写：
- 日志串：`(scope.emailDomains.takeIf { it.isNotEmpty() }?.let { " + domains=" + it.joinToString(",") } ?: "")`
- KV 桥接 `toSnapshot`（`:1284`）：`emailDomains = emailDomain.ifBlank { null }?.let { listOf(it) } ?: emptyList()`
- KV 桥接 `toBatchSendConfig`（`:1300`）：`emailDomain = emailDomains.firstOrNull().orEmpty()`（降级取首个，I2a-6 同款语义）

⚠️ `buildMaterialReminderEsFilters`（`:1102-1128`）是零调用方死代码（主计划 X-1）。它引用 `config.emailDomain`（`BatchSendConfig` KV data class 的字段，**不是**实体字段），该 KV data class 本计划不改，故此函数**无需改动也不会编译失败**。**不要顺手删它**（Out of scope）。

### T2a-5 配置服务映射全集（M-2 / M-3 / I2a-5 / I2a-6）

文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`

| 位置 | 改法 |
|---|---|
| `:72` `create` | `emailDomainsJson = normalized.emailDomainsJson` |
| `:105` `update` | 同上 |
| `:185` `updateLegacyConfig` | **`emailDomains = parseEmailDomains(existing.emailDomainsJson)`**（I2a-6：显式保留，绝不从 `request.emailDomain` 重建） |
| `:200` `updateLegacyConfig` 返回值 | `emailDomain = view.emailDomains.firstOrNull().orEmpty()` |
| `:227` `toLegacyConfig()` | `emailDomain = parseEmailDomains(row.emailDomainsJson).firstOrNull().orEmpty()` |
| `:255` `normalizeAndValidate` | 见下方代码块 |
| `:290` `NormalizedConfig` 构造 | `emailDomainsJson = emailDomainsJson` |
| `:397` `toView()` | `emailDomains = parseEmailDomains(row.emailDomainsJson)` |
| `:488` `ConfigFields` | `val emailDomains: List<String>` |
| `:507` `NormalizedConfig` | `val emailDomainsJson: String` |
| `:525` / `:543` / `:561` 三个 `*Fields()` | `emailDomains = emailDomains`（前两个来自 command）/ `emailDomains = parseEmailDomains(emailDomainsJson)`（第三个来自实体） |

`normalizeAndValidate` 内替换 `:255` 的 `val emailDomain = normalizeOptionalFilter(fields.emailDomain)` 为：

```kotlin
        // I2a-2 / I2a-5：trim、丢空、去重保序；空集合 = 不限。逗号是前端 picker 的
        // 分隔符（K-batch-picker-comma-delimited-contract），含逗号的域名会在回显时被拆坏。
        val emailDomains = fields.emailDomains
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        emailDomains.forEach {
            require(!it.contains(",")) { "emailDomain must not contain a comma: $it" }
        }
        val emailDomainsJson = objectMapper.writeValueAsString(emailDomains)
```

新增私有 helper（照 `parseRegions` / `parseTags` 的既有范式）：

```kotlin
    private fun parseEmailDomains(json: String?): List<String> {
        val text = json?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        return try {
            objectMapper.readValue(text, object : TypeReference<List<String>>() {})
                .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        } catch (e: Exception) {
            log.warn("Failed to parse email_domains_json, treating as unrestricted: {}", e.message)
            emptyList()
        }
    }
```

⚠️ **不得**给 `BatchSendConfig`（`BatchSendSettingService.kt:249/262` 的 KV 兼容 data class）加多值字段 —— M-3 明确要求不把 KV 兼容层拖进变更范围。

### T2a-6 测试（全部不变量）

文件：`src/test/kotlin/.../campaign/service/BatchSendTaskConfigServiceTest.kt`
- `emailDomains = ["a.com","b.com"]` 保存后 `toView().emailDomains` 顺序一致（I2a-1）
- `["  a.com  ", "", "a.com"]` → `["a.com"]`（I2a-5 去空白 + 去重）
- `["a,b.com"]` → 抛 `IllegalArgumentException`（I2a-5）
- `null` / `[]` → `emailDomainsJson == "[]"`，`toView().emailDomains` 为空（I2a-2）
- **`updateLegacyConfig` 保留测试（I2a-6，本计划最关键的一条）**：先建含 `["a.com","b.com"]` 的配置 → 用只含 `emailDomain=""` 的旧 `BatchSendConfigUpdateRequest` 调 `updateLegacyConfig` 只改 cron → 断言 `toView().emailDomains == ["a.com","b.com"]`（未被裁成 1 个、未被清空）
- `toLegacyConfig()` 的 `emailDomain` 等于 `"a.com"`（首个降级）

文件：`src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt`
- `buildEsFiltersForLevel`（经由 `countBySnapshot` 或反射/包内可见性，照该文件既有写法）对 `emailDomains=["a.com","b.com"]` 产出**恰好一个** `bool.should` 项，`should` 长度 2，`minimum_should_match == 1`（I2a-3）
- `emailDomains=[]` 时产出的 filter 列表中**不含**任何 `wildcard` 项（I2a-2）
- `matchesExpert`：`email="x@b.com"` + `emailDomains=["a.com","b.com"]` → true；`email="x@c.com"` → false；`emailDomains=[]` + `email=null` → true（不判定，I2a-2/I2a-4）
- **ES/DB 同口径断言（I2a-4）**：同一组 `emailDomains` 下，`matchesExpert` 对一批构造的 profile 的判定结果，与按 `emailDomainsFilter` 语义手工计算的结果逐条相等

---

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V97__add_email_domains_to_batch_send_task_config.sql` | 新建 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | 修改 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | 修改 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 修改 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | 修改（仅 `:575` 快照构造改名） |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 修改（仅新增 2 个 companion 函数） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | 修改 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | 修改（`:218/:225/:237/:260` 的 `RecipientScope.fromSnapshot` 与 `baseSnapshot(emailDomain = "edu.cn")` 适配） |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 修改（**A5 授权**：仅刷新 `EXCLUDED_NOISE_SITES` 的 11 条行号 —— BatchExecutionModels 107→110 / 243→255，BatchSendTaskConfigService 74→77 / 107→110 / 187→190 / 292→304 / 399→423 / 527→551 / 545→569 / 563→587，ExpertSearchService 345→386。M-5：守卫判定逻辑一行不改） |

文件数：**11**（原 10 上限 + A5 授权放宽 1 个测试文件；执行中若发现仍需改第 12 个文件，**停下来报告**）  子系统数：**2**（campaign / expert，≤2 ✅）

**不改**：`app.js`、`index.html`、`styles.css`、`BatchSendSettingService.kt`、`BatchSendConfigController.kt`、任何已应用迁移。

---

## 验证命令

见主计划「验证命令」节。本计划相关的专用命令：

```bash
# 本计划的两个测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest

# 迁移集成测试（V97 必跑；需本地 Docker）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 写入守卫（M-5）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OperatorStatusWriteSeamGuardTest
```

---

## 验收标准

- **I2a-1**：`grep -rn "email_domain\b" src/main/resources/db/migration/V97*.sql` 命中 DROP 语句；`grep -rn "emailDomain\b" src/main/kotlin/com/weibo/talentintroduction/campaign/` 的结果中不再出现实体字段 `emailDomain`（只允许出现在 KV 兼容层 `BatchSendConfig` 相关的降级映射处，且每处都有 `firstOrNull()`）。`FlywayMigrationIntegrationTest` 绿。
- **I2a-2**：`ManualInitialOutreachServiceTest` 的空集合用例绿；`emailDomainsFilter(emptyList())` 返回 `null`。
- **I2a-3**：`ManualInitialOutreachServiceTest` 的 should/minimum_should_match 形状断言绿；`grep -n "emailDomainsFilter" src/main/kotlin/.../ManualInitialOutreachService.kt` 恰好 **2** 处（`buildEsFiltersForLevel` 的两个非-CANDIDATE 分支），加 `notContactedWithEmailDomainsFilters` **1** 处。
- **I2a-4**：ES/DB 同口径用例绿。
- **I2a-5**：去重/去空白/逗号三条用例绿。
- **I2a-6**：`updateLegacyConfig` 保留用例绿；`grep -n "emailDomains = parseEmailDomains(existing" src/main/kotlin/.../BatchSendTaskConfigService.kt` 有命中（M-2）。
- **N2a-1 / N2a-2**：`git diff src/main/kotlin/.../ExpertSearchService.kt` 只有**新增**行，无删除行、无既有函数签名修改。
- **M-3**：`grep -rn "emailDomains" src/main/kotlin | wc -l` 的输出贴进复验报告，与本计划列出的映射点数量对齐（期望 ≥ 20，逐点核对，不接受"通读印象"，见 `K-plan-quantified-claims-need-grep-receipts`）。
- **M-5**：`OperatorStatusWriteSeamGuardTest` 绿。
- 回归：执行主计划「验证命令」节的全量测试命令通过。

---

## 人工验收清单

> 本计划无 UI。以下用 curl / Postman 直接打接口。`{BASE}` 为服务地址。

### A2a-1: 多域配置保存与回读
- 前置条件：服务已启动，V97 已应用。
- 操作步骤：
  1. `POST {BASE}/api/mail/batch-send/configs`，body 含 `"configName":"多域测试","emailDomains":["gmail.com","outlook.com"]` 及必填的 cron/roundSize 等。
  2. `GET {BASE}/api/mail/batch-send/configs`，找到该条。
- 预期结果：第 2 步返回该配置的 `emailDomains` 为 `["gmail.com","outlook.com"]`（**数组**，顺序一致），且响应中**不含** `emailDomain` 字段。
- 覆盖：O-2、I2a-1

### A2a-2: 多域预估命中数 = 各单域命中数之和（去重后）
- 前置条件：ES 中 CANDIDATE 层有 gmail.com 与 outlook.com 的专家各若干。
- 操作步骤：
  1. `POST {BASE}/api/mail/batch-send/recipients/preview`，snapshot 中 `emailDomains: ["gmail.com"]`，记下 `pending` = P1。
  2. 同上换成 `["outlook.com"]`，记下 P2。
  3. 同上换成 `["gmail.com","outlook.com"]`，记下 P3。
- 预期结果：`P3 == P1 + P2`（一个邮箱不可能同时属于两个域，故无重叠）。若 P3 为 0 而 P1/P2 非 0，即为 I2a-3 被写成 AND 的经典症状。
- 覆盖：I2a-3、IP-1

### A2a-3: 空数组 = 不限
- 前置条件：同上。
- 操作步骤：`POST .../recipients/preview`，`emailDomains: []`；再来一次 `emailDomains` 字段整个省略。
- 预期结果：两次 `pending` 相等，且 **> 0**（等于不加任何服务商筛选时的命中数）。若为 0 即为 I2a-2 被违反。
- 覆盖：I2a-2

### A2a-4: 重试联系人与 ES 同口径（M-1 交互点）
- 前置条件：`expert_contact` 中存在 campaign_code=`MANUAL_OUTREACH` 且状态可重试（NEW / SENT / EMAIL_INVALID）的联系人，其中 A 的邮箱是 `@gmail.com`、B 的是 `@qq.com`。
- 操作步骤：`POST .../recipients/preview`，`emailDomains: ["gmail.com"]`，观察响应的 `retryable`。
- 预期结果：`retryable` 计入 A、**不计入** B。（若 B 也被计入，说明 `matchesExpert` 未接入 → 实际发送会错发给 B。）
- 覆盖：I2a-4、M-1、IP-2

### A2a-5: 回归 —— 旧 typed API 不裁剪多值
- 前置条件：存在一条 `legacy_code = 'INTRODUCTION'` 的配置，先用新接口把它的 `emailDomains` 设为 `["gmail.com","outlook.com"]`。
- 操作步骤：
  1. `PUT {BASE}/api/mail/batch-send/types/INTRODUCTION/config`，body 用旧形状（含 `"emailDomain": ""`），只把 cron 改成 `0 0 7 * * ?`。
  2. `GET {BASE}/api/mail/batch-send/configs` 查该条。
- 预期结果：cron 已变为 `0 0 7 * * ?`；`emailDomains` 仍是 `["gmail.com","outlook.com"]`（**未被清空、未被裁成 1 个**）。
- 覆盖：I2a-6、M-2、IP-3

### A2a-6: 回归 —— 专家列表页单选筛选未受影响
- 前置条件：无。
- 操作步骤：打开专家列表页，用「邮箱服务商」下拉选一个域名。
- 预期结果：列表正常过滤；浏览器网络面板中请求参数仍为 `emailDomain=<单值>`。
- 覆盖：N2a-1、N2a-2、主计划 N-1

### A2a-7: 回归 —— 其余五个筛选维度不变
- 前置条件：无。
- 操作步骤：`POST .../recipients/preview` 分别单独设置 `funnelLevel`、`tags`、`regions`、`discipline`、`operatorStatus`，各跑一次。
- 预期结果：五次命中数与改动前一致（改动前先记录基线）。
- 覆盖：N2a-4
