# 专家管理页：手动批量首发邮件 + 全局自动回复开关 + 检查回复移出弹窗 — 开发计划

> 本计划交给执行 agent 实施。实施前请通读「现状分析」一节；行号基于 2026-06-12 代码，可能有少量漂移，请以符号名定位。
> 设计原则：**保守上线**。不开启定时调度（`MAIL_SCHEDULING_ENABLED` 保持 false），所有发送由操作员在页面手动触发、可随时停止、进度可审计。

---

## 一、需求描述

### 需求 1：批量发送介绍邮件（手动触发 + 进度 + 可停止）

在专家管理页（`#view-contacts`）工具栏添加按钮「批量发送介绍邮件」：

1. 点击后向**所有未联系的专家**（CANDIDATE 层、有邮箱、尚无任何 ExpertContact 记录）逐个发送 INTRODUCTION 模板邮件。
2. 专家列表上方展示执行进度面板：**待发送数 / 已发送数 / 发送失败数** + 进度条。
3. **刷新页面后进度依然展示**（服务端持久化进度，前端恢复轮询）。
4. 任务运行中按钮变为「停止发送」，点击后任务在当前这封发完后停止。
5. 发送成功后专家状态必须正确变更：会话状态 `NEW → INTRO_SENT`（写状态历史），跟进状态 `operatorStatus` 改为 `CONTACTED`（已联系）。
6. 测试必须完善：覆盖各种状态变更、邮件往来记录（timeline）正确返回、失败/取消/账号额度耗尽等分支。

### 需求 2：全局自动回复开关

在专家管理页工具栏添加按钮，一键控制**所有已联系专家**（即所有 ExpertContact）的 `autoReplyEnabled` 开/关。按钮需反映当前聚合状态（全部开启 / 全部关闭 / 部分开启）。

### 需求 3：「检查回复」按钮移出弹窗 + 日志内联展示

现状：「检查回复」在「后台任务」下拉菜单里，点击后打开 `#taskProgressModal` 弹窗确认；弹窗打开期间列表被遮挡，**无法继续勾选/改选专家**。要求：

1. 把「检查回复」做成工具栏上的直接按钮（移出下拉菜单、去掉确认弹窗），点击即按当前已勾选专家（未勾选则全部已联系专家）执行。
2. 执行日志（轮询日志）不再用 dialog 弹窗，改为**直接放在专家列表上方的内联面板**，样式与需求 1 的进度/日志区一致。

---

## 二、现状分析（务必先读）

### 2.1 可复用的任务进度基础设施（这是本计划的骨架）

- `task/service/TaskProgressStore.kt`：内存 + DB（`task_progress_log`，V22 迁移）双写的进度存储。
  - `tryStartWithToken(taskType, initial)` → `(started, pendingToken)`：原子抢占，防并发重复启动。
  - `bindExecutionId(taskType, token, executionId)`：绑定真实 `task_execution.id`。
  - `update(taskType, progress, expectedExecutionId)`：写进度（带过期保护），同时落 `task_progress_log`。
  - `get(taskType)`：内存 miss 时 `restoreFromLog` 从 DB 恢复 → **这就是"刷新页面进度依然展示"甚至服务重启后仍可见的机制**。
  - `requestCancel(taskType)` / `isCancelled(taskType, executionId)`：取消机制。
  - `clearExecutionContext(taskType, execId)`：终态清理。
- `task/controller/TaskProgressController.kt`：
  - `GET /api/task-progress/{taskType}`：拉进度（204 = 无进度）。
  - `POST /api/task-progress/{taskType}/cancel`：请求取消（无 taskType 白名单限制）。
  - `GET /api/task-progress/{taskType}/logs?executionId=`：批次日志（无白名单限制）。
  - `GET /api/task-progress/{taskType}/executions`：**有白名单** `allowedTaskTypes = setOf("EXPERT_REVALIDATION", "RAW_PROMOTION_SCAN", "EXPERT_DISCOVERY")`，如需执行历史列表要把新 taskType 加进去。
