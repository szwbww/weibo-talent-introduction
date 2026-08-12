# 05 · 地区下拉中文化 + 学科「未分类」打通

> 主计划：`batch-send-rhythm-and-filter-00-master.md`
> 覆盖需求：第 4 条、第 5 条
> 依赖：**03 与 04b 已完成**（地区多选控件由它们引入，本计划为其加中文标签层）

## 需求描述

### Observable outcome

1. 全部地区下拉/展示位置显示中文（中国 / 亚洲（日韩）/ 亚洲（其他）/ 欧洲 / 北美洲 / 南美洲 / 非洲 / 大洋洲 / 其他），覆盖三处：专家列表的地区筛选、批量任务配置的地区多选、监控页的「按专家地区分布」表格。
2. 批量任务配置与手动执行的「学科」下拉新增「未分类」选项，选中后能正确筛出 `disciplineCategory` 字段缺失的专家——ES 查询路径与 MySQL 重试路径口径一致。

### What must NOT change

- **地区的传输值、存储值、ES 查询值仍为英文常量**。本计划只改显示标签（主计划 G-1）。
- `CountryContinentMapping.kt` 一字不改（9 个常量、`REGION_ORDER`、`toRegion`、`countriesForRegion`、`esTermVariants`、`allKnownEsTermValues` 全部原样）。
- `ExpertSearchService.disciplineFilter()` 的方法体逐字不变（`:55-65`），只改可见性。
- `ExpertSearchService.ALLOWED_DISCIPLINES`（`:53`）已含 `UNCLASSIFIED`，不改。
- 专家列表的学科下拉（`index.html:529-534`）已有「未分类」option，不改。
- 专家列表 `/api/experts?discipline=UNCLASSIFIED` 的现有行为不变（它本就正确）。
- 地区筛选的命中结果集不变（只换标签，不换查询）。
- 无新增 CSS、无新增 DOM 结构、无新增 class。

### Out of scope

- `BatchSendSettingService.ALLOWED_DISCIPLINES`（`:236` `setOf("", "STEM", "HUMANITIES")`）——旧 typed API 的 KV 兼容层白名单。**本计划刻意不改**，与 02b 确立的「KV 兼容层保持原样」边界一致。后果：通过旧 typed API `PUT /types/{sendType}/config` 传 `discipline = "UNCLASSIFIED"` 会被拒（`:152`），且 `disciplineValue()`（`:196`）会回退为默认值。新控制台走实体 API，不受影响。**此项作为已知限制记录在 A-7。**
- 国家名（`country` 字段原值）的中文化——本计划只做「大区」层级
- 地区筛选改为多选（专家列表仍单选）
- 学科分类体系扩展（仍是 STEM / HUMANITIES / UNCLASSIFIED 三值）

## 关键不变量

### Invariant I-1: 中文只进 label，不进 value
- Rule: 前端地区选项统一为 `{ value: <英文常量>, label: <中文> }` 结构。`option.value`、`params.set("region", ...)` 的实参、批量配置 payload 的 `regions` 数组元素、以及任何发往后端的地区字符串，必须是英文常量原串。中文只出现在 `option.textContent` / chip 文案 / 表格单元格文本。
- Applies to: `app.js` 的 `REGION_LABELS` 映射、`loadRegions()`（`:3895-3921`）、`BATCH_REGION_OPTIONS`（04b 引入）、`renderMonitoringRegionDistribution()`（`:10265-10284`）、`renderBatchConfigRow()` 的收件范围摘要（04b 引入）。
- Violation consequence: 中文串进 `regionFilter()` → `countriesForRegion()` 对未知 region 返回 `emptySet()`（`CountryContinentMapping.kt:265`）→ ES `terms: []` → **命中 0 条且无任何报错**。这是本计划唯一的高危失效模式。
- 来源: 主计划 G-1（K-region-constant-not-display-label）

### Invariant I-2: 标签映射是单一权威，未知值原样回退
- Rule: 全仓只有**一份** `REGION_LABELS` 映射（定义在 `app.js` 顶层常量区），三个展示点全部复用它。查表函数 `regionLabel(value)` 对映射中不存在的值必须**返回原值**而非空串或 `undefined`。
- Applies to: `app.js` 新增的 `REGION_LABELS` 与 `regionLabel()`。
- Violation consequence: 三处各写一份映射 → 后续增删大区时漏改其一；未知值返回空串 → 若后端新增第 10 个大区，下拉里会出现一个无文字的空选项且无法排查。
- 来源: original（K-agg-filter-source-of-truth 的同构教训：同一口径不得在多处各写一套）

