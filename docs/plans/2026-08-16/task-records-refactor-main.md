# 主计划：任务记录页重构（性能 / 任务语义 / 明细跳转 / 审计保留）

创建日期：2026-08-16　　最后修订：2026-08-16（与批量控制台修复族合链、补缓存键不变量、修正 O-3）
编号：本族为 **B1–B5**，是全链后 5 份。全链（A1–A3 + B1–B5）执行顺序见 `00-execution-order.md`。
子计划数：5（B1 / B2 / B3 / B4 / B5），**必须串行**，且**整体在 A1–A3 之后**
迁移版本占用：V100（P0）、V101（P2a）、V102（P3）——当前最大为 `V99__add_gate_filter_enabled_to_batch_send_task_config.sql`

---

## 需求描述

### Observable outcomes

1. **O-1** 「任务记录」页首屏加载从当前的「全表返回 + 全量 TEXT」变为分页返回，默认 50 条；切换 Tab 不再整页阻塞。
2. **O-2** 表格「任务类型」列显示中文名而非裸枚举；类型下拉覆盖后端实际写入的**全部** taskType，而非当前硬编码的 5 项。
3. **O-3** 「发信统计/成功数」这一列**保持单列**。列内数值语义随任务类型变化并在行内以灰色小字标注（例：`MANUAL_INITIAL_OUTREACH` 显示 `10/0 已发送/失败`，`AUTO_REPLY_ACCOUNT` 显示 `3/1 已回复/转人工`）。
   ⚠️ **2026-08-16 修正**：列表列渲染的是**已持久化的** `success_count` / `failure_count`（M-1 禁止列表读 `result_summary`），而这两个值由写入侧的反射机制决定 —— 对多数任务类型它们恒为 `0/0`（如 `EXPERT_ENRICHMENT` 的字段是 `enriched`/`failed`，反射名单一个都不命中）。因此**只有存量计数确实可信的少数类型**才给语义标签，其余一律显示 `— 无统计`，真实业务指标改由**展开明细**承载（extractor 三级取数）。成文版本曾承诺「每种任务都有语义数字」，那是错的；逐条证据见 B2 的 `T1-1` 表。
4. **O-4** 展开任意任务行都能看到内容：有结构化明细的按类型渲染，无明细的回落为 `requestPayload` / `resultSummary` 的 JSON 折叠视图，不再出现「暂无明细」空态。
5. **O-5** 邮件类任务（`MANUAL_INITIAL_OUTREACH` / `INITIAL_OUTREACH`）行内提供「查看本次发出的邮件」链接，跳转到收发件箱并按该次执行过滤；专家类任务（`AUTO_REPLY_ALL` / `CHECK_REPLIES`）的明细行内专家可点击跳转到专家详情。
6. **O-6** 结果落在 ES 的任务（`EXPERT_ENRICHMENT` / `RAW_PROMOTION_SCAN` / `EXPERT_DISCOVERY`）**明确置灰**跳转入口并标注「该任务无个体明细」，不提供任何近似跳转。
7. **O-7** `task_execution` 与 `task_progress_log` 具备 90 天保留策略，由定时任务清理。

### What must NOT change

- **N-1** `GET /api/task-executions/recent-polls` 与 `/recent-polls/{id}/detail` 的响应形状与语义不变——「轮询日志」弹窗（`app.js:6361` / `:6425`）依赖它们。
- **N-2** `GET /api/task-progress/{taskType}/logs` 的 `batchOnly` 参数语义不变，其两条既有用例（`TaskProgressControllerExecutionsTest` 的 "batchOnly filters out batchNumber zero and negative" 与 "batchOnly false returns all logs"）必须继续通过。（来源: K-progress-log-batchonly-two-readers）
- **N-3** `TaskExecutionService.runAndRecord` / `runAndRecordWithResult` 的**签名与调用时序**不变；23 个调用点（见 X-3）一行不改。
- **N-4** `task_execution` 已有列的语义与写入时机不变；本轮不改 `success_count` / `failure_count` 的**写入**，只改**展示**。
- **N-5** `GET /api/task-executions?taskType=CANDIDATE_OPERATOR_STATUS_SYNC`（`app.js:5276`）必须继续可用（P0 会改响应形状，该调用点须同步适配，见 P0 的 I0-4）。
- **N-6** `mail_record` 既有列与既有读取路径不变；`task_execution_id` 为新增可空列，为 null 时所有既有行为逐字相同。
- **N-7** `MeetingScheduleService` / `ManualExpertMailService` / `PendingMailOperationService` / `AutoMailReplyService` / `ManualReplySendAttemptService` / `MailboxService` 里的 `MailRecord(...)` 构造点一行不改（见 X-4 的 grep 回执）。

### Out of scope（本轮明确不做）

- **不做** `TaskResultSummary.from()` 反射机制的**删除**。P1 只是让它不再决定**展示**（catalog 优先），写入侧继续沿用，避免动到 23 个 `runAndRecord` 调用点的状态判定。删除留待后续计划。
- **不做** 进程重启后遗留 RUNNING 行的启动收敛。这是真问题（见 X-2），但它属于任务生命周期而非记录页，且与 `K-clearExecutionContext-status-leak` 耦合，单独立项。
- **不做** ES 文档补 `lastEnrichmentRunId`（已与需求方确认走 O-6 置灰路线）。
- **不做** `task_progress_log` 的写放大治理（K-progress-log-per-mail-write-amplification 的方案①）。P3 只做保留窗口（方案②）。
- **不做** 新增侧栏视图。跳转一律复用既有 `mailbox` / `contacts` 视图，规避 `K-view-registration-triad` 的四联注册。
- **不做** 任务记录页的自动刷新 / 轮询。

