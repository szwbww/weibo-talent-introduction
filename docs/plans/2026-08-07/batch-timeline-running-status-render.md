# 批量执行日志：时间线行不再渲染"运行中"

> 计划类型：单期可独立交付与验证，纯前端渲染层，2 文件。
> 前置：`docs/plans/2026-08-06/batch-execution-log-process-visibility-p1.md`（以下简称「可见性 P1」）已合入。
> **本计划修订可见性 P1 的 S-1「可选元素规则」**，详见 `## 对前置计划的修订` 一节；
> 已同步在可见性 P1 文件追加 `## 修正记录`。

## 背景（问题来源）

线上手动执行批量发送后，操作端反馈「长期处于 运行中 正在初始化发送队列...」。
经线上实测（需求方执行）：执行 11626 / 11627 / 11628 三次均在亚秒内 `SUCCESS` 完成，
`task_progress_log` 为完整的 INIT + FINAL 成对记录，线程栈无阻塞，
`manual-outreach-1` 已空闲回池。**后端无故障**。

操作端看到的那一行是日志抽屉时间线的 `INIT` 行，其状态列渲染自该行持久化的
`status = "RUNNING"`，经 `statusLabel()` 映射为「运行中」——它是历史快照的如实渲染，
不是实时状态，且刷新页面永不改变。

进一步审计发现问题范围大于 INIT 行：`ManualInitialOutreachService` 的全部轮内 / 轮末进度行
调用均硬编码传 `"RUNNING"`（`:264` / `:340` / `:353` / `:537` / `:600` / `:728` / `:742`），
仅 `batchNumber == 0` 的终态行传 `finalStatus`（`:186` / `:448`）。
因此一次已完成执行的时间线形如：

```
初始化   15:25:57  运行中   正在初始化发送队列...        已处理 0
批次 #1  15:26:03  运行中   第1轮完成，已发送 12 封      已处理 12
结束     15:26:41  已完成   发送任务已完成              已处理 12
```

状态列仅在终态行承载信息，在 INIT / ROUND 行是恒定噪声，且恰好读作"正在运行"。

---

## 需求描述

### Observable outcome

1. 已结束执行的日志时间线中，不再出现任何「运行中」字样；行的语义由阶段标签
   （`初始化` / `批次 #N` / `结束`）、时间、消息文本与已处理数表达。
2. 正在运行的执行，实时状态仍且仅由日志抽屉顶部的 live 区块表达
   （状态徽标「运行中」、轮次、进度条、取消按钮）。

### What must NOT change

1. `statusLabel()` 自身的映射表与函数签名（`app.js:14126-14136`）。它被多处共用，
   现有断言：`batchExecutionLogTimeline.test.js:150-162`、`batchSendControls.test.js:156-158`、
   `taskModalTwoLevelUi.test.js:102`（后者为 `renderRunRow`，另一函数）。
2. live 区块 `#batchLogLive` 的「运行中」徽标文案与显隐逻辑
   （`renderBatchLiveSection`，`app.js:13961-13967`；断言 `batchManualExecutionLog.test.js:297`）。
3. 进度行的 INIT / ROUND / FINAL 三分类、同批次去重与按 `id` 升序
   （可见性 P1 的 I-1；后端 `BatchSendConfigController.buildProgressRows`，`:134-147`；
   断言 `BatchSendExecutionDetailTest.kt:93-112`）。
4. `FAILED` / `CANCELLED` 行的 `is-failed` 底色判定与其状态文案渲染
   （`app.js:14104`，判定读 `r.status`，与本改动正交）。
5. `.batch-timeline-stop`（终止原因）与 `.batch-timeline-errors`（错误样例）的
   条件输出规则（可见性 P1 的 S-1）。
6. 后端任何代码：`task_progress_log` 的写入内容、`toExecutionProgressRow` 的字段映射
   （`BatchSendConfigController.kt:266-281`，逐字透传 `status` / `message`）均不改动。
   本计划**不触碰任何 `.kt` 文件**。
7. 批量发送的任何写路径（发信、contact 状态流转、mail_record、ES 回写）。

### Out of scope（显式延后）

- **改写后端持久化的 `message` 文案**（如"正在初始化发送队列..."本身）。它是历史数据，
  渲染层不应改写；且改后端文案会波及 `BatchSendExecutionDetailTest.kt:98/110/256` 的断言。
