# P4b：邮件模版门禁过滤开关（前端）

主计划：`batch-task-filters-main.md`
前置计划：**P4a 必须已合并**（后端接受 `gateFilterEnabled`）；**P3b 必须已合并**（`app.js` 的编辑器/手动面板已稳定在多选形态）
子系统数：1（前端）  文件数：4

视觉基准：本计划的三种状态与列表 pill 已有交付过的可交互预览（`batch-gate-filter-preview.html`，2026-08-15）。**契约中的逐字 CSS 与 DOM 即该预览的落地版本**，以本文为准。

---

## 需求描述

### Observable outcome

定时任务编辑器与手动执行面板的「收件范围」区新增「邮件模版门禁过滤」开关，三种状态：

1. **开启** —— 收件预估显示「命中 N 位（未联系 x、可重试 y）；门禁过滤已排除 **M** 位」，下方以只读徽标列出该模板参与预筛的必填字段。
2. **关闭** —— 显示原命中数，另起一行橙色提示「其中 M 位缺少该模板必填字段，发送时会被门禁拦下并计入失败」。
3. **不可用（置灰）** —— 所选模板 `required_keys` 为空（当前全库皆是）时开关禁用，提示「该模板未配置门禁字段（required_keys 为空），门禁本身未启用，开启无效」。

任务列表「收件范围」列显示三态 pill：开（蓝，含字段数）/ 关（灰）/ 模板无门禁字段（虚线）。手动执行的开关参与「已修改」标红。

### What must NOT change

- **N4b-1** 专家列表页「按模板门禁」筛选器（`#expertGateTemplateFilter` + `initExpertGateFilter`，`app.js:11585-11760`）一行不改。
- **N4b-2** 四个多选 picker（标签/地区/邮箱服务商/专家状态）行为不变。
- **N4b-3** 收件预估的 500ms 防抖 + 请求序号丢弃过期响应机制不变（`refreshRecipientPreview`，`app.js:13975-14000`）。
- **N4b-4** 手动执行「已修改」对其余字段的行为不变。
- **N4b-5** 既有 CSS 规则块零修改 —— 只允许**追加**新规则。

### Out of scope

- 后端 —— 归 P4a。
- 在预览面板里显示"这封信会不会被拦"（`previewDraft` 不跑门禁，见 `intro-mail-fallback-renders-as-title`）。
- 模板 `required_keys` 的编辑入口。

---

## 关键不变量

### Invariant I4b-1: 「排除数」由两次同源预估相减得出，不新增后端字段
- Rule: `refreshRecipientPreview(kind)` 在需要展示排除数时，对**同一份 snapshot** 发两次 `POST /api/mail/batch-send/recipients/preview`：一次 `gateFilterEnabled: true`、一次 `false`。排除数 = `totalSendable(false) − totalSendable(true)`。两次请求共用同一个 `seq`，两者都返回后才渲染。
- Applies to: `refreshRecipientPreview`。
- Violation consequence: 若另开一个"排除数"接口或让后端塞进 `PendingOutreachSummary`，就会出现第二套计算路径，与 M-4「预估与执行同源」冲突（P4a 的 N4a-5 已锁死 DTO 形状）。
- 来源: M-4 / K-recipient-count-preview-parity

### Invariant I4b-2: 陈旧响应必须按 seq 丢弃，两次请求同 seq
- Rule: 沿用既有 `recipientPreviewRequestSeq[kind]` 机制。两次请求在**发起前**取同一个 `seq = ++recipientPreviewRequestSeq[kind]`；`Promise.all` 结算后先比 `seq !== recipientPreviewRequestSeq[kind]` 再渲染。任一请求失败则整体走失败分支，显示「预估失败：<原因>」，**不**渲染半份结果。
- Applies to: `refreshRecipientPreview`。
- Violation consequence: 两次请求分别比 seq 会渲染出"开启态的命中数 + 上一轮的总数"这种混合结果，排除数为负或荒谬值。
- 来源: K-ai-preflight-stale-response-draft-identity（既有代码 `app.js:13981-13996` 已用此范式）

### Invariant I4b-3: 三态由「模板是否有门禁字段」唯一决定，不由开关值决定
- Rule: 每次模板选择变化，调 `GET /api/compose-templates/{id}/gate-fields` 取 `esFields`。
  - `templateId` 为空 **或** 请求失败 **或** `esFields` 为空 → **不可用态**：`checkbox.disabled = true`、`checked = false`、字段徽标区隐藏、提示走 warn 文案。
  - 否则 → 可用态，徽标区渲染 `esFields`。