- `TaskProgress`（`TaskProgressStore.kt` 底部 data class）：有 `processedCount/totalCount/batchPassed/batchRejected/details(Map)/errors` 字段，足够承载 待发送/已发送/失败 三个数字（用 `details` 传 `{"pending":n,"sent":n,"failed":n}` 最直观）。
- 启动模式参考 `expert/controller/ExpertIndexController.revalidateCandidates`（L87–126）：`tryStartWithToken` → 冲突返回 409 → `taskExecutionService.runAndRecordWithResult(..., onStarted = { bindExecutionId })` → finally `clearExecutionContext`。**注意该模式是同步阻塞 HTTP 请求执行的**；邮件批量发送可能跑几十分钟，本计划改为异步执行（见 3.1.4）。
- 前端已有整套配套：`app.js` 的 `taskButtonMapping`（L196）、`startTaskWatcher`/`pollTaskWatcher`（3s 轮询）、`resumeProgressPollingIfNeeded`（L229，页面加载时恢复运行中任务的按钮态和轮询）、列表上方进度条 `#taskProgressBar` 和错误日志 `#taskErrorLog`（index.html `#view-contacts` 内，"Task Progress Bar" 注释处）。

### 2.2 现有首发邮件链路及其问题（不要直接复用 `sendInitialBatch`）

`campaign/service/InitialOutreachService.sendInitialBatch(campaignId, size)` 存在以下问题，**新的手动批量发送服务必须规避**，不要在其上修补（保留原方法给 scheduler/queue 用，避免影响现有调用方）：

1. **整批一个 `@Transactional`**：SMTP 发送在事务内，第 N 封失败回滚整批 DB 记录，但前 N-1 封邮件已实际发出 → 重跑会给专家重复发信。
2. **候选不滚动**：`expertSearchService.searchExpertsWithEmail(size, CANDIDATE)` 固定排序取前 N 条，已联系专家不被排除，只靠 `existsByCampaignIdAndOrcidId` 跳过 → 跑一轮后每轮都拉到同样的人，sent 恒为 0。
3. 创建 `ExpertContact` 时直接 `save`，**不写状态历史**（绕过了 `ConversationStateService.transition`），且 `operatorStatus` 落默认值 `NOT_CONTACTED`——即发了介绍信的专家在前端"跟进状态"上仍显示**未联系**，与需求 1 第 5 点直接冲突。
4. `SenderAccountAssignmentService.selectAccount` 用 `findAllByEnabledTrue()` **未排除 `SIMULATOR_NOOP`** 模拟账号（V20 已将其禁用，但若有人在账号页重新启用会被选中）。
5. `todaySentCount` 只有手动 reset API（`POST /api/mail/sender-accounts/{code}/reset-today-sent-count`），无每日自动清零。手动触发场景下可接受（额度耗尽任务即停，操作员可手动 reset），但进度面板要把"账号额度耗尽"作为明确的停止原因展示。

### 2.3 专家列表与"未联系"的定义

- 前端 `app.js loadContacts()`（L1260）：无跟进状态筛选时走 `GET /api/experts?level=CANDIDATE&...`（ES），返回项带 `contactId`/`contactStatus`/`operatorStatus`（由后端 merge）；列表项中 `contactId` 为空即显示「未联系」徽章，复选框 `.expert-select-cb` 对无 `contactId` 的行是 `disabled`。
- 后端"未联系专家"的权威定义 = **ES CANDIDATE 层、`email` 存在、且 `expert_contact` 表中无该 `orcidId` 的记录（跨 campaign）**。注意 `ExpertContactRepository` 现在只有 `existsByCampaignIdAndOrcidId`，需新增按 `orcidId` 跨 campaign 的查询。
- 遍历 ES 用 `ExpertSearchService.scrollExperts(level, batchSize, handler)`（已有，`ExpertRevalidationService`/`ExpertDiscoveryService` 在用，handler 返回 false 可中断 —— 用于响应取消）。