- **手动执行 tab 的"最近执行/日志"再入口（跨执行历史列表）**。
  这是**已关闭的决策**，不是本计划的遗漏：`batch-manual-execution-observability-p2.md`
  的 Out of scope 明确写有「独立手动执行的历史记录列表（跨执行浏览）；本期独立执行只支持
  「按 executionId 直达」」。该期已交付「执行后日志抽屉自动打开并停在本次执行，
  无论是否关联配置」（`confirmManualExecution`，`app.js:13692-13697`）。
  若需重开此决策，应另立计划并说明理由，本计划不得顺手扩围。
- **ES `RestTemplate` 无 connect/read timeout**（`RestTemplateConfig.kt:40` 的裸 `RestTemplate()`，
  为全仓唯一未设超时的出站客户端）与 **`task_execution` 无启动时孤儿行清扫**
  （`TaskExecutionRepository` 无相应查询，`BatchSendControlService.restartRecoveryOnStartup`
  只归一 KV 运行时状态）。两者均为**观察项**，与本次现象无因果关系（已由线上实测排除），
  需独立立项评估，不在本计划内。

---

## 对前置计划的修订

可见性 P1 的 `## 样式契约 S-1` 规定了时间线行的 DOM 骨架，其中
`<span class="batch-timeline-status">已暂停</span>` 出现在 `.batch-timeline-main` 内，
且「可选元素规则」只声明 `.batch-timeline-stop` 与 `.batch-timeline-errors` 为条件输出，
隐含 `.batch-timeline-status` 为**无条件输出**。

本计划将 `.batch-timeline-status` 改为**条件输出**（`status !== "RUNNING"` 时才输出）。

- 修订对象：可见性 P1 `## 样式契约` → `### S-1` → 「可选元素规则」。
- 修订内容：可选元素集合由 `{stop, errors}` 扩展为 `{status, stop, errors}`，
  其中 `status` 的输出条件为 `r.status !== "RUNNING"`。
- 理由：可见性 P1 的目标是让 `batchNumber == 0` 的行可见（终止原因不再不可读），
  其 S-1 的 DOM 样例用的是终态行（`已暂停`），未考虑 INIT / ROUND 行的 status 恒为 `RUNNING`。
  无条件输出导致已完成执行的每一行都显示「运行中」。
- 已在 `docs/plans/2026-08-06/batch-execution-log-process-visibility-p1.md` 追加 `## 修正记录` 回指本计划。

---

## 关键不变量

### Invariant I-1: 时间线状态元素只在承载信息时输出

- Rule: `renderBatchTimeline` 中，`r.status === "RUNNING"` 的行**不得**输出
  `.batch-timeline-status` 元素（不输出标签本身，而非置空或隐藏）；
  其余一切 `status` 取值（含 `""`、`null`、未知字符串）**一律**经
  `escapeHtml(statusLabel(r.status || ""))` 照常输出，映射逻辑不变。
- Applies to: `app.js:renderBatchTimeline`（`.batch-timeline-status` 的全仓唯一产出点，
  grep `batch-timeline-status` 共 2 处：产出 `app.js:14110`，样式定义 `styles.css:9038`）。
- Violation consequence: 已完成执行的 INIT 与全部 ROUND 行继续读作"正在运行"，即本次误判的直接成因；
  若改为 `visibility/display:none` 隐藏而非不输出，`.batch-timeline-main` 的
  `gap: 4px`（`styles.css:9046`）会留下 4px 空隙。
- 来源: original

### Invariant I-2: liveness 的唯一表达者是 live 区块

- Rule: `renderBatchTimeline` 只消费 `progressRows` 数组元素自身的字段
  （`kind` / `batchNumber` / `status` / `message` / `stopReason` / `processedCount` /
  `batchProcessed` / `errors` / `createdAt`）。
  **禁止**读取 `d.status`、`d.live` 或任何执行级状态来推导行的显示。
  实时态由 `renderBatchLiveSection`（`app.js:13961-13967`：`if (!d.live) { live.hidden = true; return; }`）独占表达。
- Applies to: `app.js:renderBatchTimeline`；`app.js:renderBatchExecutionDetail`（`:13950-13959`，调用顺序不变）。
- Violation consequence: 若执行 agent 改为"把执行级状态传进时间线"，轮询半途会出现
  time line 与 live 区块互相矛盾的状态；且 `batchExecutionLogTimeline.test.js` 的沙箱
  （`createTimelineSandbox` 只注入 `renderBatchTimeline` / `renderIntegrityWarning` / `statusLabel`
  三个函数，见测试文件 `:17-40`）将无法再独立测试该函数，属结构性回归。