- Applies to: 新增的 `refreshBatchGateState(kind)`。
- Violation consequence: 允许在无门禁字段时开启，开关状态与实际效果不一致，运营误以为已生效（正是 `intro-mail-fallback-renders-as-title` 记录的那个坑）。
- 来源: original + intro-mail-fallback-renders-as-title

### Invariant I4b-4: 徽标只展示**能预筛**的字段，且标注被丢弃的
- Rule: `gate-fields` 返回的 `esFields` 中，落在前端已知的 6 个可预筛字段（`employment / degree / institution / researchFields / patentTitles / recentWorkTitles`）内的渲染为徽标；其余渲染为一句灰色说明「另有 N 个必填字段无法预筛（<字段名>），这些专家仍可能在发送时被拦下」。
- Applies to: 徽标区渲染。
- Violation consequence: P4a 的 I4a-3 已在后端丢弃这些字段。前端若不标注，运营会以为"开了就一个都不会被拦"，与实际不符。
- 来源: P4a 的 I4a-3

### Invariant I4b-5: 手动执行 diff 的 5 个注册点必须同步
- Rule: 同 I2b-4 / I3b-4 的 5 点，key 为 `gateFilterEnabled`，新 DOM id 为 `manualFieldGateFilter`。
- Applies to: `app.js`。
- 来源: K-recipient-scope-status-filter

### Invariant I4b-6: 不可用态不得触发预估的第二次请求
- Rule: 三态为「不可用」时，`refreshRecipientPreview` 只发**一次**请求（`gateFilterEnabled` 恒为 false），不做两次相减。
- Applies to: `refreshRecipientPreview`。
- Violation consequence: 当前全库 `required_keys` 皆空，所有任务都是不可用态；发两次请求会让 ES 计数查询量直接翻倍，且第二次的结果恒等于第一次。
- 来源: original（推导自 P4a 现状审计「required_keys 当前全库为空」）

---

## 样式契约

> 既有 class 行号见主计划 X-3。本计划**是四个前端计划里唯一新增 CSS 的**。

### S4b-1: 门禁开关字段（编辑器 + 手动面板共用）
- **复用**：`.batch-config-field`（styles.css:8873）、`.batch-config-field-label`（:8676）、`.batch-task-status-toggle`（:8699）、`.batch-task-status-switch`（:8723）及其 `::after`（:8733）、`:checked`（:8746 / :8750）、`:focus-visible`（:8754）、`:checked ~ label`（:8758）、`.tag-chip`（:583）与 `.tag-chip.active`（:608）、`.batch-config-diff-badge`（:8904）、`.batch-config-diff-original`（:8913）、`.batch-config-editor-hint`（:8602）及其 `strong`（:8612）。
  **禁止**自造开关、自造徽标。
- **新增**：以下 CSS **逐字**追加到 `styles.css` 的 `/* ── Batch Send Task Console ── */` 段末尾（`.batch-tag-picker-empty` 规则块之后）。执行 agent 必须原样复制，不得增删属性或改值：

```css
/* ── 邮件模版门禁过滤（P4b / S4b-1、S4b-2） ─────────────────────── */
.batch-gate-field { grid-column: 1 / -1; }

.batch-gate-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.batch-gate-toggle {
  flex-direction: row;
  align-items: center;
  gap: 8px;
}

.batch-gate-toggle .batch-task-status-label {
  font-size: 12px;
  font-weight: 600;
}

.batch-gate-field.is-disabled { opacity: .6; }
.batch-gate-field.is-disabled .batch-gate-toggle { cursor: not-allowed; }

.batch-gate-hint {
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.5;
}

.batch-gate-hint.is-warn { color: var(--warning-strong); }

.batch-gate-keys {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.batch-gate-keys[hidden] { display: none; }

.batch-gate-keys-label {
  color: var(--text-sidebar);
  font-size: 11px;
  font-weight: 600;
}

.batch-gate-keys .tag-chip {
  cursor: default;
  pointer-events: none;
}

.batch-gate-keys-dropped {
  flex-basis: 100%;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.5;
}

.batch-config-editor-hint .batch-gate-excluded {
  color: var(--error-strong);
  font-weight: 600;
}

.batch-config-editor-hint .batch-gate-warnline {
  display: block;
  margin-top: 4px;
  color: var(--warning-strong);
}

.batch-gate-pill {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 8px;
  border: 1px solid var(--primary);
  border-radius: var(--radius-lg);
  background: var(--primary-light);
  color: var(--primary);
  font-size: 11px;
  font-weight: 600;
}

.batch-gate-pill.is-off {
  border-color: var(--border);
  background: transparent;
  color: var(--text-muted);
}

.batch-gate-pill.is-na {
  border-style: dashed;
  border-color: var(--border);
  background: transparent;
  color: var(--text-muted);
}
```

