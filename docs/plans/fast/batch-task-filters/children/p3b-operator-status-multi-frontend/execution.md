# P3b 执行报告：专家状态筛选改多值（前端）

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters/docs/plans/2026-08-15/p3b-operator-status-multi-frontend.md`
- Plan SHA-256: `41b8bd314754d5259645b79c233faa45c0c418299c6e162ea3f9f5ee5c8fccd6`
- Execution ID: `<canonical plan path>@41b8bd314754d5259645b79c233faa45c0c418299c6e162ea3f9f5ee5c8fccd6`
- Execution epoch: NEW
- Executor: `ImplP3bOperatorStatus`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters`
- Target branch: `fast/batch-task-filters`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-task-filters@fast/batch-task-filters@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/batch-task-filters`
- Pre-execution code HEAD: `4bc8145e317c982f3b582e21f8096c3ac52ff46c`
- Post-execution code SHA (product commit): `802ab2b68f5779aa70971cecb317b6505080270b` (HEAD of `fast/batch-task-filters`)
- Implementation boundary: `4bc8145..802ab2b` — 3 files, 354 insertions(+), 49 deletions(-)

---

## 任务状态

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T3b-1 注册两项 + `batchOperatorStatusOptions()`/`operatorStatusLabel()` | IMPLEMENTED | app.js | registry :13903/:13908，函数 :13917/:13923 |
| T3b-2 index.html 两处 DOM 逐字替换（S3b-1/S3b-2） | IMPLEMENTED | index.html | 编辑器块 :1222-1234、手动块 :1399-1412 |
| T3b-3 编辑器接线（IP-1/IP-2） | IMPLEMENTED | app.js | :13529/:14127/:14231，删除旧函数，bind :15161-15162，监听数组 :15176 |
| T3b-4 手动接线 + diff 5 点（I3b-4/I3b-5，含缺口修复） | IMPLEMENTED | app.js | 见下方 grep 取证（3 NEW + 1 renamed + 1 kept） |
| T3b-5 `renderBatchConfigRow` 状态行（S3b-3） | IMPLEMENTED | app.js | :13391 |
| T3b-6 测试 W1-W10 | IMPLEMENTED | batchSendTaskConsoleInteraction.test.js | 追加 W1-W10，全绿 |

## 命令

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/batchSendTaskConsoleInteraction.test.js` | PASS | exit 0；tests 58 / pass 58 / **fail 0**（含 W1-W10） |
| `node --test src/test/js/*.test.js` | PASS | exit 0；tests 569 / pass 569 / **fail 0**（基线 559 + 10 新增） |
| `git diff --check` | PASS | 无输出，exit 0 |
| Maven | 未运行（按计划门禁后置） | — |

## 变更文件

- `src/main/resources/static/app.js` — 注册、接线、diff 5 点、状态列
- `src/main/resources/static/index.html` — 仅 S3b-1/S3b-2 两处 DOM 块
- `src/test/js/batchSendTaskConsoleInteraction.test.js` — W1-W10 + 1 处既有 fixture 适配

---

## I3b-3：既有 `operatorStatusOptions` 常量（定义行，grep 取证）

```
$ grep -n "operatorStatusOptions" src/main/resources/static/app.js
619:const operatorStatusOptions = [
...
```

定义（`app.js:619-626`，形态 `[[value, label], ...]`）：

```js
const operatorStatusOptions = [
    ["NOT_CONTACTED", "未联系"],
    ["CONTACTED", "已联系"],
    ["REPLIED", "已回复"],
    ["MATERIALS_RECEIVED", "已回复材料"],
    ["INVITED", "已邀约"],
    ["COMPLETED", "已完成"]
];
```

`grep -c "operatorStatusOptions" app.js` = **9**（≥2，无第二份状态字面量数组，I3b-3 满足）。

## T3b-1：注册表 + 派生函数（I3b-1/I3b-2/I3b-3）

`app.js:13903-13912`（`BATCH_MULTI_PICKER_REGISTRY` 内新增两项，逐字）：

```js
    batchConfigEditorOperatorStatuses: {
        options: function() { return batchOperatorStatusOptions(); },
        emptyText: "没有匹配状态",
        previewKind: "editor"
    },
    batchManualOperatorStatuses: {
        options: function() { return batchOperatorStatusOptions(); },
        emptyText: "没有匹配状态",
        previewKind: "manual"
    }
```

