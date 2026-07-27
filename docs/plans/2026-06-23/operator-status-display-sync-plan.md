# 专家联系列表 operatorStatus 显示一致性 + ES 同步可靠性 + 定时对账

> 使用 create-p 技能编写。可在 1–2 轮复验内验证。

## 需求描述

**可观察结果**

1. 专家联系列表页对某专家显示的状态，与该专家详情页一致（不再出现“详情已回复、列表未联系”）。
2. 每次把 `operatorStatus` 写入 ES 候选索引时，结果可观测：匹配 0 条、写入失败都会留下可见日志/记录，不再被静默吞掉。
3. 新增一个定时对账任务，周期性把 MySQL 的 `operatorStatus` 回刷到 ES 候选索引；页面上保留手动“回刷 ES”按钮；鼠标悬浮按钮时显示**最近一次同步**的时间与结果摘要。

**不可改变的行为（must NOT change）**

- `NOT_CONTACTED` 在 ES 中仍以 **“`operatorStatus` 字段缺失”** 表示，绝不写入字符串 `"NOT_CONTACTED"`（现有 `notContactedWithEmailFilters` 的 `must_not exists operatorStatus` 依赖此约定）。
- 按 `operatorStatus` 过滤、排序、分页仍在 ES 端进行（`ExpertSearchService.searchExperts`），本次不迁移到 MySQL。
- 详情页状态来源（MySQL `contact.operatorStatus` / `currentStatus`）不变。
- 自动回复管线写 `operatorStatus` 的既有时机与值不变（`AutoMailReplyService`、`AutomaticApplicationPromotionService`、`ExpertOperatorStatusService.updateAutomatically`）。
- RabbitMQ / scheduling 两个 opt-in 开关语义不变；定时对账必须同样受 `talent-introduction.scheduling.enabled` 控制。

**Out of scope（明确推迟）**

- 不把 `operatorStatus` 的过滤/分页改为以 MySQL 为准。
- 不重构工具栏 / 列表 UI 其它部分。
- 不调整 `OperatorStatus` 枚举值或 `ConversationStatus` 状态机。
- 不新增 ES 字段、不改 ES mapping（仅复用既有 `operatorStatus`）。
- 不为对账新增独立 DB 表——复用既有 `TaskExecution` 审计行承载“最近一次同步日志”。

---

## 关键不变量

### Invariant I-1: operatorStatus 事实源为 MySQL，ES 为派生副本
- Rule: 列表**显示**的 operatorStatus 必须优先取已查出的 MySQL `contact.operatorStatus`；ES 文档上的 `operatorStatus` 仅作为该专家无 MySQL contact 时的兜底，以及过滤/分页用途。
- Applies to: `ExpertIndexController.listExperts`（构造 `ExpertIndexResponse` 时）。
- Violation consequence: ES 与 MySQL 漂移时，列表显示与详情页不一致（即当前 bug）。

### Invariant I-2: NOT_CONTACTED == ES 中字段缺失
- Rule: 当目标状态为 `NOT_CONTACTED` 时，ES 同步必须**移除** `operatorStatus` 字段，绝不写字符串 `"NOT_CONTACTED"`。其余状态写入对应字符串。
- Applies to: `ExpertIndexWriterService.syncCandidateOperatorStatus`、`syncCandidateOperatorStatusBatch`，以及新的对账方法（复用上述两者，不得另写分支）。
- Violation consequence: `notContactedWithEmailFilters` 的 `must_not exists operatorStatus` 失效，“未联系”筛选会漏掉/错收专家。

### Invariant I-3: 每次同步写入都可观测
- Rule: 单条同步 `syncCandidateOperatorStatus` 必须返回结果（命中条数 / 是否失败），并在“匹配 0 条”或“异常”时以 WARN 级别记录 orcidId 与原因；不得仅 `debug` 或静默 `catch`。批量同步 `syncCandidateOperatorStatusBatch` 既有的 `BulkSyncResult`（total/success/failure/skipped/errors）保持不变并被对账任务消费。
- Applies to: `ExpertIndexWriterService.syncCandidateOperatorStatus`。
- Violation consequence: 实时同步悄悄失败，漂移只能靠人手补刷（当前现象）。

