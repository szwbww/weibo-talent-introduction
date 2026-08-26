# 子计划 02：批量发送研发类型筛选

> **前置**：本计划调用子计划 01 新增的 `ExpertSearchService.expertTypesFilter` 与 `ALLOWED_EXPERT_TYPES`。
> 01 必须先落地合并，本计划才能开始；两者共享 `ExpertSearchService.kt` / `app.js` / `index.html`，禁止并行修改。

## 需求描述

可观察结果：批量发送任务配置（定时配置面板与手动执行面板各一处）新增「研发类型」多选，选中后该任务的收件范围在既有 `sendable=true` 硬门禁之内进一步收窄到所选类型；收件人预估与实际发送目标同步收窄；未选任何类型时表示不限。

必须保持不变：

- `ManualInitialOutreachService.buildEsFiltersForLevel:1323-1325` 的 `expertSendableFilter()` 调用逐字保留；`RecipientScope.matchesExpert`（`BatchExecutionModels.kt:65-69`）的分类硬门禁判定逐字保留。
- MATERIAL_REMINDER 的目标计算、预估、发送零变化。
- 既有 `discipline` / `emailDomains` / `operatorStatuses` / `regions` / `tags` / `gateEsFields` 六个维度行为不变。
- 存量 `batch_send_task_config` 行迁移后语义为「不限」，发送行为与迁移前逐字相同。
- 旧 typed API（`/api/mail/batch-send/types/{sendType}/config`）保存配置时不得把已设置的类型筛选重置。

范围外：

- 专家列表端筛选与展示（子计划 01）。
- 给 `BatchSendConfig`（KV 兼容 data class，`BatchSendSettingService.kt:240`）新增字段；两个 legacy snapshot 构造器（`ManualInitialOutreachService.toSnapshot:1329`、`BatchSendControlService.toLegacySnapshot:564`）保持现状——它们同样不传 `operatorStatuses`，legacy 路径一律降级为「不限」，本轮沿用该既定先例。
- 修改 `BatchSendSettingService.ALLOWED_DISCIPLINES`（`:236`，仍为 `setOf("", "STEM", "HUMANITIES")` 的旧白名单）。
- 把多选 picker 的既有 tag / region / emailDomains / operatorStatuses 实现迁移到别的基座。

## 关键不变量

### Invariant I2-1: 筛选与硬门禁并存且为 AND
- Rule: `expertTypes` 非空时，`buildEsFiltersForLevel` 的返回数组中必须**同时**存在 `expertTypesFilter(...)` 与 `expertSendableFilter()` 两个元素；`matchesExpert` 中类型判定必须在硬门禁判定**之后**执行，且不得改写硬门禁的任一条件。
- Applies to: `ManualInitialOutreachService.buildEsFiltersForLevel`（`:1296-1327`）、`RecipientScope.matchesExpert`（`BatchExecutionModels.kt:62-121`）。
- Violation consequence: 硬门禁额外校验 `version == ExpertClassificationService.VERSION`（`ExpertSearchService.kt:60`），筛选不校验；替代即导致策略升版后旧分类结果继续放行。
- 来源: original

### Invariant I2-2: 两条活体目标来源，缺一即分裂
- Rule: 类型筛选必须同时落在 **且仅落在** 两处：(1) `buildEsFiltersForLevel`（被 `countEsTargets:1262`、`fetchEsPage:1271`、`buildMaterialReminderSnapshotFromScope` 共 3 个调用点复用）；(2) `RecipientScope.matchesExpert`（MySQL NEW 重试联系人内存过滤，调用点 `:1030`）。禁止在 `countBySnapshot`、`countEsTargets`、`fetchEsPage`、`buildRetryableTargets` 中各写一份判定。
- Applies to: 上述两个函数。
- Violation consequence: 预估与执行数字漂移，或重试路径绕过筛选。
- 来源: K-batch-multi-value-filter-seams、K-batch-send-filter-retry-parity、K-recipient-count-preview-parity

### Invariant I2-3: 空集合 = 不限
- Rule: `expertTypesFilter(emptyList())` 返回 `null`（由子计划 01 保证）；`buildEsFiltersForLevel` 用 `?.let { filters.add(it) }` 追加；`matchesExpert` 在 `expertTypes.isEmpty()` 时不做任何判定直接跳过。迁移默认值为 `'[]'`。
- Applies to: `buildEsFiltersForLevel`、`matchesExpert`、`V108` 迁移、`BatchSendTaskConfig.expertTypesJson` 的 Kotlin 默认值。
- Violation consequence: 存量任务全部静默停发。
- 来源: K-batch-multi-value-filter-seams

