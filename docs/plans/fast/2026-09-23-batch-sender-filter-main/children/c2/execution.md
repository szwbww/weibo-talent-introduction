# Fast-P Child Execution — c2（前端：定时与手动多选）

- Child: `c2` / 权威子计划 `docs/plans/2026-09-23/02-batch-sender-filter-frontend.md`（身份 `commit:a58ce98`）
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-batch-sender-filter-main`
- Branch: `fast/2026-09-23-batch-sender-filter-main`
- `child_base_sha`（= 实现前 HEAD）: `47505d3`（c1 的 Code head `248c30a` 之上只有 `47505d3 docs(fast-p): record c1 light verification`，产品代码等同 `248c30a`）
- 实现提交: `feat(fast-p): implement c2` → `75cc1714611ac085341cf372d28c057bb332796d`（`docs/plans/**` 已从该提交排除）
- 结果: **READY_FOR_VERIFICATION**

## 变更文件（仅授权的 3 个）

| 文件 | 变更 |
|---|---|
| `src/main/resources/static/index.html` | S-1/S-2 两个 `batch-config-field` 块；两处标题副文案；11 个资源键 bump |
| `src/main/resources/static/app.js` | +57 / −4：账号预加载、选项派生、registry/绑定、回显/保存/克隆/读取/差异/快照 |
| `src/test/js/batchSenderFilter.test.js` | 新建，16 个用例（真实 `index.html` 源文本 + 真实 `app.js` 函数 + DOM stub） |

`styles.css` 零 diff（`git diff -- src/main/resources/static/styles.css | wc -l` → `0`）。未新建其他文件；未改 Kotlin/迁移/计划/`docs/plans/fast/**`；未 push/merge/rebase/amend。

## 逐文件变更摘要

### `index.html`

1. **S-1**（定时「收件范围」网格内，「邮箱服务商」之后）逐字新增块：`data-tag-picker="batchConfigEditorSenderAccounts"`、`#batchConfigEditorSenderAccountsChips/Search/Dropdown`、隐藏值 input `#batchConfigEditorSenderAccounts`、placeholder「搜索发件邮箱；不选则全部可发送账号」。无新 class、无 inline style。
2. **S-2**（手动「收件范围」内，`manualFieldEmailDomain` 之后）逐字新增块：`#manualFieldSenderAccounts` 包裹、`data-tag-picker="batchManualSenderAccounts"`、四个 `batchManualSenderAccounts*` id，并带既有 `.batch-config-diff-badge`/`.batch-config-diff-original`。
3. 既有副文案按契约替换文字、父元素与 class 原样保留：
   - `.batch-config-editor-section-heading` 下 `<h4>收件范围</h4>` → `所有条件同时生效；已绑定发件账号的专家会跳过；发件邮箱留空使用全部可发送账号`
   - `.batch-manual-section-heading` 下 `<h4>模板与收件范围</h4>` → `邮件类型根据模板自动确定；已绑定发件账号的专家会跳过；发件邮箱留空使用全部可发送账号`
4. 11 个带版本资源键（`index.html:11-15` 5 个 CSS/Link + `:2194-2199` 6 个 script）统一改为同一新值；未新增/删除任何 script 或 link。

### `app.js`

| 位置 | 变更 |
|---|---|
| `batchTaskState` / `resetBatchTaskState` | 新增并跨弹窗重置保留 `preloadedSenderAccounts` |
| `preloadBatchSendLookups` | 独立 `GET /api/mail/sender-accounts`（只读）；成功后写入并 `renderBatchMultiPicker` 两个 picker；失败只 `console.error`，**不清空**已加载列表与 hidden input 历史值 |
| `BATCH_MULTI_PICKER_REGISTRY` | 新增 `batchConfigEditorSenderAccounts`（`previewKind:"editor"`）、`batchManualSenderAccounts`（`"manual"` + `draftKey:"senderAccountCodes"`） |
| `batchSenderAccountOptions()`（新增） | value = 原始 `accountCode`；label = `senderEmail · accountCode`；`enabled === false` 追加「（已停用，本次不会发信）」；不读 `inboundMailboxCode`/`imapUsername`，不去重不分组 |
| `notifyBatchMultiPickerChanged` | 写入键改为 `meta.draftKey \|\| "emailDomains"`（既有 picker 行为逐字不变；仅新手动 picker 声明 `draftKey`） |
| `buildConfigEditorRecipientSnapshot` | `senderAccountCodes: readBatchMultiPickerValue("batchConfigEditorSenderAccounts")` |
| `saveBatchConfigEditor` | payload 增加同字段（POST/PUT 共用同一 payload） |
| `showBatchConfigEditor` | `setBatchMultiPickerValue("batchConfigEditorSenderAccounts", config.senderAccountCodes ?? [])` |
| `deepCloneConfig` / `fillManualFormDefaults` / `fillManualFormFromDraft` | 克隆携带（`.slice()` 复制）；独立手动默认 `[]`；从草稿回填手动 picker |
| `readManualFormValues` | `senderAccountCodes: readBatchMultiPickerValue("batchManualSenderAccounts")` |
| `buildManualExecutionSnapshot` | `senderAccountCodes: values.senderAccountCodes`（与同族 `tags/regions/emailDomains/expertTypes` 同为裸投影） |
| `normalizeManualSnapshot` / `formatManualDiffValue` / `computeManualDiffs` / `computeAndRenderDiffs` / `clearAllDiffMarkers` | 归一化（trim/去空/sort 用于比较）、空值显示「全部可发送账号」、`{key:"senderAccountCodes", label:"发件邮箱"}` 差异项、`manualFieldSenderAccounts` 差异节点映射与清理 |
| `bindBatchSendTaskEvents` | 新增 `bindBatchMultiPicker("batchConfigEditorSenderAccounts")`、`bindBatchMultiPicker("batchManualSenderAccounts")` |

### `src/test/js/batchSenderFilter.test.js`（新建，16 用例 / 5 suite）

- **真实 DOM 契约**：9 个新 id 与 2 个 `data-tag-picker` 在真实 `index.html` 源文本中各恰好 1 次；S-1/S-2 块与契约逐字（空白归一后）比对；块内 class 白名单（仅既有 10 个 class）、无 `style=`、hidden input/aria-controls/listbox 属性齐全；两处副文案正则（保留父元素与 class）；11 个 `?v=` 同值。
- **注册与读取路径**：registry 两项、`bindBatchSendTaskEvents` 两次绑定、`preloadBatchSendLookups` 的账号 endpoint 与两次 re-render，以及 `buildConfigEditorRecipientSnapshot`/`saveBatchConfigEditor`/`showBatchConfigEditor`/`deepCloneConfig`/`fillManualFormDefaults`/`fillManualFormFromDraft`/`readManualFormValues`/`buildManualExecutionSnapshot`/`normalizeManualSnapshot`/`formatManualDiffValue`/`computeManualDiffs`/`computeAndRenderDiffs`/`clearAllDiffMarkers` 的逐点断言。
- **I-1**：同物理收件箱两个逻辑 code → 两个 option（value/label 逐字断言）；停用标注；账号列表为空时历史 code 仍作为 chip 可见且读路径仍返回；预加载成功写入并重渲染；**请求失败不抹掉已加载列表**。
- **I-2**：配置保存 POST payload = `["A"]` → `showBatchConfigEditor` 回显 A → `deepCloneConfig` 携带 A（并断言是副本）→ `fillManualFormFromDraft` 带入 A；历史/未知 code 经回显、预估快照与保存 payload 原样保留；手动改 B → 差异项 `原：A / 新：B` + `is-config-diff` + badge/`原：A` 渲染；预估与执行快照均为 `["B"]`；独立手动空选 `readManualFormValues` 与 POST `snapshot.senderAccountCodes` 均为 `[]`。

## 必需命令与结果

| # | 命令 | Exit | 结果 |
|---|---|---:|---|
| 1 | `node --check src/main/resources/static/app.js` | 0 | 语法通过 |
| 2 | `node --test src/test/js/batchSenderFilter.test.js src/test/js/batchSendTaskConsoleInteraction.test.js` | 0 | 97 tests / 97 pass / 0 fail（16 新 + 81 既有） |
| 3 | `node --test src/test/js/*.test.js` | 0 | 1137 tests / 227 suites / 1137 pass / 0 fail |

### 基线 vs 本次

| 指标 | 基线（`baseline/js.txt`） | 本次 | 差异 |
|---|---:|---:|---|
| `node --check` | exit 0 | exit 0 | — |
| tests | 1121 | 1137 | +16（全部为本 child 新增文件） |
| suites | 222 | 227 | +5（新增 5 个 describe） |
| pass / fail | 1121 / 0 | 1137 / 0 | 0 新增失败、0 既有失败 |

**无「本次新增失败」，也无「基线已失败」。** 过程中出现过一次由本 child 引入的既有测试失败并已消除（见下「过程中的偏差与处理」）。

## 不变量逐条证据

- **I-1（选项以逻辑账号为单位）**：`batchSenderAccountOptions()` 只读 `accountCode`/`senderEmail`/`enabled`；测试断言同一 `inboundMailboxCode`/`imapUsername` 的 `LuKai` 与 `LuKai_QF` 产出两个 option，label 分别为 `lukai@example.com · LuKai`、`lukai.qf@example.com · LuKai_QF（已停用，本次不会发信）`。真实页面同样确认（见下）。历史/停用值不丢：`renderBatchMultiPicker` 对未知 code 走 `{value, label: value}` fallback（未改动的既有实现），且 `preloadBatchSendLookups` 失败分支不清空 `preloadedSenderAccounts`，`showBatchConfigEditor` 不做任何 code 白名单过滤 → 测试覆盖「空列表 + 历史 code」与「账号请求失败保留列表」两条。
- **I-2（所有前端快照同字段）**：`buildConfigEditorRecipientSnapshot`、`saveBatchConfigEditor`（POST/PUT 同一 payload）、`deepCloneConfig`、`readManualFormValues`、`buildManualExecutionSnapshot` 五处全部携带 `senderAccountCodes`（测试逐点断言 id/字段名，并对保存 payload、预估快照、执行快照断言实际值）。空选 `[]`：`readBatchMultiPickerValue` 对空 hidden input 返回 `[]`，`fillManualFormDefaults` 默认 `[]`；真实页面 POST 到 `/api/mail/batch-send/manual-executions` 的 `snapshot.senderAccountCodes` 实测为 `[]`。顺序/去重：hidden input 为逗号串，toggle 语义天然不产生重复，读取按选择顺序返回（归一化仅在差异比较时 sort，不进 payload）。
- **I-3（真实 DOM 与现有样式）**：测试从真实 `index.html` 源文本断言 9 个新 id、2 个 `data-tag-picker`、块内 class ⊆ 既有 10 个 class、无 `style=`；`styles.css` `git diff` 为空；`index.html` 11 个资源键同值（测试断言 11 个 `?v=` 只有 1 个不同值且形如 `<yyyymmdd>-<slug>`）。
- **S-1/S-2**：两个块以空白归一后的逐字契约字符串断言（`assert.ok(normalized(indexSource).includes(normalized(expected)))`）；两处副文案以保留父元素+class 的正则断言；手动块断言保留 `.batch-config-diff-badge`/`.batch-config-diff-original`；`computeAndRenderDiffs` 断言映射到 `manualFieldSenderAccounts`，`clearAllDiffMarkers` 断言包含该 id。

### 真实页面冒烟（静态目录 + 拦截 `/api/**`，headless Chromium）

用 `python3 -m http.server` 提供 `src/main/resources/static`，拦截 `/api/**` 返回：`/api/auth/me` → `{authenticated:true}`；`/api/mail/sender-accounts` → `LuKai`(enabled, 共享 inbox) / `LuKai_QF`(disabled, 同 inbox) / `Other`；`/api/mail/batch-send/configs` → 1 个 `senderAccountCodes:["LuKai"]` 的任务。经真实入口 `#bulkOutreachBtn` → `#batchConfigCreateBtn` / `#batchManualTab` 驱动：

- 账号预加载后编辑器下拉实测 3 项：`lukai@example.com · LuKai`、`lukai.qf@example.com · LuKai_QF（已停用，本次不会发信）`、`other@example.com · Other`（同 inbox 未合并）。
- 点击停用项 → `#batchConfigEditorSenderAccounts` = `LuKai_QF`，chip 文案含「已停用，本次不会发信」，`is-selected` 落在该项；新块内 inline style 计数 = 0。
- 手动页默认空（`""`）；`focus` 即展开下拉（用户路径）；选择来源任务后带入 `LuKai`；再选 `Other` → hidden value `LuKai,Other`，`#manualFieldSenderAccounts` 加 `is-config-diff`、badge 显示、`原：LuKai`。
- 实发 payload：配置保存 `POST .../configs` body `senderAccountCodes:["LuKai"]`（页面提示「保存成功」）；编辑器预估 `POST .../recipients/preview` 同值；独立手动执行 `POST .../manual-executions` body `snapshot.senderAccountCodes:[]` 且 snapshot key 集合含 `senderAccountCodes`。
- 截图（宽屏 1400px，双列）目测：新字段落在既有两列网格、chip/focus/dropdown/「已修改」样式均为既有规则；`/tmp/c2-editor.png`、`/tmp/c2-manual.png`（临时产物，不随提交）。

## 缓存键

- 反查（执行前）：`grep -c '20260922-discovery-continuous' src/main/resources/static/index.html` → `11`；`grep -rn '20260922-discovery-continuous' src/test` → **0 命中**（与计划记载一致，无需修订变更文件清单）。
- 采用的新值：**`20260923-batch-sender-filter`**（`<yyyymmdd>-<slug>`）。
- 执行后：`grep -c '20260923-batch-sender-filter'` → `11`；`grep -c '20260922-discovery-continuous'` → `0`。
- `taskActivityCenter.test.js` 的 I-8 会扫描 `src/test/js/*.js` 禁止固化当前键字面量，故新测试**不写死**键值，改为从 `index.html` 派生（11 个同值 + 格式校验）。

## 过程中的偏差与处理

1. **`batchSendTaskConsoleInteraction.test.js` 的既有用例「uses one complete manual snapshot for preview and execution (I-2)」** 用注入的 `readManualFormValues` stub 与 `deepStrictEqual` 钉住快照键集合。最初实现写了 `Array.isArray(values.senderAccountCodes) ? ... : []`，导致 stub 无该键时快照多出 `senderAccountCodes: []` 而失败。**未改该既有测试**（不在授权文件清单内），改为与同族字段（`tags`/`regions`/`emailDomains`/`expertTypes`）一致的裸投影 `senderAccountCodes: values.senderAccountCodes`；生产中 `readManualFormValues` 恒返回数组，I-2 语义不变（空选仍为 `[]`，真实页面已实测）。改后该文件 81/81 通过。
2. **`notifyBatchMultiPickerChanged` 的 `manualDraft` 写入键**：该函数原本硬编码写 `manualDraft.emailDomains`（对既有状态/类型 picker 亦如此，仅在 DOM 读取覆盖下被掩盖）。新增手动 picker 若沿用会把发件账号写进 `emailDomains`。处理：写入键改为 `meta.draftKey || "emailDomains"`，只给新手动 picker 声明 `draftKey: "senderAccountCodes"`，**既有 6 个 picker 行为逐字不变**（未顺手修既有偏差）。
3. **未做列表摘要「发件邮箱: A/B」**：计划「现状审计」把它列为可选（「可新增」），且会改变 `renderBatchConfigRow` 的 `scopeParts` 顺序/条数（既有用例对作用域行有断言）。本 child 未新增该展示，也未触碰该函数——不属任何验收项。

## 未运行项与原因

- **窄屏单列 / chip / focus / dropdown / 「已修改」数值目测**：子计划明确把视觉验收放在联合验收 **A-4**（宽窄屏各一次）；本 child 只保证复用既有 class 与 DOM 契约（已由真实页面截图 + 源文本断言佐证）。窄屏媒体查询未逐点截图。
- **Maven/Kotlin 测试、Flyway IT、全量 `mvn test`**：不在本 child 的必需命令内（后端由 c1 负责），且并行 sibling 可能同时改动；项目级验证由主 agent 统一执行。
- **formatter/linter**：按分配要求跳过。
- **真实后端联调**（真 MySQL/Spring、真实 `GET /api/mail/sender-accounts` 与 422 未知 code 路径）：本 child 用契约拦截验证前端 payload；后端 422 语义由 c1 计划覆盖，未在本 child 复测。

## 提交

- Commit: `feat(fast-p): implement c2` = **`75cc1714611ac085341cf372d28c057bb332796d`**（仅 `index.html`、`app.js`、`src/test/js/batchSenderFilter.test.js`；`git show --name-only HEAD | grep -c docs/plans` → `0`，`ledger.md` 与 `children/c2/execution.md` 的未提交改动均未纳入）