### Invariant I-3: UNCLASSIFIED 的过滤实现必须收口到 disciplineFilter()
- Rule: `ExpertSearchService.disciplineFilter()`（`:55-65`）是学科过滤的唯一权威实现。**唯一的活跃 ES term 旁路** `ManualInitialOutreachService.buildEsFiltersForLevel()` 的 else 分支（`:1219`）必须改为调用它。改造后全仓 `mapOf("term" to mapOf("disciplineCategory" to ...))` 的**活跃**出现点应只剩 `disciplineFilter()` 内部一处。
- Applies to: `ManualInitialOutreachService.kt:1219` + `ExpertSearchService.disciplineFilter()` 的可见性。
- Violation consequence: 旁路对 `UNCLASSIFIED` 生成 `term: {disciplineCategory: "UNCLASSIFIED"}`，而该值在 ES 中不存在（`UNCLASSIFIED` 的语义是**字段缺失**）→ 命中 0 条。表现为「界面能选未分类，一封都发不出去且不报错」。
- 来源: K-batch-send-filter-retry-parity（同类复发）

> **更正记录（2026-08-12，写作期自查）**：本不变量初稿把 `ManualInitialOutreachService.buildMaterialReminderEsFilters()`（`:1088`）也列为待修旁路。grep 实证该方法**零调用点，是死代码**：
> ```
> $ grep -rn "buildMaterialReminderEsFilters" --include=*.kt src/ | grep -v "private fun buildMaterialReminderEsFilters"
> （无输出）
> ```
> 材料提醒的发送与统计两条路径实际都经 `buildMaterialReminderSnapshotFromScope()`（`:1120`）的 `:1128` 调用 `buildEsFiltersForLevel()`。
> **处理**：本计划仍对 `:1088` 做同样的一行替换（成本一行，防止该方法未来被复活时重新引入缺陷），但明确它**当前无运行时效果**——因此该项只能由 grep 断言验收（见「验收标准」I-3），**不得**为它编写运行时测试或人工验收项。

### Invariant I-4: 重试路径的 UNCLASSIFIED 判定用「字段为空」而非字符串相等
- Rule: `RecipientScope.matchesExpert()`（`BatchExecutionModels.kt:54`）当前是 `if (!discipline.isNullOrBlank() && profile.disciplineCategory != discipline) return false`。必须改为区分两种语义：
  - `discipline == "UNCLASSIFIED"` → 命中条件是 `profile.disciplineCategory.isNullOrBlank()`
  - 其余 → 保持 `profile.disciplineCategory == discipline`
- Applies to: `RecipientScope.matchesExpert()`。
- Violation consequence: 重试路径下 `disciplineCategory` 为 null 的专家永远不等于字符串 `"UNCLASSIFIED"`，被全量过滤——即使 ES 路径已修好，MySQL `NEW` 重试联系人仍发不出去，且两条路径口径分裂（K-batch-send-filter-retry-parity 的核心反例）。
- 来源: K-batch-send-filter-retry-parity

### Invariant I-5: 配置层白名单必须放行 UNCLASSIFIED
- Rule: `BatchSendTaskConfigService.ALLOWED_DISCIPLINES`（`:473`，当前 `setOf("STEM", "HUMANITIES")`）加入 `"UNCLASSIFIED"`，与 `ExpertSearchService.ALLOWED_DISCIPLINES`（`:53`）取值一致。
- Applies to: `BatchSendTaskConfigService.normalizeAndValidate()`（`:243-247`）。
- Violation consequence: 前端加了 option 但保存时被 422 拒绝，用户看到「discipline must be one of [STEM, HUMANITIES] or ALL/empty」。这是本计划中**最先暴露**的失效点——若只改前端不改白名单，第一次点保存就报错。
- 来源: original（grep `ALLOWED_DISCIPLINES` 实测：全仓 3 处定义，取值互不相同）

## 样式契约

> 本计划**无新增 CSS、无新增 class、无 DOM 结构变更**，仅替换既有元素的文本内容与新增两个 `<option>`。

### S-1：专家列表地区下拉（纯文本替换）
- **复用**：`index.html:523-525` 的 `select#expertRegionFilter`，位于 `label.toolbar-label`（`styles.css:353`，select 无 class）内。选项由 `app.js:3906-3912` 动态生成。
- **新增 CSS**：无。
- **DOM 结构**：不变。仅把 `opt.textContent` 从 `` `${d.region} (${d.count})` `` 改为 `` `${regionLabel(d.region)} (${d.count})` ``；`opt.value = d.region` **保持不变**（I-1）。静态兜底 option `<option value="">全部地区</option>` 已是中文，不改。
- **禁止项**：改 `opt.value`；给 select 加 class；inline style。