### Invariant I2-4: JSON 列是唯一事实源
- Rule: `expert_types_json TEXT NOT NULL` 是唯一存储；不得并列保留任何单值列。解析失败按空集合（不限）处理，不抛异常。
- Applies to: `V108` 迁移、`BatchSendTaskConfig.kt`、`BatchExecutionModels.kt` 的 entity→snapshot 解析（`:275-283` 的 `operatorStatuses` 解析块是逐字范式）。
- Violation consequence: 双事实源，旧 typed API 改一次即分叉。
- 来源: K-batch-multi-value-filter-seams

### Invariant I2-5: 旧 typed 适配器必须显式保留新列
- Rule: `BatchSendTaskConfigService.updateLegacyConfig`（`:173-195`）调用全量 `update(...)`，必须显式写 `expertTypes = parseExpertTypes(existing.expertTypesJson)`，加入既有保留集合（`:193` 的 `operatorStatuses` 即先例）。
- Applies to: `BatchSendTaskConfigService.updateLegacyConfig`。
- Violation consequence: 运营从旧界面改任意一个字段，已设置的类型筛选被静默重置为空，无报错。
- 来源: K-batch-config-legacy-adapter-field-preservation

### Invariant I2-6: MATERIAL_REMINDER 零影响
- Rule: 类型筛选只在 `scope.mailType == BatchSendType.INTRODUCTION.name` 时生效，与 `expertSendableFilter()` 的既有条件（`:1323`）同一个 if 分支或等价判定；`matchesExpert` 中同样只在 INTRODUCTION 下判定。
- Applies to: `buildEsFiltersForLevel`、`matchesExpert`。
- Violation consequence: 材料提醒对象是已在沟通的联系人，按研发类型筛会漏发已承诺回材料的人。
- 来源: original

## 样式契约

### S2-1: 定时配置面板的研发类型多选
- 复用：`.batch-config-field`（DOM 约定见现状审计）+ `.batch-tag-picker` 族全部规则块——`.batch-tag-picker`（`styles.css:9219-9223`）、`-control`（`:9224-9236`）、`:focus-within -control`（`:9237-9241`）、`-chips`（`:9242-9245`）、`-chip`（`:9246-9259`）、`-chip button`（`:9260-9269`）、`-search`（`:9270-9280`）、`-chevron`（`:9281-9288`）、`-dropdown`（`:9289-9302`）、`-option`（`:9303-9316`）、`-option:hover/.is-selected`（`:9317-9322`）、`-check`（`:9323-9334`）、`.is-selected -check`（`:9335-9339`）、`-empty`（`:9340-9344`）。
  **禁止**新增任何 picker 相关 class 或修改上述规则块。
- 新增：无新增 CSS。
- DOM 结构：插入在 `index.html` 中 `#batchConfigEditorOperatorStatuses` 所在 `.batch-config-field` **之后**。骨架逐字照抄该块，仅替换 id 与文案：

```html
<div class="batch-config-field">
    <span class="batch-config-field-label">研发类型</span>
    <div class="batch-tag-picker" data-tag-picker="batchConfigEditorExpertTypes">
        <div class="batch-tag-picker-control">
            <div id="batchConfigEditorExpertTypesChips" class="batch-tag-picker-chips"></div>
            <input type="search" id="batchConfigEditorExpertTypesSearch" class="batch-tag-picker-search" placeholder="搜索并选择研发类型" autocomplete="off" aria-controls="batchConfigEditorExpertTypesDropdown" aria-expanded="false">
            <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
        </div>
        <input type="hidden" id="batchConfigEditorExpertTypes" value="">
        <div id="batchConfigEditorExpertTypesDropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
    </div>
</div>
```

- 禁止项：外层用 `<label>`（点击 chip 会触发隐式聚焦转移导致下拉立刻收起，来源: K-batch-picker-comma-delimited-contract）；inline style；新增 class；id 命名偏离 `<valueId>` / `<valueId>Chips` / `<valueId>Search` / `<valueId>Dropdown` 四件套。

