# P4a：邮件模版门禁过滤开关（后端）

主计划：`batch-task-filters-main.md`
前置计划：**P3a 必须已合并**（`V98` 已占用，本计划用 `V99`；`buildEsFiltersForLevel` 已是多值形态）
子系统数：2（campaign / expert）  文件数：10

---

## 需求描述

### Observable outcome

配置与快照接受 `gateFilterEnabled: boolean`（默认 `false`）。为 `true` 时，收件范围额外要求专家具备该任务所选模板 `required_keys` 对应的 ES 字段（多个字段之间取 **且**）；ES 新目标与 MySQL 重试联系人两条来源同口径生效。

### What must NOT change

- **N4a-1** `gateFilterEnabled = false`（默认，也是所有存量配置的回填值）时，ES 查询与 `matchesExpert` 的行为与改动前**逐字相同**。
- **N4a-2** 发送路径门禁 `PersonalizationGateService.evaluate` 及其两个调用点（`IntroductionMailComposer.kt:28-29`、`ManualExpertMailService.kt:230-232`）一行不改。本计划只做**预筛**，不改**拦截**。
- **N4a-3** `MailComposeTemplateService.effectiveRequiredKeys` / `requiredEsFields`（`:140` / `:149`）、`GET /api/compose-templates/{id}/gate-fields`（`MailComposeTemplateController.kt:51`）一行不改。
- **N4a-4** `ExpertSearchService.ALLOWED_HAS_FIELDS`（`:24`）、`BLANK_EXCLUDABLE_FIELDS`（`:32`）、`fieldPresenceFilter`（`:41`）的**取值与判定逻辑**不变（仅把 `fieldPresenceFilter` 的可见性从 `private` 提升，见 T4a-3）。
- **N4a-5** `PendingOutreachSummary` 的 DTO 形状不变（`pending` / `retryable` / `totalSendable`）；`POST /api/mail/batch-send/recipients/preview` 的签名不变（M-4）。
- **N4a-6** 其余六个筛选维度行为不变。

### Out of scope

- 前端 —— 归 P4b。
- 给 `mail_compose_template.required_keys` 写 seed / 回填（主计划 Out of scope）。
- 把 `ALLOWED_HAS_FIELDS` 扩到 `ES_FIELD_BY_KEY` 的全部字段（见 I4a-3 的显式降级决策）。
- 专家列表页「按模板门禁」筛选器。

---

## 关键不变量

### Invariant I4a-1: 开关关闭时零行为变化
- Rule: `gateFilterEnabled = false` 或 `templateId` 为 null 或该模板 `requiredEsFields` 为空时，**不向 ES 查询追加任何 filter**，`matchesExpert` **不做任何字段判定**。
- Applies to: `resolveScope`、`buildEsFiltersForLevel`、`matchesExpert`。
- Violation consequence: 全部存量任务（V99 回填为 `FALSE`）的收件范围漂移 → 大面积漏发。
- 来源: original

### Invariant I4a-2: 门禁字段之间是 AND
- Rule: N 个门禁 ES 字段产生 N 个独立的 field-presence filter，平铺进 `bool.filter`（AND）。**不是** should。
- Applies to: `buildEsFiltersForLevel`、`matchesExpert`。
- Violation consequence: 门禁语义就是"每个必填变量都得有值"（`PersonalizationGateService.evaluate` 里 `requiredKeys.filter { it in fallbackKeys }` 非空即 blocked —— 任一缺失即拦）。写成 OR 会让只满足一个字段的专家进入队列，发送时被门禁拦下计入失败，等于预筛没起作用。
- 来源: original（推导自 `PersonalizationGateService.kt:44-57` 逐字实现）

### Invariant I4a-3: 预筛是「不弱于实际门禁」的**子集近似**，差额必须可观测
- Rule: `MailComposeTemplateService.requiredEsFields(templateId)` 的返回值可能包含 `ExpertSearchService.ALLOWED_HAS_FIELDS` **之外**的字段。本计划只对交集做预筛，**丢弃**交集外字段，并在 `resolveScope` 处 `log.info` 记录被丢弃的字段名。
- 证据：
  - `ALLOWED_HAS_FIELDS = setOf("employment","degree","institution","researchFields","patentTitles","recentWorkTitles")`（`ExpertSearchService.kt:24`）
  - `ES_FIELD_BY_KEY` 的非 null 值集合（`MailPlaceholderService.kt:141-161`）= `{familyNames, researchFields, institution, keyword, country, employment, hIndex, worksCount, lastPublicationYear, degree, recentWorkTitles, patentTitles}`
  - **差集**（可被 `requiredEsFields` 返回但无法预筛）= `{familyNames, keyword, country, hIndex, worksCount, lastPublicationYear}`