### Invariant I-4: 手动与定时对账共用同一代码路径并各留一条审计记录
- Rule: 手动“回刷 ES”按钮与定时对账任务必须调用同一个 reconcile 方法；两者都通过 `TaskExecutionService.runAndRecord(...)` 以 `taskType = "CANDIDATE_OPERATOR_STATUS_SYNC"` 写入一条 `TaskExecution`（手动 triggerType=`MANUAL`，定时 triggerType=`SCHEDULED`）。reconcile 前仍需校验 `checkCandidateOperatorStatusMapping()`，失败则不执行并记录失败原因。
- Applies to: `ExpertIndexController.backfillOperatorStatus`、新 `MailAutomationScheduler` 定时方法、新 reconcile 服务方法。
- Violation consequence: 两条路径逻辑分叉，或手动按钮无法回显“最近一次同步日志”。

---

## 现状审计

### CANDIDATE ES 索引（字段 `operatorStatus`）
- Schema/mapping: `operatorStatus` 需为 keyword 类型；运行期由 `ExpertIndexService.checkCandidateOperatorStatusMapping()` 校验声明存在。`NOT_CONTACTED` 以字段缺失表示。
- Write paths（写入/移除 operatorStatus）:
  1. `ExpertIndexWriterService.syncCandidateOperatorStatus(orcidId, status)` — `_update_by_query`，`term orcidId`，仅 CANDIDATE 索引；`NOT_CONTACTED` 走移除字段脚本，其余写字符串。**异常仅 warn、`updated==0` 仅 debug（违反 I-3）**。
  2. `ExpertIndexWriterService.syncCandidateOperatorStatusBatch(updates)` — 先 `resolveOrcidToDocIds`（按 `term orcidId` 查 `_id`），再 `_bulk`；返回 `BulkSyncResult`。doc 不存在计 skipped。
  3. 调用方（均写 MySQL 后调 sync）：
     - `ExpertOperatorStatusService.changeStatus` / `updateAutomatically`（:42 / :62）
     - `ManualOutreachTxHelper`（:84，写 `CONTACTED`）
     - `ManualInitialOutreachService`（:333，写 `EMAIL_INVALID`）
     - `BounceCollectionService`（:72）
     - 回复入站经 `AutoMailReplyService`（:584 → `updateAutomatically REPLIED`）、`AutomaticApplicationPromotionService`（:48/:90）
  4. 手动批量：`ExpertIndexController.backfillOperatorStatus` → `syncCandidateOperatorStatusBatch`（遍历全部 contact，取最新 operatorStatus）。
- Read paths（读 operatorStatus）:
  1. `ExpertSearchService.searchExperts` — 用 `operatorStatus` 做 ES `filter`（过滤/分页）；`NOT_CONTACTED` 用 `must_not exists`。读字段 `expert.operatorStatus` 经 `toExpertProfile`。
  2. `ExpertIndexController.listExperts` — 当前用 `expert.operatorStatus ?: "NOT_CONTACTED"` 作为列表显示值（**忽略 MySQL contact，违反 I-1，即 bug 根因**）。前端 `app.js:1657` 优先用此字段渲染。
- Interaction points:
  - **IP-1**（本次修复核心）：write path #1/#2（ES 写）× read path #2（列表显示）。修 I-1 后显示改读 MySQL，IP-1 不再受 ES 漂移影响。
  - **IP-2**：write path（ES 写）× read path #1（ES 过滤）。过滤/分页仍依赖 ES 正确，故必须靠 I-3（实时可靠）+ 定时对账兜底；**显示修复不能修复过滤**。详见验收 S-3。

### MySQL `expert_contact.operator_status`
- Schema: `ExpertContact.operatorStatus: String = "NOT_CONTACTED"`（事实源）。
- Write paths: `ExpertOperatorStatusService.changeStatus` / `updateAutomatically`（save 后同步 ES）。
- Read paths: `ExpertContactManagementService.getContactDetail`（详情页）；`ExpertIndexController.listExperts` 已经 `findByOrcidIdIn` 查出 contactMap（但显示未用其 operatorStatus）。

### TaskExecution 审计（承载“最近一次同步日志”）
- `TaskExecutionService.runAndRecord(taskType, triggerType, request) { ... }` 持久化一条记录，含 status/successCount/failureCount/errorMessage/resultSummary/startedAt/finishedAt。
- 既有查询：`GET /api/task-executions?taskType=X` → `listExecutions`，按 `startedAt` 倒序返回 `TaskExecutionResponse` 列表（取第一条即最近一次）。本次复用，无需新 endpoint。