### S2-2: 手动执行面板的研发类型多选
- 复用：同 S2-1 全部规则块。
- 新增：无新增 CSS。
- DOM 结构：插入在 `index.html` 中 `#manualFieldOperatorStatus` 所在 `.batch-config-field` **之后**，骨架同 S2-1，但外层带 id 且尾部补两个 diff 元素（照 `#manualFieldDiscipline`（`index.html:1448-1457`）的既有结构）：

```html
<div class="batch-config-field" id="manualFieldExpertTypes">
    <span class="batch-config-field-label">研发类型</span>
    <div class="batch-tag-picker" data-tag-picker="batchManualExpertTypes">
        <div class="batch-tag-picker-control">
            <div id="batchManualExpertTypesChips" class="batch-tag-picker-chips"></div>
            <input type="search" id="batchManualExpertTypesSearch" class="batch-tag-picker-search" placeholder="搜索并选择研发类型" autocomplete="off" aria-controls="batchManualExpertTypesDropdown" aria-expanded="false">
            <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
        </div>
        <input type="hidden" id="batchManualExpertTypes" value="">
        <div id="batchManualExpertTypesDropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
    </div>
    <span class="batch-config-diff-badge" hidden>已修改</span>
    <div class="batch-config-diff-original" hidden></div>
</div>
```

- 禁止项：同 S2-1；另禁止遗漏 `.batch-config-diff-badge` / `.batch-config-diff-original` 两个元素（手动面板的「已修改」标记依赖它们）。

## 现状审计

### `batch_send_task_config` 表
- Schema: `BatchSendTaskConfig.kt:10-32`。既有多值列一律为 `TEXT NOT NULL DEFAULT '[]'` 的 JSON：`tagsJson`、`regionsJson`、`emailDomainsJson`、`operatorStatusesJson`（`:22-25`）；单值 `discipline: String?`（`:24`）。**A2 复核（2026-08-26）**：最新迁移为 `V107__strip_controlled_keys_from_program_overview.sql`；V100 已被 `V100__add_task_execution_indexes.sql` 占用（评审时审计前提「最新为 V99」已过期），故新迁移编号 **V108**。
- 迁移范式（`V98__add_operator_statuses_to_batch_send_task_config.sql` 逐字）：MySQL 的 `TEXT` 列不能带 `DEFAULT`，故分两步 `ALTER ... ADD COLUMN ... NOT NULL AFTER <col>` + `UPDATE ... SET ... = '[]'`。
- **Flyway 约束**：本仓库 `application.yml:8-13` 已显式设 `placeholder-replacement: false`，并有回归断言 `UnsubscribeBodyLinkMigrationTest.kt:46`。V108 不含 `${...}`，无新增风险，但**不得在清理 yml 时删除该配置项**（来源: K-flyway-placeholder-replacement）。
- Write paths:
  1. `BatchSendTaskConfigService.create`（`:59` 经 `cmd.toFields()`）
  2. `BatchSendTaskConfigService.update`（`:93` 经 `cmd.toFields()`）
  3. `BatchSendTaskConfigService`（`:133`，启用时 `existing.toFields().copy(autoEnabled = true)`）
  4. `BatchSendTaskConfigService.updateLegacyConfig`（`:173-195`）—— 旧 typed API 适配器，调用全量 `update`
  5. `V108` 迁移（本计划新增）
- Read paths:
  1. `BatchSendTaskConfigService.toView`（`:428-450`，`:446` 解析 `operatorStatusesJson`）→ 前端配置面板
  2. `BatchSendTaskConfigService.toLegacyConfig`（`:225`）→ `BatchSendConfig` KV 兼容层
  3. `BatchExecutionModels.kt:240-300` 的 entity→`BatchExecutionSnapshot` 转换（`:275-283` 是 `operatorStatuses` 的解析块，逐字可仿）
  4. `BatchSendTaskConfigService.toFields`（`:601-620`，entity→`ConfigFields` 走校验）
- Interaction points:
  - 写路径 4（旧 typed API）× 读路径 3（执行路径）：漏保留即静默重置（I2-5）。
  - 写路径 1/2 × 读路径 1：新列不进 `toView` 则前端永远读不到已保存的值。
  - `ConfigFields` 三个构造器（`:563` create、`:582` update、`:601` entity）必须全部加字段，否则校验绕过或字段丢失。