---

## 子计划与执行顺序

| 全链序 | 文件 | 主题 | 子系统 | 文件数 | 缓存键 | 前置 |
|---|---|---|---|---|---|---|
| 4 | `b1-task-execution-list-performance.md` | 列表投影 + 分页 + 索引 | task / frontend | 9 | `20260817-v4-task-records-paging` | **A3** |
| 5 | `b2-task-type-catalog-semantics.md` | TaskTypeCatalog + 单列语义 + 通用明细 | task / frontend | 10 | `20260817-v5-task-type-catalog` | B1 |
| 6 | `b3-mail-record-execution-link-backend.md` | `mail_record.task_execution_id` 写入 | campaign / mail | 10 | 不适用（纯后端） | B2 |
| 7 | `b4-task-drilldown-frontend.md` | 跳转读取路径 + UI | mail / frontend | 10 | `20260817-v6-task-drilldown` | B3 |
| 8 | `b5-task-audit-retention.md` | 90 天保留清理 | task | 9 | 不适用（纯后端） | B1 |

### 为什么必须串行（具体冲突点，不是保守）

- **P0 → P1**：两者都改 `TaskExecutionController.listExecutions` 的返回 DTO。P0 把它从 `TaskExecutionResponse`（全字段）换成 `TaskExecutionListItem`（投影），P1 再往这个投影上加 `taskTypeLabel` / `metricLabel` / `summaryText`。并行必冲突。
- **P1 → P2a**：P2a 的跳转能力要挂在 P1 的 `TaskTypeCatalog.drilldown` 声明上。没有 catalog，P2a 只能硬编码 if-else，就退化成本次要消灭的东西。
- **P2a → P2b**：P2b 的查询 `WHERE task_execution_id = ?` 依赖 P2a 的列存在且已有数据写入。
- **B1 → B5**：B5 的清理 SQL 依赖 B1 建的 `idx_te_started`（按 `started_at` 批量删除，无索引则每次清理都是全表扫）。
- B5 与 B2/B3/B4 无冲突，可在 B1 后随时插入；表中排最后仅因优先级最低。
- **A3 → B1（跨族）**：A1/A2/A3 三份都 bump `index.html` 的缓存键三连并改 `batchSendTaskConsoleVisualFix.test.js` 的 literal 断言。B1/B2/B4 同样要改这两个文件，缓存键值按链依序取 v4/v5/v6。并行必冲突且测试红。见 M-7。

### 迁移版本必须依序占用

`V100`（P0）→ `V101`（P2a）→ `V102`（P3）。若实际合并顺序变化，**以合并顺序重编号**，不得跳号占位。

---

## 跨计划共享不变量

### Invariant M-1: 列表接口禁止携带大 TEXT 列

- Rule：任何**列表**性质的 task_execution 接口（返回多行），其 SQL 的 SELECT 列表中**不得出现** `request_payload` 或 `result_summary`；这两列只允许由**单行**详情接口（`/{id}` 或 `/{id}/detail`）读取。
- Applies to：`TaskExecutionRepository` 所有新增的分页/列表查询；`TaskExecutionController.listExecutions`；P1 的 `/task-types` 统计端点。
- Violation consequence：单条 `AUTO_REPLY_ALL` 的 `result_summary` 内嵌 `accounts[].repliedExperts[]`，50 行即可达数 MB，分页优化被完全抵消。
- 来源：original（本轮 Phase 1b 实测：`TaskExecutionResponse` 携带这两列，而 `app.js:8913` 的 `loadTasks` 只渲染 7 个标量字段）

### Invariant M-2: 运行中执行的指标取数走三级优先级

- Rule：任何展示单次执行聚合指标的代码，取数顺序必须是 ① `resultSummary`（终态权威）→ ② 该 executionId 最新一条 `task_progress_log.detailsJson` + 该行 `totalCount` → ③ `successCount` / `failureCount`。**禁止**以 `resultSummary` 为唯一数据源。
- Applies to：P1 的 `TaskExecutionSummaryExtractor`；P1 的 `/{id}/detail`；`TaskProgressController.getExecutions`（既有，改造后共用同一 extractor）。
- Violation consequence：`resultSummary` 只在 `runAndRecordWithResult` 的 block 返回后才写入（`TaskExecutionService.kt` 内 `repository.save(running.copy(resultSummary = ...))`），执行进行中一律解析为 null，所有指标渲染成 0——正是当前 `EXPERT_ENRICHMENT 运行中 0/0` 的成因之一。
- 来源：K-execution-detail-running-needs-progress-log

### Invariant M-3: taskType 语义只有一个声明源