- Applies to: `resolveScope`。
- Violation consequence: 若不丢弃而直接传给 `fieldPresenceFilter`，会命中 `require(field in ALLOWED_HAS_FIELDS)` 抛 `IllegalArgumentException` → 收件预估 500、定时任务执行崩溃。
- 语义后果（必须让运营看见）：预筛后的命中集**仍可能**包含发送时被门禁拦下的专家（因为差集字段没筛）。P4b 必须在 UI 上标注实际参与预筛的字段，不得让运营以为"开了就一个都不会被拦"。
- 先例：前端专家列表的门禁筛选器已是同款降级 —— `app.js:11650-11655` 的 `applyGateFields` 对无对应 chip 的 esField 打日志后忽略。
- 来源: original（本轮新发现，Phase 6 已登记为 `K-gate-esfields-exceed-allowed-hasfields`）

### Invariant I4a-4: 门禁字段的解析只有一个 seam
- Rule: `templateId → requiredEsFields → 交集 → RecipientScope.gateEsFields` 的解析**只允许**发生在 `ManualInitialOutreachService.resolveScope(snapshot)` 一处。该服务内**全部 4 处** `RecipientScope.fromSnapshot(snapshot)` 调用（`:174`、`:426`、`:431`、`:482`）必须改为 `resolveScope(snapshot)`。
- Applies to: `ManualInitialOutreachService`。
- Violation consequence: 预估与执行走不同解析 → M-4 被破坏，预估数与实发数漂移；漏改任一处则该路径的门禁筛选静默失效。
- 来源: M-4 / K-recipient-count-preview-parity

### Invariant I4a-5: `matchesExpert` 的"有值"判定与 ES 的 `fieldPresenceFilter` 同口径
- Rule: 对 `BLANK_EXCLUDABLE_FIELDS`（`researchFields, recentWorkTitles, patentTitles, degree, country`，`ExpertSearchService.kt:32-34`）中的字段，ES 用 `exists AND NOT term ""` —— 即**空串不算有值**。内存侧必须同口径：`String?` 字段用 `!isNullOrBlank()`，`List<String>?` 字段用 `!isNullOrEmpty() && any { it.isNotBlank() }`。非 BLANK_EXCLUDABLE 的字段（`employment`、`institution`）ES 只用 `exists`，内存侧对应 `!= null`（**注意：不是 `isNotBlank`** —— 空串在 ES 里 `exists` 为真）。
- Applies to: `RecipientScope.matchesExpert`。
- Violation consequence: M-1 的两条来源判定不一致 → 重试联系人与新目标口径不同。
- `ExpertProfile` 的对应属性（`ExpertProfile.kt:3-32` 逐字核对）：`employment: String?`（:11）、`degree: String?`（:13）、`researchFields: String?`（:18）、`institution: String?`（:20）、`recentWorkTitles: List<String>?`（:29）、`patentTitles: List<String>?`（:30）—— 6 个字段全部存在。
- 来源: original

### Invariant I4a-6: 新增列必须在 `updateLegacyConfig` 显式保留（M-2）
- Rule: `gateFilterEnabled = existing.gateFilterEnabled`。
- Violation consequence: 旧 typed API 一次调用把开关静默重置为 `false`。
- 来源: M-2

---

## 样式契约

**不适用** —— 本计划零前端文件。

---

## 现状审计

> 表结构与迁移版本见主计划 X-2。**下一个可用版本：V99**（P2a 占 V97、P3a 占 V98）。

### 门禁的既有实现（逐字，改动前基线）

`MailComposeTemplateService.kt:140-152`：

```kotlin
    fun effectiveRequiredKeys(templateId: Long): List<String> {
        val template = findTemplate(templateId)
        return parseRequiredKeys(template.requiredKeys)
    }

    /**
     * Maps [effectiveRequiredKeys] through the variable→ES-field table, dropping
     * keys without an ES field, deduplicated, in stable order.
     */
    fun requiredEsFields(templateId: Long): List<String> =
        effectiveRequiredKeys(templateId)
            .mapNotNull { MailPlaceholderService.ES_FIELD_BY_KEY[it] }
            .distinct()
```

