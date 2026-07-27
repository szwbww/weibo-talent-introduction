# 专家数据补充 — 前端 UI 改造

> 计划系列: expert-enrichment (2/2)
> 前置: expert-enrichment-backend (Plan 1)
> 后续: 无

---

## 需求描述

**可观测结果**: 专家联系页面支持按学术指标（H-Index、引用数、近 N 年发表）和数据完整度（有职位/学历/机构/研究方向/专利）筛选专家；详情面板改为 sub-tab 结构（学术档案 / 联系详情 / 模板预览），学术档案展示完整学术信息卡片，模板预览展示当前专家可用模板变量及覆盖状态；列表项新增 H-Index 徽章和 enrichment 状态标识；发现专家下拉菜单新增"补充数据"选项，触发后展示任务进度。

**不可变更的行为**:

- 现有筛选器（排序、漏斗层级、显示行数、跟进状态、人工干预、回复模式、标签、邮箱服务商、地区）的位置和功能不变
- `loadContacts()` 的双路径逻辑（ES path vs DB path）不变，新增筛选参数只在 ES path 下传递
- `renderContactListItems()` 现有列表行结构（checkbox + name block + status badge + sub row）保持不变，新元素以追加方式插入
- `loadContactDetail()` 与 `showExpertDetail()` 作为两个独立入口的分工不变（showExpertDetail 为未联系专家、loadContactDetail 为已联系专家）
- `openTaskModal()` / task watcher 模式不变
- 整体视觉风格（Inter 字体、panel-bg 白底、--primary #2563eb、--radius-sm 7px、metadata-card 样式）保持一致
- K-view-registration-triad: 不新增顶部 nav-tab，本计划在现有 contacts view 内部改造

**不在范围内**:

- 后端 API 改造（Plan 1 范围）
- 新增顶层视图 / nav-tab
- 移动端适配优化
- Semantic Scholar 相关 UI

---

## 关键不变量

### Invariant F-1: 新筛选参数仅 ES path 传递

- Rule: `loadContacts()` 中新增的筛选参数（hIndexMin、citationCountMin、recentYears、hasField）只在 ES path（`!useDbContactPath`）分支追加到 URLSearchParams。DB path 分支不传递这些参数，且对应的 filter UI 控件在 DB path 下 disabled + 半透明。
- Applies to: `app.js` — `loadContacts()` 函数
- Violation consequence: DB path API 不支持这些参数，报错或被忽略，用户困惑

### Invariant F-2: ExpertIndexResponse 新字段透传到 contact 对象

- Rule: Plan 1 在 `ExpertIndexResponse` 新增的字段（hIndex、citationCount、lastPublicationYear、researchFields、institution、worksCount、enrichedAt）必须在 `loadContacts()` 的 ES path 中映射到 contact 对象属性。DB path 下这些字段设为 null。
- Applies to: `app.js` — `loadContacts()` ES path 的 `contacts = rawExperts.map(e => ({...}))` 块
- Violation consequence: 列表项和详情面板无法读取学术数据

### Invariant F-3: sub-tab 切换不触发 API 重复请求

- Rule: showExpertDetail / loadContactDetail 加载数据后，sub-tab 切换（学术档案 ↔ 联系详情 ↔ 模板预览）仅操作 DOM 可见性（display/hidden），不重新请求数据。模板预览 tab 首次激活时才懒加载 `/api/experts/template-variables` 一次，后续切换不重复请求。
- Applies to: `app.js` — sub-tab 切换逻辑
- Violation consequence: tab 切换卡顿，API 浪费

### Invariant F-4: 样式增量不覆盖已有类

- Rule: 新增的 CSS 类名使用 `academic-` / `enrichment-` / `tpl-var-` 命名空间前缀，不修改任何已有 CSS 规则的选择器或属性。`.metadata-card`、`.metadata-grid`、`.toolbar-filters` 等现有样式保持不变。
- Applies to: `styles.css`
- Violation consequence: 其他视图样式被意外影响