### S-2：批量任务配置的地区多选（纯文本替换）
- **复用**：04b 引入的 `.batch-tag-picker` 家族（`styles.css:8856-8985`），本计划**零改动**。
- **新增 CSS**：无。
- **DOM 结构**：不变。仅把 04b 的 `BATCH_REGION_OPTIONS` 中 9 个 `label` 字段从英文改为中文；`value` 字段**逐字不动**（I-1）。04b 已刻意把选项设计为 `{ value, label }` 双字段，本计划即是其预留的注入点。
- **禁止项**：改 `value`；改 chip / option 的 DOM 模板。

### S-3：监控页「按专家地区分布」表格（纯文本替换）
- **复用**：`app.js:10265-10284` 的 `renderMonitoringRegionDistribution()`，表格容器 `#monitoringRegionDistributionTable`（`index.html:219` 所在 panel），沿用 `.data-table` 既有样式。
- **新增 CSS**：无。
- **DOM 结构**：不变。仅把 `:10277` 的 `<td><strong>${escapeHtml(row.region)}</strong></td>` 改为 `<td><strong>${escapeHtml(regionLabel(row.region))}</strong></td>`。表头 `<th>地区</th>`（`:10272`）已是中文，不改。
- **禁止项**：改列数；改 `<strong>` 结构。

### S-4：学科下拉新增「未分类」option
- **复用**：两处 `select.bsc-input.bsc-select`（`styles.css:5246-5256` + `:5257-5264`），均位于 `label.batch-config-field`（`styles.css:8814-8820`）内。
- **改动前基线**（逐字摘录，grep 实证）：
  - 配置编辑器 `index.html:1196-1203`：
    ```html
    <label class="batch-config-field">
        <span class="batch-config-field-label">学科</span>
        <select id="batchConfigEditorDiscipline" class="bsc-input bsc-select">
            <option value="">全部学科</option>
            <option value="STEM">仅理工科</option>
            <option value="HUMANITIES">仅文社科</option>
        </select>
    </label>
    ```
  - 手动 tab `index.html:1333-1341`（注意它比编辑器多两行 diff 标记元素）：
    ```html
    <label class="batch-config-field" id="manualFieldDiscipline">
        <span class="batch-config-field-label">学科</span>
        <select id="batchManualDiscipline" class="bsc-input bsc-select">
            <option value="">全部学科</option>
            <option value="STEM">仅理工科</option>
            <option value="HUMANITIES">仅文社科</option>
        </select>
        <span class="batch-config-diff-badge" hidden>已修改</span>
        <div class="batch-config-diff-original" hidden></div>
    </label>
    ```
- **新增 CSS**：无。
- **DOM 结构**：两处各在 `<option value="HUMANITIES">仅文社科</option>`（分别是 `:1201` 与 `:1338`）之后追加**一行**：
  ```html
  <option value="UNCLASSIFIED">未分类</option>
  ```
  > 文案取「未分类」，与专家列表既有的 `index.html:533` `<option value="UNCLASSIFIED">未分类</option>` **逐字一致**——该行是本项目对 UNCLASSIFIED 的既定中文文案基线。
- **禁止项**：改 value 大小写；在专家列表（`:533`）重复添加；改动手动 tab 的两行 diff 标记元素（`.batch-config-diff-badge` / `.batch-config-diff-original`）。

## 现状审计

### 地区显示站点（grep `region` in `app.js` 实测全集）

| # | 位置 | 现状 | 本计划 |
|---|---|---|---|
| 1 | `app.js:3906` `filterDropdown.innerHTML = '<option value="">全部地区</option>'` | 静态兜底，已中文 | 不改 |
| 2 | `app.js:3909-3910` `opt.value = d.region` / `opt.textContent = \`${d.region} (${d.count})\`` | value 与 label 同源，显示英文 | **改 label，不改 value** |
| 3 | `app.js:10277` `<td><strong>${escapeHtml(row.region)}</strong></td>` | 监控页地区分布表，显示英文 | **改** |
| 4 | `app.js:10216` `["覆盖地区数", activeRegionCount, ...]` | 只有计数，无地区名 | 不改 |
| 5 | 04b 的 `BATCH_REGION_OPTIONS` label | 04b 中 label = value（英文） | **改 label** |
| 6 | 04b 的 `renderBatchConfigRow()` 收件范围摘要 `"地区: " + c.regions.join(", ")` | 英文 | **改为 `c.regions.map(regionLabel).join("、")`** |

**传值站点（一律不改，I-1）**：`app.js:3848`、`:3928`、`:4054`、`:4478`、`:11401`（`params.set("region", ...)` 与读 `#expertRegionFilter.value`）、`:11142`（活跃筛选计数，只判空）、`:11160`（change 监听 id 数组）。