- 来源: original；与 `K-execution-detail-running-needs-progress-log` 的
  "运行中 / 终态分层取数"同向

---

## 样式契约

### S-1: 时间线行（`.batch-timeline-row`）状态元素条件化

- **复用**（全部为既有 class，本计划**不修改任何规则块**，`styles.css` 的 diff 必须为空）：

  | class | styles.css 行号 |
  |---|---|
  | `.batch-timeline` | 9033 |
  | `.batch-timeline-row` | 9034、9042（`align-items`） |
  | `.batch-timeline-row:last-child` | 9035 |
  | `.batch-timeline-batch` | 9036 |
  | `.batch-timeline-time` | 9037 |
  | `.batch-timeline-status` | 9038 |
  | `.batch-timeline-count` | 9039 |
  | `.batch-timeline-row.is-phase` | 9043 |
  | `.batch-timeline-row.is-failed` | 9044 |
  | `.batch-timeline-phase` | 9045 |
  | `.batch-timeline-main` | 9046 |
  | `.batch-timeline-message` | 9047 |
  | `.batch-timeline-row.is-failed .batch-timeline-message` | 9048 |
  | `.batch-timeline-stop` | 9049 |
  | `.batch-timeline-errors` | 9050 |

- **新增**：无。本计划不新增任何 class、不新增任何 CSS 规则。
- **`.batch-timeline-status` 使用点声明**（grep `batch-timeline-status`，全仓共 2 处）：
  定义 `styles.css:9038`（`.batch-timeline-status { color: #64748b; }`）；
  产出 `app.js:14110`。本计划**不修改该规则块**（终态行仍需要它），
  只改变其产出条件 → 属"就地保留、条件化产出"，不派生新 class。
- **改动前基线**（`app.js:14109-14116`，逐字）：

```js
        var main = '<span class="batch-timeline-main">' +
            '<span class="batch-timeline-status">' + escapeHtml(statusLabel(r.status || "")) + '</span>' +
            (r.message ? '<span class="batch-timeline-message">' + escapeHtml(r.message) + '</span>' : '') +
            (r.stopReason ? '<span class="batch-timeline-stop">终止原因：' + escapeHtml(r.stopReason) + '</span>' : '') +
            (Array.isArray(r.errors) && r.errors.length > 0
                ? '<pre class="batch-timeline-errors">' + r.errors.map(escapeHtml).join("\n") + '</pre>'
                : '') +
            '</span>';
```

- **改动后代码**（逐字替换上述 8 行，其余行不得改动）：

```js
        var main = '<span class="batch-timeline-main">' +
            (r.status === "RUNNING" ? '' : '<span class="batch-timeline-status">' + escapeHtml(statusLabel(r.status || "")) + '</span>') +
            (r.message ? '<span class="batch-timeline-message">' + escapeHtml(r.message) + '</span>' : '') +
            (r.stopReason ? '<span class="batch-timeline-stop">终止原因：' + escapeHtml(r.stopReason) + '</span>' : '') +
            (Array.isArray(r.errors) && r.errors.length > 0
                ? '<pre class="batch-timeline-errors">' + r.errors.map(escapeHtml).join("\n") + '</pre>'
                : '') +
            '</span>';
```

- **改动后 DOM 骨架**（`.batch-timeline-main` 内部）：

```html
<!-- status === "RUNNING"（INIT 与全部 ROUND 行）：无 status 元素 -->
<span class="batch-timeline-main">
  <span class="batch-timeline-message">第1轮完成，已发送 12 封</span>
</span>

<!-- status 为其他值（FINAL 行）：与可见性 P1 的 S-1 骨架完全一致 -->
<span class="batch-timeline-main">
  <span class="batch-timeline-status">已暂停</span>
  <span class="batch-timeline-message">批量发送已暂停：无可用邮箱账号，请检查并恢复账号。</span>
  <span class="batch-timeline-stop">终止原因：NO_AVAILABLE_ACCOUNT</span>
  <pre class="batch-timeline-errors">发送失败 (a@b.com): TIMEOUT</pre>
</span>
```

- **布局说明**：`.batch-timeline-main` 为 `display:flex; flex-direction:column; gap:4px`
  （`styles.css:9046`），移除首个子元素后其余子元素依次上移，无需任何 CSS 补偿。
  （该结论由 CSS 规则推导，未经渲染实测；A-5 为其目测验收项。）