### Invariant F-5: enrichment 触发复用 discover 下拉与 task modal 模式

- Rule: "补充数据" 入口放在发现专家的下拉菜单中，点击后调用 `POST /api/expert-discovery/enrich`，使用现有 `openTaskModal()` 展示进度。不新增弹窗组件。
- Applies to: `index.html` — `#discoverModeMenu`、`app.js` — `handleDiscoverOption()`
- Violation consequence: UI 模式不一致，增加维护成本

---

## 现状审计

### index.html — contacts view (line 447-561)

- **筛选工具栏** (`.toolbar.contacts-toolbar`):
  - 筛选按钮 `#filterToggleBtn` 控制 `#contactsFilterGroup` 展开/折叠
  - 9 个 `<select>` 筛选器在 `#contactsFilterGroup` 中，均为 `<label class="toolbar-label">` 包裹
  - 操作按钮区 `.toolbar-actions` 含刷新、发现专家（split-button）、批量发送、检查回复等
  - **Write paths**: 筛选器 change 事件触发 `loadContacts()`；discover 按钮触发 `handleDiscoverClick()` / `handleDiscoverOption()`
  - **Read paths**: `loadContacts()` 读取所有 `<select>.value` 组装 URLSearchParams

- **发现专家下拉** (`#discoverModeMenu`):
  - 3 项: 快速晋升、深度发现、重新验证
  - **交互**: 分别调用 `handleDiscoverOption('quick'/'deep'/'revalidate')`
  - **扩展点**: 新增 `<button class="dropdown-item">` + `handleDiscoverOption('enrich')` 分支

- **任务进度条** (`#taskProgressBar`): 已有，在筛选工具栏下方
- **面板布局**: `.contacts-list-panel` (左侧列表) + `.layout-resizer` + `.contact-detail-panel` (右侧详情)

### app.js — loadContacts() (line 2848-3014)

- **双路径**: `useDbContactPath = needsAttention || replyMode`
  - DB path: `/api/expert-contacts?...` → 映射 12 个字段（不含学术数据）
  - ES path: `/api/experts?...` → 映射 12 个字段（同样不含学术数据）
- **ES path 当前参数**: level, size, from, tag, operatorStatus, emailDomain, region, sortBy
- **ES path contact 映射**: orcidId, email, displayName, indexLevel, indexLevelName, contactId, contactStatus, operatorStatus, needsManualAttention, country, employment, keyword, tags, updatedAt
- **缺失**: hIndex, citationCount, lastPublicationYear, researchFields, institution, worksCount, enrichedAt 均未从 API response 映射

### app.js — renderContactListItems() (line 3016-3069)

- **列表行结构**: checkbox → `.expert-content-wrapper` → `.expert-row-main` (name block + status badge) → `.expert-row-sub` (employment + tags)
- **扩展点**: 在 `.expert-row-sub` 中追加 H-Index 徽章和 enrichment 状态标识
- **注意**: `contact.employment || tagsHtml` 条件控制 sub row 显示——新增字段需加入条件

### app.js — showExpertDetail() (line 4609-4714)

- **当前 metadata-grid**: 6 张卡片 — ORCID、国家/国籍、年龄/学历、阶段状态、当前单位(span-all)、专业关键词(span-all)
- **缺失**: H-Index、引用数、发表数、最近发表年、研究方向、机构、近期论文、专利
- **无 sub-tab 结构**: 当前直接渲染所有卡片在一个 `.detail` div 中
- **改造方案**: 将 `.detail` 内的内容拆为 3 个 tab-panel div，用 sub-tab 导航切换

### app.js — loadContactDetail() (line 4927-5130+)