- Rule：任务类型的中文名、分组、计数列语义标签、summary 提取规则、drilldown 声明，**全部**只在 `TaskTypeCatalog` 声明一次。`TaskProgressController.allowedTaskTypes` 必须从 catalog **派生**（`catalog.entries.filter { it.hasProgressUi }.keys`），不得再手工维护第二份字符串集合；前端不得硬编码任何 taskType 中文名或选项列表。
- Applies to：`TaskTypeCatalog.kt`（新）、`TaskProgressController.kt`、`TaskExecutionController.kt`、`app.js`、`index.html`。
- Violation consequence：这正是当前故障形态——`index.html:940` 的下拉写死 5 项、`TaskProgressController.allowedTaskTypes` 写死 6 项、`app.js:678` 的 `taskButtonMapping` 写死 6 项，三份名单互不相同且都不等于实际写入的 19 种。新增任务类型时必然漏改其中一处。
- 来源：K-allowedTaskTypes-whitelist（本轮再验证：该白名单仍是硬编码 set，且 `parseResultSummary` / `fallbackFromLog` 各需同步加分支——一处新增要改三处）

### Invariant M-4: 无 drilldown 声明的类型必须显式置灰

- Rule：`TaskTypeCatalog` 中 `drilldown = null` 的类型，前端渲染的跳转入口必须是**禁用态 + 明示文案**（`该任务无个体明细`），不得渲染成可点击链接，也不得用时间窗近似查询代替。
- Applies to：P1 的列表渲染、P2b 的明细渲染。
- Violation consequence：ES 类任务（`EXPERT_ENRICHMENT` / `RAW_PROMOTION_SCAN`）没有 run id，唯一可用的近似是拿 `started_at..finished_at` 卡 ES 的 `enrichedAt`；而线上 `enrichedAt` 映射是 `keyword` 不是 `date`，range 查询能工作纯属字符串格式巧合。给它一个"看起来能点"的链接会输出不可信数据。
- 来源：K-es-mapping-live-vs-repo-drift（项目记忆）+ 需求方 2026-08-16 决策

### Invariant M-5: `mail_record.task_execution_id` 的写入点封闭

- Rule：`task_execution_id` 只允许在 `ManualOutreachTxHelper.recordSuccess` / `recordFailure` 两处写入非 null 值。其余 14 处 `MailRecord(...)` 构造点保持不传该参数（走 Kotlin 默认值 null）。
- Applies to：见 X-4 的 16 处构造点全集。
- Violation consequence：多点写入会让「本次执行发了哪些邮件」出现来源不一致；且 `AutoMailReplyService` 的 INBOUND 记录若被误赋值，会把收信错算进发信批次。
- 来源：original（本轮 grep 取证，见 X-4）

### Invariant M-7: 缓存键三连与其测试断言必须同 commit 同步

- Rule：本族中**任何改动 `app.js` / `index.html` / `styles.css` 的计划**（B1 / B2 / B4），必须在同一 commit 内把 `index.html` 的三处 `?v=` 改为该计划分配到的新值，并把 `src/test/js/batchSendTaskConsoleVisualFix.test.js` 的三条 literal 断言改成同一字符串。取值：B1 = `20260817-v4-task-records-paging`，B2 = `20260817-v5-task-type-catalog`，B4 = `20260817-v6-task-drilldown`（承接 A1/A2/A3 的 v1/v2/v3）。B3、B5 为纯后端，不适用。
- Applies to：B1 的 S0-3/I0-6、B2 的 S1-5/I1-8、B4 的 S2b-4/I2b-6。
- Violation consequence：只 bump 不改断言 → 构建期 node 测试失败、WAR 构建中止（2026-08-13 发布 eda4853 实测踩坑）；只改代码不 bump → 浏览器加载旧 `app.js`，改动看着完全没生效，极易被误判为实现缺陷而重做一遍。
- 来源：K-frontend-cache-key-triad（**成文时本族五份计划全部漏载此条**，2026-08-16 复盘补入，见 X-8）

### Invariant M-6: 保留清理按 `created_at` 删，不按 executionId 关联

- Rule：`task_progress_log` 的清理条件必须是 `created_at < :cutoff`，**不得**写成「删除 task_execution 已不存在的行」或任何依赖 `task_execution_id` 关联的形式。
- Applies to：P3 的清理 SQL。
- Violation consequence：`TaskProgressStore.tryStartWithToken()` 落的初始化行持久化时 `task_execution_id = -System.nanoTime()`（负值），`bindExecutionId()` 之后若回写失败即成为永久孤儿；按关联删除会漏掉它们，按 `created_at` 删除才能清掉。
- 来源：K-progress-log-pending-token-orphan

---

## 现状审计（跨计划共享部分）

### X-1 ⚠️ 既有知识条目 `K-plan-quantified-claims-need-grep-receipts` 的陷阱 #3 已过期，本计划予以更正

该条目写「写 Spring Data JDBC `@Query` 返回 DTO 投影前，先 grep 本仓库全部 `@Query` 的返回类型——实测只有实体、标量、`List<String>` 三类，**零个 DTO 投影**」。

**该判断在 2026-08-16 已不成立。** grep 回执：

```
$ grep -rn -A8 "@Query(" src/main/kotlin --include=*.kt | grep -E "fun .*: (List<)?[A-Z][A-Za-z]+" | grep -vE ": (List<)?(String|Long|Int|Boolean|Double)"
mail/repository/InboundMailProcessingRepository.kt:79:    fun countGroupedByReasonType(): List<ReasonTypeCount>
mail/repository/InboundMailProcessingRepository.kt:157:   fun findLastReceivedAtPerAccount(): List<SenderAccountLastReceived>
mail/repository/InboundMailTagRepository.kt:40:           fun countQaTagsGroupedByRule(): List<QaTagCount>
mail/repository/InboundMailTagRepository.kt:63:           fun countCustomTagsGroupedByLabel(): List<CustomTagCount>
task/repository/TaskExecutionRepository.kt:66:            fun findLastStartedAtByBatchConfigIds(...): List<BatchConfigLastExecution>
campaign/repository/ExpertContactRepository.kt:122:      fun countBindingsByAccount(): List<AccountBindingCount>
```