- **禁止项**：inline style；本契约未声明的新 class；修改 `styles.css` 任何一行；
  用 `display:none` / `visibility:hidden` / 空字符串内容代替"不输出元素"；
  改动 `cls`、`head`、`time`、`count` 四段的任何逻辑。

---

## 现状审计

### `task_progress_log`（本计划只读，不写）

- 相关字段：`status`、`message`、`batch_number`、`processed_count`、`batch_processed`、
  `task_execution_id`、`details_json`、`errors_json`、`created_at`。
- **写路径**（决定了 status 的取值分布，是本缺陷的上游）：
  1. `TaskProgressStore.tryStartWithToken` → `persistProgressLog`
     （`TaskProgressStore.kt:145-156`、`:186-207`）— INIT 行，`status = "RUNNING"`，
     message 由 `BatchSendControlService.kt:356` 传入 `"正在初始化发送队列..."`。
  2. `ManualInitialOutreachService` 轮内 / 轮末 `updateProgressWithAccumulator` —
     `:264`、`:340`、`:353`、`:537`、`:600`、`:728`、`:742`，**七处全部传字面量 `"RUNNING"`**，
     `batchNumber = roundNumber > 0` → ROUND 行。
  3. 同文件终态调用 — 调用起始 `:185`、`:447`，状态参数在 `:186`、`:448`
     （`emptyFinal`），`roundNumber = 0` 且 `batchNumber` 取默认 0 → FINAL 行。
     其余终态路径经 `finalStatus` 传 `PAUSED` / `COMPLETED` / `FAILED` / `CANCELLED`。
  4. `TaskProgressStore.requestCancel` → `persistProgressLog`（`:81-101`）— `status = "CANCELLING"`。
- **读路径**：
  1. `BatchSendConfigController.buildProgressRows`（`:134-147`）→ `toExecutionProgressRow`（`:266-281`）
     — **逐字拷贝** `log.status` 与 `log.message`，不做任何归一化；
     经 `ExecutionProgressRow` DTO 出到前端。
  2. `TaskProgressController.getProgressLogs` — 另一语义读取口，受 `batchOnly` 参数控制，
     被 `task-modal-runtime.js:130`（通用任务进度弹窗）依赖，
     由 `TaskProgressControllerExecutionsTest` 两条用例锁定。
     **本计划不触碰**（来源: K-progress-log-batchonly-two-readers，明确禁止顺手统一两个读取口）。
- **Interaction point**：写路径 2（七处硬编码 `RUNNING`）× 读路径 1（逐字透传）×
  前端 `statusLabel("RUNNING") → "运行中"`（`app.js:14127`）= 本缺陷。
  修复点选在链路最末端的渲染层：前两段都是有意的历史事实记录，改动它们会波及
  后端测试与 `task_progress_log` 语义，代价与风险都远高于渲染层条件化。

### 前端样式盘点

- **可复用 class**：见 S-1 表格（15 条，全部已存在，全部不修改）。
- **设计基准 token 实值**（本区域，取自 `styles.css:9033-9050`）：
  行文字 `#475569`；时间 `#94a3b8`，`min-width: 140px`；状态 `#64748b`；
  批次号 `#2563eb` / `700` / `min-width: 50px`；阶段标签 `#64748b` / `700` / `min-width: 50px`；
  已处理计数 `#1e293b` / `600`；phase 行底色 `#f8fafc`；failed 行底色 `#fef2f2`，
  其内 message 转 `#e11d48`；终止原因 `#d97706` / `600`；
  错误块 背景 `#fff7ed` / 文字 `#c2410c` / `11px` / `line-height: 1.5` / `white-space: pre-wrap`；
  行内边距 `8px 10px`、列间距 `gap: 10px`、字号 `12px`、`align-items: flex-start`；
  行分隔线 `1px solid rgba(15, 23, 42, .04)`，末行 `border-bottom: 0`；
  `.batch-timeline-main` 为 `flex` 列向、`gap: 4px`、`flex: 1`、`min-width: 0`。
- **DOM 结构约定**：`renderBatchTimeline` 以字符串拼接生成，所有插值经 `escapeHtml()`；
  行内固定顺序为 `head`（`.batch-timeline-phase` 或 `.batch-timeline-batch`）→
  `.batch-timeline-time` → `.batch-timeline-main` → `.batch-timeline-count`。
- **改动前基线**：见 S-1 的「改动前基线」代码块（`app.js:14109-14116` 逐字）。