- **当前结构**: profile header → tag editor → mail timeline → meeting schedule → metadata-grid (8+ cards: 阶段状态、推荐下一步、人工处理、ORCID、国家、单位、关键词、人工流转、邮箱别名、阶段历史、文档、操作日志)
- **数据源**: `/api/expert-contacts/{id}` + state.contacts 中的 expert 对象
- **改造方案**: 同 showExpertDetail，将内容分组到 3 个 sub-tab；学术档案从 expert 对象 (state.contacts) 读取；联系详情保留现有全部卡片和邮件时间线；模板预览 tab 懒加载

### app.js — handleDiscoverOption() (line 3719-3727)

- **当前分支**: 'quick' → handlePromoteRaw(), 'revalidate' → handleRevalidateCandidates(), else → handleDiscover()
- **扩展**: 新增 'enrich' 分支 → handleEnrichExperts()

### CSS — 相关规则

- `.metadata-grid` (line 1283): `grid-template-columns: repeat(auto-fill, minmax(160px, 1fr))`
- `.metadata-card` (line 1290): `bg rgba(15,23,42,0.02)`, `border 1px solid var(--panel-border)`, `border-radius var(--radius-sm)`, `padding 10px 12px`
- `.expert-tag` (line 4053): `font-size 11px`, `padding 2px 8px`, `border-radius 999px`
- `.toolbar-label` / `.toolbar-filters`: flex 布局，响应式折叠

---

## 实现方案

### Stage 1: loadContacts 数据透传 (F-2)

**Task 1.1**: ES path 的 contact 对象映射新增 7 个字段：

```javascript
// 在 contacts = rawExperts.map(e => ({...})) 中追加:
hIndex: e.hIndex ?? null,
citationCount: e.citationCount ?? null,
lastPublicationYear: e.lastPublicationYear ?? null,
researchFields: e.researchFields || "",
institution: e.institution || "",
worksCount: e.worksCount ?? null,
enrichedAt: e.enrichedAt || null
```

DB path 的 contact 对象映射同样追加这 7 个字段，值全部为 null/空（DB 接口不返回学术数据）。

- 文件: `app.js` — `loadContacts()` (约 line 2967-2982 ES path、line 2937-2952 DB path)

### Stage 2: 筛选器扩展 (F-1)

**Task 2.1**: `index.html` 在 `#contactsFilterGroup` 末尾（`#expertRegionFilter` 的 `</label>` 之后）新增筛选控件：

```html
<label class="toolbar-label">
    H-Index ≥:
    <input type="number" id="expertHIndexMinFilter" min="0" step="1" placeholder="不限" style="width: 70px;">
</label>
<label class="toolbar-label">
    引用数 ≥:
    <input type="number" id="expertCitationMinFilter" min="0" step="1" placeholder="不限" style="width: 70px;">
</label>
<label class="toolbar-label">
    近 N 年发表:
    <select id="expertRecentYearsFilter">
        <option value="">不限</option>
        <option value="3">近 3 年</option>
        <option value="5">近 5 年</option>
        <option value="10">近 10 年</option>
    </select>
</label>
<label class="toolbar-label">
    数据完整度:
    <select id="expertHasFieldFilter" multiple size="1" style="min-width: 100px;">
        <option value="employment">有职位</option>
        <option value="degree">有学历</option>
        <option value="institution">有机构</option>
        <option value="researchFields">有研究方向</option>
        <option value="patentTitles">有专利</option>
    </select>
</label>
```

- 文件: `index.html` — contacts view 筛选区域 (约 line 519-524 之后)
- 注意: `<input type="number">` 用于 H-Index 和引用数（自由输入），`<select>` 用于年份，`<select multiple>` 用于数据完整度

**Task 2.2**: `app.js` — `loadContacts()` 中读取新筛选器值并追加到 ES path 的 URLSearchParams：

```javascript
// 在 ES path 分支中，params.set("sortBy", sortBy) 之后追加:
const hIndexMin = $("#expertHIndexMinFilter")?.value || "";
const citationMin = $("#expertCitationMinFilter")?.value || "";
const recentYears = $("#expertRecentYearsFilter")?.value || "";
const hasFieldEl = $("#expertHasFieldFilter");
const hasField = hasFieldEl ? Array.from(hasFieldEl.selectedOptions).map(o => o.value) : [];

if (hIndexMin) params.set("hIndexMin", hIndexMin);
if (citationMin) params.set("citationCountMin", citationMin);
if (recentYears) params.set("recentYears", recentYears);
hasField.forEach(f => params.append("hasField", f));
```

