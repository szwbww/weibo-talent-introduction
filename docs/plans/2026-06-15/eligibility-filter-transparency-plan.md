# 筛选条件透明化 + 自动回复按钮修复 — 开发计划

> 本计划包含两部分：
> 1. **筛选条件透明化**：当前"发现专家（快速）"和"重新验证"两个任务的资格筛选条件完全是黑盒，本计划将筛选条件在前端可视化，并在任务执行过程中分条件统计拒绝原因。
> 2. **自动回复按钮修复**：工具栏"自动回复"按钮当前不可用，存在两个已知 bug。

---

## 一、需求描述

### 现状问题

1. `CandidateEligibilityService.evaluateEligibility()` 检查多个条件（邮箱格式、一次性邮箱、博士学位、年龄、中国国籍、H-Index、引用数、活跃度），但前端完全看不到哪些条件开启了。
2. 任务执行时只汇总 `filtered` 计数，不区分各拒绝原因的分布，无法判断是哪个条件导致大面积过滤。
3. `EmailValidationService.validate()` 单独做的 MX 检查也有拒绝（`emailRejected`），但原因（`NO_MX_RECORD` vs `INVALID_FORMAT` vs `DISPOSABLE_EMAIL`）不透明。

### 目标

1. **筛选条件可视化 + 可编辑**：在"发现专家（快速）"和"重新验证"的启动确认弹窗中，显示当前生效的筛选条件，支持页面上开关各条件，修改后持久化到数据库，所有后续任务（含定时任务）立即生效。
2. **拒绝原因分布统计**：任务执行过程中按拒绝原因分类计数，在任务完成结果和进度详情中展示各原因的过滤数量。

---

## 二、实现方案

### 阶段 1：后端 — 筛选条件持久化到数据库

#### 1.1 数据库迁移

新增 Flyway 迁移 `V<next>__eligibility_filter_settings.sql`，创建单行配置表：

```sql
CREATE TABLE eligibility_filter_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(64) NOT NULL UNIQUE,
    setting_value VARCHAR(255) NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 插入所有筛选条件的初始值（与当前 application.yml 默认值一致）
INSERT INTO eligibility_filter_setting (setting_key, setting_value) VALUES
    ('candidate.requireValidEmail', 'true'),
    ('candidate.requireDoctoralDegree', 'false'),
    ('candidate.excludeChineseNationality', 'true'),
    ('candidate.enableAgeFilter', 'false'),
    ('candidate.maxAgeExclusive', '70'),
    ('academic.enableHIndexFilter', 'false'),
    ('academic.minHIndex', '5'),
    ('academic.enableCitationFilter', 'false'),
    ('academic.minCitationCount', '50'),
    ('academic.enableActivityFilter', 'false'),
    ('academic.recentYearsThreshold', '5'),
    ('email.enableMxCheck', 'true');
```

#### 1.2 Repository + Domain

**新增** `EligibilityFilterSetting` domain class 和 `EligibilityFilterSettingRepository`：

```kotlin
@Table("eligibility_filter_setting")
data class EligibilityFilterSetting(
    @Id val id: Long? = null,
    val settingKey: String,
    val settingValue: String,
    val updatedAt: LocalDateTime? = null
)

interface EligibilityFilterSettingRepository : CrudRepository<EligibilityFilterSetting, Long> {
    fun findBySettingKey(settingKey: String): EligibilityFilterSetting?
    fun findAll(): List<EligibilityFilterSetting>
}
```

#### 1.3 EligibilityFilterService

**新增服务**：读取 DB 配置并构造与现有 `CandidateFilterProperties` / `AcademicFilterProperties` / `EmailValidationProperties` 相同结构的配置对象。提供 `getAll()` 和 `update(key, value)` 方法。

```kotlin
@Service
class EligibilityFilterService(
    private val repository: EligibilityFilterSettingRepository,
    private val candidateDefaults: CandidateFilterProperties,
    private val academicDefaults: AcademicFilterProperties,
    private val emailDefaults: EmailValidationProperties
) {
    fun getAll(): EligibilityFiltersResponse { ... }
    fun update(key: String, value: String): EligibilityFilterSetting { ... }
    fun getCandidateFilter(): CandidateFilterProperties { ... }
    fun getAcademicFilter(): AcademicFilterProperties { ... }
    fun getEmailValidationConfig(): EmailValidationProperties { ... }
}
```

DB 有值就用 DB 的，DB 没有就 fallback 到 `@ConfigurationProperties` 的默认值。

#### 1.4 修改 CandidateEligibilityService