- **DOM 结构（编辑器）**：插入到 `index.html` 定时任务编辑器「收件范围」`.batch-config-editor-grid` 的**最后一个子元素**位置（学科/专家状态字段之后），**逐字**：

```html
                        <div class="batch-config-field batch-gate-field" id="editorFieldGateFilter">
                            <span class="batch-config-field-label">邮件模版门禁过滤</span>
                            <div class="batch-gate-row">
                                <label class="batch-task-status-toggle batch-gate-toggle">
                                    <input type="checkbox" id="batchConfigEditorGateFilter">
                                    <span class="batch-task-status-switch"></span>
                                    <span class="batch-task-status-label" id="batchConfigEditorGateFilterLabel">已关闭</span>
                                </label>
                                <span class="batch-gate-hint" id="batchConfigEditorGateFilterHint">仅向满足该模板必填字段的专家发送，缺字段的会在发送时被门禁拦下并计入失败。</span>
                            </div>
                            <div class="batch-gate-keys" id="batchConfigEditorGateFilterKeys" hidden></div>
                        </div>
```

- **DOM 结构（手动面板）**：插入到手动执行「模板与收件范围」`.batch-config-editor-grid` 的最后一个子元素位置，**逐字**：

```html
                    <div class="batch-config-field batch-gate-field" id="manualFieldGateFilter">
                        <span class="batch-config-field-label">邮件模版门禁过滤</span>
                        <div class="batch-gate-row">
                            <label class="batch-task-status-toggle batch-gate-toggle">
                                <input type="checkbox" id="batchManualGateFilter">
                                <span class="batch-task-status-switch"></span>
                                <span class="batch-task-status-label" id="batchManualGateFilterLabel">已关闭</span>
                            </label>
                            <span class="batch-gate-hint" id="batchManualGateFilterHint">仅影响本次执行，不修改原定时任务。</span>
                        </div>
                        <div class="batch-gate-keys" id="batchManualGateFilterKeys" hidden></div>
                        <span class="batch-config-diff-badge" hidden>已修改</span>
                        <div class="batch-config-diff-original" hidden></div>
                    </div>
```

- **禁止项**：inline style；本契约未声明的新 class；对既有 class 规则块的**任何**修改（N4b-5，只许追加）。

### S4b-2: 任务列表「收件范围」列的三态 pill
- **复用**：`.batch-task-scope-line`（styles.css:8520-8521）。
- **新增**：`.batch-gate-pill` / `.is-off` / `.is-na`（已在 S4b-1 的 CSS 块中逐字给出）。
- **DOM 结构**：`renderBatchConfigRow` 内，在全部 scopeParts 之后追加一行（**始终**输出，三态之一）：
  ```js
  scopeParts.push(batchGatePillHtml(c));
  ```
  `batchGatePillHtml(c)` 返回三者之一（逐字）：
  ```
  <span class="batch-gate-pill">门禁过滤 · N 字段</span>
  <span class="batch-gate-pill is-off">门禁过滤 · 关</span>
  <span class="batch-gate-pill is-na">模板无门禁字段</span>
  ```
- ⚠️ 列表行渲染时**不发** `gate-fields` 请求（N 行会打 N 次）。`is-na` 态的判定退化为：`c.templateId` 为空。字段数 N 的来源见 T4b-5 的说明。
- **禁止项**：不改列宽；不改 `.batch-task-scope-line` 规则块。

---

## 现状审计

> 前端样式盘点见主计划 X-3；`refreshRecipientPreview` 的 seq/防抖实现见下。

### 收件预估的既有实现（`app.js:13964-14000`，逐字，改动前基线）