DB path 分支中，新增筛选控件 disabled + 半透明处理（与现有 tag/region 的 disable 逻辑对齐）：

```javascript
// 在 useDbContactPath 分支中追加:
["expertHIndexMinFilter", "expertCitationMinFilter", "expertRecentYearsFilter", "expertHasFieldFilter"].forEach(id => {
    const el = $(`#${id}`);
    if (el) {
        el.disabled = true;
        el.parentElement.style.opacity = "0.5";
        el.parentElement.title = "学术筛选仅在 ES 查询模式下可用";
    }
});
```

ES path 分支中的恢复逻辑同理。

- 文件: `app.js` — `loadContacts()`

**Task 2.3**: 为新筛选控件注册 change 事件以触发 `loadContacts()`：

在现有筛选器事件绑定区域（搜索 `addEventListener("change"` 找到绑定 `#expertSortBy`、`#expertIndexLevel` 等的位置），追加:

```javascript
["expertHIndexMinFilter", "expertCitationMinFilter", "expertRecentYearsFilter", "expertHasFieldFilter"].forEach(id => {
    const el = $(`#${id}`);
    if (el) el.addEventListener("change", () => { state.contactsPage = 0; loadContacts(); });
});
// number input 需要额外 blur/enter 事件
["expertHIndexMinFilter", "expertCitationMinFilter"].forEach(id => {
    const el = $(`#${id}`);
    if (el) el.addEventListener("keydown", e => { if (e.key === "Enter") loadContacts(); });
});
```

- 文件: `app.js` — 筛选器事件绑定区域

**Task 2.4**: 筛选活跃计数 badge (`#filterActiveCount`) 的逻辑中纳入新筛选器。

搜索 `filterActiveCount` 找到计数逻辑，追加对 `expertHIndexMinFilter`、`expertCitationMinFilter`、`expertRecentYearsFilter`、`expertHasFieldFilter` 的非空检查。

- 文件: `app.js` — `updateFilterActiveCount()` 或等效逻辑

### Stage 3: 列表项增强 (F-2)

**Task 3.1**: `renderContactListItems()` 中在 `.expert-row-sub` 区域追加 H-Index 徽章和 enrichment 状态：

```javascript
// 在 tagsHtml 计算之后追加:
const hIndexBadge = contact.hIndex != null
    ? `<span class="academic-badge academic-hindex" title="H-Index">h ${contact.hIndex}</span>`
    : "";
const enrichedBadge = contact.enrichedAt
    ? `<span class="academic-badge academic-enriched" title="数据已补充 ${escapeHtml(contact.enrichedAt)}">已补充</span>`
    : "";
```

修改 `.expert-row-sub` 的条件和 HTML:

```javascript
// 原条件:
// ${contact.employment || tagsHtml ? `<div class="expert-row-sub">...` : ""}
// 改为:
${contact.employment || tagsHtml || hIndexBadge || enrichedBadge ? `
<div class="expert-row-sub">
    ${contact.employment ? `<span>${escapeHtml(contact.employment)}</span>` : ""}
    ${hIndexBadge}${enrichedBadge}
    ${tagsHtml ? `<span class="expert-row-tags">${tagsHtml}</span>` : ""}
</div>` : ""}
```

- 文件: `app.js` — `renderContactListItems()`

### Stage 4: 详情面板 sub-tab 结构 (F-3)

**Task 4.1**: 定义 sub-tab 渲染辅助函数 `renderDetailSubTabs(activeTab)`：