### 2.4 状态模型

- 会话状态：`common/domain/ConversationStatus`，首发后应为 `INTRO_SENT`。**必须经 `ConversationStateService.transition(...)` 变更**（同时落 `expert_contact_status_history`）。`transition` 要求 `contact.id` 非空，所以"先 save NEW 再 transition 到 INTRO_SENT"是正确顺序。
- 跟进状态：`campaign/domain/OperatorStatus`（`NOT_CONTACTED/CONTACTED/REPLIED/...`），`ExpertContact.operatorStatus` 默认 `NOT_CONTACTED`。transition 的 `update` lambda 里可一并 `copy(operatorStatus = "CONTACTED")`。
- 自动回复：`ExpertContact.autoReplyEnabled`；已有单个开关 `pauseAutoReply`/`resumeAutoReply`（`ExpertContactManagementService` L145/L184，内部走 transition 同状态 + `OperatorActionLogService` 审计），需求 2 直接按 contact 循环复用这两个方法。
- `MANUAL_HANDOFF` 状态的 contact：`autoReplyEnabled=false` 且 `manualHandoffRequired=true`，恢复自动回复有专门语义（`switchToAuto` 会完结 handoff 工单）。**批量开启时必须跳过这些 contact**，避免绕过工单流程。

### 2.5 campaign 归属

`expert_contact` 表 `(campaign_id, orcid_id)` 唯一键，campaign 必填。页面没有 campaign 概念。方案：新增 `CampaignRepository`（目前只有 domain `Campaign.kt`，无 repository），服务启动批量发送时 get-or-create `campaign_code='MANUAL_OUTREACH'` 的 campaign（`sender_account_id` 取任一启用的非模拟账号；建表见 V1 L66）。不用迁移插数据（campaign 需要引用账号 id，运行期解析更稳）。

### 2.6 「检查回复」现状

- 入口：`index.html` `#taskMenu` 下拉里的 `#checkRepliesBtn`，`onclick="handleCheckReplies()"`。
- `handleCheckReplies` → `openTaskLaunchModal("CHECK_REPLIES")`（`taskLaunchConfigs` L1477）打开 `#taskProgressModal`，弹窗里点「开始执行」才调 `executeCheckReplies`（L~1440）→ `POST /api/mail/auto-reply/check-replies`（body 可带 `contactIds`），完成后调 `showPollLog()`。
- `showPollLog`（L1768）拉 `GET /api/task-executions/recent-polls?limit=10`，**动态创建 `<dialog id="pollLogDialog">` 弹窗**渲染轮询日志表（含 `togglePollDetail` 行内展开账号明细）。
- 问题确认：弹窗打开时无法操作列表复选框；需求 3 要求按钮直点直执行、日志内联到列表上方。
- 回归注意：`taskLaunchConfigs` 中其余三个任务（EXPERT_REVALIDATION / RAW_PROMOTION_SCAN / EXPERT_DISCOVERY）和 `task-modal-runtime.js` 的弹窗流程**保持不动**。

---

## 三、设计方案

### 3.1 需求 1：手动批量首发（taskType = `MANUAL_INITIAL_OUTREACH`）

> 用新 taskType 而不是复用 `INITIAL_OUTREACH`，避免与 scheduler/queue 的执行记录语义混淆，前端 `#view-tasks` 的 `taskTypeFilter` 加一个对应 option。

#### 3.1.1 新服务 `campaign/service/ManualInitialOutreachService.kt`

```
fun countPending(): PendingOutreachSummary        // 待发送预览（启动确认框用）
fun runBulkOutreach(executionId: Long): ManualOutreachResult
```