```js
function recipientPreviewHintId(kind) {
    return kind === "editor" ? "batchConfigEditorRecipientHint" : "batchManualRecipientHint";
}

function scheduleRecipientPreview(kind) {
    if (kind !== "editor" && kind !== "manual") return;
    clearTimeout(recipientPreviewTimers[kind]);
    recipientPreviewTimers[kind] = setTimeout(function() {
        refreshRecipientPreview(kind);
    }, 500);
}

function refreshRecipientPreview(kind) {
    if (kind !== "editor" && kind !== "manual") return;
    var hint = document.getElementById(recipientPreviewHintId(kind));
    if (!hint) return;
    var seq = ++recipientPreviewRequestSeq[kind];
    var snapshot = kind === "editor" ? buildConfigEditorRecipientSnapshot() : buildManualExecutionSnapshot();
    hint.innerHTML = "当前条件命中 <strong>计算中…</strong>";
    api("/api/mail/batch-send/recipients/preview", {
        method: "POST",
        body: JSON.stringify(snapshot)
    }).then(function(res) {
        // 丢弃过期响应：只有本次请求序号仍是最新时才渲染（K-ai-preflight-stale-response-draft-identity）
        if (seq !== recipientPreviewRequestSeq[kind]) return;
        var pending = Number(res.pending || 0);
        var retryable = Number(res.retryable || 0);
        var total = Number(res.totalSendable != null ? res.totalSendable : (pending + retryable));
        hint.innerHTML = "当前条件命中 <strong>" + total + "</strong> 位专家（其中未联系 " + pending +
            "、可重试 " + retryable + "）";
    }).catch(function(error) {
        // 失败不打断编辑：保留接口错误原因，便于校正筛选参数（A-2）
        if (seq !== recipientPreviewRequestSeq[kind]) return;
        var message = error && error.message ? String(error.message) : "请求失败";
        console.warn("Recipient preview failed", error);
        hint.textContent = "预估失败：" + message;
    });
}
```

### 可直接复用的既有门禁前端代码（专家列表页，**禁改**，只作参考）

`app.js:11609-11760` 的 `initExpertGateFilter` 已经：
- 调 `GET /api/compose-templates/${id}/gate-fields`（`:11723`）取 `esFields`
- 对无对应 chip 的字段打日志后忽略（`:11650-11655`）—— 即 I4b-4 所述的降级
- 接口失败时清除全部门禁状态、只提示一次、不硬编码回退字段（`:11738-11742`）

本计划**不复用其代码**（那是列表页专属的 chip 联动），但**复用其失败处理策略**。

### 交互点

| IP | 说明 |
|---|---|
| IP-1 | 模板下拉变化 → `refreshBatchGateState(kind)` → 三态与徽标；同时必须重新触发预估（模板变了排除数也变） |
| IP-2 | 开关变化 → `scheduleRecipientPreview(kind)` |
| IP-3 | 两次预估请求 → 一次渲染：I4b-1 / I4b-2 |
| IP-4 | 来源配置带入 → diff：I4b-5 |
| IP-5 | 保存 payload → P4a 的 `gateFilterEnabled` |

---

## 实现方案

### T4b-1 CSS 追加（S4b-1）

文件：`src/main/resources/static/styles.css`
把 S4b-1 的 CSS 块**逐字**追加到 `.batch-tag-picker-empty` 规则块之后。**不修改任何既有规则块**（N4b-5）。

### T4b-2 index.html DOM 新增（S4b-1）

文件：`src/main/resources/static/index.html`
按契约在两处 `.batch-config-editor-grid` 末尾各插入一个字段块。其余一行不改。

### T4b-3 三态解析（I4b-3 / I4b-4）

文件：`src/main/resources/static/app.js`，新增：

```js
/* 前端已知的可预筛字段（与后端 ExpertSearchService.ALLOWED_HAS_FIELDS 一致）。
   I4b-4：其余字段无法预筛，只标注不筛。 */
var BATCH_GATE_FILTERABLE_FIELDS = {
    employment: "有职位", degree: "有学历", institution: "有机构",
    researchFields: "有研究方向", patentTitles: "有专利", recentWorkTitles: "有近期论文"
};

var batchGateState = { editor: { available: false, fields: [], dropped: [] },
                       manual:  { available: false, fields: [], dropped: [] } };

async function refreshBatchGateState(kind) { /* 见下方规格 */ }
```