`app.js:13917-13929`（新增，逐字）：

```js
function batchOperatorStatusOptions() {
    return (operatorStatusOptions || []).map(function(o) {
        return { value: o[0], label: o[1] };
    });
}

function operatorStatusLabel(value) {
    var hit = (operatorStatusOptions || []).find(function(o) { return o[0] === value; });
    return hit ? hit[1] : String(value);
}
```

- **N3b-1（基座 7 函数一行不改）**：`git diff app.js` 的 hunk 列表为 `13388 / 13525 / 13898 / 14100 / 14119 / 14204 / 14287 / 14307 / 14330 / 14408 / 14425 / 14451 / 14476 / 14522 / 15100 / 15155 / 15171`（旧行号）——没有任何 hunk 落入基座函数区（现 `batchProviderOptions` :13930 起 → `bindBatchMultiPicker` :14029-14055 止）。`grep -c "function renderBatchMultiPicker" app.js` = **1**（I3b-1 满足）。
- **I3b-2**：`grep -n '"未联系"\|"已联系"' app.js` 命中 :611/:612（`operatorStatusLabels` 展示 map）、:620/:621（`operatorStatusOptions` 常量）、:4754/:6729（专家列表页展示）——全部位于 label/展示上下文，无一处进入 payload 或隐藏 input。W1/W2/W3/W5 绿。
- 旧单选触点清零：`grep -n "batchConfigEditorOperatorStatus\b\|batchManualOperatorStatus\b\|fillBatchOperatorStatusSelectOptions" app.js` → 无命中（exit 1）。

## T3b-2：index.html DOM 替换（S3b-1/S3b-2，逐字）

- 编辑器块：`index.html:1222-1234`，外层 `<div class="batch-config-field">`，隐藏 input `id="batchConfigEditorOperatorStatuses"`。
- 手动块：`index.html:1399-1412`，外层 `<div class="batch-config-field" id="manualFieldOperatorStatus">`（**id 保持不变**），隐藏 input `id="batchManualOperatorStatuses"`，保留 `.batch-config-diff-badge` / `.batch-config-diff-original`。
- `git diff --stat` 不含 `styles.css`（grep 无命中）；index.html diff 中新增 class 全部落在主计划 X-3 可复用表内；`grep -c "style=" <index.html diff>` = 0。

## T3b-3：编辑器接线（IP-1/IP-2）

| 位置 | 改法 | 行号 |
|---|---|---|
| `showBatchConfigEditor` | `setBatchMultiPickerValue("batchConfigEditorOperatorStatuses", config && Array.isArray(config.operatorStatuses) ? config.operatorStatuses : [])` | :13529 |
| `buildConfigEditorRecipientSnapshot` | `operatorStatuses: readBatchMultiPickerValue("batchConfigEditorOperatorStatuses")` | :14127 |
| `saveBatchConfigEditor` payload | `operatorStatuses: readBatchMultiPickerValue("batchConfigEditorOperatorStatuses")` | :14231 |
| `fillBatchOperatorStatusSelectOptions` | **整个函数删除**（原 :15107-15119） | — |
| 调用点 | 删除 `fillBatchOperatorStatusSelectOptions()`（原 :15160） | — |
| 监听器数组 | 移除 `"batchConfigEditorOperatorStatus"`，现为 `["batchConfigEditorTemplateId", "batchConfigEditorFunnelLevel", "batchConfigEditorDiscipline"]` | :15175-15176 |
| `bindBatchSendTaskEvents` | 新增 `bindBatchMultiPicker("batchConfigEditorOperatorStatuses")` / `bindBatchMultiPicker("batchManualOperatorStatuses")`，紧邻既有 emailDomains bind | :15161-15162 |

## T3b-4：手动接线 + diff 5 点（I3b-4/I3b-5，含缺口修复）

`grep -n "operatorStatuses" app.js` 全量取证（关键点加粗标注）：