### 既有契约测试盘点（来源: K-ui-removal-retires-obsolete-contract-tests）

- `src/test/js/batchExecutionLogTimeline.test.js`（**共 163 行，已通读**）— 12 条用例，
  覆盖 `renderBatchTimeline` / `renderIntegrityWarning` / `statusLabel`。
  现有断言涉及：函数体不含 `updatedAt` / `startedAt` / `style="`；`is-phase` 与阶段文案；
  `批次 #1` 标签；三行时间渲染；`escapeHtml` 转义；空 `stop` / `errors` 不输出；
  `is-failed` 计数为 2；空态文案。
  **无任何一条断言 INIT / ROUND 行的状态文案**，故本改动不会打破现有断言。
  基线已实测：`node --test` → `# tests 12 / # pass 12 / # fail 0`（node v22.22.3）。
- `src/test/js/batchManualExecutionLog.test.js:297` — 断言 live 区块
  `batchLogLiveStatus.textContent === "运行中"`。本计划不触及该路径，必须保持通过。
- `src/test/js/taskModalTwoLevelUi.test.js:102` — 断言 `renderRunRow`（执行列表行，另一函数）
  输出"运行中"。与本计划无关，必须保持通过。
- `src/test/kotlin/.../BatchSendExecutionDetailTest.kt` — 断言 INIT/ROUND/FINAL 分类、
  同批次去重、id 升序。本计划不改后端，必须保持通过。

---

## 实现方案

### T-1：条件化状态元素（遵循 I-1、I-2、S-1）

文件：`src/main/resources/static/app.js`

按 S-1 的「改动后代码」逐字替换 `renderBatchTimeline` 中 `main` 变量的赋值
（当前 `:14109-14116`）。**只改第 2 行**（status 元素那行），其余 7 行逐字保持。

不得改动：
- `cls` 的计算（`:14102-14104`），`is-failed` 仍按 `r.status === "FAILED" || r.status === "CANCELLED"` 判定；
- `head` / `phase` / `count` 的计算（`:14101`、`:14105-14108`）；
- 返回的行骨架拼接（`:14117-14122`）；
- 空数组的早返回与空态文案（`:14096-14099`）；
- `statusLabel` 函数本身（`:14126-14136`）。

### T-2：契约测试（锁定 I-1、I-2）

文件：`src/test/js/batchExecutionLogTimeline.test.js`

在 `describe("renderBatchTimeline (I-5 / S-1)")` 内新增 4 条用例，
复用该文件既有的 `createTimelineSandbox()` 与 `extractFn()` helper（`:10-40`）：

1. **INIT 行（`status: "RUNNING"`）不输出状态元素**：断言输出不含
   `batch-timeline-status`，不含 `运行中`；同时断言仍含
   `<span class="batch-timeline-phase">初始化</span>` 与该行的 message 文本。
2. **ROUND 行（`status: "RUNNING"`）不输出状态元素**：断言输出不含
   `batch-timeline-status`、不含 `运行中`；同时断言仍含 `批次 #1` 与 message 文本。
3. **FINAL 行仍输出状态元素**：对 `PAUSED` / `SUCCESS` / `FAILED` / `CANCELLED` 四种
   status 各构造一行，断言每行均含 `<span class="batch-timeline-status">` 及对应中文标签
   （`已暂停` / `已完成` / `失败` / `已取消`）。
4. **源码级断言守 I-2**：`extractFn("renderBatchTimeline")` 的函数体字符串
   **不包含** `d.live` 与 `d.status`（沿用该文件既有手法——现有用例已用同样方式断言
   函数体不含 `updatedAt` / `startedAt` / `style="`）。

新增用例不得修改现有 12 条用例的任何断言。

---

## 变更文件清单

| # | 文件 | 改动 | 子系统 |
|---|------|------|--------|
| 1 | `src/main/resources/static/app.js` | `renderBatchTimeline` 的 `main` 拼接条件化 status 元素（1 行） | 前端渲染 |
| 2 | `src/test/js/batchExecutionLogTimeline.test.js` | 新增 4 条用例 | 前端渲染 |
| 3 | `docs/plans/2026-08-06/batch-execution-log-process-visibility-p1.md` | 追加 `## 修正记录`（文档，非代码） | — |

代码文件数 2 ≤ 10；子系统数 1 ≤ 2；无新增数据字段；无后端改动；无 CSS 改动。

---

## 验证命令