`refreshBatchGateState(kind)` 规格：
1. 取当前模板 id（editor: `#batchConfigEditorTemplateId`；manual: `#batchManualTemplateId`）。
2. id 为空 → 置不可用态并 return（不发请求）。
3. `await api("/api/compose-templates/" + id + "/gate-fields")`；异常 → 置不可用态 + `console.error` 一次，**不弹窗**（照 `initExpertGateFilter` 的失败策略），return。
4. `esFields` 为空 → 不可用态。
5. 否则：`fields = esFields.filter(f => f in BATCH_GATE_FILTERABLE_FIELDS)`；`dropped = esFields.filter(f => !(f in BATCH_GATE_FILTERABLE_FIELDS))`。`fields` 为空（全部落在差集）→ **也置不可用态**（预筛不会产生任何效果），但提示文案改为「该模板的必填字段均无法预筛（<dropped>）」。
6. 可用态：`checkbox.disabled = false`；徽标区 `hidden = false`，渲染
   `<span class="batch-gate-keys-label">该模板必填字段</span>` + 每个 field 一个 `<span class="tag-chip active">标签</span>`；`dropped` 非空时追加 `<div class="batch-gate-keys-dropped">另有 N 个必填字段无法预筛（…），这些专家仍可能在发送时被拦下</div>`。
7. 同步开关 label 文案：不可用 → `不可用`；可用且 checked → `已开启`；可用未 checked → `已关闭`。
8. 结束后调 `scheduleRecipientPreview(kind)`（IP-1）。

置不可用态的统一动作：`field.classList.add("is-disabled")`、`checkbox.disabled = true`、`checkbox.checked = false`、`keys.hidden = true`、`hint.classList.add("is-warn")` + warn 文案。可用态则做相反操作。

### T4b-4 预估双请求（I4b-1 / I4b-2 / I4b-6）

文件：`app.js`，改写 `refreshRecipientPreview`：

```js
function refreshRecipientPreview(kind) {
    if (kind !== "editor" && kind !== "manual") return;
    var hint = document.getElementById(recipientPreviewHintId(kind));
    if (!hint) return;
    var seq = ++recipientPreviewRequestSeq[kind];          // I4b-2：两次请求共用同一 seq
    var snapshot = kind === "editor" ? buildConfigEditorRecipientSnapshot() : buildManualExecutionSnapshot();
    hint.innerHTML = "当前条件命中 <strong>计算中…</strong>";

    var post = function(gateOn) {
        return api("/api/mail/batch-send/recipients/preview", {
            method: "POST",
            body: JSON.stringify(Object.assign({}, snapshot, { gateFilterEnabled: gateOn }))
        });
    };
    // I4b-6：不可用态只发一次（当前全库 required_keys 为空，双发会让 ES 计数量翻倍且结果恒等）
    var gateAvailable = batchGateState[kind].available;
    var requests = gateAvailable ? [post(false), post(true)] : [post(false)];

    Promise.all(requests).then(function(results) {
        if (seq !== recipientPreviewRequestSeq[kind]) return;   // I4b-2
        var totalOf = function(r) {
            var p = Number(r.pending || 0), t = Number(r.retryable || 0);
            return Number(r.totalSendable != null ? r.totalSendable : (p + t));
        };
        var off = results[0];
        var offTotal = totalOf(off);
        var gateOn = gateAvailable && document.getElementById(gateToggleId(kind)).checked;
        if (!gateAvailable) {
            hint.innerHTML = baseHintHtml(offTotal, off);
            return;
        }
        var onTotal = totalOf(results[1]);
        var excluded = Math.max(0, offTotal - onTotal);        // I4b-1
        if (gateOn) {
            hint.innerHTML = baseHintHtml(onTotal, results[1]) +
                "；门禁过滤已排除 <span class=\"batch-gate-excluded\">" + excluded + "</span> 位";
        } else {
            hint.innerHTML = baseHintHtml(offTotal, off) +
                (excluded > 0
                    ? "<span class=\"batch-gate-warnline\">其中 " + excluded +
                      " 位缺少该模板必填字段，发送时会被门禁拦下并计入失败。</span>"
                    : "");
        }
    }).catch(function(error) {
        if (seq !== recipientPreviewRequestSeq[kind]) return;   // I4b-2：失败也要比 seq
        var message = error && error.message ? String(error.message) : "请求失败";
        console.warn("Recipient preview failed", error);
        hint.textContent = "预估失败：" + message;
    });
}
```