### 目标计算 seam
- `buildEsFiltersForLevel`（`ManualInitialOutreachService.kt:1296-1327`）—— **ES 侧唯一 seam**。3 个调用点：`countEsTargets:1265`、`fetchEsPage:1277`、`buildMaterialReminderSnapshotFromScope:1211`。既有 `discipline` 处理在 `:1302`（notContacted 基座内）与 `:1308`（else 分支），两处均已复用 `ExpertSearchService.disciplineFilter`。硬门禁在 `:1323-1325`。
- `RecipientScope.matchesExpert`（`BatchExecutionModels.kt:62-121`）—— **内存侧唯一 seam**，调用点 `ManualInitialOutreachService.kt:1030`。既有判定顺序：分类硬门禁（`:65-69`）→ `operatorStatuses`（`:72-79`）→ `discipline`（`:80-88`）→ `emailDomains`（`:90-95`）→ `tags`（`:96-99`）→ `regions`（`:100-105`）→ `gateEsFields`（`:110-119`）。
- `countBySnapshot`（`:451-470`）与执行路径共用 `resolveScope`（`:428-443`）→ 同一个 `RecipientScope` → 分别落到上述两个函数。**因此不需要三处分别改**，改这两个函数即四条路径同源。
- `buildMaterialReminderEsFilters`（`:1153-1172`）—— **零调用方，是死代码**。
  2026-08-25 实测：`grep -rn "buildMaterialReminderEsFilters" src/main/kotlin src/test/kotlin` 只命中定义行本身（`:1153`）。
  材料提醒的真实目标构建走 `buildMaterialReminderSnapshotFromScope`，其 `:1211` 调的是
  **同一个 `buildEsFiltersForLevel`**。因此 MATERIAL_REMINDER 的零影响不能靠「不改这个死函数」来保证，
  **必须**靠 `buildEsFiltersForLevel` 内部的 `mailType == INTRODUCTION` 分支判定（I2-6）。（来源: K-recipient-scope-status-filter 的更正段，本轮复核成立）

**对 K-discipline-unclassified-filter-bypasses 的复核更正（Phase 6 须回写）**：该条目声称存在「三条未复用 `disciplineFilter` 的 ES 旁路」与「两处不含 `UNCLASSIFIED` 的白名单」。2026-08-25 实测：
- 三条旁路**均已修复**——`buildEsFiltersForLevel:1308` 已调 `ExpertSearchService.disciplineFilter`；`buildMaterialReminderEsFilters:1165` 已调同一函数；`matchesExpert:81-87` 已有 `UNCLASSIFIED` → `disciplineCategory.isNullOrBlank()` 分支。
- 两处白名单**修复其一**——`BatchSendTaskConfigService.kt:633` 已改为 `val ALLOWED_DISCIPLINES = ExpertSearchService.ALLOWED_DISCIPLINES`（含 `UNCLASSIFIED`）；`BatchSendSettingService.kt:236` 仍为 `setOf("", "STEM", "HUMANITIES")`，使用点在 `:152` 与 `:196`，属 KV 兼容层，本计划范围外。

### 前端样式盘点
- 可复用 class：`.batch-tag-picker` 族 13 个规则块 —— `styles.css:9219-9344`（逐块行号见 S2-1）；`.batch-config-field` / `.batch-config-field-label` / `.batch-config-diff-badge` / `.batch-config-diff-original` —— 见 `index.html:1245-1262` 与 `:1448-1457` 的既有用法。
- 设计基准 token：本计划零新增样式，全部沿用上述规则块，无需另立 token。
- DOM 结构约定：多选字段外层必须是 `<div class="batch-config-field">`（**不是** `<label>`）；picker 四件套 id 为 `<valueId>` / `<valueId>Chips` / `<valueId>Search` / `<valueId>Dropdown`；外壳带 `data-tag-picker="<valueId>"` 供 outside-click 判定。
- JS 注册约定（`app.js` 实测）：多选 picker 已有通用基座与注册表——
  - `BATCH_MULTI_PICKER_REGISTRY`（`:14450-14470`），每个 valueId 一项 `{ options, emptyText, previewKind }`
  - `bindBatchMultiPicker(valueId)`（调用点 `:15965-15966`）
  - `readBatchMultiPickerValue(valueId)`（调用点 `:14822`、`:14964`、`:15180`）
  - `setBatchMultiPickerValue(valueId, arr)`（调用点 `:14082`、`:15096`）
  - 选项来源函数范式：`batchOperatorStatusOptions()`（`:14474-14478`）从既有常量派生，`value` 为英文枚举名、`label` 仅用于展示。
  **因此新增维度只需注册，不需实现第三份 picker。**