将构造器注入的 `CandidateFilterProperties` 和 `AcademicFilterProperties` 替换为 `EligibilityFilterService`，每次调用 `evaluateEligibility()` 时从 service 读取最新配置（DB 查询可加缓存，过期时间 1 分钟即可）。

同理修改 `EmailValidationService` 中对 `enableMxCheck` 的读取。

#### 1.5 API

**新增接口**：

- `GET /api/experts/eligibility-filters` — 返回所有筛选条件当前值
- `PUT /api/experts/eligibility-filters` — 批量更新，请求体为 key-value map

```kotlin
@GetMapping("/eligibility-filters")
fun getEligibilityFilters(): ResponseEntity<EligibilityFiltersResponse> {
    return ResponseEntity.ok(eligibilityFilterService.getAll())
}

@PutMapping("/eligibility-filters")
fun updateEligibilityFilters(@RequestBody updates: Map<String, String>): ResponseEntity<EligibilityFiltersResponse> {
    updates.forEach { (key, value) -> eligibilityFilterService.update(key, value) }
    return ResponseEntity.ok(eligibilityFilterService.getAll())
}
```

**DTO** `EligibilityFiltersResponse` 保持不变：

```kotlin
data class EligibilityFiltersResponse(
    val candidateFilter: CandidateFilterView,
    val academicFilter: AcademicFilterView,
    val emailValidation: EmailValidationView
)

data class CandidateFilterView(
    val requireDoctoralDegree: Boolean,
    val requireValidEmail: Boolean,
    val excludeChineseNationality: Boolean,
    val enableAgeFilter: Boolean,
    val maxAgeExclusive: Int
)

data class AcademicFilterView(
    val enableHIndexFilter: Boolean,
    val minHIndex: Int,
    val enableCitationFilter: Boolean,
    val minCitationCount: Int,
    val enableActivityFilter: Boolean,
    val recentYearsThreshold: Int
)

data class EmailValidationView(
    val enableMxCheck: Boolean
)
```

### 阶段 2：后端 — 拒绝原因分布统计

**修改**: `PromotionScanStats`

在 `PromotionScanStats` 中新增 `filterReasons: MutableMap<String, Int>` 字段，记录每个拒绝原因的计数。

**修改**: `ExpertRevalidationService.promoteEligibleRawExperts()`

eligibility 不通过时，累加各 rejectReason 到 `stats.filterReasons`：

```kotlin
if (!eligibility.eligible) {
    stats.filtered++
    for (reason in eligibility.rejectReasons) {
        stats.filterReasons.merge(reason, 1) { a, b -> a + b }
    }
    continue
}
```

email 验证不通过时同理：

```kotlin
if (!emailResult.valid) {
    stats.emailRejected++
    stats.filterReasons.merge("EMAIL:${emailResult.rejectReason}", 1) { a, b -> a + b }
    continue
}
```

**修改**: `TaskProgress` 的 `details` map

在 `progressStore.update()` 调用中将 `filterReasons` 传入 details：

```kotlin
details = mapOf(
    "promoted" to stats.promoted,
    "filtered" to stats.filtered,
    "filterReasons" to stats.filterReasons
)
```

**同样修改 `revalidateCandidates()`**：该方法已有 `stats.demotionReasons`，确认它也被传入 progress details 即可（当前已传入）。

### 阶段 3：前端 — 启动弹窗展示筛选条件

**修改文件**: `app.js`、`index.html`

#### 3.1 HTML

在 `taskModalConfigSection` 中 `taskLaunchDesc` 下方新增一个筛选条件展示区域：

```html
<div id="taskLaunchFiltersRow" hidden>
    <div class="task-modal-filters-panel" id="taskLaunchFiltersPanel"></div>
</div>
```

#### 3.2 JS — preload 加载筛选条件

修改 `taskLaunchConfigs` 中 `RAW_PROMOTION_SCAN` 和 `EXPERT_REVALIDATION` 的配置，增加 `preload` 函数：

```javascript
RAW_PROMOTION_SCAN: {
    title: "发现专家（快速）",
    desc: "将扫描 RAW 层专家，符合筛选条件的将被晋升到 CANDIDATE 层。",
    btnId: "discoverBtn",
    showKeyword: false,
    showMaxPromotions: true,
    showFilters: true,  // 新增
    preload: async () => {
        const filters = await api("/api/experts/eligibility-filters");
        return { desc: "将扫描 RAW 层专家，符合以下筛选条件的将被晋升到 CANDIDATE 层。", canRun: true, filters };
    },
    run: executePromoteRaw
},
```

`EXPERT_REVALIDATION` 同理。

#### 3.3 JS — 渲染可编辑筛选条件面板