新增 helper `baseHintHtml(total, res)` 产出既有那句「当前条件命中 **N** 位专家（其中未联系 x、可重试 y）」，与改动前**逐字一致**；`gateToggleId(kind)` 返回 `batchConfigEditorGateFilter` / `batchManualGateFilter`。

⚠️ `scheduleRecipientPreview` 的 500ms 防抖与 `recipientPreviewRequestSeq` 结构**不改**（N4b-3）。

### T4b-5 接线（IP-1 / IP-2 / IP-5）+ 列表 pill（S4b-2）

| 位置 | 改法 |
|---|---|
| `showBatchConfigEditor`（`:13525` 邻域） | `document.getElementById("batchConfigEditorGateFilter").checked = Boolean(config && config.gateFilterEnabled);` 然后 `refreshBatchGateState("editor")` |
| `buildConfigEditorRecipientSnapshot`（`:13919`） | 加 `gateFilterEnabled: gateToggleChecked("editor"),` |
| `saveBatchConfigEditor`（`:14035` 邻域） | payload 加 `gateFilterEnabled: gateToggleChecked("editor"),` |
| `buildManualExecutionSnapshot`（`:13944`） | 加 `gateFilterEnabled: values.gateFilterEnabled,` |
| `deepCloneConfig` / `fillManualFormDefaults` / `fillManualFormFromDraft` / `readManualFormValues` | 各加 `gateFilterEnabled`（默认 `false`） |
| `bindBatchSendTaskEvents` | 两个模板下拉的 `change` 追加 `refreshBatchGateState(kind)`；两个开关 `change` 追加 `updateGateToggleLabel(kind)` + `scheduleRecipientPreview(kind)` |
| `renderBatchConfigRow`（`:13386`） | 按 S4b-2 追加 `scopeParts.push(batchGatePillHtml(c));` |

`batchGatePillHtml(c)` 的三态判定（S4b-2 的 ⚠️ 约束：列表不发请求）：
- `!c.templateId` → `is-na`（模板为空即无门禁字段可言）
- `c.gateFilterEnabled` → 蓝 pill，文案 `门禁过滤 · 开`（**不显示字段数** —— 列表拿不到 `esFields`，显示假数字比不显示更糟）
- 否则 → `is-off`

> 与预览稿的差异：预览里蓝 pill 写的是「门禁过滤 · 3 字段」。落地版改为「门禁过滤 · 开」，理由是列表渲染不能为每行发一次 `gate-fields` 请求。此差异为**有意偏离**，须在实现说明中记录。

### T4b-6 diff 5 点（I4b-5）

| 点 | 改法 |
|---|---|
| #1 `normalizeManualSnapshot` | `gateFilterEnabled: Boolean(v.gateFilterEnabled),` |
| #2 `formatManualDiffValue` | `if (key === "gateFilterEnabled") return value ? "开启" : "关闭";` |
| #3 `computeManualDiffs` fieldDefs | `{ key: "gateFilterEnabled", label: "邮件模版门禁过滤" }` |
| #4 `computeAndRenderDiffs` fieldMap | `gateFilterEnabled: "manualFieldGateFilter"` |
| #5 `clearAllDiffMarkers` fields 数组 | 追加 `"manualFieldGateFilter"` |

### T4b-7 测试

文件：`src/test/js/batchSendTaskConsoleInteraction.test.js`，追加：