`PersonalizationGateService.kt:44-57` —— 门禁语义的权威定义（I4a-2 的依据）：

```kotlin
    fun evaluate(
        rawTexts: List<String>,
        variables: Map<String, String>,
        requiredKeys: List<String>
    ): PersonalizationGateResult {
        if (requiredKeys.isEmpty()) {
            return PersonalizationGateResult(blocked = false, missingKeys = emptyList())
        }
        ...
        val missing = requiredKeys.filter { it in fallbackKeys }
        return PersonalizationGateResult(blocked = missing.isNotEmpty(), missingKeys = missing)
    }
```

`requiredKeys` 为空 = 门禁关闭（I-4）；任一必填 key 落到兜底即 blocked → **AND 语义**（I4a-2）。

`ExpertSearchService.kt:24-51` —— 字段存在性判定：

```kotlin
        val ALLOWED_HAS_FIELDS = setOf("employment", "degree", "institution", "researchFields", "patentTitles", "recentWorkTitles")

        val BLANK_EXCLUDABLE_FIELDS = setOf(
            "researchFields", "recentWorkTitles", "patentTitles", "degree", "country"
        )

        private fun fieldPresenceFilter(field: String): Map<String, Any> =
            if (field in BLANK_EXCLUDABLE_FIELDS) {
                mapOf(
                    "bool" to mapOf(
                        "must" to listOf(mapOf("exists" to mapOf("field" to field))),
                        "must_not" to listOf(mapOf("term" to mapOf(field to "")))
                    )
                )
            } else {
                mapOf("exists" to mapOf("field" to field))
            }
```

⚠️ `fieldPresenceFilter` 当前是 `private`，需提升可见性（T4a-3），**判定逻辑不改**（N4a-4）。

### 全局事实：`required_keys` 当前全库为空

`V84__add_required_keys_to_compose_template.sql` 注释明写 "No backfill: existing rows stay NULL"；仓库中**没有任何迁移或 seed 给 `required_keys` 赋值**。因此：
- 除非运营在模板界面手工配过，否则任何模板的 `requiredEsFields` 都返回空列表 → 门禁开关打开也**不产生任何 filter**（I4a-1 的第三个条件）。
- 这不是缺陷，是既定事实（`intro-mail-fallback-renders-as-title`）。P4b 必须把这一状态呈现为**置灰不可用**，而不是"开着但无效"。
- 本计划**不**改变它（主计划 Out of scope）。

### 依赖关系

`ManualInitialOutreachService` 的构造器现有 **24 个**依赖（`ManualInitialOutreachService.kt:60-84`），**不含** `MailComposeTemplateService`。本计划新增第 25 个。

> 该服务依赖数已偏高，但拆分不在本轮范围。加依赖是当前唯一能满足 I4a-4「单一解析 seam」的方案：解析必须发生在 preview 与 execution 共同经过的那一层，而 `RecipientScope.fromSnapshot` 是 companion 函数无法注入。

### `RecipientScope.fromSnapshot` 的全部调用点（grep 取证）

```
$ grep -rn "RecipientScope.fromSnapshot" src/main src/test
src/main/.../ManualInitialOutreachService.kt:174:        val scope = RecipientScope.fromSnapshot(snapshot)
src/main/.../ManualInitialOutreachService.kt:426:            val scope = RecipientScope.fromSnapshot(snapshot)
src/main/.../ManualInitialOutreachService.kt:431:            val scope = RecipientScope.fromSnapshot(snapshot)
src/main/.../ManualInitialOutreachService.kt:482:        val scope = RecipientScope.fromSnapshot(snapshot)
src/test/.../BatchSendTaskRuntimeIntegrationTest.kt:218,225,237,260
```

main 侧 **4 处**（I4a-4 要求全改）；test 侧 4 处需同步适配（新增 `gateEsFields` 参数或用新的构造入口）。

### 交互点

| IP | 说明 |
|---|---|
| IP-1 | 配置保存 → `resolveScope` 解析门禁字段 → `buildEsFiltersForLevel`：模板换了、`required_keys` 改了，下一次执行/预估必须重新解析（`resolveScope` 每次调用都查，不缓存） |
| IP-2 | `resolveScope` → `matchesExpert`：两条来源同口径（I4a-5、M-1） |
| IP-3 | 预估 `countBySnapshot`（`:426`/`:431`）与执行（`:174`/`:482`）都走 `resolveScope`：M-4 |
| IP-4 | `requiredEsFields` 返回差集字段 → 被丢弃：日志 + P4b 的 UI 标注，否则运营误判（I4a-3） |