> 运行时前提：
> - JS 用例用系统 `node` 直接跑（实测环境 node v22.22.3）。
> - Kotlin/Maven 必须用 JDK 11（zulu-11），裸 `mvn` 会构建失败（来源：CLAUDE.md「Commands」）。
> - JS 用例由 `exec-maven-plugin` 绑定在 `test` phase（`pom.xml:188-203`：
>   `bash -lc 'node --test src/test/js/*.test.js'`），故 `mvn test` **推断**已覆盖。
>   注意 `<skip>${skipNodeTests}</skip>`（`pom.xml:201/216/231`）所引用的
>   `skipNodeTests` 属性在 `pom.xml:19-25` 的 `<properties>` 中**未定义**，
>   推断解析为非 true 即不跳过；**此点未经实测**，见下方「首次执行确认项」。
> - `verify.sh` 只跑 `normalizeDiscoveryResultSummary.test.js` 单个 JS 文件，
>   **不覆盖**本计划的时间线用例，不可用作本计划的门禁。

```bash
# 本计划权威门禁（已实测：改动前基线 # tests 12 / # pass 12 / # fail 0）
node --test src/test/js/batchExecutionLogTimeline.test.js

# app.js 语法检查
node --check src/main/resources/static/app.js

# 未被本计划改动但共用相关函数的 JS 用例（回归）
node --test src/test/js/batchManualExecutionLog.test.js
node --test src/test/js/taskModalTwoLevelUi.test.js

# 全量回归（Kotlin + 全部 JS 用例）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check

# 样式文件零改动（S-1 硬性要求）
git diff --exit-code src/main/resources/static/styles.css
```

**通过判据**
- `node --test`：输出 `# fail 0`，且时间线用例数由 12 增至 16。
- `node --check`：退出码 0，无输出。
- `mvn test`：`Tests run: N, Failures: 0, Errors: 0` 且 `BUILD SUCCESS`。
- `git diff --check`：退出码 0，无输出。
- `git diff --exit-code ... styles.css`：退出码 0（无改动）。

**首次执行确认项**：首次跑 `mvn test` 时，确认输出中出现
`node --test src/test/js/*.test.js` 的执行记录。若未出现，说明 `skipNodeTests`
被外部 profile / `settings.xml` 置真，此时本计划的 JS 门禁以上方 `node --test` 单跑命令为准，
并应把该发现回写 `CLAUDE.md`。

**来源**：CLAUDE.md 项目元信息（mvn 命令）+ `pom.xml:188-203`（JS 测试绑定）+
实测（node 单跑命令、node 版本与改动前 12 pass 基线）。

---

## 验收标准

- **I-1**：
  - grep `renderBatchTimeline` 函数体，`batch-timeline-status` 的输出被
    `r.status === "RUNNING" ? '' : ...` 三元包裹；
  - T-2 用例 1、2 通过（RUNNING 行无该元素、无「运行中」）；
  - T-2 用例 3 通过（四种终态 status 均照常输出元素与中文标签）；
  - 全文 grep 确认无 `display:none` / `visibility` / 空 `<span class="batch-timeline-status">` 的替代实现。
- **I-2**：
  - T-2 用例 4 通过（函数体不含 `d.live` / `d.status`）；
  - `git diff` 中 `renderBatchLiveSection` 与 `renderBatchExecutionDetail` 无改动。
- **S-1**：
  - `git diff --exit-code src/main/resources/static/styles.css` 退出码 0；
  - `app.js` 的 diff 仅 1 行，且与 S-1「改动后代码」第 2 行逐字一致；
  - diff 中无新增 class、无 `style="`。
- **回归**：执行「验证命令」节的全量测试命令与两条 JS 回归命令，全部通过。

---

## 人工验收清单

### A-1：已完成执行的时间线无「运行中」

- **前置条件**：存在一次已结束的批量执行。可直接用线上既有记录
  执行 `11626` / `11627` / `11628`（`task_execution.status = SUCCESS`，
  各含 1 条 INIT + 1 条 FINAL 进度行）。
  *来源说明：该三条执行的状态与日志行构成来自需求方线上排查结论，非本计划实测。*
- **操作步骤**：
  1. 打开「批量邮件任务控制台」→「定时任务」tab；
  2. 对应 MATERIAL_REMINDER 配置行点「日志」；
  3. 在执行下拉中选中 `2026/08/07 15:25:57` 那条。