| 用例 | 断言 |
|---|---|
| G1 | `gate-fields` 返回 `esFields: []` → 开关 `disabled === true`、`checked === false`、徽标区 `hidden === true`、hint 含「未配置门禁字段」且带 `is-warn` class（I4b-3） |
| G2 | 模板 id 为空 → **不发请求**（断言 api 调用次数为 0）且为不可用态（I4b-3） |
| G3 | `gate-fields` 请求抛错 → 不可用态、不弹窗、`console.error` 被调用一次（I4b-3） |
| G4 | `esFields: ["institution","researchFields"]` → 可用态；徽标区渲染 2 个 `.tag-chip.active`，文案为「有机构」「有研究方向」（I4b-4、S4b-1） |
| G5 | `esFields: ["institution","keyword","hIndex"]` → 徽标 1 个；出现 `.batch-gate-keys-dropped`，文案含 `keyword` 与 `hIndex`（I4b-4） |
| G6 | `esFields: ["keyword"]`（全部不可预筛）→ **不可用态**，提示文案为「该模板的必填字段均无法预筛」（I4b-3 第 5 条） |
| G7 | 不可用态下 `refreshRecipientPreview` 只发 **1** 次请求（I4b-6） |
| G8 | 可用 + 开关关：发 **2** 次请求；渲染含 `.batch-gate-warnline`，排除数 = off − on（I4b-1） |
| G9 | 可用 + 开关开：渲染含 `.batch-gate-excluded`，命中数用的是 gate=true 那次的 total（I4b-1） |
| G10 | 两次请求返回期间又发起一轮新预估（seq 递增）→ 旧结果**不渲染**（I4b-2） |
| G11 | 任一请求 reject → hint 文案为 `预估失败：<msg>`，**不**出现半份结果（I4b-2） |
| G12 | `formatManualDiffValue("gateFilterEnabled", true)` === `"开启"`；`computeManualDiffs` 在 draft=true/source=false 时判为 diff（I4b-5） |
| G13 | `batchGatePillHtml({templateId:null})` → `is-na`；`{templateId:1, gateFilterEnabled:true}` → 蓝 pill；`{templateId:1, gateFilterEnabled:false}` → `is-off`（S4b-2） |
| G14 | 回归：P2b（V1-V9）与 P3b（W1-W9）用例继续绿 |

---

## 变更文件清单

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/resources/static/styles.css` | 修改（**仅追加** S4b-1 的 CSS 块） |
| 2 | `src/main/resources/static/index.html` | 修改（2 处新增字段块） |
| 3 | `src/main/resources/static/app.js` | 修改 |
| 4 | `src/test/js/batchSendTaskConsoleInteraction.test.js` | 修改（追加 G1-G14） |

文件数：**4** ✅  子系统数：**1** ✅
**不改**：任何 `.kt`、任何 `.sql`。

---

## 验证命令

见主计划。专用：`node --test src/test/js/batchSendTaskConsoleInteraction.test.js`

---

## 验收标准

- **I4b-1**：G8 / G9 绿；`grep -c "recipients/preview" src/main/resources/static/app.js` == 1（只有 `refreshRecipientPreview` 里那一个 URL 字面量，不得为门禁另开接口）。
- **I4b-2**：G10 / G11 绿；`grep -n "recipientPreviewRequestSeq" app.js` 显示 seq 只在 `refreshRecipientPreview` 开头自增一次。
- **I4b-3**：G1 / G2 / G3 / G6 绿。
- **I4b-4**：G4 / G5 绿；`BATCH_GATE_FILTERABLE_FIELDS` 的 6 个 key 与 `ExpertSearchService.ALLOWED_HAS_FIELDS`（`ExpertSearchService.kt:24`）逐字相同 —— **贴两边的 grep 输出对比**。
- **I4b-5**：G12 绿；5 个注册点逐个 `grep -n "gateFilterEnabled" app.js` 贴行号与上下文。
- **I4b-6**：G7 绿。
- **S4b-1**：`git diff src/main/resources/static/styles.css` **只有新增行，无删除行、无修改行**（N4b-5）；新增内容与契约代码块 `diff` 为空（逐字一致）。
- **S4b-2**：G13 绿；`git diff index.html | grep -c 'style='` == 0。
- **N4b-1**：`git diff app.js` 的 hunk 不覆盖 `:11585-11760`（`populateExpertGateTemplateFilter` / `initExpertGateFilter`）。
- **N4b-3**：`scheduleRecipientPreview` 函数体无改动行。
- 回归：主计划全量测试命令通过；G14 绿。

---

## 人工验收清单

### A4b-1: 不可用态（当前全库的默认形态）
- 前置条件：任选一个 `required_keys` 为空的模板（当前全部模板皆是）。
- 操作步骤：「定时任务」→「新增任务」→ 模板选该模板 → 观察「邮件模版门禁过滤」字段。
- 预期结果：整块半透明（opacity .6）；开关灰色、点不动；标签为「不可用」；右侧提示为橙色（`#b45309`）的「该模板未配置门禁字段（required_keys 为空），门禁本身未启用，开启无效。」；无字段徽标；预估行只有常规那一句，**无**排除数、**无**橙色警告行。
- 覆盖：I4b-3、I4b-6、S4b-1