> K-expert-filter-registration-sites 记录了专家筛选控件的**五处注册点**（`loadContacts` 参数、`collectBatchMailContactIds` 参数、筛选摘要文案、`updateFilterBadge` 计数数组、change 监听数组）。本计划**不新增筛选控件**，只改标签，故五处注册点全部无需变更——**唯一例外是第③处「筛选摘要文案」**：若摘要中会渲染地区名，需一并接入 `regionLabel()`。
> **执行前必做**：`grep -n 'parts.push' src/main/resources/static/app.js`，逐条检查是否有地区维度的 push；有则加入 A-6 的接入清单，无则在提交信息中记录「已核对，摘要不含地区」。
> ⚠ 该知识条目的行号在 2026-08-12 复核中被证实漂移千行量级（`.toolbar-label` 记 `:353` 实为 `:431`），**只能当存在性提示，必须 grep 复核**。

### 学科过滤的 6 个待打通点（grep 实测）

| # | 位置 | 现状 | 缺陷 |
|---|---|---|---|
| 1 | `ExpertSearchService.kt:53` `ALLOWED_DISCIPLINES = setOf("STEM","HUMANITIES","UNCLASSIFIED")` | ✅ 正确 | — |
| 2 | `ExpertSearchService.kt:55-65` `disciplineFilter()` | ✅ 正确（`UNCLASSIFIED` → `must_not exists`） | 但是 **`private`**，旁路无法复用 |
| 3 | `ManualInitialOutreachService.kt:1219` `scope.discipline?.let { base.add(mapOf("term" to mapOf("disciplineCategory" to it))) }` | ❌ **活跃旁路** | `UNCLASSIFIED` 命中 0 条。**这是唯一有运行时影响的缺陷点** |
| 4 | `ManualInitialOutreachService.kt:1088` `filters.add(mapOf("term" to mapOf("disciplineCategory" to config.discipline)))` | ⚠️ **死代码**（grep 零调用点，见 I-3 更正记录） | 当前无运行时影响；顺手修正以防复活 |
| 5 | `BatchExecutionModels.kt:54` `profile.disciplineCategory != discipline` | ❌ 直等比较 | 重试路径全量被过滤 |
| 6 | `BatchSendTaskConfigService.kt:473` `ALLOWED_DISCIPLINES = setOf("STEM","HUMANITIES")` | ❌ 白名单缺项 | 保存即 422 |
| 7 | `BatchSendSettingService.kt:236` `ALLOWED_DISCIPLINES = setOf("","STEM","HUMANITIES")` | ❌ 白名单缺项 | **本计划 out of scope**，见 A-7 |

前端下拉现状：

| 位置 | 现状 |
|---|---|
| `index.html:529-534` 专家列表 | ✅ 已有 `<option value="UNCLASSIFIED">未分类</option>`（`:533`） |
| `index.html:1196-1203` 配置编辑器（`#batchConfigEditorDiscipline`） | ❌ 只有 全部/仅理工科/仅文社科（option 在 `:1199-1201`） |
| `index.html:1333-1341` 手动 tab（`#batchManualDiscipline`） | ❌ 同上（option 在 `:1336-1338`） |
| `app.js:13110` 收件范围摘要的学科文案映射 | ❌ 三元链只映射 STEM / HUMANITIES，其余走 `escapeHtml(c.discipline)` → 会显示裸 `UNCLASSIFIED` |
| `app.js:13737-13743` diff 字段值格式化的 `discipline` 分支 | ❌ 实测已确认无 UNCLASSIFIED 分支，末尾 `return String(value)` → 会显示裸 `UNCLASSIFIED`。逐字现状：<br>`if (key === "discipline") {`<br>`    if (!value) return "全部学科";`<br>`    if (value === "STEM") return "仅理工科";`<br>`    if (value === "HUMANITIES") return "仅文社科";`<br>`    return String(value);`<br>`}` |

### 交互点

| # | 写路径 | 读路径 | 处理 |
|---|---|---|---|
| X-1 | 配置编辑器保存 `discipline = "UNCLASSIFIED"` | `BatchSendTaskConfigService.normalizeAndValidate()` 白名单 | I-5：不改白名单则第一次保存就 422 |
| X-2 | `regions_json` 中的英文常量（03 写入） | 前端 chip / 摘要的中文渲染 | I-1、I-2：渲染时查表，存储不变 |
| X-3 | 地区下拉的 `option.value`（英文） | `GET /api/experts?region=X` → `regionFilter()` | I-1：value 中文化即静默命中 0 条 |
| X-4 | 配置的 `discipline = "UNCLASSIFIED"` | ES 路径（#3/#4）与重试路径（#5） | I-3、I-4：两条必须同时改，否则口径分裂 |