**6 个 DTO 投影先例，其中一个（`BatchConfigLastExecution`）就在本计划要改的 `TaskExecutionRepository` 里。** 因此 P0 的 `TaskExecutionListItem` 投影**无需 spike**，直接照 `BatchConfigLastExecution` 的写法（列别名与 DTO 属性名对齐）即可。Phase 6 已回写更正。

### X-2 `task_execution` 表结构（迁移逐条核对）

`V4__create_task_execution.sql` 建表，全文：

```sql
CREATE TABLE task_execution (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_type VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_payload TEXT,
    result_summary TEXT,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at DATETIME NOT NULL,
    finished_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

后续只有 `V73__add_batch_config_id_to_task_execution.sql` 动过它：加 `batch_config_id BIGINT NULL`、加 `idx_task_execution_batch_config_started (batch_config_id, started_at)`、加 FK。

**索引现状（全集）**：主键 `id`、`idx_task_execution_batch_config_started`。**没有** `started_at` 单列索引，**没有** `task_type` 索引，**没有** `status` 索引。因此当前 `ORDER BY started_at DESC`、`WHERE task_type = ?`、`WHERE status = ?` 全部是全表扫 + filesort。

**status 值域**（写入侧 grep 回执，`TaskExecutionService.kt`）：`RUNNING`、`SUCCESS`、`PARTIAL_SUCCESS`、`FAILED`；`TaskExecutionSummaryProvider.taskFinalStatus` 另可返回 `CANCELLED`（`ManualOutreachResult` 的 `wasCancelled -> "CANCELLED"`）。而 `index.html:948` 的状态下拉只有 `RUNNING` / `SUCCESS` / `FAILED` **三项**——`PARTIAL_SUCCESS` 与 `CANCELLED` 的执行在当前 UI 上**无法被筛出**。

**无启动收敛**：`grep -rn "ApplicationReadyEvent" src/main/kotlin` 无命中；`@PostConstruct` 的 5 处命中（`UnsupportedAnswerIndexService` / `TimeZoneConfig` / `BatchSendScheduler` / `BatchSendControlService` / `ExpertIndexService`）均不涉及 task_execution 状态收敛。故进程重启后遗留的 RUNNING 行永久停留（本轮 Out of scope，记录在案）。

### X-3 `runAndRecord` / `runAndRecordWithResult` 调用点全集（grep 回执，N-3 依据）

```
$ grep -rn "runAndRecord" src/main/kotlin --include=*.kt | grep -v "fun runAndRecord"
llm/service/AiQaExtractionScheduler.kt:19            "AI_QA_EXTRACTION"
postmaster/service/PostmasterScheduler.kt:17         "POSTMASTER_REPUTATION"
mail/controller/MailAutomationController.kt:145      (WithResult)
mail/queue/MailQueueConsumer.kt:23 / :41 / :55       (3 处)
discovery/controller/ExpertDiscoveryController.kt:82 / :163 / :232
discovery/service/ExpertDiscoveryScheduler.kt:44     "EXPERT_DISCOVERY"
task/service/MailAutomationScheduler.kt:31 / :52 / :70 / :82
task/service/BounceCollectionScheduler.kt:32         "BOUNCE_COLLECTION"
task/service/DailyCountResetScheduler.kt:28          "DAILY_COUNT_RESET"
campaign/service/BatchSendControlService.kt:353      (WithResult<ManualOutreachResult>)
expert/controller/ExpertIndexController.kt:136 / :175 / :205 / :226
```

**共 23 个调用点。** 本计划全部不改。

### X-4 `MailRecord(...)` 构造点全集（grep 回执，M-5 / N-7 依据）

```
$ grep -rn "MailRecord(" src/main/kotlin --include=*.kt | grep -v "domain/MailRecord.kt"
mail/service/MailboxService.kt:210 / :241            ← 读取侧（toDetailFromMailRecord），非构造
mail/service/AutoMailReplyService.kt:265 / :579 / :769 / :967
mail/service/ManualReplySendAttemptService.kt:229 / :307
mail/service/ManualExpertMailService.kt:70
campaign/service/MeetingScheduleService.kt:145
campaign/service/ManualOutreachTxHelper.kt:59 / :108   ← ★ P2a 的唯一写入点
```

**实际构造点 14 处**（`MailboxService` 的 2 处是读取侧签名与调用，不构造新行）。其中只有 `ManualOutreachTxHelper.kt:59`（recordSuccess）与 `:108`（recordFailure）属于「批量/首发任务的发信」范畴。

`recordSuccess` / `recordFailure` 的**调用方**（排除同名的 `AccountRateLimiter.recordSuccess` 与 `BatchExecutionModels` 的 accumulator）：

```
生产侧（3 处 → txHelper）：
  ManualInitialOutreachService.kt:741   txHelper.recordSuccess
  ManualInitialOutreachService.kt:754 / :768 / :792 / :808   txHelper.recordFailure（4 处）
  InitialOutreachService.kt:90          txHelper.recordSuccess
  InitialOutreachService.kt:99          txHelper.recordFailure