### A4b-2: 可用态 + 开关开
- 前置条件：给某模板配 2 个必填变量，其 ES 字段落在 6 个可预筛字段内（如「机构」+「研究方向」）。
- 操作步骤：模板切到该模板 → 观察字段块 → 打开开关 → 观察预估行。
- 预期结果：
  - 字段块恢复不透明；开关可点；徽标区出现「该模板必填字段」+ 两个蓝底白字徽标「有机构」「有研究方向」，**不可点击**（`pointer-events: none`）。
  - 开关打开后标签变「已开启」、开关轨道变主色 `#1e40af`、滑块右移。
  - 预估行形如「当前条件命中 **N** 位专家（其中未联系 x、可重试 y）；门禁过滤已排除 **M** 位」，其中 M 为红色加粗（`#be123c`）。
- 覆盖：O-1、I4b-1、I4b-4、S4b-1

### A4b-3: 可用态 + 开关关的橙色提示
- 前置条件：接 A4b-2。
- 操作步骤：把开关关掉。
- 预期结果：命中数变回不预筛的总数；下方另起一行橙色文字「其中 M 位缺少该模板必填字段，发送时会被门禁拦下并计入失败。」，M 与 A4b-2 的排除数相同。
- 覆盖：I4b-1

### A4b-4: 差集字段的标注
- 前置条件：给某模板同时配「机构」（可预筛）与「关键词」（不可预筛，ES 字段 `keyword`）两个必填变量。
- 操作步骤：切到该模板，打开开关。
- 预期结果：徽标只有「有机构」一个；下方灰色小字「另有 1 个必填字段无法预筛（keyword），这些专家仍可能在发送时被拦下」。
- 覆盖：I4b-4、P4a 的 I4a-3

### A4b-5: 手动执行的「已修改」标红
- 前置条件：存在一条 `gateFilterEnabled = true` 且模板有门禁字段的定时任务。
- 操作步骤：「手动执行」→ 采用该配置 → 观察门禁字段 → 关掉开关 → 再打开。
- 预期结果：采用后开关为开、无红框；关掉后出现红框 + 「已修改」+ 「邮件模版门禁过滤: 开启」原配置行；再打开后全部消失。
- 覆盖：I4b-5、IP-4

### A4b-6: 任务列表三态 pill
- 前置条件：三条任务 —— A（有模板 + 开关开）、B（有模板 + 开关关）、C（模板为空）。
- 操作步骤：看「定时任务」列表的「收件范围」列。
- 预期结果：A 行有蓝色 pill「门禁过滤 · 开」；B 行有灰色 pill「门禁过滤 · 关」；C 行有虚线灰 pill「模板无门禁字段」。三者均在 `.batch-task-scope-line` 的独立一行。
- 覆盖：S4b-2

### A4b-7: 保存与回显
- 前置条件：接 A4b-2。
- 操作步骤：开关打开 → 保存 → 重新打开该任务。
- 预期结果：开关仍为开、标签「已开启」、徽标仍是两个。
- 覆盖：IP-5

### A4b-8: 快速切换不出现陈旧结果
- 前置条件：接 A4b-2。
- 操作步骤：快速连续切换模板下拉 5 次（每次间隔 < 0.5 秒），最后停在某个模板。
- 预期结果：最终显示的徽标与预估数字属于**最后选中**的模板；中途不出现闪烁的错配结果；排除数不为负数。
- 覆盖：I4b-2、N4b-3

### A4b-9: 回归 —— 专家列表页「按模板门禁」筛选器不变
- 前置条件：无。
- 操作步骤：专家列表页顶部工具栏使用「按模板门禁」下拉选一个模板、再切回「不限」。
- 预期结果：字段 chip 联动、「符合 N / M」摘要、切回「不限」恢复手动 chip 选择 —— 全部与改动前一致。
- 覆盖：N4b-1

### A4b-10: UI 目测 —— 开关与既有任务启停开关一致
- 前置条件：无。
- 操作步骤：把门禁开关与「定时任务」列表里既有的任务启停开关（`.batch-task-status-toggle`）并排截图对比。
- 预期结果：轨道 36×20、滑块 16×16、圆角、开启态主色、滑块位移、键盘聚焦光晕**逐项一致**（同一套 CSS）。
- 覆盖：S4b-1、N4b-5