新增 `renderFilterPanel(filters)` 函数，将筛选条件渲染为可切换的开关列表。每个布尔条件用 checkbox toggle，数值条件用 inline input：

```javascript
const filterItems = [
    { key: "candidate.requireValidEmail",        label: "要求有效邮箱",  type: "bool" },
    { key: "candidate.requireDoctoralDegree",     label: "要求博士学位",  type: "bool" },
    { key: "candidate.excludeChineseNationality", label: "排除中国国籍",  type: "bool" },
    { key: "candidate.enableAgeFilter",           label: "年龄限制",     type: "bool" },
    { key: "candidate.maxAgeExclusive",           label: "最大年龄",     type: "number", dependsOn: "candidate.enableAgeFilter" },
    { key: "academic.enableHIndexFilter",         label: "H-Index 门槛", type: "bool" },
    { key: "academic.minHIndex",                  label: "最低 H-Index", type: "number", dependsOn: "academic.enableHIndexFilter" },
    { key: "academic.enableCitationFilter",       label: "引用数门槛",   type: "bool" },
    { key: "academic.minCitationCount",           label: "最低引用数",   type: "number", dependsOn: "academic.enableCitationFilter" },
    { key: "academic.enableActivityFilter",       label: "活跃度过滤",   type: "bool" },
    { key: "academic.recentYearsThreshold",       label: "近 N 年有发表", type: "number", dependsOn: "academic.enableActivityFilter" },
    { key: "email.enableMxCheck",                 label: "MX 邮箱验证",  type: "bool" }
];

function renderFilterPanel(filters) {
    // 将嵌套的 DTO 展平为 key→value map
    const flat = flattenFilters(filters);
    return filterItems.map(item => {
        const value = flat[item.key];
        if (item.type === "bool") {
            const checked = value === true || value === "true";
            return `<label class="filter-toggle">
                <input type="checkbox" data-filter-key="${item.key}" ${checked ? "checked" : ""}>
                <span>${escapeHtml(item.label)}</span>
            </label>`;
        } else {
            const parentEnabled = item.dependsOn ? (flat[item.dependsOn] === true || flat[item.dependsOn] === "true") : true;
            return `<label class="filter-number ${parentEnabled ? "" : "filter-disabled"}">
                <span>${escapeHtml(item.label)}:</span>
                <input type="number" data-filter-key="${item.key}" value="${value}" min="1" ${parentEnabled ? "" : "disabled"}>
            </label>`;
        }
    }).join("");
}
```

当用户切换开关或修改数值后，调用 `PUT /api/experts/eligibility-filters` 保存。使用 debounce 避免频繁请求。面板上方加一行提示：「以下配置修改后立即生效，影响所有后续任务。」

#### 3.4 openTaskLaunchModal 修改

在 `openTaskLaunchModal` 中处理 `showFilters` 和 preload 返回的 `filters`：

```javascript
const filtersRow = $("#taskLaunchFiltersRow");
filtersRow.hidden = !config.showFilters;

if (config.preload) {
    // ... 现有逻辑 ...
    const pre = await config.preload();
    // ... 现有逻辑 ...
    if (pre.filters && config.showFilters) {
        $("#taskLaunchFiltersPanel").innerHTML = renderFilterPanel(pre.filters);
        filtersRow.hidden = false;
        bindFilterToggleEvents();  // 绑定开关事件
    }
}
```

`bindFilterToggleEvents()` 对面板内所有 `[data-filter-key]` 元素绑定 `change` 事件，收集变更后调用 `PUT /api/experts/eligibility-filters`。bool toggle 同时控制关联的数值 input 的 disabled 状态。

#### 3.5 CSS

在 `styles.css` 中新增筛选条件面板样式：

```css
.task-modal-filters-panel {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 8px;
    padding: 10px;
    background: var(--bg-secondary);
    border-radius: 6px;
    border: 1px solid var(--border);
}

.filter-tag {
    display: inline-block;
    padding: 3px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-weight: 500;
}

.filter-tag-on {
    background: var(--success-bg, #dcfce7);
    color: var(--success-text, #166534);
    border: 1px solid var(--success-border, #bbf7d0);
}

.filter-tag-off {
    background: var(--bg-tertiary, #f3f4f6);
    color: var(--text-muted);
    border: 1px solid var(--border);
    text-decoration: line-through;
}
```

### 阶段 4：前端 — 任务结果展示拒绝原因分布

**修改**: 任务完成后的通知和弹窗中，如果 `details.filterReasons` 存在且非空，展示各原因的计数。

#### 4.1 进度详情面板