## 实现方案

### A-1 `ExpertSearchService.kt`：开放 disciplineFilter（I-3）

将 companion object 中的 `private fun disciplineFilter(discipline: String)`（`:55`）改为 `fun disciplineFilter(discipline: String)`。**方法体一字不改**。`ALLOWED_DISCIPLINES`（`:53`）同步由 `private val` 改为 `val`（供 `BatchSendTaskConfigService` 引用，避免第二份字面量）。

### A-2 `ManualInitialOutreachService.kt`：消灭 term 旁路（I-3）

- `:1219`（**活跃路径，本计划的实质修复**）：`scope.discipline?.let { base.add(mapOf("term" to ...)) }` → `scope.discipline?.let { base.add(ExpertSearchService.disciplineFilter(it)) }`
- `:1088`（**死代码，防复活**）：`filters.add(mapOf("term" to ...))` → `filters.add(ExpertSearchService.disciplineFilter(config.discipline))`（该行已在 `if (config.discipline.isNotBlank())` 内，无需额外判空）

### A-3 `BatchExecutionModels.kt`：重试路径区分语义（I-4）

`RecipientScope.matchesExpert()`（`:53-64`）首行改为：
```kotlin
if (!discipline.isNullOrBlank()) {
    val matched = if (discipline == "UNCLASSIFIED") {
        profile.disciplineCategory.isNullOrBlank()
    } else {
        profile.disciplineCategory == discipline
    }
    if (!matched) return false
}
```
其余三项判定（`emailDomain`、`tags`、03 引入的 `regions`）逐字不变。

### A-4 `BatchSendTaskConfigService.kt`：白名单对齐（I-5）

`:473` 的 `private val ALLOWED_DISCIPLINES = setOf("STEM", "HUMANITIES")` 改为直接引用权威来源：
```kotlin
private val ALLOWED_DISCIPLINES = ExpertSearchService.ALLOWED_DISCIPLINES
```
（避免第二份字面量；若产生循环依赖或分层顾虑，退而写 `setOf("STEM", "HUMANITIES", "UNCLASSIFIED")` 并加注释指向 `ExpertSearchService.kt:53`——**二选一，执行时择定并在提交信息说明**。）

### A-5 `index.html`（S-4）

在 `:1201`（配置编辑器 `#batchConfigEditorDiscipline` 的 `HUMANITIES` option）与 `:1338`（手动 tab `#batchManualDiscipline` 的 `HUMANITIES` option）之后各追加一行：
```html
<option value="UNCLASSIFIED">未分类</option>
```
两处缩进分别与其上一行对齐（编辑器为 28 空格，手动 tab 为 28 空格；执行时以相邻行为准，不要手算）。

### A-6 `app.js`（I-1、I-2、S-1、S-2、S-3）

**新增顶层常量与查表函数**（放在 `BATCH_REGION_OPTIONS` 附近，或已有的常量区）：
```javascript
// 地区显示标签。key 必须与 CountryContinentMapping.REGION_ORDER 的 9 个英文常量逐字一致；
// 这里只影响展示，value/传参/存储一律用英文常量（见 docs/knowledge/expert/K-region-constant-not-display-label.md）。
var REGION_LABELS = {
    "China": "中国",
    "Asia (Japan & Korea)": "亚洲（日韩）",
    "Asia (Other)": "亚洲（其他）",
    "Europe": "欧洲",
    "North America": "北美洲",
    "South America": "南美洲",
    "Africa": "非洲",
    "Oceania": "大洋洲",
    "Other": "其他"
};

function regionLabel(value) {
    if (!value) return "";
    return REGION_LABELS[value] || value;   // 未知值原样回退（I-2）
}
```

**三处展示点接入**：
- `:3910` → `opt.textContent = \`${regionLabel(d.region)} (${d.count})\``（`:3909` 的 `opt.value = d.region` **不动**）
- `:10277` → `<td><strong>${escapeHtml(regionLabel(row.region))}</strong></td>`
- 04b 的 `BATCH_REGION_OPTIONS` 9 项 `label` 改为 `REGION_LABELS[<对应 value>]`（或直接写中文字面量，但**推荐引用 `REGION_LABELS` 以保证 I-2 的单一权威**）
- 04b 的 `renderBatchConfigRow()` 地区摘要 → `c.regions.map(regionLabel).join("、")`

**学科文案**（两处，逐字给出改法）：
- `app.js:13110` 的三元链追加分支。现状：
  ```javascript
  if (c.discipline) scopeParts.push("学科: " + (c.discipline === "STEM" ? "仅理工科" : c.discipline === "HUMANITIES" ? "仅文社科" : escapeHtml(c.discipline)));
  ```
  改为在 `HUMANITIES` 之后、`escapeHtml` 兜底之前插入 `c.discipline === "UNCLASSIFIED" ? "未分类" :`。