- **预期结果**：时间线共 2 行。
  第 1 行：`初始化` ｜ `2026/08/07 15:25:57` ｜ `正在初始化发送队列...` ｜ `已处理 0`，
  **不含「运行中」三字**；
  第 2 行：`结束` ｜ `已完成` ｜ `没有需要发送材料提醒的专家` ｜ `已处理 0`。
  抽屉顶部 live 区块不显示。刷新页面后结果不变。
- **覆盖**：I-1、Observable outcome 1

### A-2：运行中执行仍能看出正在运行

- **前置条件**：需要一次 `sendable ≥ 2` 的真实执行。
  当前范围（CANDIDATE+APPLICATION + tag=`test`）内唯一候选
  `TEST-LUKAI-18014905480` 昨日已发过材料提醒，会被
  `hasSentMaterialReminder` 去重排除 → sendable=0、执行亚秒结束、**无法观察**。
  故须先构造：新增未发过提醒的专家，或改用新 tag，使 sendable ≥ 2。
- **操作步骤**：
  1. 在「手动执行」tab 选中该配置并「确认并执行」；
  2. 日志抽屉自动打开后，在执行进行中观察。
- **预期结果**：
  - 抽屉顶部 live 区块可见：徽标显示「运行中」、有轮次、进度条与「取消执行」按钮；
  - 时间线中 `初始化` 与 `批次 #N` 行**不显示「运行中」**，只有阶段/批次标签、时间、
    消息文本与已处理数；
  - 执行结束后 live 区块自动消失，时间线末尾出现 `结束` 行并显示终态中文标签。
- **说明**：观察窗口由 `accountRateLimiter.getIntervalMs(account, provider, config.perMailIntervalMs)`
  决定（`ManualInitialOutreachService.kt:346` / `:733` 的 `Thread.sleep(intervalMs)`），
  `config.perMailIntervalMs` 仅为入参种子，动态限流器可覆盖，不保证等值；
  且每轮最后一封后不 sleep。
- **覆盖**：I-2、Observable outcome 2、must-NOT-change 第 2 条

### A-3：异常终态仍显示状态文案与配色（回归）

- **前置条件**：需要一次终态为 `PAUSED` 的执行。
  构造方式：**停用/删除全部发件账号**，使 `mailSenderAccountService.listEnabledAccounts()`
  过滤掉模拟器账号后为空 → `classifyNoSendableOutcome`
  （`ManualInitialOutreachService.kt:775-786`）返回 `StopOutcome("NO_AVAILABLE_ACCOUNT", "PAUSED")`。
  *注意区分*：若账号仍启用而仅额度耗尽，走 `classifyLimitReachedOutcome`（`:788-799`），
  得到的是 `DAILY_LIMIT_REACHED` + **`COMPLETED`**，不是 PAUSED，属另一场景。
- **操作步骤**：① 触发一次手动执行；② 打开该执行日志。
- **预期结果**：`结束` 行显示状态文案「已暂停」，其下一行显示
  `终止原因：NO_AVAILABLE_ACCOUNT`（橙色 `#d97706`、加粗）。
  若构造出 FAILED / CANCELLED 终态，该行底色为 `#fef2f2`，其 message 文字为 `#e11d48`。
- **覆盖**：I-1（非 RUNNING 一律输出）、must-NOT-change 第 4 条、S-1

### A-4：三分类与保序未回退（回归）

- **前置条件**：任一含 ≥2 轮的历史执行（`task_progress_log` 中存在 `batch_number` 为 1 和 2 的行）。
- **操作步骤**：打开其日志抽屉时间线。
- **预期结果**：仍可见 `初始化` 行、每轮一条 `批次 #N` 行（同批次只保留最后一条，不出现重复批次号）、
  `结束` 行，整体按时间升序排列。
- **覆盖**：must-NOT-change 第 3 条（可见性 P1 的 I-1，来源 K-progress-log-batchonly-two-readers）

### A-5：UI 目测对照样式基线

- **前置条件**：任一已结束执行的日志抽屉。
- **操作步骤**：逐项核对时间线区域视觉。
- **预期结果**：
  - 行内边距 `8px 10px`、列间距 `10px`、字号 `12px`；
  - 时间列宽 ≥140px，颜色 `#94a3b8`；
  - `初始化` / `结束` 行底色 `#f8fafc`，阶段标签 `#64748b` 加粗；
  - `批次 #N` 标签 `#2563eb` 加粗；已处理数 `#1e293b` 加粗；
  - 行间分隔线极淡（`rgba(15,23,42,.04)`），最后一行无分隔线；
  - **移除状态元素后，message 上方无多余 4px 空隙**，message 与阶段标签顶端对齐
    （`align-items: flex-start`）。
