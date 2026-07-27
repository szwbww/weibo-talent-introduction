# 子计划 04：前端 — 弹框配置/三按钮/模式与每账号统计 + 列表页暂停 banner

> 主计划：`2026-06-18-scheduled-batch-send-00-master.md`。依赖子计划 01（config API）、02（账号暂停字段）、03（控制端点/状态视图）。

## 需求描述
- 可观察结果：
  - 「批量发送介绍邮件」弹框 CONFIG 区新增定时定量表单：cron（或「每天 HH:mm」）、每日上限、每轮数量、每封间隔(秒)、每轮间隔(秒)、自动开关，可保存（PUT `/config`）。
  - 弹框新增「暂停」「手动」按钮（连同既有「开始执行」）：手动按钮**仅在 PAUSED 时可点**，其余置灰。
  - 弹框 PROGRESS 区显示**执行模式**（自动定时 / 手动）与**流程状态**（运行中/已暂停/空闲），及**每个邮箱**今日已发/上限/成功/失败/是否暂停。
  - 账号列表页对「自动暂停」账号给出标识；当流程因无可用账号 PAUSED 时，页面顶部 banner 提示，**刷新后仍在**（来自 `GET /batch-send/status`）。
- 不可改变：既有任务弹框轮询机制、其他任务（CHECK_REPLIES 等）行为、legacy 即时外联入口。
- 不做：后端逻辑（前序子计划）。

## 关键不变量（引用 + 专属）
- 引用 I-2（模式展示）、I-5（刷新保留暂停提示）、I-8（每账号统计渲染）、I-9（状态驱动按钮可用性）。
- Invariant L4-1：按钮可用性由后端状态驱动，不靠前端本地猜测。
  - 规则：开始/暂停/手动 三按钮 enable/disable 完全依据 `GET /batch-send/status` 的 `status`：IDLE→仅「开始」可点；RUNNING→仅「暂停」可点；PAUSED→「手动」与「开始（恢复/继续）」可点、「暂停」置灰。手动按钮在非 PAUSED 一律禁用（呼应 I-9 后端 409 兜底）。
  - 适用于：弹框按钮绑定、状态轮询回调。
  - 违反后果：误触发被后端拒，UI 与后端不一致。
- Invariant L4-2：banner 状态来源唯一且持久。
  - 规则：列表页 banner 只读 `GET /batch-send/status`，页面加载即拉取一次并轮询；不依赖任何仅存于内存/会话的前端状态。
  - 适用于：列表页初始化、刷新。
  - 违反后果：刷新后提示丢失（违反 I-5）。

## 现状审计（专属）
- `static/app.js`：
  - `taskLaunchConfigs.MANUAL_INITIAL_OUTREACH`（约行 1740）：CONFIG 模式，`preload` 调 `/manual-outreach/pending-count`，`run=executeManualOutreach`。
  - `executeManualOutreach`（约行 2128）：`openTaskModal(...,{launchRequested:true})`→POST `/manual-outreach/start`→`bindTaskModalExecution`。
  - `openTaskModal` PROGRESS 模式轮询 `progress`，渲染 `details`。
  - `openTaskLaunchModal`（约行 1772）：渲染 CONFIG 区（`#taskModalConfigSection`、`#taskLaunchDesc`、`#taskLaunchRunBtn`、filters/keyword 行等），`runBtn.onclick` 切到 PROGRESS 并 `config.run()`。
- `static/index.html`：任务弹框结构（`#taskProgressModal`、`#taskModalConfigSection`、`#taskModalProgressSection`、`#taskLaunchRunBtn` 等）；账号管理列表区。
- `static/styles.css`：弹框/表格/banner 样式。
- 进度 `details` 字段契约由子计划 03 `BatchSendStatusView`/`AccountStatRow` 与 `TaskProgress.details`（I-8）给定。

## 实现方案