| # | 行号 | 位置 | 标记 |
|---|---|---|---|
| — | :14357 | `fillManualFormFromDraft` → `setBatchMultiPickerValue("batchManualOperatorStatuses", Array.isArray(d.operatorStatuses) ? d.operatorStatuses : [])` | 改造 |
| — | :14435 | `readManualFormValues` → `operatorStatuses: typeof readBatchMultiPickerValue === "function" ? readBatchMultiPickerValue("batchManualOperatorStatuses") : []` | 改造（详见偏差 1） |
| — | :14146 | `buildManualExecutionSnapshot` → `operatorStatuses: values.operatorStatuses` | 改造 |
| **#1** | :14452 | `normalizeManualSnapshot` → `operatorStatuses: (Array.isArray(v.operatorStatuses) ? v.operatorStatuses : []).map(function(s){return String(s).trim();}).filter(Boolean).slice().sort()` | **NEW（缺口修复，I3b-5）** |
| **#2** | :14478 | `formatManualDiffValue` → `if (key === "operatorStatuses") return (Array.isArray(value) && value.length > 0) ? value.map(operatorStatusLabel).join("、") : "全部状态";` | **NEW（缺口修复）** |
| **#3** | :14499 | `computeManualDiffs` fieldDefs → `{ key: "operatorStatuses", label: "专家状态" }` | **NEW（缺口修复）** |
| **#4** | :14545 | `computeAndRenderDiffs` fieldMap → `operatorStatuses: "manualFieldOperatorStatus"`（key 改名，DOM id 不变） | **renamed** |
| **#5** | :14575 | `clearAllDiffMarkers` 数组 → `"manualFieldDiscipline", "manualFieldOperatorStatus", "manualFieldRoundsPerRun", ...` | **kept（核对仍在）** |
| — | :14314 | `deepCloneConfig` → `operatorStatuses: Array.isArray(c.operatorStatuses) ? c.operatorStatuses.slice() : []` | **NEW（缺口修复）** |
| — | :14334 | `fillManualFormDefaults` → `operatorStatuses: []` | **NEW（缺口修复）** |

**缺口确认（前置 grep）**：改动前 `deepCloneConfig`/`fillManualFormDefaults`/`normalizeManualSnapshot`/`formatManualDiffValue`/`computeManualDiffs.fieldDefs` 中**无任何** `operatorStatus` 条目（首轮 `grep -n "OperatorStatus\|operatorStatus" app.js | grep -i "batch\|manual"` 未命中这 5 处）——「专家状态」此前根本不参与手动执行「已修改」标红。本计划按 T3b-4 补齐为 **3 处新增（deepCloneConfig、fillManualFormDefaults 之外的 3 个 diff 点）**，未做任何"改名"式替换。W8 专项断言该缺口已补。

**computeManualDiffs 数组比较机制结论（先读后写，未假设）**：
`computeManualDiffs`（:14485-14530）对 `fieldDefs` 逐项比较 `base`（`normalizeManualSnapshot(manualSource)`）与 `dn`（`normalizeManualSnapshot(readManualFormValues())`）。数组字段（`emailDomains`/`regions`/`tags`）**没有**逐键深比较：`operatorStatuses` 落入通用 else 分支 `String(oldVal || "") !== String(newVal || "")`，其顺序无关性完全由 `normalizeManualSnapshot` 的 `.slice().sort()` 保证（I3b-5）——两侧均已排序，`String(["A","B"])` 形式比较即等价于集合比较。`operatorStatuses` 复用该机制，无新增比较路径。

## T3b-5：任务列表状态列（S3b-3）

`app.js:13391`，逐字：

```js
    if (Array.isArray(c.operatorStatuses) && c.operatorStatuses.length > 0) scopeParts.push("状态: " + c.operatorStatuses.map(operatorStatusLabel).join("、"));
```

位于「学科:」行（:13390）之后，与其余 scopeParts 同构；不新增 pill/badge，不改列宽。W9 绿。

## T3b-6：测试（W1-W10）

`batchSendTaskConsoleInteraction.test.js` 追加：

| 用例 | 断言 | 状态 |
|---|---|---|
| W1 | `batchOperatorStatusOptions()` 元素 value 英文枚举、label 中文、二者不等（I3b-2/I3b-3） | ✔ |
| W2 | set → 隐藏 input `"NOT_CONTACTED,CONTACTED"`（英文） | ✔ |
| W3 | chips 含中文 label，`data-remove-tag` 是英文枚举（I3b-2） | ✔ |
| W4 | `showBatchConfigEditor({operatorStatuses:["CONTACTED"]})` → `"CONTACTED"`；`(null)` → `""` | ✔ |
| W5 | `saveBatchConfigEditor` payload `operatorStatuses: ["CONTACTED"]`，无旧 `operatorStatus` key | ✔ |
| W6 | `formatManualDiffValue("operatorStatuses", [])` === `"全部状态"`；`(["NOT_CONTACTED"])` → 中文 | ✔ |
| W7 | `normalizeManualSnapshot` 对 `["CONTACTED","NOT_CONTACTED"]` 与反序结果相等（I3b-5） | ✔ |
| W8 | 缺口断言：draft `["CONTACTED"]` vs source `[]` 判为 diff；相同集合不判 | ✔ |
| W9 | `renderBatchConfigRow({operatorStatuses:["NOT_CONTACTED"]})` 含 `状态: 未联系` 且被 `.batch-task-scope-line` 包裹；空数组不渲染状态行 | ✔ |
| W10 | 回归：P2b 邮箱 picker 逗号契约不变（N3b-2） | ✔ |