- 改动前基线（`index.html:1254-1262`，定时配置面板的专家状态 picker，S2-1 将照抄其结构）：

```html
<div class="batch-config-field">
    <span class="batch-config-field-label">专家状态</span>
    <div class="batch-tag-picker" data-tag-picker="batchConfigEditorOperatorStatuses">
        <div class="batch-tag-picker-control">
            <div id="batchConfigEditorOperatorStatusesChips" class="batch-tag-picker-chips"></div>
            <input type="search" id="batchConfigEditorOperatorStatusesSearch" class="batch-tag-picker-search" placeholder="搜索并选择专家状态" autocomplete="off" aria-controls="batchConfigEditorOperatorStatusesDropdown" aria-expanded="false">
            <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
        </div>
        <input type="hidden" id="batchConfigEditorOperatorStatuses" value="">
```

## 实现方案

### Task 1：迁移（I2-3、I2-4）

新增文件：`src/main/resources/db/migration/V108__add_expert_types_to_batch_send_task_config.sql`

逐字照 V98 的两步范式（不删任何旧列——本维度无旧单值列）：

```sql
-- I2-4: expert_types_json 是唯一事实源；空数组 [] = 不限（与「不追加 filter」等价）。
-- 照 V98 两步范式：TEXT 列不能带 DEFAULT。
ALTER TABLE batch_send_task_config
    ADD COLUMN expert_types_json TEXT NOT NULL AFTER operator_statuses_json;

UPDATE batch_send_task_config SET expert_types_json = '[]';
```

### Task 2：实体与视图（I2-4）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt`

- 实体在 `operatorStatusesJson`（`:25`）之后新增 `val expertTypesJson: String = "[]"`。
- `BatchSendTaskConfigView`（`:34` 起）新增 `val expertTypes: List<String> = emptyList()`。

### Task 3：快照与目标匹配（I2-1、I2-2、I2-3、I2-6）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`

四处：

1. `BatchExecutionSnapshot`（`:10-26`）在 `operatorStatuses`（`:22`）之后新增 `val expertTypes: List<String> = emptyList()`。
2. `RecipientScope`（`:51-60`）同位置新增同名字段。
3. `RecipientScope.fromSnapshot`（`:129-145`）照 `:140` 的 `operatorStatuses` 写法做 `map/trim/filter/distinct`。
4. `matchesExpert`（`:62-121`）：在既有 `operatorStatuses` 判定块（`:72-79`）**之后**、`discipline` 判定块（`:80`）之前插入：

```kotlin
// I2-1/I2-6: 类型筛选是硬门禁之内的可选收窄，只在 INTRODUCTION 下判定；
// 空集合不判定（I2-3）。硬门禁（:65-69）不得被本段替代。
if (mailType == BatchSendType.INTRODUCTION.name && expertTypes.isNotEmpty()) {
    val typeName = profile.expertClassification?.type?.name
    val matched = expertTypes.any {
        if (it == "UNCLASSIFIED") typeName == null else typeName == it
    }
    if (!matched) return false
}
```

5. entity→snapshot 转换（`:240-300`）：照 `:275-283` 的 `operatorStatuses` 解析块逐字仿写 `expertTypes`（`try/catch` 返回 `emptyList()`），并加入 `:284-300` 的 `BatchExecutionSnapshot(...)` 构造。

### Task 4：ES 目标过滤（I2-1、I2-2、I2-3、I2-6）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

`buildEsFiltersForLevel`（`:1296-1327`）：在 `:1323-1325` 的硬门禁 if 块内、`filters.add(ExpertSearchService.expertSendableFilter())` 那一行的**紧邻上方**插入：

```kotlin
ExpertSearchService.expertTypesFilter(scope.expertTypes)?.let { filters.add(it) }
```