```

⚠️ **测试侧依赖（P2a 改签名必然波及，共 3 个文件 11 处）**：

```
src/test/kotlin/.../campaign/service/InitialOutreachServiceTest.kt:93 / :101 / :132 / :165 / :173
src/test/kotlin/.../campaign/service/ManualOutreachTxHelperTest.kt:81 / :159 / :180 / :207
src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt:371 / :1116 / :1862
```

即使用 Kotlin 默认参数新增 `taskExecutionId: Long? = null`，生产侧传入非 null 后，这些 `Mockito.verify(txHelper).recordSuccess(6 个实参)` 仍会 argument mismatch 失败。**P2a 必须把这 3 个测试文件列入变更清单**，不得以「默认参数向后兼容」为由略过。（来源: K-plan-quantified-claims-need-grep-receipts 陷阱 #2）

### X-5 `ManualOutreachTxHelper` 两个方法的现签名（改动前基线）

```kotlin
fun recordSuccess(
    contact: ExpertContact,
    accountCode: String,
    deliveredMessageId: String?,
    subject: String?,
    body: String?,
    attemptId: Long
) { ... }

fun recordFailure(
    contactId: Long,
    accountCode: String,
    messageId: String?,
    errorSummary: String?,
    subject: String?,
    body: String?,
    attemptId: Long?
) { ... }
```

两条路径共用（来源: K-dual-outreach-paths）：`InitialOutreachService.sendInitialBatch()`（cron，简单循环）与 `ManualInitialOutreachService`（round-based 引擎）。**`ManualInitialOutreachService` 作用域内已持有 `executionId`**（用于 `progressStore.isCancelled("MANUAL_INITIAL_OUTREACH", executionId)`，见 `:207` / `:549`），无需新增传参链路；`InitialOutreachService` 需要通过 `runAndRecord` 的 `onStarted` 回调获取，这是 P2a 的主要工作量。

### X-6 `task_progress_log` 表结构（P3 依据）

`V22__create_task_progress_log.sql` 建表，索引只有两个：`idx_tpl_task_type (task_type)`、`idx_tpl_execution_id (task_execution_id)`。**无 `created_at` 索引，无任何清理策略。**

`TaskProgressStore.update()` 每次调用都 `persistProgressLog()` 落一行，而 `ManualInitialOutreachService.updateProgressWithAccumulator()` 每发一封邮件调用一次，每行 `details_json` 内嵌 `buildAccountStats()` 的完整启用账号数组。**该表的增长速度远超 `task_execution`**，P3 的保留策略必须覆盖它。（来源: K-progress-log-per-mail-write-amplification）

### X-7 前端样式盘点（P0/P1/P2b 共享）

**可复用 class（全部已存在，本计划不新增分页/徽章/链接类样式）**

| class | styles.css 行号 | 用途 |
|---|---|---|
| `.list-pager` | 1105-1113 | 分页条容器（flex 居中，`border-top: 1px solid var(--line)`） |
| `.list-pager-info` | 1115-1119 | 页码信息（`font-size:12px`、`color:var(--text-muted)`、`font-family:var(--font-mono)`） |
| `.button.small` | 2316-2321 | 分页按钮（`height:26px`、`padding:0 8px`、`font-size:11px`） |
| `.link-btn` | 2517-2530 | 行内文字链接（`color:var(--primary)`、`font-size:11px`、`font-weight:600`、hover 下划线） |
| `.text-muted` | 2323-2326 | 弱化文案（`color:var(--text-muted)`、`font-size:12px`） |
| `.badge` / `.ok` / `.warn` / `.error` / `.info` / `.warn-yellow` | 900 / 914 / 920 / 926 / 932 / 944 | 状态徽章 |
| `.table-wrap` | 833 | 表格滚动容器 |
| `.panel` / `.panel-head` | 801 / 815 | 面板 |
| `.toolbar` | 351（含 `input,select` 362-363） | 顶部筛选条 |
| `.pre` | 1721 | 等宽预格式化块（P1 的 JSON 折叠视图复用） |

**`.list-pager` 的既有 DOM 骨架（5 处一致，P0 逐字复用）**

```html
<div id="suppressionPager" class="list-pager" hidden>
    <button class="button small" id="suppressionPrevPage">上一页</button>
    <span id="suppressionPageInfo" class="list-pager-info"></span>
    <button class="button small" id="suppressionNextPage">下一页</button>
