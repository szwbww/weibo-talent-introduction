# Execution Report — p4b-template-gate-filter-frontend

## Result

**READY_FOR_VERIFICATION**

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters/docs/plans/2026-08-15/p4b-template-gate-filter-frontend.md`
- Plan SHA-256: `9a26534254c2d8499803a8aa6f81b40133d4898026cbabcf70d49f3658679955` (unchanged before/after execution)
- Execution ID: `…/docs/plans/2026-08-15/p4b-template-gate-filter-frontend.md@9a26534254c2d8499803a8aa6f81b40133d4898026cbabcf70d49f3658679955`
- Execution epoch: NEW
- Executor: `ImplP4bGateFilter`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters`
- Target branch: `fast/batch-task-filters`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters@fast/batch-task-filters@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/batch-task-filters`
- Pre-execution HEAD: `f35cef7702c8f2adf01cc16632fade8fc5ebb0e2`（p4a light-verification docs commit；p4a 代码头为 `9cde7473`）
- Post-execution code SHA / commit: **`2250a66667c691ef2ab826bb3164e76e9f6515b7`** (`feat(fast-p): implement p4b-template-gate-filter-frontend`)，worktree HEAD，仅含 4 个授权文件
- Maven: NOT run（按要求推迟到 merge gate）

## Changed files (commit `2250a66`, exactly the 4 authorized files)

| # | File | Change |
|---|---|---|
| 1 | `src/main/resources/static/styles.css` | APPEND S4b-1 block verbatim（styles.css:9043-9139），0 删除行 |
| 2 | `src/main/resources/static/index.html` | 2 个字段块：editor `#editorFieldGateFilter`（index.html:1234）、manual `#manualFieldGateFilter`（index.html:1431），各自为 grid 最后一个子元素 |
| 3 | `src/main/resources/static/app.js` | 门禁三态 + 双请求预估 + 接线 + diff 5 点 + 列表 pill |
| 4 | `src/test/js/batchSendTaskConsoleInteraction.test.js` | 追加 G1-G14；对既有 I-3/V5/W5/U 系列/V9/W9 沙箱做最小适配（新增依赖注入） |

`docs/plans/fast/…` 未纳入 commit（brief.md、ledger.md 保持未暂存）。

## T4b 逐项证据（file:line）

### T4b-1 CSS 追加 — S4b-1 逐字