**预扫描（构建快照）**：`scrollExperts(CANDIDATE)` 全量滚动，过滤 `email != null` 且 `!expertContactRepository.existsByOrcidId(orcidId)`，得到待发送列表（内存快照，只存 orcidId/email/姓名/国家等轻量字段）。快照大小即 `totalCount`（待发送数）。

> 补发逻辑（容错）：同时把 `expert_contact` 中 `current_status='NEW'` 的记录（上次发送失败遗留，见下）加入待发送快照头部，实现失败重试。

**逐个发送循环**（每个专家独立处理，无外层事务）：

1. `progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)` → true 则停止，状态 CANCELLED。
2. 选账号：复用 `SenderAccountAssignmentService.selectAccount`，但先修复 2.2-4：在该服务的 `findAllByEnabledTrue()` 结果上排除 `SIMULATOR_NOOP`（把 `MailSenderAccountService.SIMULATOR_ACCOUNT_CODE` 提为公共常量复用）。无可用账号（全部额度耗尽）→ 停止，状态 COMPLETED + message「发件账号今日额度耗尽，已停止；可在账号页重置后继续」，剩余计入待发送。
3. **先占位再发送**（防崩溃重复发信）：若该专家无 contact，先 `expertContactRepository.save(ExpertContact(status=NEW, operatorStatus=NOT_CONTACTED, campaignId=MANUAL_OUTREACH...))`；已有 NEW contact（补发场景）直接复用。
4. `IntroductionMailComposer.compose(account.accountCode, expert)` → `mailDeliveryService.send(account, mail)`，**try/catch 包裹**：
   - 成功：在一个小事务里（新方法标 `@Transactional`，或拆 helper）：
     a. `conversationStateService.transition(contact, INTRO_SENT, reason="MANUAL_BULK_OUTREACH", source="MANUAL") { it.copy(operatorStatus = "CONTACTED", lastMailAt = now) }`（写状态历史 NEW→INTRO_SENT）；
     b. `mailRecordRepository.save(direction=OUTBOUND, mailType=INTRODUCTION, sendStatus=SENT, triggeredBy=MANUAL, messageId=...)`；
     c. 账号 `todaySentCount + 1`、`lastSentAt`。
     `sentCount++`。
   - 失败：contact 保持 `NEW`（下次任务自动补发），落一条 `mailRecord(sendStatus=FAILED, body=异常摘要)` 便于 timeline 审计，`failedCount++`，异常信息追加进 `TaskProgress.errors`（截断保留最近 ~20 条）。**不中断循环**，继续下一个。
5. 每处理 1 个专家更新一次进度（量级不大，无需攒批）：
   ```
   progressStore.update(taskType, TaskProgress(
     processedCount = sent+failed, totalCount = snapshot.size,
     batchNumber = 当前序号, batchPassed = sentCount, batchRejected = failedCount,
     details = mapOf("pending" to 剩余, "sent" to sentCount, "failed" to failedCount),
     message = "正在发送：xxx@yyy", status = "RUNNING"), executionId)
   ```
6. 发送间隔：新增配置 `talent-introduction.manual-outreach.send-interval-ms`（默认 1000，env `MANUAL_OUTREACH_SEND_INTERVAL_MS`），每封之间 `Thread.sleep`，避免打爆 SMTP。新增 `config/ManualOutreachProperties.kt` + application.yml 条目。

返回 `ManualOutreachResult(total, sent, failed, skippedNoAccount, wasCancelled)`，由 `runAndRecord` 序列化进 `task_execution.result_summary`。

#### 3.1.2 Repository 改动

`ExpertContactRepository` 新增：
- `fun existsByOrcidId(orcidId: String): Boolean`
- `fun findAllByCurrentStatus(status: String): List<ExpertContact>`（补发扫描用）
- `fun findByCampaignIdAndOrcidId(campaignId: Long, orcidId: String): ExpertContact?`（占位复用）