- `app.js:13739-13741` 的 `discipline` 分支追加一行。现状：
  ```javascript
  if (key === "discipline") {
      if (!value) return "全部学科";
      if (value === "STEM") return "仅理工科";
      if (value === "HUMANITIES") return "仅文社科";
      return String(value);
  }
  ```
  在 `HUMANITIES` 行之后追加 `if (value === "UNCLASSIFIED") return "未分类";`。**保留** `return String(value)` 兜底（未知值原样回退，与 I-2 的地区处理同构）。

### A-7 测试

**`ManualInitialOutreachServiceTest.kt`** — +4 用例：
- `scope.discipline = "UNCLASSIFIED"` 时 `buildEsFiltersForLevel` 的 **else 分支**产出的 filter 含 `must_not.exists.disciplineCategory`，**不含** `term`（I-3）
- `scope.discipline = "UNCLASSIFIED"` 时 `buildEsFiltersForLevel` 的 **INTRODUCTION+CANDIDATE 分支**（走 `notContactedWithEmailFilters` → 已正确调用 `disciplineFilter`）同样含 `must_not.exists`（回归确认该分支本就正确，未被本计划改坏）
- 重试路径：`disciplineCategory = null` 的专家在 `discipline = "UNCLASSIFIED"` 下**被保留**（I-4）
- 重试路径：`disciplineCategory = "STEM"` 的专家在 `discipline = "UNCLASSIFIED"` 下**被过滤**（I-4）

> **不为 `buildMaterialReminderEsFilters` 写测试**——它是死代码，任何针对它的运行时测试都只是在测试一段不会被执行的分支，属虚假覆盖率。该项由「验收标准」的 grep 断言覆盖。

**`BatchSendTaskConfigServiceTest.kt`** — +2 用例：
- `discipline = "UNCLASSIFIED"` 创建成功，View 返回同值（I-5）
- `discipline = "OTHER_STUFF"` 仍被拒（白名单未被放宽为任意值）

**`batchSendTaskConsoleInteraction.test.js`** — +3 用例：
- `REGION_LABELS` 的 9 个 **key** 逐字等于 9 个英文常量（I-1、I-2，**这条锁住主计划 G-1**）
- `regionLabel("Mars")` 返回 `"Mars"`（未知值原样回退，I-2）
- 编辑器保存时 payload 的 `regions` 数组元素为**英文常量**，即使 UI 显示中文（I-1，**这是本计划最关键的一条断言**）

**`loadContactsFilter.test.js`** — +2 用例：
- `loadRegions()` 生成的 option 中 `value` 为英文、`textContent` 以中文开头（I-1、S-1）
- 选中中文显示的选项后，`loadContacts` 的 `params.get("region")` 为英文常量（I-1）

> **选此文件的依据（grep 实证，非猜测）**：该文件 `:83` 已 `vm.runInContext(extractFn("loadRegions"), sandbox)` 抽取了目标函数；`:257-258` 已有 `/api/experts/regions` 的 stub 返回 `[{ region: "Europe", count: 10 }]`；`:205`/`:227`/`:246`/`:274` 已有 `region=Europe` / `region=Asia` 的断言。新增用例可直接复用既有 sandbox 与 stub，无需新建测试脚手架。

## 变更文件清单

| # | 文件 | 类型 | 改动摘要 |
|---|---|---|---|
| 1 | `src/main/kotlin/.../expert/service/ExpertSearchService.kt` | 修改 | `disciplineFilter` 与 `ALLOWED_DISCIPLINES` 去 `private`；方法体零改动 |
| 2 | `src/main/kotlin/.../campaign/service/ManualInitialOutreachService.kt` | 修改 | 2 条 term 旁路改调 `disciplineFilter()` |
| 3 | `src/main/kotlin/.../campaign/domain/BatchExecutionModels.kt` | 修改 | `matchesExpert` 区分 UNCLASSIFIED 语义 |
| 4 | `src/main/kotlin/.../campaign/service/BatchSendTaskConfigService.kt` | 修改 | 白名单加入 UNCLASSIFIED |
| 5 | `src/main/resources/static/index.html` | 修改 | 2 处学科下拉各加 1 个 option |
| 6 | `src/main/resources/static/app.js` | 修改 | `REGION_LABELS` + `regionLabel()`；3 处地区展示接入；2 处学科文案 |
| 7 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | 修改 | +4 用例 |
| 8 | `src/test/kotlin/.../campaign/service/BatchSendTaskConfigServiceTest.kt` | 修改 | +2 用例 |
| 9 | `src/test/js/batchSendTaskConsoleInteraction.test.js` | 修改 | +3 用例 |
| 10 | `src/test/js/loadContactsFilter.test.js` | 修改 | +2 用例 |