要求：
- 放在同一个 `if (scope.mailType == BatchSendType.INTRODUCTION.name)` 块内，自然满足 I2-6。
- `expertSendableFilter()` 那一行**逐字不动**（I2-1）。
- 不修改死代码 `buildMaterialReminderEsFilters`（`:1153-1172`，零调用方）；MATERIAL_REMINDER 的零影响由上述 if 分支保证，不由它保证。
- 不修改 `toSnapshot`（`:1329-1343`）与 `toBatchSendConfig`（`:1345`）——legacy KV 路径按范围外声明保持「不限」。

### Task 5：配置服务（I2-4、I2-5）

修改文件：`src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt`

- `ConfigFields` data class 新增 `expertTypes: List<String> = emptyList()`。
- 三个 `toFields()`（`:563` create、`:582` update、`:601` entity）全部加映射；entity 版用新增的 `parseExpertTypes(expertTypesJson)`（照 `:615` 的 `parseOperatorStatuses` 写法，解析失败返回 `emptyList()`）。
- `normalizeAndValidate`（`:275` 一带）加白名单校验：`require(it in ExpertSearchService.ALLOWED_EXPERT_TYPES)`，**直接引用子计划 01 的常量**，不另建名单（M-2）。另加 `require(!it.contains(","))`（前端隐藏 input 是逗号分隔串，含逗号的值回显时被拆坏，来源: K-batch-picker-comma-delimited-contract）。
- `updateLegacyConfig`（`:173-195`）在 `:193` 的 `operatorStatuses = ...` 之后追加 `expertTypes = parseExpertTypes(existing.expertTypesJson)`（I2-5）。
- `toView`（`:428-450`）在 `:446` 之后追加 `expertTypes = parseExpertTypes(row.expertTypesJson)`。
- `toLegacyConfig`（`:225`）**不加**（范围外：`BatchSendConfig` 不新增字段）。

### Task 6：前端（S2-1、S2-2）

修改文件：`src/main/resources/static/index.html` —— 按 S2-1、S2-2 插入两个 picker 块。

修改文件：`src/main/resources/static/app.js`

- 新增选项来源函数 `batchExpertTypeOptions()`，照 `batchOperatorStatusOptions()`（`:14474-14478`）范式：`value` 为 `ExpertType` 英文枚举名 + `"UNCLASSIFIED"`，`label` 为中文（与子计划 01 的 chip 文案逐字一致）。
- `BATCH_MULTI_PICKER_REGISTRY`（`:14450-14470`）新增两项：`batchConfigEditorExpertTypes`（`previewKind: "editor"`）、`batchManualExpertTypes`（`previewKind: "manual"`）。
- `bindBatchMultiPicker` 调用点（`:15965-15966`）追加两行。
- 读值：`:14822`、`:14964`（editor 保存 payload）、`:15180`（manual payload）各追加 `expertTypes: readBatchMultiPickerValue("...")`。
- 回填：`:14082`（editor 载入）、`:15096`（manual 载入）各追加 `setBatchMultiPickerValue("...", Array.isArray(x.expertTypes) ? x.expertTypes : [])`。
- **不要**把该 id 加进任何 `change` 监听数组——picker 无 `change` 事件；收件预估由 `toggleBatchMultiPickerValue` 内部按 `previewKind` 主动触发（既有基座已实现，来源: K-batch-picker-comma-delimited-contract）。

### Task 7：测试