---

## 实现方案

### T4a-1 迁移 V99（I4a-1 / I4a-6）

新建 `src/main/resources/db/migration/V99__add_gate_filter_enabled_to_batch_send_task_config.sql`：

```sql
-- I4a-1: 存量配置一律回填 FALSE，保证行为零漂移。
-- BOOLEAN 列可带 DEFAULT（与 TEXT 不同），故无需 V93 的两步范式。
ALTER TABLE batch_send_task_config
    ADD COLUMN gate_filter_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER template_id;
```

⚠️ 不得含 `${` 字面量（主计划 X-2）；不得编辑已应用迁移。

### T4a-2 实体、配置服务映射（M-2 / M-3 / I4a-6）

- `BatchSendTaskConfig.kt` 4 个 data class 各加 `val gateFilterEnabled: Boolean = false`（位置在 `templateId` 之后，与迁移列序一致）。
- `BatchSendTaskConfigService.kt`：`create` / `update` / `toView()` / `ConfigFields` / `NormalizedConfig` / 三个 `*Fields()` 各加该字段；`normalizeAndValidate` 直接透传（Boolean 无需校验）；`updateLegacyConfig` 写 `gateFilterEnabled = existing.gateFilterEnabled`（I4a-6）。
- **不加** `toLegacyConfig()` 与 `updateLegacyConfig` 返回值（M-3：KV 兼容层不拖进来）。

### T4a-3 ES 字段存在性 filter 提升为可复用（N4a-4 / I4a-2）

文件：`src/main/kotlin/.../expert/service/ExpertSearchService.kt`

1. `fieldPresenceFilter`（`:41`）去掉 `private`，改为 `fun fieldPresenceFilter(field: String): Map<String, Any>` —— **函数体一行不改**（N4a-4）。
2. companion 内新增：

```kotlin
        /**
         * I4a-2: 门禁字段之间是 AND —— 每个字段产出一个独立 filter，由调用方平铺进
         * bool.filter。空集合返回空列表（I4a-1）。
         * 调用方必须已把字段裁剪到 [ALLOWED_HAS_FIELDS] 之内（I4a-3）；此处仍保留
         * require 作为兜底，越界即 fail-fast，不静默忽略。
         */
        fun fieldPresenceFilters(fields: List<String>): List<Map<String, Any>> =
            fields.distinct().map {
                require(it in ALLOWED_HAS_FIELDS) { "Invalid gate ES field: $it" }
                fieldPresenceFilter(it)
            }
```

### T4a-4 快照、scope 与解析 seam（I4a-1 / I4a-3 / I4a-4）

文件：`BatchExecutionModels.kt`
- `BatchExecutionSnapshot` 加 `val gateFilterEnabled: Boolean = false`
- `RecipientScope` 加 `val gateEsFields: List<String> = emptyList()`（**存已解析的字段，不存开关** —— scope 是纯过滤器描述，不该再持有需要外部服务才能解释的布尔）
- `fromSnapshot` **不解析** `gateEsFields`（companion 无 DI），保持默认空 —— 解析在 `resolveScope`
- `toExecutionSnapshot` 透传 `gateFilterEnabled`

文件：`ManualInitialOutreachService.kt`
- 构造器新增第 25 个依赖 `private val mailComposeTemplateService: MailComposeTemplateService`
- 新增私有方法（I4a-3 / I4a-4）：

```kotlin
    /**
     * I4a-4: 门禁字段解析的**唯一** seam。预估与执行共用，保证 M-4 同源。
     * I4a-1: 开关关闭 / 无模板 / 模板无 required_keys → gateEsFields 为空，零行为变化。
     * I4a-3: requiredEsFields 可能返回 ALLOWED_HAS_FIELDS 之外的字段
     * （familyNames/keyword/country/hIndex/worksCount/lastPublicationYear），
     * 这些字段无法做存在性预筛，此处丢弃并记录 —— 预筛因此是子集近似，
     * 仍可能有专家在发送时被门禁拦下。
     */
    private fun resolveScope(snapshot: BatchExecutionSnapshot): RecipientScope {
        val base = RecipientScope.fromSnapshot(snapshot)
        if (!snapshot.gateFilterEnabled) return base
        val templateId = snapshot.templateId ?: return base
        val required = mailComposeTemplateService.requiredEsFields(templateId)
        if (required.isEmpty()) return base
        val usable = required.filter { it in ExpertSearchService.ALLOWED_HAS_FIELDS }
        val dropped = required - usable.toSet()
        if (dropped.isNotEmpty()) {
            log.info(
                "Gate filter: {} of template {} cannot be pre-filtered (not in ALLOWED_HAS_FIELDS), dropped: {}",
                dropped.size, templateId, dropped
            )
        }
        return base.copy(gateEsFields = usable)
    }
```