</div>
```

既有实例：`index.html:439`（退订名单）、`:658`（专家联系）、`:822`（AI 训练 QA）、`:891`（模拟邮件）、`:928`（未支持回答）。**新增的任务分页条必须逐字沿用该骨架，只改三个 id 前缀。**

**改动前基线：任务记录视图当前 DOM（`index.html:937-977`，逐字）**

```html
<!-- View 6: Async Task execution log audit -->
<section class="view" id="view-tasks">
    <div class="toolbar">
        <select id="taskTypeFilter">
            <option value="">全部自动化任务</option>
            <option value="INITIAL_OUTREACH">首发邮件任务 (Initial Outreach)</option>
            <option value="MANUAL_INITIAL_OUTREACH">手动批量首发邮件</option>
            <option value="AUTO_REPLY_ALL">全量账号自动收信回复任务</option>
            <option value="AUTO_REPLY_ACCOUNT">单账号轮询自动回复任务</option>
            <option value="AUTO_REPLY_ALL_DISPATCH">批量分发与调度任务</option>
        </select>
        <select id="taskStatusFilter">
            <option value="">全部执行状态</option>
            <option value="RUNNING">RUNNING - 执行中</option>
            <option value="SUCCESS">SUCCESS - 执行成功</option>
            <option value="FAILED">FAILED - 执行失败</option>
        </select>
        <button class="button primary" id="loadTasksBtn">查询任务执行记录</button>
    </div>

    <section class="panel">
        <div class="panel-head"><h2>定时任务审计与消费日志 (Spring Job Audit)</h2></div>
        <div class="table-wrap">
            <table>
                <thead>
                <tr>
                    <th>审计 ID</th>
                    <th>任务类型</th>
                    <th>触发方式</th>
                    <th>当前状态</th>
                    <th>发信统计/成功数</th>
                    <th>开始时间</th>
                    <th>异常堆栈/错误原因</th>
                </tr>
                </thead>
                <tbody id="tasksTable"></tbody>
            </table>
        </div>
    </section>