### 任务 1：弹框 CONFIG 区 — 定时定量表单（PUT /config）
文件：`static/index.html`、`static/app.js`、`static/styles.css`
- index.html：在 `#taskModalConfigSection` 内为批量发送增加一组表单字段容器（cron/每天时间、dailyCap、roundSize、perMailInterval 秒、perRoundInterval 秒、autoEnabled 勾选），用独立 id（如 `#batchSendConfigPanel`），默认 hidden。
- app.js：
  - `MANUAL_INITIAL_OUTREACH` 的 `preload` 增加拉取 `GET /api/mail/batch-send/config` 与 `GET /api/mail/batch-send/status`，回填表单并据 status 设置初始描述。
  - `openTaskLaunchModal` 中当 `taskType==="MANUAL_INITIAL_OUTREACH"` 时显示 `#batchSendConfigPanel`，其余隐藏。
  - 「保存配置」：PUT `/api/mail/batch-send/config`（秒→毫秒换算），失败提示。

### 任务 2：三按钮与模式/状态展示（L4-1/I-2/I-9）
文件：`static/index.html`、`static/app.js`
- index.html：在弹框增加「暂停」`#batchSendPauseBtn`、「手动」`#batchSendManualBtn`（既有 `#taskLaunchRunBtn` 复用为「开始执行/继续」）；PROGRESS 区增加「执行模式 / 状态」展示位 `#batchSendModeBadge`、`#batchSendStatusBadge`。
- app.js：
  - 新增 `refreshBatchSendControls()`：调 `GET /batch-send/status`，据 `status` 按 L4-1 设置三按钮 disabled，并渲染模式/状态徽标（AUTO→「自动定时」，MANUAL→「手动」）。
  - 「开始执行」→ POST `/manual-outreach/start`（沿用）；「暂停」→ POST `/batch-send/pause`；「手动」→ POST `/batch-send/manual`（409 时提示「请先暂停」）。
  - 进度轮询回调中调用 `refreshBatchSendControls()` 保持同步。

### 任务 3：每账号统计渲染（I-8）
文件：`static/index.html`、`static/app.js`、`static/styles.css`
- 在 PROGRESS 区增加账号统计表 `#batchSendAccountTable`：列 = 邮箱 / 今日已发 / 上限 / 成功 / 失败 / 状态(正常·暂停+原因)。
- app.js：从 `progress.details.accounts`（或 `/batch-send/status` 的 accounts）渲染该表；流程级汇总（dailyCap/已发总数/成功/失败/轮次）渲染到概览行。

### 任务 4：账号列表页标识 + 全局 banner（I-5/L4-2）
文件：`static/index.html`、`static/app.js`、`static/styles.css`
- 账号列表渲染处：对 `autoSendPaused=true` 的账号显示「自动暂停」标签 + 原因 tooltip；提供「恢复发送」按钮 → POST `/api/mail/sender-accounts/{code}/resume-auto-send`。
- 全局 banner：在主页面顶部增加 `#batchSendPausedBanner`（默认 hidden）。页面加载与轮询时调 `GET /batch-send/status`，当 `status==="PAUSED" && pauseReason==="NO_AVAILABLE_ACCOUNT"` 显示 banner（文案如「批量发送已暂停：无可用邮箱账号，请检查并恢复账号」）；其余隐藏。banner 状态仅来自该端点（L4-2），刷新后由初始化重新拉取。

## 变更文件清单（3）
| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | 改（配置表单/三按钮/模式状态位/账号表/banner DOM） |
| 2 | `src/main/resources/static/app.js` | 改（config 读写/控制按钮/状态轮询/每账号渲染/banner） |
| 3 | `src/main/resources/static/styles.css` | 改（表单/徽标/账号表/banner 样式） |

测试（不计上限，若有 JS 测试基建）：`src/test/js/` 增按钮状态机用例（参考既有 `taskModalStateMachine.test.js`）。