在 `task-modal-runtime.js`（或 `app.js` 中进度渲染部分）中，当任务完成时读取 `progress.details.filterReasons`，渲染为简表：

```
过滤原因分布:
  CHINESE_NATIONALITY: 18,432
  INVALID_EMAIL_FORMAT: 3,215
  NO_DOCTORAL_DEGREE: 1,139
```

使用中文标签映射：

```javascript
const filterReasonLabels = {
    MISSING_ORCID: "缺少 ORCID",
    INVALID_EMAIL_FORMAT: "邮箱格式无效",
    DISPOSABLE_EMAIL: "一次性邮箱",
    NO_DOCTORAL_DEGREE: "无博士学位",
    AGE_EXCEEDED: "超龄",
    CHINESE_NATIONALITY: "中国国籍",
    H_INDEX_TOO_LOW: "H-Index 过低",
    CITATION_COUNT_TOO_LOW: "引用数过低",
    INACTIVE: "近期无发表",
    "EMAIL:NO_MX_RECORD": "邮箱 MX 记录不存在",
    "EMAIL:INVALID_FORMAT": "邮箱格式无效",
    "EMAIL:DISPOSABLE_EMAIL": "一次性邮箱域名",
    "EMAIL:EMPTY_EMAIL": "邮箱为空"
};
```

#### 4.2 渲染位置

在任务弹窗的完成结果区域（`taskModalProgressSection` 底部），以紧凑表格或标签列表形式展示。

---

## 三、文件变更清单

| 文件 | 变更 |
|------|------|
| `V<next>__eligibility_filter_settings.sql` | 新建 `eligibility_filter_setting` 表 + 种子数据 |
| `EligibilityFilterSetting.kt` (新增) | domain class |
| `EligibilityFilterSettingRepository.kt` (新增) | Spring Data JDBC repository |
| `EligibilityFilterService.kt` (新增) | 读写 DB 配置，提供 `getAll()` / `update()` / `getCandidateFilter()` 等 |
| `CandidateEligibilityService.kt` | 注入改为 `EligibilityFilterService`，每次从 DB 读取配置 |
| `EmailValidationService.kt` | `enableMxCheck` 改为从 `EligibilityFilterService` 读取 |
| `ExpertIndexController.kt` | 新增 `GET/PUT /api/experts/eligibility-filters` 端点和 DTO |
| `ExpertRevalidationService.kt` | `promoteEligibleRawExperts` 累加 filterReasons；`revalidateCandidates` 确认 demotionReasons 传入 details |
| `PromotionScanStats.kt` | 新增 `filterReasons: MutableMap<String, Int>` 字段 |
| `index.html` | `taskModalConfigSection` 中新增 `taskLaunchFiltersRow` |
| `app.js` | `renderFilterPanel()`（可编辑开关）、`bindFilterToggleEvents()`、`filterReasonLabels`、修改 `taskLaunchConfigs` 和 `openTaskLaunchModal` |
| `app.js` (自动回复) | Bug 1: `operatorName` 改用登录用户名；Bug 3: 按钮文案简化 |
| `styles.css` | `.task-modal-filters-panel`、`.filter-toggle`、`.filter-number`、`.filter-disabled` |
| `task-modal-runtime.js` | 完成结果区域渲染 filterReasons 分布 |

### 阶段 5：自动回复按钮修复

工具栏"自动回复"按钮（`#bulkAutoReplyBtn`）当前处于不可用状态，存在两个 bug。

#### Bug 1：「请先设置操作员姓名」

**根因**：点击按钮后，`initBulkAutoReply` 中通过 `getConfiguredOperatorName()` 读取 `localStorage.getItem("operatorName")`（app.js:176-178），但整个前端**没有任何 UI** 可以设置这个 localStorage 值，所以永远返回空字符串，导致 app.js:4334-4336 直接 `return`。

**修复方案**：`operatorName` 改为使用当前登录用户名，与其他操作（如 `handleOperatorStatusChange`、`handleIndexLevelChange`）保持一致。

**具体修改** (`app.js`):

```javascript
// app.js:4333 — 将:
const operatorName = getConfiguredOperatorName();
if (!operatorName) {
    showStatus("请先设置操作员姓名", "error");
    return;
}

// 改为:
const operatorName = $("#currentUserDisplay")?.textContent?.trim() || "console";
```

这样直接复用登录时写入 `#currentUserDisplay` 的用户名（app.js:4479-4481 `startAuthenticatedApp` 中设置），与 `handleOperatorStatusChange`（app.js:2952）和 `handleIndexLevelChange`（app.js:2961）中 `localStorage.getItem("operatorName") || "console"` 的 fallback 逻辑对齐。