新增 `campaign/repository/CampaignRepository.kt`（`CrudRepository<Campaign, Long>` + `findByCampaignCode`）。

#### 3.1.3 账号分配修复（连带）

`SenderAccountAssignmentService.selectAccount` 与 `MailSenderAccountService.selectAccountForSending`：过滤条件追加 `it.accountCode != SIMULATOR_NOOP`。补单测。

#### 3.1.4 Controller：`mail/controller/MailAutomationController.kt` 新增端点

- `GET /api/mail/manual-outreach/pending-count` → `{ pending: n, retryable: m }`（启动确认用，做轻量实现：scroll 计数即可）。
- `POST /api/mail/manual-outreach/start`：
  1. `tryStartWithToken("MANUAL_INITIAL_OUTREACH", initial RUNNING progress)`；失败 → 409 `{message:"任务正在执行中"}`。
  2. **提交到专用单线程 executor 异步执行**（新增 `@Bean("manualOutreachExecutor") ThreadPoolTaskExecutor`，core=max=1，queueCapacity=0 —— tryStartWithToken 已保证不会并发提交）。executor 内部走 `taskExecutionService.runAndRecordWithResult("MANUAL_INITIAL_OUTREACH", "MANUAL", ..., onStarted={ bindExecutionId })`，try/catch 写 FAILED 进度，finally `clearExecutionContext`（完整照抄 `revalidateCandidates` 的保护结构，只是搬进异步任务体内）。
  3. HTTP 立即返回 202 `{message:"已启动"}`。
- 停止：复用现有 `POST /api/task-progress/MANUAL_INITIAL_OUTREACH/cancel`，不需要新端点。
- `TaskProgressController.allowedTaskTypes` 加入 `"MANUAL_INITIAL_OUTREACH"`（如需执行历史；进度与日志端点本就不限制）。

#### 3.1.5 前端（`index.html` + `app.js`）

1. **按钮**：`#view-contacts` 的 `.toolbar-actions` 中、「发现专家」旁新增 `<button class="button primary" id="bulkOutreachBtn">批量发送介绍邮件</button>`。
2. **进度面板**：在 `#taskProgressBar` 旁新增内联面板（列表上方）：
   ```html
   <div id="outreachProgressPanel" class="task-progress-bar" hidden>
     <div class="task-progress-header">
       <span class="task-progress-label">批量发送介绍邮件</span>
       <span id="outreachCounters">待发送 0 · 已发送 0 · 失败 0</span>
       <span id="outreachProgressPercent"></span>
     </div>
     <div class="task-progress-track"><div id="outreachProgressFill" class="task-progress-fill"></div></div>
     <div id="outreachProgressDetail" class="task-progress-detail"></div>
     <div id="outreachErrors" class="task-error-log-content" hidden></div>
   </div>
   ```
   计数来自 `progress.details.pending/sent/failed`，错误列表来自 `progress.errors`。
3. **交互**：
   - 点击 → `GET pending-count` → `confirm("将向 N 位未联系专家发送介绍邮件（含 M 位上次失败待补发），是否开始？")` → `POST start`。
   - 启动后按钮文案变「停止发送」（class 加 danger 样式），再点 → `POST /api/task-progress/MANUAL_INITIAL_OUTREACH/cancel`，按钮变「停止中...」disabled，直至终态。
   - 轮询：每 3s `GET /api/task-progress/MANUAL_INITIAL_OUTREACH` 更新面板；终态（COMPLETED/FAILED/CANCELLED）后停止轮询、恢复按钮、`loadContacts()` 刷新列表（已发送专家徽章应变「已联系」）。**面板在终态后保留展示**（不自动隐藏），由用户手动关闭（加个小关闭按钮）。
   - **刷新恢复**：扩展 `resumeProgressPollingIfNeeded`（它遍历 `taskButtonMapping`）—— 将 `MANUAL_INITIAL_OUTREACH` 加入映射，但其按钮行为是"切换为停止发送"而非通用 disable，需要在恢复逻辑里对该 taskType 走专用恢复函数（恢复面板显示 + 按钮停止态 + 轮询）。注意不要让通用 `setTaskButtonRunning` 把按钮 disable 掉。
   - 非运行态但 store/DB 里有上次终态进度时（`GET` 返回非 204 且终态），页面加载默认**不**显示面板（避免常驻干扰），仅运行中/取消中恢复。