- 追加位置：`.batch-tag-picker-empty` 规则块之后（styles.css:9043 起，段末 9139，`.batch-manual-actions` 仍在其后）。
- 逐字校验：以 plan 中 ```css 代码块为基准，与 styles.css 中从 `/* ── 邮件模版门禁过滤…` 到 `.batch-gate-pill.is-na { … }` 结尾的字节序列 diff 为空（`python3` 字节比对输出 `VERBATIM` / `file region minus boundary blank == plan: True`）。git diff 中第 98 行 `+` 空行是块与 `.batch-manual-actions` 之间既存空行的 diff 归因，非规则修改。
- `git diff src/main/resources/static/styles.css`：98 added / **0 deleted**（N4b-5 ✓）。

### T4b-2 index.html — 两个字段块逐字

- editor 块（index.html:1234-1245）：`#editorFieldGateFilter`，`#batchConfigEditorGateFilter`，`#batchConfigEditorGateFilterLabel`（初始「已关闭」），`#batchConfigEditorGateFilterHint`，`#batchConfigEditorGateFilterKeys hidden`。grid 最后子元素 ✓（块后紧跟 grid 闭合 `</div>`）。
- manual 块（index.html:1431-1444）：`#manualFieldGateFilter`，`#batchManualGateFilter`，`#batchManualGateFilterLabel`，`#batchManualGateFilterHint`，`#batchManualGateFilterKeys hidden`，`.batch-config-diff-badge` / `.batch-config-diff-original` 为直接子元素 ✓。grid 最后子元素 ✓。
- 两块的逐字校验：`python3` 以 plan 的 ```html 块为基准 `editor block present verbatim: True` / `manual block present verbatim: True`；`git diff index.html | grep -c 'style='` == **0**（S4b-2 ✓）。

### T4b-3 三态解析 — refreshBatchGateState（I4b-3 / I4b-4）

- `BATCH_GATE_FILTERABLE_FIELDS`（app.js:14122-14125）—— 6 key 与后端逐字一致，见下方对比。
- `batchGateState`（app.js:14127-14128）；`gateToggleId`（app.js:14130）；`gateToggleChecked`（app.js:14134）；`updateGateToggleLabel`（app.js:14139）；`refreshBatchGateState`（app.js:14158）。
- 8 步规格全部实现：① 模板 id（editor `#batchConfigEditorTemplateId` / manual `#batchManualTemplateId`）；② id 空 → 不可用 + return 不发请求；③ `await api("/api/compose-templates/" + id + "/gate-fields")`，异常 → 不可用 + `console.error` 一次、不弹窗（照 `initExpertGateFilter` 失败策略）；④ `esFields` 空 → 不可用（warn 文案「该模板未配置门禁字段（required_keys 为空），门禁本身未启用，开启无效。」）；⑤ 差集拆分，`fields` 空 → 仍不可用（warn 文案「该模板的必填字段均无法预筛（<dropped>）」）；⑥ 可用态：`checkbox.disabled=false`、徽标区渲染 `batch-gate-keys-label` + 每个字段一个 `<span class="tag-chip active">`、`dropped` 非空追加 `.batch-gate-keys-dropped`；⑦ label 同步（不可用/已开启/已关闭）；⑧ 全部路径以 `scheduleRecipientPreview(kind)` 收尾。
- 不可用统一动作：`field.classList.add("is-disabled")`、`checkbox.disabled=true`、`checkbox.checked=false`、`keys.hidden=true`、`hint.classList.add("is-warn")` + warn 文案；可用态反向。
- 实现注记：`fields/dropped` 判定用 `Object.prototype.hasOwnProperty.call(BATCH_GATE_FILTERABLE_FIELDS, f)` 而非 `f in …`（避免原型链 key 误判，对 6 个真实 key 行为与规格一致）。

### T4b-4 预估双请求 — refreshRecipientPreview 改写（I4b-1 / I4b-2 / I4b-6）

- `baseHintHtml`（app.js:14319）：产出与改动前逐字一致（`"当前条件命中 <strong>" + total + "</strong> 位专家（其中未联系 " + pending + "、可重试 " + retryable + "）"`）。
- `refreshRecipientPreview`（app.js:14326-14374）按 plan 逐字实现：seq 在函数开头 `++recipientPreviewRequestSeq[kind]` **自增一次**、两次请求（`post(false)` / `post(true)`）共用同一 seq；`Promise.all` 结算后先比 `seq !== recipientPreviewRequestSeq[kind]` 再渲染；`excluded = Math.max(0, offTotal − onTotal)`（I4b-1）；不可用态 `requests = [post(false)]` 只发一次（I4b-6）；成功与失败分支**都**先比 seq（I4b-2）。
- `recipientPreviewRequestSeq` 结构不变（app.js:13257）；`scheduleRecipientPreview` 函数体零改动行（N4b-3 ✓，见 grep 收据）。

### T4b-5 接线 + 列表 pill（S4b-2）

| 位置 | app.js 行 | 改法 |
|---|---|---|
| `showBatchConfigEditor` | 13550 | `gateCheckbox.checked = Boolean(config && config.gateFilterEnabled)` + `refreshBatchGateState("editor")`（typeof 守卫，兼容既有测试沙箱） |
| `buildConfigEditorRecipientSnapshot` | 14281 | `gateFilterEnabled: gateToggleChecked("editor"),` |
| `saveBatchConfigEditor` payload | 14419 | `gateFilterEnabled: gateToggleChecked("editor"),` |
| `buildManualExecutionSnapshot` | 14301 | `gateFilterEnabled: values.gateFilterEnabled,` |
| `readManualFormValues` | 14631 | `gateFilterEnabled: Boolean(gateCheckboxEl && gateCheckboxEl.checked)`（内联读 `#batchManualGateFilter`；默认 false。内联原因：既有 `expertTagBatchFix.test.js`（非授权文件）直接执行该函数，调 helper 会断其沙箱） |
| `deepCloneConfig` | 14503 | `gateFilterEnabled: c.gateFilterEnabled === true`（默认 false） |
| `fillManualFormDefaults` | 14524 | `gateFilterEnabled: false,` |
| `fillManualFormFromDraft` | 14555 | `gateCheckbox.checked = Boolean(d.gateFilterEnabled)` + label 同步 |
| `bindBatchSendTaskEvents` | 15378-15388 | 两个模板下拉 `change` 追加 `refreshBatchGateState(kind)`；两个开关 `change` 追加 `updateGateToggleLabel(kind)` + `scheduleRecipientPreview(kind)` |
| `renderBatchConfigRow` | 13412 | scopeParts 之后追加 pill 行（恒输出，独立 `.batch-task-scope-line`） |
| `batchGatePillHtml` | 13387-13391 | 三态之一 |

**有意偏离（须记录，plan T4b-5 明示）**：列表蓝 pill 文案为「门禁过滤 · 开」**而非**「门禁过滤 · N 字段」——列表行渲染不发 `gate-fields` 请求，拿不到该行 esFields，显示假数字比不显示更糟（A4b-6 / G13 断言即此文案）。`is-na` 判定退化为 `!c.templateId`。

**实现注记（非行为偏离）**：plan 代码片段 `scopeParts.push(batchGatePillHtml(c));` 若照抄会把 pill 并入 scopeParts，导致无筛选行时「无限制」分支被顶掉（V9/W9 回归断言 `emptyHtml.includes("无限制")` 会红）。按 S4b-2 行为文本「在全部 scopeParts 之后追加一行（始终输出，三态之一）」实现为 scopeHtml 之后追加独立 pill 行（app.js:13411-13412）。

### T4b-6 diff 5 点（I4b-5）

| # | 位置（app.js） | 内容 |
|---|---|---|
| 1 | 14649（`normalizeManualSnapshot`） | `gateFilterEnabled: Boolean(v.gateFilterEnabled),` |
| 2 | 14659（`formatManualDiffValue`） | `if (key === "gateFilterEnabled") return value ? "开启" : "关闭";` |
| 3 | 14698（`computeManualDiffs` fieldDefs） | `{ key: "gateFilterEnabled", label: "邮件模版门禁过滤" },` |
| 4 | 14745（`computeAndRenderDiffs` fieldMap） | `gateFilterEnabled: "manualFieldGateFilter",` |
| 5 | 14775（`clearAllDiffMarkers` fields） | 追加 `"manualFieldGateFilter"` |

### T4b-7 测试 G1-G14

追加于 `src/test/js/batchSendTaskConsoleInteraction.test.js`（describe 末尾）：

| 用例 | 断言核心 | 结果 |
|---|---|---|
| G1 | esFields 空 → disabled/checked=false/keys hidden/hint 含「未配置门禁字段」+ is-warn | PASS |
| G2 | 模板空 → api 调用 0 次 + 不可用态 | PASS |
| G3 | gate-fields 抛错 → 不可用、不弹窗、console.error 恰好 1 次 | PASS |
| G4 | 2 个可筛字段 → 可用 + 2 个 `.tag-chip.active` +「有机构」「有研究方向」 | PASS |
| G5 | 混合字段 → 1 个徽标 + `.batch-gate-keys-dropped` 含 keyword/hIndex | PASS |
| G6 | 全差集 → 不可用 +「该模板的必填字段均无法预筛」 | PASS |
| G7 | 不可用态 → 只发 1 次请求、`gateFilterEnabled:false`、无排除行 | PASS |
| G8 | 可用+开关关 → 2 次请求 `[false,true]`、warnline、排除数 15−7=8 | PASS |
| G9 | 可用+开关开 → `.batch-gate-excluded`、total 用 gate=true 那次（60）、排除 40 | PASS |
| G10 | 两轮在途 → 旧轮结果不渲染（seq 丢弃） | PASS |
| G11 | 任一请求 reject → 「预估失败：boom」、无半份结果 | PASS |
| G12 | `formatManualDiffValue("gateFilterEnabled",true)`==「开启」；draft=true/source=false 判 diff（oldDisplay 关闭/newDisplay 开启）；相同不判 | PASS |
| G13 | `batchGatePillHtml`：`{templateId:null}`→is-na；`{templateId:1,gateFilterEnabled:true}`→蓝 pill「门禁过滤 · 开」；false→is-off | PASS |
| G14 | 回归：P2b 逗号契约 + P3b 状态选项派生仍绿 | PASS |

既有测试最小适配（同文件，均为新契约依赖注入，未弱化断言）：
- I-3（preview 失败/过期）沙箱加 `batchGateState: { editor: {available:false}, manual: {available:false} }` + `gateToggleId` stub；
- V5/W5 + U 系列 4 个 save 测试沙箱加 `gateToggleChecked` stub，V5/W5 并断言 payload `gateFilterEnabled === true`（来自 gateToggleChecked）；
- V9/W9 沙箱加 `batchGatePillHtml: () => ""` stub（各自断言不变）。

## 验收 grep 收据

### 1) `recipients/preview` 字面量 — 只有一个 URL

```bash
$ grep -n "recipients/preview" src/main/resources/static/app.js
14335:        return api("/api/mail/batch-send/recipients/preview", {     # ← 唯一 URL 字面量（refreshRecipientPreview 内）
14841:    // 与预估（recipients/preview）同源（I-2）：两条路径复用同一完整快照。   # ← 既有注释（HEAD 基线即有）
```

`grep -c "recipients/preview"` == 2 但**基线（HEAD）即 2**：`git show HEAD:src/main/resources/static/app.js | grep -c "recipients/preview"` → 2。本实现**没有**为门禁另开任何接口；URL 字面量恰好 1 处（验收标准的本意「只有 refreshRecipientPreview 里那一个 URL 字面量」满足）。

### 2) seq 单点自增（I4b-2）

```bash
$ grep -n "recipientPreviewRequestSeq" src/main/resources/static/app.js
13257:var recipientPreviewRequestSeq = { editor: 0, manual: 0 };     # 结构不变（N4b-3）
14330:    var seq = ++recipientPreviewRequestSeq[kind];          # ← 唯一自增点，refreshRecipientPreview 开头
14345:        if (seq !== recipientPreviewRequestSeq[kind]) return;   # 成功分支比 seq
14370:        if (seq !== recipientPreviewRequestSeq[kind]) return;   # 失败分支比 seq
```

### 3) I4b-5 的 5 个注册点（`grep -n "gateFilterEnabled" app.js` 全量）

```
13389:    if (c.gateFilterEnabled) return '<span class="batch-gate-pill">门禁过滤 · 开</span>';   # S4b-2 pill
13550:    if (gateCheckbox) gateCheckbox.checked = Boolean(config && config.gateFilterEnabled);  # showBatchConfigEditor
14281:        gateFilterEnabled: gateToggleChecked("editor"),       # buildConfigEditorRecipientSnapshot
14301:        gateFilterEnabled: values.gateFilterEnabled,          # buildManualExecutionSnapshot
14337:            body: JSON.stringify(Object.assign({}, snapshot, { gateFilterEnabled: gateOn }))  # refreshRecipientPreview
14419:        gateFilterEnabled: gateToggleChecked("editor"),       # saveBatchConfigEditor payload
14503:        gateFilterEnabled: c.gateFilterEnabled === true,      # deepCloneConfig（默认 false）
14524:        gateFilterEnabled: false,                             # fillManualFormDefaults
14555:    if (gateCheckbox) gateCheckbox.checked = Boolean(d.gateFilterEnabled);  # fillManualFormFromDraft
14631:        gateFilterEnabled: Boolean(gateCheckboxEl && gateCheckboxEl.checked),  # readManualFormValues（默认 false）
14649:        gateFilterEnabled: Boolean(v.gateFilterEnabled),      # ① normalizeManualSnapshot
14659:    if (key === "gateFilterEnabled") return value ? "开启" : "关闭";  # ② formatManualDiffValue
14698:        { key: "gateFilterEnabled", label: "邮件模版门禁过滤" },   # ③ computeManualDiffs fieldDefs
14745:        gateFilterEnabled: "manualFieldGateFilter",           # ④ computeAndRenderDiffs fieldMap
14775:        "manualFieldDiscipline", "manualFieldOperatorStatus", "manualFieldGateFilter", …  # ⑤ clearAllDiffMarkers
```

### 4) ALLOWED_HAS_FIELDS vs BATCH_GATE_FILTERABLE_FIELDS（I4b-4 逐字一致）

```bash
$ grep -n "ALLOWED_HAS_FIELDS\s*=" src/main/kotlin/.../expert/service/ExpertSearchService.kt
24:        val ALLOWED_HAS_FIELDS = setOf("employment", "degree", "institution", "researchFields", "patentTitles", "recentWorkTitles")

$ grep -n -A4 "var BATCH_GATE_FILTERABLE_FIELDS" src/main/resources/static/app.js
14122:var BATCH_GATE_FILTERABLE_FIELDS = {
14123-    employment: "有职位", degree: "有学历", institution: "有机构",
14124-    researchFields: "有研究方向", patentTitles: "有专利", recentWorkTitles: "有近期论文"
14125-};
```

对比：后端 setOf 6 个 key = `employment, degree, institution, researchFields, patentTitles, recentWorkTitles`；前端对象 6 个 key = 同 6 个（顺序一致）。**逐字相同** ✓。

### 5) styles.css diff 仅新增（N4b-5 / S4b-1）

```bash
$ git diff --numstat src/main/resources/static/styles.css
98	0	src/main/resources/static/styles.css     # 98 新增 / 0 删除
```

字节比对：`python3` 对 plan 代码块 vs styles.css 追加区 → `VERBATIM`（见 T4b-1）。

### 6) N4b-1 — app.js diff hunk 不覆盖 :11585-11760

`git diff app.js` 全部 hunk 起始行最小为 **13381**（`@@ -13381,6 …`）> 11760；`populateExpertGateTemplateFilter`/`initExpertGateFilter`（app.js:11589/11612）零改动 ✓。

### 7) N4b-3 — scheduleRecipientPreview 函数体零改动

`git diff app.js | grep -c "^[-+].*recipientPreviewTimers\|^[-+].*clearTimeout\|^[-+].*setTimeout"` → **0**。相关 hunk `@@ -14160,27 +14315,59 @@` 的改动行全部落在 `refreshRecipientPreview`（其后函数）与新增 `baseHintHtml`，`scheduleRecipientPreview` 本身无增减行。

### 8) index.html 无 inline style（S4b-2）

`git diff src/main/resources/static/index.html | grep -c "style="` → **0**。

## 验证命令（最终状态新鲜执行）

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/batchSendTaskConsoleInteraction.test.js` | PASS | exit 0；tests 72，pass 72，fail 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0；tests 583，pass 583，fail 0（基线 569 + G1-G14 共 14 个新用例 = 583） |
| `git diff --check` | PASS | 无输出，exit 0 |
| `mvn` | NOT RUN | 按要求推迟到 merge gate |

## Deviations

- **有意偏离（plan T4b-5 明示）**：列表蓝 pill 文案「门禁过滤 · 开」而非「门禁过滤 · N 字段」（列表行不发 gate-fields 请求，无法得知字段数）。G13 与 A4b-6 即按此断言。
- **实现注记 1**：`scopeParts.push(batchGatePillHtml(c))` 片段改为 scopeHtml 之后追加独立 pill 行，保住「无限制」分支（V9/W9 回归要求），行为与 S4b-2 文本一致。
- **实现注记 2**：`readManualFormValues` 内联读取 `#batchManualGateFilter.checked`（默认 false）而非调用 `gateToggleChecked`，避免破坏非授权测试文件 `expertTagBatchFix.test.js` 的沙箱。
- **实现注记 3**：`fields/dropped` 用 `hasOwnProperty` 而非 `in` 判定。
- **基线差异说明**：`grep -c "recipients/preview"` == 2 系 HEAD 基线既有注释所致，URL 字面量仍为 1（见收据 1）。

## Freshness

- Plan identity rechecked: YES（SHA 前后一致）
- Worktree identity rechecked: YES（commit 前后 --expect-root/--expect-branch/--expect-git-dir 通过）
- Reported commit reachable from target branch: YES（`2250a66` 为 worktree HEAD，父链在 `fast/batch-task-filters`）
- Required commands run this invocation: YES（全部 3 条，最终状态）
- Historical evidence used only as baseline: YES

## Remaining Blocker

- None。

## Next Action

- `verify-p`（独立验证；merge gate 再跑 `mvn test`）。