- **覆盖**：S-1

### A-6：通用任务进度弹窗未受影响（回归）

- **前置条件**：任一可从前端触发的非批量发送任务（如专家发现 / 复核类任务）。
- **操作步骤**：触发该任务，观察通用任务进度弹窗。
- **预期结果**：弹窗内的批次表与执行列表行仍正常显示「运行中」等状态文案
  （该路径走 `task-modal-runtime.js` 与 `renderRunRow`，与本计划无关）。
- **覆盖**：must-NOT-change 第 1 条

---

## 自检清单（Phase 4）

- [x] `关键不变量` 存在，本计划无新增字段/状态，两条不变量均针对渲染条件与职责边界
- [x] `现状审计` 列出 `task_progress_log` 的全部写路径（4 类）与读路径（2 个），均由 grep 复核而非记忆
- [x] 无任务引入未被不变量覆盖的写路径（本计划零写路径）
- [x] 含前端改动 → `样式契约` 存在；唯一被改动的 DOM 元素映射到 S-1
- [x] 无"样式与现有一致 / 参考 XX / 保持风格"类模糊表述；全部为实值、逐字代码块或 `file:line`
- [x] 无新增 class（故无"新增 CSS 需全文逐字"义务）；改动后代码已逐字给出
- [x] 被影响的既有 class `.batch-timeline-status` 已列出全部 2 处使用点，并声明"就地保留、条件化产出、不派生新 class"
- [x] `验证命令` 存在且排在 `验收标准` 之前
- [x] 每条命令均可原样复制执行（含 `JAVA_HOME` 前缀）；已注明来源与通过判据；
      未实测项（`mvn test` 对 JS 用例的覆盖）已显式标注为推断并给出首次确认项
- [x] 本计划未新增测试**类**（在既有文件内新增用例），已给出该文件的单跑命令
- [x] `验收标准` / `人工验收清单` 中的"跑测试"均引用 `验证命令` 节，全文无裸 `mvn test` / `npm test`
- [x] `人工验收清单` 存在；2 条 observable outcome 分别映射 A-1、A-2
- [x] must-NOT-change 7 项 → A-2（第 2 条）、A-3（第 4 条）、A-4（第 3 条）、A-6（第 1 条）；
      第 5、6、7 条由机器验收覆盖（styles/后端零 diff、`git diff --exit-code`、无 `.kt` 改动）
- [x] interaction point（写 RUNNING × 逐字透传 × statusLabel）→ A-1 跨路径场景
- [x] 含前端改动 → A-5 为 UI 目测项，对照 token 实值与改动前基线
- [x] 每条 A-n 可黑盒执行，前置条件给出构造方式，预期结果为实值
- [x] 代码文件数 2 ≤ 10
- [x] 子系统数 1 ≤ 2
- [x] 每个任务按编号引用其不变量与样式契约
- [x] `验收标准` 对每条不变量与样式契约均有检查项
- [x] 文件清单无 "and related files" / "etc."，每个文件具名
- [x] Out of scope 显式延后了三项诱人但非必要的工作，其中"手动执行日志再入口"标注为**已关闭决策**并给出出处
- [x] Phase 0 载入的知识（K-progress-log-batchonly-two-readers、K-ui-removal-retires-obsolete-contract-tests、
      K-execution-detail-running-needs-progress-log、K-batch-console-default-log-selection 等）
      均已在审计/不变量/must-NOT-change 中显式使用或说明；未静默忽略
- [x] 计划已保存至 `docs/plans/2026-08-07/`

### 本计划的证据边界（显式声明）

以下三点**不是**本计划实测所得，执行与验证时应注意：

1. `mvn test` 是否真的执行 JS 用例 — 由 `pom.xml` 绑定与 `skipNodeTests` 未定义推断得出，
   未实际运行（撰写环境无 JDK 11）。见「验证命令」节的首次执行确认项。
2. 移除 status 元素后 `.batch-timeline-main` 的 4px 间隙消除 — 由 `gap: 4px` 的 CSS 语义推导，
   未经渲染实测。A-5 为其目测验收项。
3. A-1 的前置条件（线上执行 11626/11627/11628 的状态与进度行构成）— 来自需求方线上排查结论，
   本计划未直接核对线上数据库。