4. `#view-tasks` 的 `#taskTypeFilter` 加 `<option value="MANUAL_INITIAL_OUTREACH">手动批量首发邮件</option>`。

### 3.2 需求 2：全局自动回复开关

#### 后端（`ExpertContactManagementController` + Service）

- `GET /api/expert-contacts/auto-reply/summary` → `{ total, enabled, disabled, handoffLocked }`（`handoffLocked` = `manualHandoffRequired=true` 的数量，前端提示用）。Service 层一次 `findAll` 内存统计即可（量级可控）。
- `POST /api/expert-contacts/auto-reply/bulk`，body `{ "enabled": true|false, "operatorName": "..."? }`：
  - `enabled=false`：对所有 `autoReplyEnabled=true` 的 contact 逐个调既有 `pauseAutoReply(contactId, operatorName)`。
  - `enabled=true`：对所有 `autoReplyEnabled=false` **且 `manualHandoffRequired=false`** 的 contact 逐个调 `resumeAutoReply`；handoff 中的跳过。
  - 返回 `{ updated, skipped }`。逐个调用以保留 transition 历史与操作审计（量大时性能可接受；不要绕过去裸 update）。

#### 前端

- 工具栏新增 `<button class="button" id="bulkAutoReplyBtn">`，文案由 summary 决定：
  - 全开 → 「自动回复：全部开启 ✓（点击全部关闭）」；全关 → 「自动回复：全部关闭（点击全部开启）」；混合 → 「自动回复：部分开启 x/y（点击全部开启）」。
  - 点击 → confirm（开启时若 `handoffLocked>0` 提示「N 位人工接管中的专家将被跳过」）→ POST bulk → 重拉 summary + `loadContacts()`。
- summary 在进入 contacts 视图和每次 `loadContacts()` 后刷新。

### 3.3 需求 3：检查回复内联化

1. **按钮位置**：把 `#checkRepliesBtn` 从 `#taskMenu` 下拉移到 `.toolbar-actions`（「批量发送介绍邮件」左侧），保留「轮询日志」「重新验证候选人」「扫描 RAW 可晋升」在下拉里。
2. **去弹窗**：`handleCheckReplies` 不再走 `openTaskLaunchModal`；从 `taskLaunchConfigs` 删除 `CHECK_REPLIES` 条目。点击后直接：
   - 读当前勾选 `$$(".expert-select-cb:checked")`；无勾选 → `confirm("未勾选专家，将检查所有已联系专家的回复，继续？")`。
   - 执行原 `executeCheckReplies` 主体（按钮 loading 态、`POST /api/mail/auto-reply/check-replies`）。
3. **日志内联**：新增列表上方面板：
   ```html
   <div id="pollLogPanel" class="panel" hidden>
     <div class="panel-head"><h2>自动回复轮询日志（近 10 次）</h2>
       <button class="button small" id="closePollLogPanelBtn">关闭</button></div>
     <div class="table-wrap"><table class="data-table"> ...同 dialog 中的表头... <tbody id="pollLogBody"></tbody></table></div>
   </div>
   ```
   - 重构 `showPollLog()`：渲染目标从动态 `<dialog>` 改为 `#pollLogPanel`（行渲染、`togglePollDetail` 展开逻辑原样搬移，事件委托绑到 `#pollLogBody`）。下拉菜单的「轮询日志」与检查回复完成后的自动展示均调用它。
   - 检查回复完成后自动 `showPollLog()` 展示面板（替代原弹窗行为），并滚动到面板。