- **4 处** `RecipientScope.fromSnapshot(snapshot)`（`:174`、`:426`、`:431`、`:482`）全部改为 `resolveScope(snapshot)`（I4a-4）。

### T4a-5 两条目标来源接入（M-1 / I4a-2 / I4a-5）

文件：`ManualInitialOutreachService.kt`，`buildEsFiltersForLevel` 末尾（`regionsFilter` 之后、`return filters` 之前）追加：

```kotlin
        // I4a-2: 门禁字段之间 AND —— 平铺进 filter 数组，不用 should。
        // I4a-1: 空集合时 fieldPresenceFilters 返回空列表，不追加任何项。
        filters.addAll(ExpertSearchService.fieldPresenceFilters(scope.gateEsFields))
```

文件：`BatchExecutionModels.kt`，`matchesExpert` 末尾（`return true` 之前）追加（I4a-5）：

```kotlin
        // I4a-5: 与 ES 的 fieldPresenceFilter 同口径。BLANK_EXCLUDABLE_FIELDS
        // （researchFields / recentWorkTitles / patentTitles / degree）在 ES 侧是
        // `exists AND NOT term ""`，故空串不算有值；employment / institution 只有
        // `exists`，空串在 ES 里算有值，内存侧对应 `!= null`。
        if (gateEsFields.isNotEmpty()) {
            val allPresent = gateEsFields.all { field ->
                when (field) {
                    "employment" -> profile.employment != null
                    "institution" -> profile.institution != null
                    "degree" -> !profile.degree.isNullOrBlank()
                    "researchFields" -> !profile.researchFields.isNullOrBlank()
                    "recentWorkTitles" -> profile.recentWorkTitles?.any { it.isNotBlank() } == true
                    "patentTitles" -> profile.patentTitles?.any { it.isNotBlank() } == true
                    else -> true   // I4a-3 已裁剪，理论不可达；保守放行，不静默排除
                }
            }
            if (!allPresent) return false
        }
```

文件：`BatchSendControlService.kt` —— 快照构造处透传 `gateFilterEnabled`。

### T4a-6 测试

`BatchSendTaskConfigServiceTest.kt`
- `gateFilterEnabled = true` 保存 → `toView().gateFilterEnabled == true`；不传 → `false`
- **M-2 保留用例**：先设 `true` → 旧 typed API 只改 cron → 断言仍为 `true`

`ManualInitialOutreachServiceTest.kt`
- **I4a-1 零漂移用例（最关键）**：`gateFilterEnabled = false` 时，`buildEsFiltersForLevel` 产出的 filter 列表与**不带该字段的基线逐字相等**（硬编码基线）
- `gateFilterEnabled = true` 但 `templateId = null` → 同上零漂移
- `gateFilterEnabled = true` 且模板 `required_keys` 为空 → 同上零漂移
- **I4a-2 AND 用例**：模板 required 解析出 `["institution","researchFields"]` → filter 列表**新增恰好 2 项**，且**不含** `bool.should`
- **I4a-3 裁剪用例**：mock `requiredEsFields` 返回 `["institution","keyword","hIndex"]` → 只新增 1 项（`institution`），且**不抛异常**
- **I4a-4 单 seam 用例**：`countBySnapshot`（预估）与执行路径对同一 snapshot 解析出的 `gateEsFields` 相等
- **I4a-5 同口径用例**：构造 profile 矩阵（各字段分别为 null / 空串 / 空 List / 有值），对每个 `gateEsFields` 组合断言 `matchesExpert` 与 `fieldPresenceFilter` 的 ES 语义逐条一致；特别覆盖 `institution = ""`（ES `exists` 为真 → 内存侧必须 `true`）与 `degree = ""`（ES `must_not term ""` → 内存侧必须 `false`）