```javascript
function renderDetailSubTabs(activeTab = "academic") {
    const tabs = [
        { key: "academic", label: "学术档案" },
        { key: "contact", label: "联系详情" },
        { key: "template", label: "模板预览" }
    ];
    return `
    <div class="detail-sub-tabs">
        ${tabs.map(t => `
            <button class="detail-sub-tab ${t.key === activeTab ? "active" : ""}" data-sub-tab="${t.key}">
                ${t.label}
            </button>
        `).join("")}
    </div>`;
}
```

- 文件: `app.js` — 新增函数（放在 `showExpertDetail` 之前）

**Task 4.2**: 改造 `showExpertDetail()` — 在 profile header + tag editor 之后插入 sub-tab 导航，将 metadata-grid 拆为 3 个 tab-panel：

```
<div class="detail">
    [profile header]
    [tag editor]
    [sub-tab 导航]
    <div class="detail-tab-panel" data-panel="academic">
        [学术档案内容 — 新增: H-Index/引用数/发表数/最近发表年卡片 + 研究方向卡片 + 机构卡片 + 近期论文列表 + 专利列表]
    </div>
    <div class="detail-tab-panel" data-panel="contact" hidden>
        [联系详情 — 保留现有: ORCID、国家/国籍、年龄/学历、阶段状态、当前单位、专业关键词]
    </div>
    <div class="detail-tab-panel" data-panel="template" hidden>
        [模板预览 — 占位，首次激活时懒加载]
    </div>
</div>
```

学术档案 panel 内容（新增）:

```javascript
// 学术指标卡片行 (4 列)
`<div class="metadata-grid academic-metrics-row">
    <div class="metadata-card">
        <div class="metadata-card-header"><span>H-INDEX</span></div>
        <div class="metadata-card-value academic-metric-value">${expert.hIndex ?? "-"}</div>
    </div>
    <div class="metadata-card">
        <div class="metadata-card-header"><span>引用数</span></div>
        <div class="metadata-card-value academic-metric-value">${expert.citationCount != null ? expert.citationCount.toLocaleString() : "-"}</div>
    </div>
    <div class="metadata-card">
        <div class="metadata-card-header"><span>发表数</span></div>
        <div class="metadata-card-value academic-metric-value">${expert.worksCount ?? "-"}</div>
    </div>
    <div class="metadata-card">
        <div class="metadata-card-header"><span>最近发表</span></div>
        <div class="metadata-card-value academic-metric-value">${expert.lastPublicationYear ?? "-"}</div>
    </div>
</div>`

// 研究方向 (span-all)
${expert.researchFields ? `
<div class="metadata-grid">
    <div class="metadata-card span-all">
        <div class="metadata-card-header"><span>研究方向</span></div>
        <div class="metadata-card-value">${escapeHtml(expert.researchFields)}</div>
    </div>
</div>` : ""}

// 机构 (span-all)
${expert.institution ? `
<div class="metadata-grid">
    <div class="metadata-card span-all">
        <div class="metadata-card-header"><span>机构</span></div>
        <div class="metadata-card-value">${escapeHtml(expert.institution)}</div>
    </div>
</div>` : ""}

// enrichment 状态
${expert.enrichedAt ? `
<div class="enrichment-status-info">
    <span>数据来源: OpenAlex</span>
    <span>更新时间: ${escapeHtml(expert.enrichedAt)}</span>
</div>` : `
<div class="enrichment-status-info enrichment-empty">
    <span>尚未补充学术数据</span>
</div>`}
```

- 文件: `app.js` — `showExpertDetail()`

**Task 4.3**: 改造 `loadContactDetail()` — 同样插入 sub-tab 导航，将内容分组：

- 学术档案 panel: 从 `expert` 对象（`state.contacts` 中匹配项）读取学术字段，渲染同 Task 4.2 的学术卡片
- 联系详情 panel: 保留现有全部内容（mail timeline、meeting schedule、metadata cards、status history、documents、operator logs）
- 模板预览 panel: 占位，首次激活时懒加载
- 默认激活 tab: 已联系专家默认显示"联系详情"，未联系专家默认显示"学术档案"