## 验收标准
- L4-1/I-9：status=IDLE 时仅「开始」可点；RUNNING 时仅「暂停」；PAUSED 时「手动」「开始(继续)」可点、「暂停」灰；手动在非 PAUSED 禁用。
- I-2：模式徽标随后端 `executionMode` 显示「自动定时」/「手动」。
- I-8：PROGRESS 区账号表显示每邮箱今日已发/上限/成功/失败/暂停状态，与后端一致。
- I-5/L4-2：构造 NO_AVAILABLE_ACCOUNT 暂停后，刷新页面 banner 依旧显示（来自 `/batch-send/status`）。
- 配置：表单回填与 PUT 往返正确（秒↔毫秒换算无误）。
- 账号列表：`autoSendPaused` 账号有标识与「恢复发送」按钮，点击后标识消失。

## 自检清单
- [x] 模式/状态/banner 均有不变量（L4-1/L4-2 + 引用 I-2/I-5/I-8/I-9）。
- [x] 文件数 3 ≤10；单子系统（前端）。
- [x] 每任务引用不变量编号。
- [x] banner 数据来源唯一且持久（L4-2 覆盖刷新场景）。
- [x] 每不变量有验收。
- [x] 文件清单无「等」。

## 修正记录（实现阶段 amend）

1. **「开始执行/继续」按钮未复用 `#taskLaunchRunBtn`，改为新建 `#batchSendStartBtn`。**
   - 原计划：既有 `#taskLaunchRunBtn` 复用为「开始执行/继续」。
   - 实际：`#taskLaunchRunBtn` 与通用 `openTaskLaunchModal` 的 CONFIG 模式流程强绑定（`runBtn.onclick = ... config.run()`，对所有任务类型生效），且位于 `#taskModalConfigSection` 内（PROGRESS 模式下整段 hidden）。若复用则需在两种模式下都保持其可见并改写 onclick，会破坏其他任务类型（CHECK_REPLIES/EXPERT_DISCOVERY 等）的现有行为。
   - 决定：新建 `#batchSendControlBar`（含 `#batchSendStartBtn`/`#batchSendPauseBtn`/`#batchSendManualBtn` + 模式/状态徽标）作为批量发送专属控件条，在 CONFIG 与 PROGRESS 两种模式下均可见；对批量发送隐藏通用 `#taskLaunchRunBtn` 所在按钮行。非批量任务不受影响（`#batchSendControlBar` hidden）。
   - 影响：L4-1 三按钮可用性语义不变（IDLE→仅开始；RUNNING→仅暂停；PAUSED→开始(继续)+手动可点、暂停置灰；手动非 PAUSED 一律禁用）。

2. **「继续/恢复」（PAUSED 态「开始」按钮）调用 `POST /batch-send/start-auto`，后端可能 409。**
   - 背景：子计划 03 落地的后端未提供显式的 PAUSED→IDLE/AUTO「恢复」端点。`startAuto()`/`startManual()` 均要求 IDLE；`runManualOnce()` 仅在 PAUSED 可用（跑一轮后回 PAUSED）。主计划 I-9「恢复 PAUSED→（立即继续，子计划 03 定义）」在 03 中未对应到独立端点。
   - 决定：PAUSED 态「继续/恢复」按钮调用 `POST /batch-send/start-auto`（恢复 AUTO 循环语义）。若后端因非 IDLE 返回 409，前端展示后端 message 并提示「可使用『手动』执行一轮或等待下次定时」。手动按钮（`runManualOnce`）在 PAUSED 始终可用，作为兜底。
   - 前向兼容：若后续后端放宽 `startAuto()` 允许 PAUSED→RUNNING，前端无需改动。
   - 已在 `src/test/js/batchSendControls.test.js` 覆盖按钮态机/徽标/banner/配置换算；端到端「继续/恢复」409 路径留待后端补齐 resume 端点后补测。