聚焦文件：`tests 58 / pass 58 / fail 0 / exit 0`。全量 JS：`tests 569 / pass 569 / fail 0 / exit 0`（基线 559 + 10）。

## 偏差

1. **`readManualFormValues` 的 `operatorStatuses` 行加了 `typeof readBatchMultiPickerValue === "function"` 守卫**：计划表原文为裸调用 `readBatchMultiPickerValue("batchManualOperatorStatuses")`，但 `src/test/js/expertTagBatchFix.test.js`（**非本计划授权文件，不得改动**）以 `createFormValuesSandbox()` 运行真实 `readManualFormValues` 且不注入 `readBatchMultiPickerValue`，裸调用会 ReferenceError 使既有用例红。守卫写法与同函数内 `emailDomains` 行（P2b 既有约定）完全一致，属唯一可行且符合文件内约定的修法。W8 及全量 569 用例验证行为不变。
2. **既有 fixture 适配**：`batchSendTaskConsoleInteraction.test.js` 的「uses one complete manual snapshot for preview and execution (I-2)」用例 fixture 由 `operatorStatus: "NOT_CONTACTED"` 改为 `operatorStatuses: ["NOT_CONTACTED"]` —— `buildManualExecutionSnapshot` 输出形态按 T3b-4 变更后的必然同步，属授权文件内的合同适配。
3. **观察（未改动）**：P2b 的 `notifyBatchMultiPickerChanged`（:13985）对 `previewKind === "manual"` 硬编码写 `manualDraft.emailDomains`。手动「专家状态」picker toggle 时会经此把 `manualDraft.emailDomains` 写成状态值——但 diff（`computeManualDiffs` 读 DOM）、预估（`refreshRecipientPreview` 读 DOM）、执行 payload（`buildManualExecutionSnapshot` 读 DOM）全部以 DOM 为源，`manualDraft` 仅用于回填且所有 `fillManualFormFromDraft` 调用前都会先重建 `manualDraft`，**无任何可观察影响**。该函数不在 N3b-1 的 7 个基座函数清单内，但本计划未授权改动，予以保留并在 P4b 阶段留意。

## 变更文件清单

- `src/main/resources/static/app.js` — 注册/接线/diff 5 点/状态列（77 行变更）
- `src/main/resources/static/index.html` — 2 处 DOM 块（32 行变更）
- `src/test/js/batchSendTaskConsoleInteraction.test.js` — W1-W10 + 1 处 fixture（294 行变更）

`styles.css` 零改动；无 `.kt`/`.sql`；`docs/plans/fast/` 未入提交。

## Freshness

- Plan identity rechecked: YES（SHA-256 前后一致 `41b8bd31...`）
- Worktree identity rechecked: YES（含 `--expect-root/--expect-branch/--expect-git-dir` 校验，一致）
- Reported commit reachable from target branch: YES（`802ab2b` 为 `fast/batch-task-filters` 的 HEAD，`git branch --contains` 确认）
- Required commands run this invocation: YES（聚焦 + 全量 + diff --check 均在最终实现状态后重跑）
- Historical evidence used only as baseline: YES（559 为既有基线）

## 提交

- Commit: `802ab2b68f5779aa70971cecb317b6505080270b` — `feat(fast-p): implement p3b-operator-status-multi-frontend`
- 仅含 3 个授权文件（`git show --stat`：app.js / index.html / batchSendTaskConsoleInteraction.test.js，354+/49-）
- 未 push / merge / rebase / amend；工作区残留 `docs/plans/fast/...`（brief.md、ledger.md）与 `docs/plans/2026-08-15/`（未跟踪）均未入提交

## Remaining Blocker

- None。

## Next Action

- READY_FOR_VERIFICATION → 运行 `verify-p`