- 文件: `app.js` — `loadContactDetail()`

**Task 4.4**: sub-tab 切换事件委托 — 在 `#contactDetail` 的事件委托中添加 `[data-sub-tab]` click 处理：

```javascript
// 在 contactDetail 的事件委托中追加:
if (target.matches("[data-sub-tab]")) {
    const tabKey = target.dataset.subTab;
    const detail = target.closest(".detail");
    detail.querySelectorAll(".detail-sub-tab").forEach(t => t.classList.toggle("active", t.dataset.subTab === tabKey));
    detail.querySelectorAll(".detail-tab-panel").forEach(p => p.hidden = p.dataset.panel !== tabKey);
    // 模板预览 tab 懒加载
    if (tabKey === "template") {
        const panel = detail.querySelector('[data-panel="template"]');
        if (panel && !panel.dataset.loaded) {
            loadTemplatePreview(panel, state.selectedExpertOrcid);
        }
    }
    return;
}
```

- 文件: `app.js` — 事件委托区域（搜索 `contactDetail` 的 click 事件绑定）

### Stage 5: 模板预览 tab (F-3, F-6 from Plan 1)

**Task 5.1**: 新增 `loadTemplatePreview(panel, orcidId)` 函数：

```javascript
async function loadTemplatePreview(panel, orcidId) {
    if (!orcidId) {
        panel.innerHTML = `<div class="tpl-var-empty">无 ORCID，无法预览模板变量。</div>`;
        return;
    }
    panel.innerHTML = `<div class="tpl-var-loading">加载模板变量中...</div>`;
    panel.dataset.loaded = "true";
    try {
        const level = $("#expertIndexLevel")?.value || "CANDIDATE";
        const vars = await api(`/api/experts/template-variables?orcidId=${encodeURIComponent(orcidId)}&level=${level}`);
        // vars: [{key, label, value, filled}]
        const filled = vars.filter(v => v.filled).length;
        const total = vars.length;
        const coveragePercent = total > 0 ? Math.round(filled / total * 100) : 0;
        panel.innerHTML = `
            <div class="tpl-var-summary">
                <span class="tpl-var-coverage">变量覆盖: ${filled}/${total} (${coveragePercent}%)</span>
                <div class="tpl-var-progress-track">
                    <div class="tpl-var-progress-fill" style="width: ${coveragePercent}%"></div>
                </div>
            </div>
            <div class="tpl-var-grid">
                ${vars.map(v => `
                    <div class="tpl-var-item ${v.filled ? "tpl-var-filled" : "tpl-var-empty-val"}">
                        <div class="tpl-var-key">\${${escapeHtml(v.key)}}</div>
                        <div class="tpl-var-value">${v.filled ? escapeHtml(v.value) : "—"}</div>
                    </div>
                `).join("")}
            </div>
        `;
    } catch (e) {
        panel.innerHTML = `<div class="tpl-var-empty">加载失败: ${escapeHtml(e.message)}</div>`;
        panel.dataset.loaded = "";
    }
}
```

- 文件: `app.js` — 新增函数

### Stage 6: enrichment 触发 UI (F-5)

**Task 6.1**: `index.html` — 在 `#discoverModeMenu` 的分隔线之前追加 enrich 选项：

```html
<button class="dropdown-item" onclick="handleDiscoverOption('enrich')">补充学术数据（OpenAlex）</button>
```

插入位置: `<hr class="dropdown-divider">` 之前

- 文件: `index.html` — `#discoverModeMenu` (约 line 539)

**Task 6.2**: `app.js` — `handleDiscoverOption()` 新增 'enrich' 分支：

```javascript
async function handleDiscoverOption(mode) {
    if (mode === 'quick') {
        await handlePromoteRaw();
    } else if (mode === 'revalidate') {
        await handleRevalidateCandidates();
    } else if (mode === 'enrich') {
        await handleEnrichExperts();
    } else {
        await handleDiscover();
    }
}
```