`BatchSendTaskRuntimeIntegrationTest.kt`
- 4 处 `RecipientScope.fromSnapshot` 调用适配（新增字段有默认值，预期只需确认编译与既有断言不变）

---

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V99__add_gate_filter_enabled_to_batch_send_task_config.sql` | 新建 |
| 2 | `src/main/kotlin/.../campaign/domain/BatchSendTaskConfig.kt` | 修改 |
| 3 | `src/main/kotlin/.../campaign/domain/BatchExecutionModels.kt` | 修改 |
| 4 | `src/main/kotlin/.../campaign/service/BatchSendTaskConfigService.kt` | 修改 |
| 5 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | 修改（+1 构造依赖、新增 `resolveScope`、4 处调用改写、filter 追加） |
| 6 | `src/main/kotlin/.../campaign/service/BatchSendControlService.kt` | 修改（快照透传） |
| 7 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | 修改（`fieldPresenceFilter` 提升可见性 + 新增 `fieldPresenceFilters`） |
| 8 | `src/test/kotlin/.../campaign/service/BatchSendTaskConfigServiceTest.kt` | 修改 |
| 9 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 |
| 10 | `src/test/kotlin/.../campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | 修改（构造适配） |

文件数：**10**（≤10 ✅，已到上限 —— 执行中若发现需改第 11 个文件，**停下来报告，不要顺手改**）
子系统数：**2** ✅

**不改**：`PersonalizationGateService.kt`、`IntroductionMailComposer.kt`、`ManualExpertMailService.kt`、`MailComposeTemplateService.kt`、`MailComposeTemplateController.kt`、`app.js`、`index.html`、`styles.css`。

---

## 验证命令