4. **修复连带**：原 `openTaskLaunchModal` 中 `CHECK_REPLIES` 特判分支（按勾选数渲染 desc 的 if）一并删除，确认其余任务弹窗不受影响。

---

## 四、实施步骤（建议顺序）

| # | 内容 | 主要文件 |
|---|------|---------|
| 1 | 账号分配排除模拟账号 + 单测 | `SenderAccountAssignmentService.kt`、`MailSenderAccountService.kt`、`SenderAccountAssignmentServiceTest.kt` |
| 2 | Repository 扩展 + CampaignRepository | `ExpertContactRepository.kt`、新 `CampaignRepository.kt` |
| 3 | `ManualOutreachProperties` + yml 配置 + executor Bean | `config/`、`application.yml` |
| 4 | `ManualInitialOutreachService`（预扫描/逐发/占位/补发/取消/进度） | 新 `campaign/service/ManualInitialOutreachService.kt` |
| 5 | start / pending-count 端点 + 进度白名单 | `MailAutomationController.kt`、`TaskProgressController.kt` |
| 6 | 需求 2 后端（summary + bulk） | `ExpertContactManagementController.kt`、`ExpertContactManagementService.kt` |
| 7 | 前端需求 1（按钮/面板/轮询/恢复/停止） | `index.html`、`app.js` |
| 8 | 前端需求 2（开关按钮） | 同上 |
| 9 | 前端需求 3（按钮外移 + 日志面板化） | 同上 |
| 10 | 测试全量补齐（见 §五）+ `mvn test` 全绿 | `src/test/kotlin/...` |