**Task 6.3**: `app.js` — 新增 `handleEnrichExperts()` 函数，复用 task modal 模式：

```javascript
async function handleEnrichExperts() {
    const taskType = "EXPERT_ENRICHMENT";
    const running = await isTaskRunning(taskType);
    if (running) {
        openTaskModal(taskType, "补充学术数据（OpenAlex）", "discoverBtn", { knownActiveAtOpen: true });
        return;
    }
    const hasRunning = await progressStoreHasRunningTask();
    if (hasRunning) {
        showStatus("已有其他任务正在执行中，请等待完成后再启动新任务", "warn");
        return;
    }
    openTaskModal(taskType, "补充学术数据（OpenAlex）", "discoverBtn", { launchRequested: true });
    const capturedGeneration = currentTaskModal?.generation;
    try {
        const response = await api("/api/expert-discovery/enrich", { method: "POST" });
        if (response && response.executionId != null) {
            await bindTaskModalExecution(taskType, capturedGeneration, response.executionId);
        }
        markTaskWatcherLaunchSucceeded(taskType, capturedGeneration);
        notifyTaskCompletionOnce({
            taskType,
            capturedGeneration,
            msg: `学术数据补充完成`,
            level: "ok",
            refreshFn: loadContacts
        });
    } catch (e) {
        showStatus(`补充学术数据失败: ${e.message}`, "error");
    }
}
```

- 文件: `app.js` — 新增函数 + `handleDiscoverOption()` 修改

### Stage 7: 样式新增 (F-4)

**Task 7.1**: `styles.css` 末尾追加新样式（全部使用命名空间前缀）：

```css
/* === 学术徽章（列表项） === */
.academic-badge {
    display: inline-flex;
    align-items: center;
    font-size: 11px;
    font-weight: 600;
    padding: 1px 7px;
    border-radius: 999px;
    line-height: 1.6;
}
.academic-hindex {
    background: #eff6ff;
    color: #1d4ed8;
    border: 1px solid #bfdbfe;
}
.academic-enriched {
    background: #f0fdf4;
    color: #15803d;
    border: 1px solid #bbf7d0;
    font-weight: 500;
}

/* === 详情 sub-tab === */
.detail-sub-tabs {
    display: flex;
    gap: 0;
    border-bottom: 1px solid var(--border);
    margin-bottom: 12px;
}
.detail-sub-tab {
    padding: 8px 16px;
    font-size: 13px;
    font-weight: 500;
    color: var(--text-muted);
    background: none;
    border: none;
    border-bottom: 2px solid transparent;
    cursor: pointer;
    transition: color 0.15s, border-color 0.15s;
}
.detail-sub-tab:hover {
    color: var(--text-main);
}
.detail-sub-tab.active {
    color: var(--primary);
    border-bottom-color: var(--primary);
}

/* === 学术指标行 === */
.academic-metrics-row {
    grid-template-columns: repeat(4, 1fr);
}
.academic-metric-value {
    font-size: 20px;
    font-weight: 700;
    color: var(--text-main);
}

/* === enrichment 状态信息 === */
.enrichment-status-info {
    display: flex;
    gap: 12px;
    font-size: 11px;
    color: var(--text-muted);
    padding: 6px 0;
    border-top: 1px solid var(--panel-border);
    margin-top: 8px;
}
.enrichment-status-info.enrichment-empty {
    color: #b45309;
    font-style: italic;
}

/* === 模板变量预览 === */
.tpl-var-summary {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
}
.tpl-var-coverage {
    font-size: 13px;
    font-weight: 600;
    color: var(--text-main);
    white-space: nowrap;
}
.tpl-var-progress-track {
    flex: 1;
    height: 6px;
    background: var(--panel-border);
    border-radius: 3px;
    overflow: hidden;
}
.tpl-var-progress-fill {
    height: 100%;
    background: var(--primary);
    border-radius: 3px;
    transition: width 0.3s;
}
.tpl-var-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 6px;
}
.tpl-var-item {
    padding: 8px 10px;
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
    display: flex;
    flex-direction: column;
    gap: 2px;
}
.tpl-var-filled {
    background: #f0fdf4;
    border-color: #bbf7d0;
}
.tpl-var-empty-val {
    background: #fffbeb;
    border-color: #fde68a;
}
.tpl-var-key {
    font-size: 11px;
    font-family: 'SF Mono', 'Fira Code', monospace;
    color: var(--text-muted);
}
.tpl-var-value {
    font-size: 13px;
    font-weight: 500;
    color: var(--text-main);
    word-break: break-all;
}
.tpl-var-empty, .tpl-var-loading {
    padding: 20px;
    text-align: center;
    color: var(--text-muted);
    font-size: 13px;
}
```