</section>
```

**改动前基线：`loadTasks()` 当前实现（`app.js:8906-8924`，逐字）**

```javascript
async function loadTasks() {
    const params = new URLSearchParams();
    const taskType = $("#taskTypeFilter").value;
    const status = $("#taskStatusFilter").value;
    if (taskType) params.set("taskType", taskType);
    if (status) params.set("status", status);
    const suffix = params.toString() ? `?${params}` : "";
    const tasks = await api(`/api/task-executions${suffix}`);
    $("#tasksTable").innerHTML = tasks.map((task) => `
        <tr class="task-row" data-task-id="${task.id}" data-task-type="${escapeHtml(task.taskType)}" onclick="toggleTaskDetail(this)" style="cursor:pointer;">
            <td>${task.id}</td>
            <td>${escapeHtml(task.taskType)}</td>
            <td>${escapeHtml(task.triggerType)}</td>
            <td>${badge(labelStatus(task.status), task.status === "SUCCESS" ? "ok" : task.status === "FAILED" ? "error" : "warn")}</td>
            <td>${task.successCount}/${task.failureCount}</td>
            <td>${escapeHtml(task.startedAt)}</td>
            <td>${escapeHtml(task.errorMessage || "")}</td>
        </tr>
    `).join("");
}
```

**分页调用范式（`app.js:3742-3755`，`loadSuppressions`，P0 逐字对齐）**

```javascript
async function loadSuppressions() {
    const size = 50;
    const params = new URLSearchParams();
    params.set("page", String(state.suppressionsPage));
    params.set("size", String(size));
    if (state.suppressionKeyword) {
        params.set("keyword", state.suppressionKeyword);
    }
    const data = await api(`/api/suppressions?${params}`);
    state.suppressions = data.items || [];
    state.suppressionsTotal = data.total ?? state.suppressions.length;
    renderSuppressionsTable();
    renderSuppressionPager(size);
}
```

**⚠️ 分页参数命名有两套并存，本计划统一取 `page` / `size`**：
- `page` / `size` + `{items, total}` —— `/api/suppressions`（扁平审计表 + `.list-pager`，与任务记录页结构同构）
- `pageSize` / `pageOffset` —— `InboundMailSummaryController:36`、`BounceController:28`、`UnmatchedInboundMailController:93`

任务记录页取**前者**，因为它的 DOM 与交互与退订名单完全同构，可直接复用 `renderSuppressionPager` 的渲染范式。

### X-8 ⚠️ 成文时的三处研究缺口与一处错误结论（2026-08-16 复盘补记）

本族计划成文后自查发现以下问题，均已在对应子计划中修正。记录在此以便追责与复用：

1. **漏载 `K-frontend-cache-key-triad`**（P1 级知识，hit_count 4，就在 `docs/knowledge/frontend/`）。Phase 0 载入知识时按关键词从 frontend 域 46 条里筛了 8 条，把它漏了 —— 这是 Phase 0 执行不到位，不是知识库缺失。后果：五份计划里改前端的三份都会踩构建中止或改动不生效。已补为 M-7。
2. **漏载 `K-js-test-invocation-surface`**。验证命令恰好写对了（与另一族的权威文本一致），但没提 pom 也跑的 `node --check src/main/resources/static/app.js`，也没提 `verify.sh` 只跑一个文件、不可当门禁。已在三份前端计划的验证命令中补入。
3. **`app.js:5276` 的用法判断错误**。成文时只标了「执行前须读」，补读后发现它取 `tasks[0]` 且读 **`task.resultSummary`** —— 而 M-1 明令列表不返回该字段。只改 `.items` 会让 `skipped` 静默恒为 0。已重写 B1 的 I0-4 为两段式请求，并把逐字基线补进 B1 的现状审计。
4. **B2 的 `metricLabel` 表是编的**。16 条里有 3 条经不起核对（`BOUNCE_COLLECTION` 的 block 返回 Unit 恒 `0/0`、`AUTO_REPLY_ACCOUNT` 实际取 `replied`/`manualReview`、`AUTO_REPLY_ALL_DISPATCH` 失败位恒 0），其余多条未核实。已逐条附结果类源码位置，未核实的标 ⏳ 并规定「先按 null 落地」。连带修正了 O-3。
5. **行号偏差**：`TaskProgressController` 三个方法成文写 `:120-172`/`:174-227`/`:229+`，实际 `:122-179`/`:180-235`/`:236+`。已修正。
6. **两条 MySQL 通用断言未在本仓库实测**：`ORDER BY col DESC` 走升序索引反向扫描（已在 B1 补标「执行时 EXPLAIN 确认」）、`DELETE ... ORDER BY ... LIMIT`（B5 成文时已标须 spike，无需补）。
7. **`git diff styles.css 为空` 这个断言写法是错的**。合链后 A1 会改 CSS，整文件 diff 必然不为空。三份前端计划已改为「本计划自身 commit 中不含 CSS 规则块增删改」。

> 教训：Phase 0 的知识载入不能只按文件名关键词筛，域内条目多时要按 severity 与 hit_count 兜一遍。severity=P1 且 hit_count≥3 的条目应当无条件读。

### 交互点（跨计划）

| # | 写入路径 | 读取路径 | 受影响子计划 |
|---|---|---|---|
| IP-1 | `TaskExecutionService.runAndRecord*` 写 `result_summary` | P1 `TaskExecutionSummaryExtractor` 读 | P1 |
| IP-2 | `TaskProgressStore.persistProgressLog` 写 `task_progress_log` | P1 extractor 的第 ② 级取数 / P3 清理 | P1, P3 |
| IP-3 | `ManualOutreachTxHelper` 写 `mail_record.task_execution_id` | P2b 的 `WHERE task_execution_id = ?` | P2a, P2b |
| IP-4 | P1 `TaskTypeCatalog` 声明 | `TaskProgressController.allowedTaskTypes` 派生 / 前端下拉 | P1 |
| IP-5 | P3 清理删除 `task_execution` 行 | P2b 的邮件反查（FK 已删但 mail_record 仍在） | P2a, P3 |

**IP-5 需显式处理**：P2a 建 `task_execution_id` 时**不得**加外键约束（或必须用 `ON DELETE SET NULL`），否则 P3 的保留清理会因 FK 约束删不掉 90 天前的 `task_execution` 行。P2a 采用**不加 FK**（与 `V73` 的 `batch_config_id` 加了 FK 的做法不同，理由：`batch_send_task_config` 是软删除永不物理删除，`task_execution` 是硬删除）。

---

## 验证命令

> 本项目**必须**用 JDK 11（zulu-11）。裸 `mvn` 会构建失败。
> JS 测试挂在 Maven `test` 阶段的 `exec-maven-plugin`（`pom.xml:184-201`），执行 `node --test src/test/js/*.test.js`；也可脱离 Maven 单跑。

```bash
# 全量测试（回归门禁，含 Kotlin + JS）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建（WAR）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 单个 Kotlin 测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskExecutionListPagingTest

# 单个 Kotlin 测试方法
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=TaskExecutionListPagingTest#methodName

# 全部 JS 测试（不走 Maven，迭代快）
node --test src/test/js/*.test.js

# 单个 JS 测试文件
node --test src/test/js/taskRecordsPaging.test.js

# Flyway 迁移集成测试（需本地 Docker；平时被 @EnabledIfSystemProperty 跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 空白/换行卫生
git diff --check
```

通过判据：
- Maven：退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0`，且 `BUILD SUCCESS`。
- `node --test`：退出码 0，输出末尾 `fail 0`。
- `git diff --check`：无输出。

来源：`CLAUDE.md`「Commands」章节 + 项目元信息 `test_command:` / `build_command:`；JS 命令取自 `pom.xml:199` 的 `exec-maven-plugin` argument 原串。

---

## 验收标准（主计划层）

- **M-1**：`grep -n "request_payload\|result_summary" src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt` 的命中，全部位于单行详情查询中；所有分页/列表查询的 SELECT 列表不含这两列。
- **M-2**：存在一条针对「RUNNING 状态执行（`resultSummary` 为 null）但 `task_progress_log` 有 `detailsJson`」的用例，断言 extractor 返回非零指标。
- **M-3**：`grep -rn "allowedTaskTypes" src/main/kotlin` 的赋值处从 catalog 派生；`grep -n "INITIAL_OUTREACH\|AUTO_REPLY_ALL\|EXPERT_ENRICHMENT" src/main/resources/static/index.html` 在 `view-tasks` 段内**无命中**（选项改为运行时注入）。
- **M-4**：JS 用例断言 `drilldown` 为 null 的行渲染出 `disabled` 属性与 `该任务无个体明细` 文案，且**不含** `href` / `data-action`。
- **M-5**：`grep -rn "taskExecutionId" src/main/kotlin --include=*.kt | grep "MailRecord("` 恰好命中 `ManualOutreachTxHelper.kt` 的 2 行。
- **M-6**：P3 的清理 SQL 文本断言含 `created_at <` 且**不含** `JOIN` / `EXISTS` / `task_execution_id`。
- 回归：执行「验证命令」节的全量测试命令通过。

---

## 人工验收清单（主计划层）

### A-M1: 五个子计划全部上线后的端到端冒烟

- 前置条件：`task_execution` 有 ≥ 200 行历史数据，其中至少各有 1 行 `MANUAL_INITIAL_OUTREACH`（已完成、`sent > 0`）、1 行 `AUTO_REPLY_ALL`、1 行 `EXPERT_ENRICHMENT`。
- 操作步骤：
  1. 打开「任务记录」Tab，掐表记录首屏出现表格数据的时间。
  2. 观察表格首屏行数与底部分页条。
  3. 查看「任务类型」列。
  4. 查看「发信统计/成功数」列在上述三种任务上的显示。
  5. 分别点开三行。
  6. 在 `MANUAL_INITIAL_OUTREACH` 行点「查看本次发出的邮件」。
- 预期结果：
  1. 首屏 < 2 秒（改动前为数十秒）。
  2. 恰好 50 行；底部出现「上一页 / 第 1 页，共 N 条 / 下一页」。
  3. 显示中文名（如「批量首发邮件」「全量账号自动收信回复」「学术数据补全」），不出现裸大写枚举。
  4. `MANUAL_INITIAL_OUTREACH` 显示形如 `10/0 已发送/失败`；`EXPERT_ENRICHMENT` 显示 **`— 无统计`**（灰色无数字，这是正确行为 —— 其存量计数恒为 0，见 O-3 的修正说明）；`AUTO_REPLY_ALL` 视 ⏳ 核实结果而定，未核实前显示 `— 无统计`。
  5. 三行均展开出内容，无「暂无明细」；`EXPERT_ENRICHMENT` 的**真实**补全成功/失败数在展开区可见。
  6. 跳转到「收发件箱」，列表被过滤为该次执行发出的邮件，条数与该行的「已发送」数一致。
- 覆盖：O-1 ~ O-5

### A-M2: ES 类任务的置灰（M-4）

- 前置条件：存在一行已完成的 `EXPERT_ENRICHMENT` 执行。
- 操作步骤：展开该行，观察跳转入口区域；尝试点击。
- 预期结果：入口为灰色不可点状态，旁边文案为「该任务无个体明细」；点击无任何反应，不发起网络请求（可在 DevTools Network 面板确认）。
- 覆盖：O-6 / M-4

### A-M3: 回归 —— 轮询日志弹窗未受影响（N-1）

- 前置条件：任意。
- 操作步骤：点击顶部导航「轮询日志」。
- 预期结果：弹窗正常打开，展示最近 10 次轮询，每行含账号数、抓取数、回复数、下次轮询时间，与改动前完全一致。
- 覆盖：N-1

### A-M4: 回归 —— 批量任务进度弹窗未受影响（N-2）

- 前置条件：在「专家联系」页发起一次批量发送任务（可设极小额度，如 1 封）。
- 操作步骤：任务运行中打开进度弹窗，切到「批次明细」。
- 预期结果：批次明细逐批展示，与改动前一致；不出现重复批次行。
- 覆盖：N-2 / K-progress-log-batchonly-two-readers

### A-M5: 回归 —— 状态同步任务查询未受影响（N-5）

- 前置条件：`task_execution` 中存在 `CANDIDATE_OPERATOR_STATUS_SYNC` 行。
- 操作步骤：进入依赖该查询的页面（`app.js:5276` 的调用点所在视图），确认数据正常渲染。
- 预期结果：数据正常显示，浏览器控制台无 `undefined` / `.map is not a function` 类报错。
- 覆盖：N-5

### A-M6: 保留策略生效（O-7）

- 前置条件：手工插入一行 `task_execution`，`started_at` 与 `created_at` 均设为 91 天前；再插入一行 `task_progress_log`，`created_at` 设为 91 天前且 `task_execution_id` 为负值（模拟孤儿行）。
- 操作步骤：手动触发保留清理任务（或将 cron 临时改为 1 分钟后），等待执行完成，查询两张表。
- 预期结果：两行均被删除；90 天内的行一行不少；`任务记录` 页出现一条 `TASK_AUDIT_RETENTION` 的执行审计行，`成功数` 等于删除总行数。
- 覆盖：O-7 / M-6

---

## 知识回写（Phase 6）

见各子计划末尾的「知识回写」小节。主计划层已执行：

1. **更正** `docs/knowledge/audit/K-plan-quantified-claims-need-grep-receipts.md` 的陷阱 #3 —— 「本仓库零个 DTO 投影」已过期，实测 6 处（见 X-1），并附 grep 回执。
2. **新增** `docs/knowledge/task/K-task-execution-list-full-scan.md` —— 列表接口三重放大的成因与不变量 M-1。
3. **新增** `docs/knowledge/task/K-task-type-semantics-三份名单.md` —— M-3 的三份互不相同的硬编码名单全集。
4. **新增** `docs/knowledge/frontend/K-list-pager-skeleton-reuse.md` —— `.list-pager` 五处一致骨架，新增分页一律逐字复用。
5. **命中并续期**（`last_used` → 2026-08-16，`hit_count` +1）：`K-allowedTaskTypes-whitelist`、`K-execution-detail-running-needs-progress-log`、`K-progress-log-batchonly-two-readers`、`K-progress-log-pending-token-orphan`、`K-progress-log-per-mail-write-amplification`、`K-dual-outreach-paths`、`K-expert-contact-two-write-sites`、`K-view-registration-triad`、`K-plan-quantified-claims-need-grep-receipts`、`K-empty-list-in-query-guard`。

`K-view-registration-triad` 的 `hit_count` 达 20，已超过晋升阈值 10 —— 其一行形式**已在** `CLAUDE.md` 的「团队沉淀知识」中（`新增前端侧栏 Tab/视图须四处同步注册…(K-view-registration-triad)`），无需重复晋升。