修改文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`
- `expertTypes` 为空时，`buildEsFiltersForLevel` 输出与改动前逐字相同（I2-3）。
- `expertTypes` 非空时，输出同时含类型 filter 与 `expertSendableFilter()` 的两个 term（I2-1）。
- `mailType = MATERIAL_REMINDER` 且 `expertTypes` 非空时，输出不含类型 filter（I2-6）。
- `matchesExpert`：硬门禁不通过者，无论类型是否命中一律 false（I2-1）；`UNCLASSIFIED` 匹配 `expertClassification == null`；空集合不判定。
- 预估与执行同源：同一 `RecipientScope` 下 `countEsTargets` 与 `fetchEsPage` 使用的 filter 数组相等（I2-2）。

修改文件：`src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt`
- `updateLegacyConfig` 后 `expertTypesJson` 保持原值（I2-5）。
- 越界类型值与含逗号的值均抛校验异常。
- entity→`ConfigFields`→entity 往返不丢值；`expertTypesJson` 为非法 JSON 时解析为空集合（I2-4）。

新增文件：`src/test/js/batchExpertTypeFilter.test.js`
- 注册表含两个新 valueId 且 `previewKind` 正确。
- `readBatchMultiPickerValue` 对空隐藏 input 返回 `[]`。
- editor 与 manual 两个 payload 均含 `expertTypes` 键。
照 `src/test/js/batchSendControls.test.js` 的 DOM stub 范式。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V108__add_expert_types_to_batch_send_task_config.sql` | 新增列（两步范式） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | 实体 + View 各加 1 字段 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | Snapshot / RecipientScope / fromSnapshot / matchesExpert / entity→snapshot 共 5 处 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | ConfigFields + 3×toFields + 校验 + updateLegacyConfig + toView |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | `buildEsFiltersForLevel` 追加一行 |
| 6 | `src/main/resources/static/index.html` | 两个 picker DOM 块 |
| 7 | `src/main/resources/static/app.js` | 选项函数 + 注册表 2 项 + bind 2 行 + 读值 3 处 + 回填 2 处 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 目标过滤与同源测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` | 配置持久化与校验测试 |
| 10 | `src/test/js/batchExpertTypeFilter.test.js` | 新增前端行为测试 |

合计 10 个文件（上限），2 个子系统（后端批量配置与目标计算、前端批量控制台）。

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 / Java 11 Maven 工程，**必须用 JDK 11（zulu-11）**，裸 `mvn` 会构建失败（`CLAUDE.md:7`）。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关后端测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=BatchSendTaskConfigServiceTest

# 空库全量迁移验证（需本机 Docker；默认跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 本计划新增的前端测试
node --test src/test/js/batchExpertTypeFilter.test.js

# 全部前端测试
node --test src/test/js/*.test.js

# 前端语法检查
node --check src/main/resources/static/app.js

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：Maven 退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`；node 退出码 0 且输出含 `# fail 0`；`git diff --check` 无输出。

来源：`CLAUDE.md:5-27`（含 `-DmigrationIt=true` 的用法）；K-js-tests-run-via-exec-plugin；K-flyway-placeholder-replacement（迁移验证范式与 Docker 前提）。

## 验收标准

- I2-1: grep 证明 `buildEsFiltersForLevel` 中 `expertSendableFilter()` 一行逐字未变；单测断言 `expertTypes` 非空时 filter 数组同时含两者；单测断言硬门禁不通过的 profile 在 `matchesExpert` 中恒 false。
- I2-2: grep 证明类型判定在仓库中只出现于 `buildEsFiltersForLevel` 与 `matchesExpert` 两处；单测断言同一 scope 下预估与执行的 filter 数组 `assertEquals` 相等。
- I2-3: 单测断言空集合时 `buildEsFiltersForLevel` 输出与改动前逐字相同；迁移 SQL 文本断言含 `'[]'`。
- I2-4: grep 证明 `batch_send_task_config` 无并列的单值类型列；单测断言非法 JSON 解析为空集合且不抛异常。
- I2-5: 单测断言 `updateLegacyConfig` 前后 `expertTypesJson` 相同。
- I2-6: 单测断言 `mailType = MATERIAL_REMINDER` 时 `buildEsFiltersForLevel` 输出不含类型 filter。
  **不得**以「`buildMaterialReminderEsFilters` 未被修改」作为验收依据——该函数零调用方，改不改都不影响行为；
  材料提醒与 INTRODUCTION 共用 `buildEsFiltersForLevel`（`:1211`），唯一屏障是其中的 `mailType` 判定。
- S2-1 / S2-2: `git diff src/main/resources/static/styles.css` 为空；grep 证明两个新 DOM 块中出现的 class 全部属于 `.batch-tag-picker` 族与 `.batch-config-field` 族，无新 class、无 `style=` 属性；grep 证明外层标签为 `<div>` 而非 `<label>`；grep 证明四件套 id 齐全。
- 回归：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A2-1: 类型筛选收窄收件范围
- 前置条件: 一个 INTRODUCTION 批量任务配置，模板已选，未设置任何类型筛选；CANDIDATE 层同时存在 `sendable=true` 的 `PRODUCTION_RND` 与 `ACADEMIC_RND` 专家。
- 操作步骤: 1. 打开该任务配置，记录收件人预估数字 N0；2. 在「研发类型」中只选「生产研发」；3. 保存并重新读取预估，记为 N1；4. 再加选「学术科研」，记为 N2。
- 预期结果: N1 < N0；N2 > N1 且 N2 ≤ N0；N1 等于用专家列表页按「生产研发」+ 相同其他筛选条件查到的可发信条数。
- 覆盖: I2-1、需求描述第 1 条