### 定时调度
- `MailAutomationScheduler` 受 `@ConditionalOnProperty(talent-introduction.scheduling.enabled=true)`，已有 `@Scheduled(cron=...)` 模式 + `taskExecutionService.runAndRecord`。
- `MailSchedulingProperties` 持有各 cron；`application.yml` `talent-introduction.scheduling.*` 提供默认（默认 `-` 表示不触发）。

### 前端
- `index.html:416` 按钮 `#backfillOperatorStatusBtn`；`app.js:2178 handleBackfillOperatorStatus()` POST `/api/experts/backfill-operator-status` 并 `showStatus`。
- 列表渲染 `app.js:1656-1696`：`operatorStatusLabels[REPLIED]="已回复"`、`NOT_CONTACTED="未联系"`。

---

## 实现方案

### Stage 1 — 列表显示以 MySQL 为准（I-1，修复 IP-1）
- **任务 1.1**：`ExpertIndexController.listExperts`，将
  `operatorStatus = expert.operatorStatus ?: "NOT_CONTACTED"`
  改为优先取 contact：
  `operatorStatus = contact?.operatorStatus ?: expert.operatorStatus ?: "NOT_CONTACTED"`。
  - 遵守 I-1。contactMap 已存在（`findByOrcidIdIn`），无需新查询。
  - read path #2 改为消费 MySQL write path（`ExpertOperatorStatusService`）的结果。

### Stage 2 — 实时同步可观测（I-3）
- **任务 2.1**：`ExpertIndexWriterService.syncCandidateOperatorStatus` 改为返回结果对象（如 `SingleSyncResult(matched: Long, ok: Boolean, error: String?)`），逻辑不变（仍遵守 I-2 的字段缺失约定）：
  - `updated == 0` → WARN 记录 `orcidId` 与“候选索引无匹配文档”，返回 `matched=0`。
  - 异常 → WARN 记录 `orcidId` + 异常，返回 `ok=false`。
  - 现有 5 个调用方忽略返回值即可（Kotlin 兼容，行为不变）；**不修改调用方**。

### Stage 3 — 抽出共享 reconcile，定时 + 手动复用（I-4）
- **任务 3.1**：新建 `CandidateOperatorStatusSyncService`（`expert/service`），方法 `reconcileAll(): BulkSyncResult`：
  - 先 `expertIndexService.checkCandidateOperatorStatusMapping()`，false 则抛出带明确 message 的异常（runAndRecord 会记为 FAILED）。
  - 复用 `ExpertIndexController.backfillOperatorStatus` 现有逻辑：遍历 `expertContactRepository.findAllByOrderByUpdatedAtDesc()`，`filter orcid 非空`，`map orcid→(operatorStatus ?: NOT_CONTACTED)`，`distinctBy orcid.lowercase()`，调 `syncCandidateOperatorStatusBatch`。遵守 I-2（批量已正确处理字段缺失）。
- **任务 3.2**：`ExpertIndexController.backfillOperatorStatus` 改为：用 `taskExecutionService.runAndRecord("CANDIDATE_OPERATOR_STATUS_SYNC", "MANUAL", request) { syncService.reconcileAll() }`，返回结果仍映射为 `BackfillResult`（保持响应结构）。遵守 I-4。
- **任务 3.3**：`MailAutomationScheduler` 新增 `@Scheduled(cron="\${talent-introduction.scheduling.operator-status-sync-cron:-}")` 方法，调 `runAndRecord("CANDIDATE_OPERATOR_STATUS_SYNC", "SCHEDULED", request) { syncService.reconcileAll() }`。`BulkSyncResult` 需让 runAndRecord 能取到 success/failure（提供 `TaskExecutionSummaryProvider` 适配或映射），不改既有 TaskExecution 语义。
- **任务 3.4**：`MailSchedulingProperties` 增 `operatorStatusSyncCron: String = "-"`；`application.yml` 增 `operator-status-sync-cron: ${MAIL_SCHEDULING_OPERATOR_STATUS_SYNC_CRON:-}`。默认 `-` 不触发，遵守“调度 opt-in”不变量。