**文件数 10 ≤ 10 ✅　独立子系统 2（专家 ES 检索 / 前端控制台）≤ 2 ✅　新增字段 0 ✅**

> **不得**修改：`CountryContinentMapping.kt`、`BatchSendSettingService.kt`（见 Out of scope）、`styles.css`（本计划零新增样式）、任何迁移文件。

## 验证命令

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。
> 前端 JS 用例的权威门禁是对目标文件的 `node --test` 单跑；`verify.sh` 只跑一个无关文件，不可用作门禁（K-js-test-invocation-surface）。

```bash
# 前端权威门禁（2 个目标测试文件）
node --test \
  src/test/js/batchSendTaskConsoleInteraction.test.js \
  src/test/js/loadContactsFilter.test.js

# 语法检查
node --check src/main/resources/static/app.js

# 后端相关测试类（Surefire 逗号分隔）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest=ManualInitialOutreachServiceTest,BatchSendTaskConfigServiceTest,ExpertSearchServiceTest

# 全量回归
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

**通过判据**：`node --test` 输出 `# fail 0`；`node --check` 无输出且退出码 0；`mvn test` 输出 `Tests run: N, Failures: 0, Errors: 0`；`git diff --check` 无输出。
**来源**：`CLAUDE.md` 的「Commands」章节 + `pom.xml:188-231` + K-js-test-invocation-surface。

## 验收标准

- **I-1**：`batchSendTaskConsoleInteraction.test.js` 的 payload 英文常量断言与 `loadContactsFilter.test.js` 的两个用例通过；grep `app.js` 中 `opt.value = d.region` 仍存在且未被 `regionLabel` 包裹；grep 全仓 `.js` 无 `params.set("region", regionLabel` 类写法。
- **I-2**：grep `app.js` 中 `REGION_LABELS` 定义**恰好 1 处**；`regionLabel("Mars")` 用例通过；grep 无第二份中文地区字面量表。
- **I-3**：执行
  ```bash
  grep -rn 'disciplineCategory" to' --include=*.kt src/main/
  ```
  结果**只剩 `ExpertSearchService.kt` 内 1 处**（`disciplineFilter` 的 `else` 分支）；`ManualInitialOutreachServiceTest` 的两个 ES 用例通过。死代码 `buildMaterialReminderEsFilters` 的修正由本 grep 一并覆盖（它若未改，grep 会多命中 1 行）。
- **I-4**：grep `BatchExecutionModels.kt` 的 `matchesExpert` 含 `isNullOrBlank()` 分支；两个重试路径用例通过。
- **I-5**：grep `BatchSendTaskConfigService.kt` 的 `ALLOWED_DISCIPLINES` 含 `UNCLASSIFIED`（或引用 `ExpertSearchService.ALLOWED_DISCIPLINES`）；两个配置用例通过。
- **S-1/S-2/S-3/S-4**：`git diff --stat -- src/main/resources/static/styles.css` 输出为空（零样式改动）；`git diff -- src/main/resources/static/index.html` 只含 2 行新增 `<option value="UNCLASSIFIED">未分类</option>`，无其他 DOM 变更、无 `style=`、无新 class。
- **不变项**：`git diff --stat -- src/main/kotlin/com/weibo/talentintroduction/expert/domain/CountryContinentMapping.kt` 与 `.../campaign/service/BatchSendSettingService.kt` 输出均为空；`git diff` 确认 `disciplineFilter()` 方法体零改动（仅可见性修饰符）。
- **回归**：执行「验证命令」节的全部命令通过。

## 人工验收清单

### A-1：三处地区展示全部中文
- 前置条件：ES 中有分布在多个大区的专家；监控页有当日发送数据。
- 操作步骤：
  1. 打开「专家」视图，展开地区下拉
  2. 打开「批量邮件任务控制台」→ 定时任务 → 编辑任一任务，展开「地区」多选
  3. 打开监控页，查看「按专家地区分布」表格的第一列
- 预期结果：三处均显示中文——`中国` / `亚洲（日韩）` / `亚洲（其他）` / `欧洲` / `北美洲` / `南美洲` / `非洲` / `大洋洲` / `其他`；**无任何英文大区名残留**；专家列表下拉每项仍带 `(N)` 计数。
- 覆盖：Observable outcome 1；S-1、S-2、S-3