### A2-2: 硬门禁未被削弱
- 前置条件: 存在一名 `expertClassification.type = PRODUCTION_RND` 但 `version` 为旧值（非 `rnd-v2-2026`）的 CANDIDATE，有有效邮箱、状态未联系。
- 操作步骤: 1. 在任务中勾选「生产研发」；2. 读取预估；3. 执行一次大小为 1 的批量首发；4. 查该专家的邮件记录。
- 预期结果: 预估不含该专家；无新增 OUTBOUND/INTRODUCTION/SENT 记录。
- 覆盖: I2-1

### A2-3: 未选类型时行为不变（回归）
- 前置条件: 部署前记录某 INTRODUCTION 任务的预估数字 M 与一次执行的实际发送数。
- 操作步骤: 1. 部署含 V108 的版本；2. 不改任何配置；3. 读取同一任务的预估；4. 执行一轮。
- 预期结果: 预估等于 M；实际发送数与部署前同条件下一致。
- 覆盖: I2-3、必须保持不变第 4 条

### A2-4: 材料提醒回归
- 前置条件: 一个 MATERIAL_REMINDER 任务；存在一名带「承诺回复材料」标签、`expertClassification` 缺失的 APPLICATION 联系人。
- 操作步骤: 1. 若面板显示「研发类型」控件，勾选「生产研发」；2. 读取预估；3. 执行一次材料提醒。
- 预期结果: 预估与勾选前相同；该联系人仍被发送。
- 覆盖: I2-6、必须保持不变第 2 条

### A2-5: 跨路径——重试联系人不绕过
- 前置条件: MySQL 中存在两个未发送成功的 NEW 联系人，其 ES profile 分别为 `PRODUCTION_RND` 与 `ACADEMIC_RND`，两者 `sendable` 均为 true。
- 操作步骤: 1. 任务中只勾选「生产研发」；2. 读取预估中的 retryable 数字；3. 执行一轮；4. 查两人的发送记录。
- 预期结果: retryable = 1；只有 `PRODUCTION_RND` 那位收到邮件；`ACADEMIC_RND` 那位无 SENT 记录。
- 覆盖: I2-2、现状审计的 interaction point

### A2-6: 跨路径——旧 typed API 不重置
- 前置条件: 某任务已保存「研发类型 = 生产研发 + 混合研发」。
- 操作步骤: 1. 通过旧界面（`/api/mail/batch-send/types/INTRODUCTION/config`）只修改「每轮条数」并保存；2. 回到新配置面板查看「研发类型」。
- 预期结果: 仍显示「生产研发」「混合研发」两个 chip，未被清空。
- 覆盖: I2-5、必须保持不变第 5 条

### A2-7: 两处面板都可用（UI 目测）
- 操作步骤: 1. 打开定时配置编辑面板，找到「研发类型」；2. 打开手动执行面板，找到「研发类型」；3. 在两处分别点开下拉、搜索「生产」、选中、再点 chip 上的删除按钮；4. 在手动面板改动后观察「已修改」标记。
- 预期结果: 两处控件与紧邻的「专家状态」picker 视觉完全一致（同高度、同圆角、同 chip 样式、同下拉宽度）；点击 chip 或下拉项时下拉**不会立刻收起**（若收起即为 S2-1/S2-2 的 `<label>` 违规）；搜索可过滤；删除按钮可用；手动面板改动后「已修改」标记出现。
- 覆盖: S2-1、S2-2

### A2-8: 预估随选择实时更新
- 操作步骤: 1. 在手动执行面板勾选一个类型；2. 不点保存，观察收件人预估数字。
- 预期结果: 预估数字自动刷新为收窄后的值。若停在旧值不动，即为「picker 无 change 事件、未在 toggle 内触发预估」的缺陷。
- 覆盖: 实现方案 Task 6 的 picker 预估约定

人工验收开始时，从本节导出 `02-batch-send-type-filter-acceptance.md`；不得提前生成。