构建/测试命令（JDK 11 必须）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
```

## 五、测试计划（需求 1 第 6 点是验收重点）

> 风格参考既有测试（mock repository / fake `MailDeliveryService`，如 `BatchAutoMailReplyServiceTest`、`ConversationStateServiceTest`）。

### `ManualInitialOutreachServiceTest`（新）

1. 正常发送：2 个未联系专家 → sent=2；每人：contact 存在、`currentStatus=INTRO_SENT`、`operatorStatus=CONTACTED`、状态历史含 `NEW→INTRO_SENT`（reason=MANUAL_BULK_OUTREACH, source=MANUAL）、mailRecord(OUTBOUND/INTRODUCTION/SENT)、账号 `todaySentCount+1`。
2. 跳过已联系：`existsByOrcidId=true` 的专家不进快照、不发送。
3. 发送失败分支：fake delivery 第 2 封抛异常 → failed=1、该 contact 停留 `NEW`、落 FAILED mailRecord、循环继续、`errors` 含异常摘要。
4. 补发：存在遗留 `NEW` contact → 进快照、复用 contact 不重复创建、成功后转 INTRO_SENT。
5. 取消：第 1 封后 `isCancelled=true` → 停止、wasCancelled=true、剩余未发送、已发的状态正确。
6. 账号额度耗尽：selectAccount 抛/无账号 → 停止、message 含额度提示、已发部分状态正确。
7. 进度断言：每封后 `progressStore.update` 被调，details 的 pending/sent/failed 数值正确。
8. campaign get-or-create：无 `MANUAL_OUTREACH` campaign 时自动创建。

### `SenderAccountAssignmentServiceTest`（补）

9. 候选含已启用的 `SIMULATOR_NOOP` → 永不选中；全部真实账号耗尽时报无可用账号。

### `ExpertContactManagementServiceTest`（补，需求 2）

10. bulk 关闭：3 个开启 → 全部 `autoReplyEnabled=false`，每个有状态历史（同状态 transition）与审计调用。
11. bulk 开启：2 关闭 + 1 关闭且 `manualHandoffRequired=true` → updated=2、skipped=1，handoff contact 不变。
12. summary 统计正确（含 handoffLocked）。

### Controller 层（`MailAutomationControllerTest` 或新建，MockMvc）

13. start：正常 202；运行中重复 start → 409。
14. pending-count 返回结构正确。
15. cancel 流（复用 TaskProgress 端点，已有覆盖可不重测，但跑通一次 MANUAL_INITIAL_OUTREACH 类型）。

### 回复链路回归（需求 1 第 6 点「邮件回复记录正确返回」）

16. 手动首发的 contact 收到入站回复（走 `AutoMailReplyService` 既有测试套路）：能按 messageId/邮箱匹配到该 contact、状态按既有规则流转（INTRO_SENT/WAITING_REPLY → QA_AUTO_REPLIED 或 MANUAL_HANDOFF）、`getContactDetail(contactId)` 的 `mails` 时间线同时含 OUTBOUND INTRODUCTION 与 INBOUND 回复且按 createdAt 升序。
17. `ExpertContactManagementServiceTest` 补：对手动首发 contact 调 `getContactDetail`，statusHistory、mails、recommendedNextAction 正确。

### 手工验收（前端，无自动化）

18. 启动 → 面板计数随发送增长；刷新页面 → 面板与「停止发送」按钮态恢复；点停止 → 当前封发完后停（CANCELLED）；终态后列表中已发专家徽章为「已联系」。
19. 全局自动回复按钮三态文案/confirm/跳过提示正确，切换后列表详情页的回复模式下拉与之一致。
20. 检查回复：列表勾选 2 人 → 点按钮直接执行（无弹窗）→ 完成后列表上方出现轮询日志面板，行可展开账号明细；未勾选时 confirm 提示全量。其余三个后台任务弹窗流程不回归。

## 六、验收标准

- [ ] 三个按钮均在 `#view-contacts` 工具栏，无弹窗遮挡列表的交互。
- [ ] 批量发送可启动、可停止、进度（待发送/已发送/失败）刷新页面后仍展示，且服务重启后运行记录可在 `#view-tasks` 审计。
- [ ] 发送成功的专家：`INTRO_SENT` + `CONTACTED` + 状态历史 + 邮件记录齐全；失败的专家可被下次任务自动补发；**任何情况下不会给同一专家重复发送介绍邮件**。
- [ ] 全局自动回复开关正确反映聚合态，MANUAL_HANDOFF 专家不被批量开启绕过。
- [ ] `mvn test` 全绿；新增逻辑均有对应测试（§五 1–17）。
- [ ] `docs/design.md` 若与会话状态/发送链路描述相关处有出入，更新之（至少补充 MANUAL_INITIAL_OUTREACH 任务类型与 MANUAL_OUTREACH campaign 约定）。

## 七、风险与注意事项

1. **不要修改任何已应用的 Flyway 迁移**；本计划无 schema 变更（不需要新迁移）。若执行中发现需要 schema 变更，新建 `V23__*.sql`。
2. **不要动 `InitialOutreachService.sendInitialBatch` 的对外行为**（scheduler/queue 在用）；新逻辑全部在新服务里。
3. Kotlin all-open 已启用，Spring Bean 不需 `open`；持久化是 Spring Data JDBC（无懒加载），新查询写在 Repository 接口上。
4. 异步 executor 必须单线程 + `tryStartWithToken` 双保险，防止并发两个批量任务同时跑同一批专家。
5. 进度 `errors` 列表注意截断（如保留最近 20 条），`task_progress_log.details_json` 别写入超长内容。
6. 前端 `resumeProgressPollingIfNeeded` 的改动注意不要影响其余三个任务按钮的恢复行为（`taskButtonMapping` 通用路径上对 `MANUAL_INITIAL_OUTREACH` 做分支处理）。
7. `todaySentCount` 无自动日重置是已知现状：额度耗尽时任务正常停止并提示，操作员可在账号页 reset 后再次点击按钮继续（补发机制保证不重发）。后续如要每日自动重置，另行立项。