见主计划。专用：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskRuntimeIntegrationTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
```

---

## 验收标准

- **I4a-1**：三条零漂移用例绿（硬编码基线断言）。
- **I4a-2**：AND 用例绿；`grep -n "fieldPresenceFilters" src/main/kotlin/.../ManualInitialOutreachService.kt` 恰好 **1** 处，且上下文是 `filters.addAll(...)` 而非 `should`。
- **I4a-3**：裁剪用例绿；`grep -n "ALLOWED_HAS_FIELDS" src/main/kotlin/.../ManualInitialOutreachService.kt` 有命中（裁剪确实发生在 `resolveScope`）。
- **I4a-4**：`grep -rn "RecipientScope.fromSnapshot" src/main/kotlin` 恰好 **1** 处（只剩 `resolveScope` 内部那一处）—— **贴 grep 输出**（`K-plan-quantified-claims-need-grep-receipts`）。单 seam 用例绿。
- **I4a-5**：同口径矩阵用例绿，含 `institution = ""` 与 `degree = ""` 两个边界。
- **I4a-6**：M-2 保留用例绿；`grep -n "gateFilterEnabled = existing.gateFilterEnabled" src/main/kotlin/.../BatchSendTaskConfigService.kt` 有命中。
- **N4a-2**：`git diff --stat` 不含 `PersonalizationGateService.kt` / `IntroductionMailComposer.kt` / `ManualExpertMailService.kt`。
- **N4a-3**：`git diff --stat` 不含 `MailComposeTemplateService.kt` / `MailComposeTemplateController.kt`。
- **N4a-4**：`git diff src/main/kotlin/.../ExpertSearchService.kt` 中，`fieldPresenceFilter` 只有可见性关键字变更，函数体无改动行；`ALLOWED_HAS_FIELDS` / `BLANK_EXCLUDABLE_FIELDS` 的字面量集合无改动。
- **N4a-5**：`grep -n "data class PendingOutreachSummary" -A 6 src/main/kotlin` 输出与改动前相同；`BatchSendConfigController.kt:97` 签名未变。
- **M-3**：`grep -rn "gateFilterEnabled\|gateEsFields" src/main/kotlin | wc -l` 贴进复验报告并逐点核对。
- 回归：主计划全量测试命令通过。

---

## 人工验收清单

### A4a-1: 开关关闭时命中数与改动前完全一致（零漂移，最重要）
- 前置条件：**改动前记录基线** —— 对一组固定筛选条件调 `POST .../recipients/preview`，记下 `pending`/`retryable`/`totalSendable` 三个数 B0。
- 操作步骤：新版本上用同一 snapshot（不带 `gateFilterEnabled`，以及显式 `false`）各调一次。
- 预期结果：两次三个数字都与 B0 **完全相等**。
- 覆盖：N4a-1、I4a-1

### A4a-2: 模板无 required_keys 时开关无效果
- 前置条件：选一个 `required_keys` 为 NULL 的模板（当前全库皆是，见现状审计）。
- 操作步骤：`POST .../recipients/preview`，`templateId` 指向该模板，`gateFilterEnabled` 分别为 `false` / `true`，各调一次。
- 预期结果：两次数字**完全相等**。
- 覆盖：I4a-1

### A4a-3: 配了 required_keys 后开关生效且是 AND
- 前置条件：在模板管理界面给某模板配 2 个必填变量，须选 ES 字段落在 `{employment, degree, institution, researchFields, patentTitles, recentWorkTitles}` 内的（如「机构」+「研究方向」）。
- 操作步骤：
  1. `gateFilterEnabled: false` → 记 T0。
  2. `gateFilterEnabled: true` → 记 T1。
  3. 只配 1 个必填变量（机构），再调一次 → 记 T2。
- 预期结果：`T1 <= T2 <= T0`，且 `T1 < T0`（两个字段的 AND 比单字段更严）。若 `T1 == T2` 且明显大于预期，检查是否被写成了 OR。
- 覆盖：O-1、I4a-2、IP-1

### A4a-4: 差集字段被丢弃且有日志（I4a-3）
- 前置条件：给某模板配一个 ES 字段落在差集里的必填变量（如「keyword」或「hIndex」），再配一个可预筛的（如「机构」）。
- 操作步骤：`gateFilterEnabled: true` 调预估，同时观察应用日志。
- 预期结果：
  - 接口**返回 200**（不是 500 —— 若 500 说明差集字段被直接传给了 `fieldPresenceFilter` 触发 `require`）。
  - 日志出现 `Gate filter: 1 of template <id> cannot be pre-filtered ... dropped: [keyword]`。
  - 命中数等于只按「机构」预筛的结果。
- 覆盖：I4a-3、IP-4

### A4a-5: 重试联系人与 ES 同口径
- 前置条件：`MANUAL_OUTREACH` 下有可重试联系人 A（其 ES profile 有 `institution`）与 B（无 `institution`）；模板必填变量含「机构」。
- 操作步骤：`gateFilterEnabled: true` 调预估，观察 `retryable`。
- 预期结果：计入 A、**不**计入 B。
- 覆盖：I4a-5、M-1、IP-2

### A4a-6: 空串边界
- 前置条件：手工准备两个专家：C 的 `institution = ""`（空串）、D 的 `degree = ""`。
- 操作步骤：分别用必填变量「机构」和「学历」调预估。
- 预期结果：C **被计入**（`institution` 非 BLANK_EXCLUDABLE，ES `exists` 为真）；D **不被计入**（`degree` 属 BLANK_EXCLUDABLE，ES `must_not term ""`）。
- 覆盖：I4a-5

### A4a-7: 预估与实发同源
- 前置条件：接 A4a-3，`gateFilterEnabled: true` 的预估数为 T1。
- 操作步骤：用同一 snapshot 执行一次手动批量（`roundSize` 设得足够大，`roundsPerRun=1`），执行结束后看执行日志的 target 数。
- 预期结果：target 数与 T1 一致（允许因执行期间数据变化的小幅差异，但不应出现数量级差异）。
- 覆盖：M-4、IP-3

### A4a-8: 回归 —— 旧 typed API 不重置开关
- 前置条件：`legacy_code='INTRODUCTION'` 的配置，先设 `gateFilterEnabled = true`。
- 操作步骤：`PUT .../types/INTRODUCTION/config` 只改 cron → `GET .../configs` 查该条。
- 预期结果：`gateFilterEnabled` 仍为 `true`。
- 覆盖：I4a-6、M-2

### A4a-9: 回归 —— 发送路径门禁未被改动
- 前置条件：某模板 `required_keys` 含「近期论文」；有一个无 `recentWorkTitles` 的专家。
- 操作步骤：`gateFilterEnabled: false`（关闭预筛）下对该专家手动发一封该模板的信。
- 预期结果：发送被门禁拦下，执行日志/失败原因为 `PERSONALIZATION_INCOMPLETE`（与改动前一致）—— 证明本计划只加预筛，没动拦截。
- 覆盖：N4a-2