### Stage 4 — 前端：保留按钮 + 悬浮显示最近一次同步（I-4）
- **任务 4.1**：`app.js` 新增加载逻辑：进入联系列表或刷新后，GET `/api/task-executions?taskType=CANDIDATE_OPERATOR_STATUS_SYNC`，取第一条，组装文案（开始时间、状态、成功/失败/跳过数、errorMessage），写入 `#backfillOperatorStatusBtn` 的 `title`（悬浮提示）。`handleBackfillOperatorStatus` 成功回调后刷新该 title。
- **任务 4.2**：`index.html` 按钮保持不变（沿用 `title` 原生悬浮）；如需多行展示，给按钮加包裹元素与 `data-*`，但不引入新组件。

---

## 变更文件清单

| # | 文件 | 改动 | Stage |
|---|------|------|-------|
| 1 | `src/main/kotlin/.../expert/controller/ExpertIndexController.kt` | listExperts 显示取 contact；backfill 改走 runAndRecord+reconcile | 1,3 |
| 2 | `src/main/kotlin/.../expert/service/ExpertIndexWriterService.kt` | syncCandidateOperatorStatus 返回结果 + 可见日志 | 2 |
| 3 | `src/main/kotlin/.../expert/service/CandidateOperatorStatusSyncService.kt`（新增） | reconcileAll() 共享方法 | 3 |
| 4 | `src/main/kotlin/.../task/service/MailAutomationScheduler.kt` | 新增定时对账方法 | 3 |
| 5 | `src/main/kotlin/.../config/MailSchedulingProperties.kt` | 新增 operatorStatusSyncCron | 3 |
| 6 | `src/main/resources/application.yml` | 新增 cron 默认值 | 3 |
| 7 | `src/main/resources/static/app.js` | 按钮悬浮显示最近一次同步日志 | 4 |
| 8 | `src/main/resources/static/index.html` | 按钮 title/包裹（如需） | 4 |

文件数 8 ≤ 10。子系统：后端（1–6）/ 前端（7–8）共 2。新增 ES 字段数 = 0。

---

## 验收标准

- **I-1**：构造一条 MySQL `operatorStatus=REPLIED` 但 ES `operatorStatus` 缺失/陈旧的 contact，调 `GET /api/experts`，断言该专家返回 `operatorStatus=REPLIED`，与详情页一致。
- **I-2**：对某 orcid 调 reconcile / `syncCandidateOperatorStatus("NOT_CONTACTED")` 后，断言 ES 文档**不含** `operatorStatus` 字段（而非字符串）；对 `REPLIED` 断言字段值为 `"REPLIED"`。
- **I-3**：对一个 ES 中不存在的 orcid 调 `syncCandidateOperatorStatus`，断言返回 `matched=0` 且产生一条 WARN 日志；模拟 ES 异常断言 `ok=false` 且 WARN 记录。
- **I-4**：点手动按钮与触发定时任务各执行一次，断言各新增一条 `TaskExecution(taskType=CANDIDATE_OPERATOR_STATUS_SYNC)`，triggerType 分别为 `MANUAL`/`SCHEDULED`，且 success/failure/skipped 计数与 `BulkSyncResult` 一致；mapping 校验失败时记为 FAILED 且不执行同步。
- **集成场景 S-3（IP-2）**：MySQL=REPLIED、ES 字段缺失时，按 `operatorStatus=REPLIED` 过滤列表**查不到**该专家（过滤走 ES）；运行 reconcile 后再查能查到。以此验证“显示修复不能替代同步，定时对账才能修复过滤一致性”。
- **前端**：悬浮 `#backfillOperatorStatusBtn` 显示最近一次同步的时间与成功/失败/跳过摘要；无历史记录时显示“暂无同步记录”。

---

## Self-Review Checklist

- [x] 关键不变量存在，且每个新行为（显示来源、字段缺失语义、可观测、共享路径）各有不变量
- [x] 现状审计列出 CANDIDATE ES 全部写/读路径（grep 核实，非凭记忆）
- [x] 无任务引入不受不变量约束的新写路径（未新增 ES 写路径，仅复用既有 batch/single）
- [x] 文件数 8 ≤ 10
- [x] 子系统数 = 2 ≤ 2
- [x] 每个任务引用其约束不变量编号
- [x] 每个不变量至少一条验收
- [x] 文件清单无“及相关文件 / 等”，均具名
- [x] Out-of-scope 明确推迟（过滤迁移、UI 重构、枚举改动、新 ES 字段、新 DB 表）