> **注意**：其他地方用 `localStorage.getItem("operatorName") || "console"` 有 fallback 所以不报错，只有自动回复按钮做了严格检查才暴露了问题。统一改用登录用户名是更合理的方案，但本次只修自动回复按钮，不改动其他已正常工作的地方，避免扩大范围。

#### Bug 3：按钮文案冗余

**现状**：`refreshAutoReplySummary()` 中按钮文案包含操作提示（app.js:4289-4296）：

```javascript
btn.textContent = "自动回复：全部开启 ✓（点击全部关闭）";
btn.textContent = "自动回复：全部关闭（点击全部开启）";
btn.textContent = `自动回复：部分开启 ${enabled}/${total}（点击全部开启）`;
```

括号内的操作提示多余，按钮本身就是可点击的，无需解释。

**修复**：去掉括号内的提示，简化为：

```javascript
btn.textContent = "自动回复：全部开启 ✓";
btn.textContent = "自动回复：全部关闭";
btn.textContent = `自动回复：${enabled}/${total} 开启`;
```

#### Bug 2：「专家列表无法选择」（checkbox 全部 disabled）

**根因**：专家列表中每个条目的 checkbox 渲染逻辑（app.js:1414）：

```javascript
<input type="checkbox" class="expert-select-cb" data-contact-id="${contact.contactId || ""}" ${!contact.contactId ? 'disabled' : ''}>
```

当专家还没被联系过时 `contactId` 为空，checkbox 被 `disabled`。对于 CANDIDATE 层的大量未联系专家，checkbox 全部不可用。

**澄清**：这个 checkbox 其实**与自动回复按钮无关**——它只被"检查回复"功能用于批量选中。自动回复按钮的操作对象是所有有 `contactId` 的专家记录（通过 `/api/expert-contacts/auto-reply/bulk` 接口），不依赖 checkbox 勾选。

**本次不修改 checkbox 逻辑**。checkbox disabled 的行为是正确的：没有 contact 记录的专家无法被"检查回复"。如果用户反馈的"无法选择"指的是点击专家行无反应，那是另一个问题需要单独排查。

---

## 四、不做 / 排除项

- **不修改**现有筛选逻辑本身（条件判断代码不变，只改配置来源从 yml → DB）。
- 筛选条件编辑不做权限控制（所有登录用户都可修改，后续再加角色限制）。

---

## 五、验证要点

1. 启动"发现专家（快速）"弹窗，确认筛选条件面板正确展示所有条件的开/关状态。
2. 在弹窗中关闭"排除中国国籍"开关，确认 DB 已更新，重新打开弹窗确认状态已持久化。
3. 关闭"排除中国国籍"后执行快速扫描，确认 `filtered` 数大幅下降，`promoted` 数上升。
4. 执行快速扫描，确认完成后弹窗中展示拒绝原因分布（注意一个专家可能命中多个原因，所以 reasons 之和 ≥ filtered）。
5. "重新验证"任务同理验证 demotionReasons 展示。
6. 自动回复按钮：登录后点击，确认不再提示"请先设置操作员姓名"，能正常执行批量开关。
7. 自动回复按钮文案：确认显示为简洁的状态文本，无括号内操作提示。

## 修正记录

| 原要求 | 修正后 | 理由 | 来源 |
|--------|--------|------|------|
| ExpertRevalidationService 仅在 evaluateEligibility 中读取配置 | ExpertRevalidationService 额外注入 EligibilityFilterService，在调用 emailValidationService.validate() 前检查 requireValidEmail | 原方案未考虑 revalidateCandidates 和 promoteEligibleRawExperts 在 eligibility 检查前后无条件调用了 emailValidationService.validate()，导致关闭邮箱过滤开关后无效 | fix-1.md P1-1 |
| app.js 进度消息仅设置 messageEl.textContent | escapeHtml(progress.message) + innerHTML 拼接原因表 | progress.message 可能含异常/用户输入，直接拼接 innerHTML 存在 HTML 注入风险 | fix-1.md P1-3 |
| 前端 toggle change 仅 debounce 300ms 发 PUT | 维护 pending promise，启动时 flushFilterSave() 先等 PUT 完成再执行任务；PUT 失败提示用户，不启动 | 用户修改后 300ms 内点击启动会使用旧 DB 配置；保存失败无提示 | fix-1.md P1-2 |
| 文件变更清单不含 ExpertRevalidationService constructors | ExpertRevalidationService 新增 eligibilityFilterService 构造参数 | P1-1 修复需要读取 requireValidEmail | fix-1.md P1-1 |