- 文件: `styles.css` — 文件末尾追加

---

## 变更文件清单

| # | 文件 | 改动类型 | 涉及不变量 |
|---|------|---------|-----------|
| 1 | `src/main/resources/static/index.html` | 新增 4 个筛选控件 + discover 下拉新增 enrich 选项 | F-1, F-5 |
| 2 | `src/main/resources/static/app.js` | loadContacts 数据透传 + 筛选参数传递 + 列表项增强 + sub-tab 渲染 + 模板预览 + enrichment 触发 | F-1, F-2, F-3, F-5 |
| 3 | `src/main/resources/static/styles.css` | 新增 academic-badge / detail-sub-tab / academic-metrics / enrichment-status / tpl-var 样式 | F-4 |

**共 3 文件**，均为前端静态资源，不涉及后端代码。

---

## 验收标准

### 不变量验证

- **F-1**: 切换"人工干预"或"回复模式"筛选器使 `useDbContactPath=true`，验证新增的 4 个学术筛选控件变为 disabled + 半透明 + tooltip 提示。切回 ES path 后控件恢复可用。
- **F-2**: 调用 `/api/experts?level=CANDIDATE&size=10`，验证返回的 JSON 中含 hIndex/citationCount/enrichedAt 等字段。在列表中选中一个有 hIndex 的专家，验证列表项显示 `h XX` 徽章。
- **F-3**: 打开专家详情面板，依次切换"学术档案"→"联系详情"→"模板预览"→"学术档案"，验证：(a) 浏览器 Network 面板不出现重复 API 请求（template-variables 仅首次点击时请求一次）；(b) 切换无闪烁无重渲染。
- **F-4**: 全局搜索 styles.css 中新增的类名，确认无一与现有规则选择器冲突。所有新类名以 `academic-`、`enrichment-`、`detail-sub-`、`tpl-var-` 开头。
- **F-5**: 点击"发现专家"下拉 → "补充学术数据"，验证 task modal 弹出且调用 `POST /api/expert-discovery/enrich`，进度条正常更新至完成。

### 集成场景

1. **端到端筛选**: 先通过"补充学术数据"enrichment 若干专家 → 设置"H-Index ≥ 10" + "数据完整度: 有研究方向" → 验证列表只显示符合条件的专家，且列表项有 `h XX` 徽章和"已补充"标签。
2. **详情面板完整性**: 选中一个已 enrich 的专家 → "学术档案" tab 显示 H-Index/引用数/发表数/最近发表年指标卡片 + 研究方向 + 机构 + enrichment 时间 → "联系详情" tab 显示原有全部卡片和邮件时间线 → "模板预览" tab 显示变量覆盖度进度条 + 各变量的 key/value 和填充状态。
3. **未 enrich 专家**: 选中一个未 enrich 的专家 → "学术档案" tab 学术指标全显示 "-" + 显示"尚未补充学术数据"提示 → "模板预览" tab 变量覆盖度较低，缺失字段标黄。
4. **视觉一致性**: 新增 UI 元素（筛选输入框、徽章、sub-tab、指标卡片、变量网格）与现有 UI 的字体、颜色、圆角、间距视觉一致。