### A-2：中文只是显示层，筛选仍然命中
- 前置条件：ES CANDIDATE 层有 `country = "Germany"` 的专家 ≥ 3。
- 操作步骤：
  1. 打开「专家」视图，地区下拉选「欧洲」
  2. 观察列表命中数
  3. 打开浏览器开发者工具的 Network 面板，查看 `/api/experts` 请求的 query string
- 预期结果：第 2 步命中数 **> 0** 且与本计划上线前选 `Europe` 时**完全相同**；第 3 步 query string 中为 `region=Europe`（**英文**），不是 `region=欧洲`。
- 覆盖：must-NOT-change 第 1 条；I-1；交互点 X-3

### A-3：批量配置的地区多选存英文
- 前置条件：控制台可访问。
- 操作步骤：
  1. 编辑任一定时任务，「地区」多选中勾选「中国」与「欧洲」
  2. 保存
  3. 执行 `SELECT regions_json FROM batch_send_task_config WHERE id = <id>;`
  4. 重新打开编辑器
- 预期结果：第 3 步的值为 `["China","Europe"]`（**英文常量**，不是中文）；第 4 步 chip 显示「中国」「欧洲」两个中文标签。
- 覆盖：I-1；交互点 X-2

### A-4：学科「未分类」可选可保存
- 前置条件：控制台可访问。
- 操作步骤：
  1. 编辑任一定时任务，「学科」下拉展开
  2. 选择「未分类」并保存
  3. 执行 `SELECT discipline FROM batch_send_task_config WHERE id = <id>;`
  4. 切到「手动执行」tab，展开其「学科」下拉
- 预期结果：第 1 步下拉有 4 项（全部学科 / 仅理工科 / 仅文社科 / **未分类**）；第 2 步保存成功，**不出现 422 或「discipline must be one of」错误**；第 3 步值为 `UNCLASSIFIED`；第 4 步手动 tab 下拉同样有「未分类」。
- 覆盖：Observable outcome 2；I-5；交互点 X-1

### A-5：未分类真的能发出邮件（ES 路径 + 重试路径）
- 前置条件：ES CANDIDATE 层有 ≥ 3 位 `disciplineCategory` 字段**缺失**的未联系专家（有邮箱）；另有 ≥ 3 位 `disciplineCategory = "STEM"` 的；MySQL 有一个 `disciplineCategory` 缺失的 `NEW` 重试联系人和一个 `STEM` 的；配置 `discipline = 'UNCLASSIFIED'`、`rounds_per_run = 1`、`round_size = 20`；账号容量充足。
- 操作步骤：
  1. 手动执行该配置，等待结束
  2. 记录成功计数
  3. 逐一核对发出的收件人在 ES 中的 `disciplineCategory`
- 预期结果：成功计数 **> 0**（这是本条的核心——修复前此处必为 0）；全部收件人的 `disciplineCategory` 均为缺失状态，**无一位是 `STEM`**；那位 `disciplineCategory` 缺失的 `NEW` 重试联系人**也在发送名单中**（证明重试路径已修）。
- 覆盖：Observable outcome 2；I-3、I-4；交互点 X-4

### A-6【回归】STEM / 文社科筛选行为不变
- 前置条件：同 A-5。
- 操作步骤：把配置改为 `discipline = 'STEM'`，重置相关联系人后手动执行。
- 预期结果：只发出 `disciplineCategory = "STEM"` 的专家；数量与本计划上线前相同；`disciplineCategory` 缺失的专家不在其中。
- 覆盖：must-NOT-change 第 6 条；I-3、I-4 的非 UNCLASSIFIED 分支

### A-7【已知限制】旧 typed API 不接受 UNCLASSIFIED
- 前置条件：无。
- 操作步骤：调用 `PUT /api/mail/batch-send/types/INTRODUCTION/config`，请求体中 `"discipline": "UNCLASSIFIED"`。
- 预期结果：返回 **4xx**，消息含 `discipline must be one of`。**这是本计划有意保留的行为**（Out of scope 已声明：KV 兼容层白名单不改）。验收人确认该行为存在且新控制台不受影响即可通过本条。
- 覆盖：Out of scope 的显式确认

### A-8【回归】专家列表的学科筛选与地区计数联动不变
- 前置条件：专家列表可访问。
- 操作步骤：
  1. 学科下拉选「未分类」，记录命中数
  2. 再叠加地区「欧洲」，记录命中数与地区下拉各项的 `(N)` 计数
- 预期结果：第 1 步命中数与本计划上线前相同（专家列表本就支持 UNCLASSIFIED）；第 2 步地区下拉的计数联动口径不变——服务商聚合不应用 emailDomain、地区聚合不应用 region（K-agg-filter-source-of-truth）。
- 覆盖：must-NOT-change 第 5、6 条

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
- 实现提交信息：`feat(fast-p): implement 05`；只提交授权文件。

