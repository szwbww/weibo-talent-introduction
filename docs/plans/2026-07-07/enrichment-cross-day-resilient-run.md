# OpenAlex 学术数据补充：限流不熔断、自动跨天续跑、进度明确

> 计划日期: 2026-07-07
> 触发背景: 手动执行「补充学术数据（OpenAlex）」一次只推进 250 条且全部失败（RATE_LIMITED:250, CIRCUIT_BREAKER:1）。目标是一次触发即可把 CANDIDATE 层 9 万+ 待补充专家全部跑完，遇限流放缓等待而非退出，跨天持续执行，进度全程可见、可暂停。

## 需求描述

**可观察结果**：运营点一次「补充学术数据」，任务在后台持续运行（HTTP 立即返回，不再同步阻塞），遇 OpenAlex 限流时按 Retry-After / 指数退避等待后**原地重试同一批**，不熔断、不跳批，直到全部待补充专家处理完毕（可跨天）。进度弹窗实时显示已处理/总数、成功/失败、限流等待状态；「暂停」按钮随时生效（含退避等待期间）。

**不得改变的现有行为**：
- `enrichedAt` 30 天再补充语义不变（成功才写 `enrichedAt`，失败者留待下次运行重试）
- `updateExpertAcademicFields` 三层索引写入逻辑不变（K-enrichment-write-three-layers）
- 前端「已补充」徽标语义不变（有 `enrichedAt` 即已补充，终态失败不得伪造 `enrichedAt`）
- 任务互斥（`tryStartWithToken`）、暂停（`requestCancel`）、执行历史（`TaskProgressController`，EXPERT_ENRICHMENT 已在白名单）机制不变
- `TaskExecution` 审计记录仍由 `runAndRecordWithResult` 产生
- discovery 主流程（`discover()`/`discoverFromSource()`）完全不动

**明确不做（Out of scope）**：
- 不新增 ES 字段（如 `enrichmentStatus`/`enrichmentAttemptedAt`）——终态失败者保持 pending，靠下次运行重试，不改 3 个 mapping 文件
- 不做应用重启后的自动续跑（重启后靠再次手动触发续接，`enrichedAt` 天然断点续传）；如需定时自动触发另起计划
- 不改 `enrichmentDelayMs` 默认值（300ms 保留，env 可调）
- 不动 `fetchWorksEnabled`/`fetchPatentsEnabled`（保持默认 false）
- 不做前端退避倒计时动画等 UI 增强，只透出文本进度

## 关键不变量

### Invariant I-1: 限流不是失败终态
- Rule: `EnrichmentOutcome.RateLimited` 一律不计入 `failed`、不计入 `failureReasons["RATE_LIMITED"]` 的"失败"口径；被限流的 ORCID 必须在退避后重试，直到得到非 RateLimited 结果或任务被暂停。独立统计 `rateLimitWaits`（退避次数）供进度展示。
- Applies to: `ExpertDiscoveryService.enrichExistingExperts()` 内整批限流分支与逐条 outcome 消费分支
- Violation consequence: 复现当前 bug——限流批被记失败且跳过，一次运行白扣 250 人
- 来源: original

### Invariant I-2: WAIT 模式下无熔断退出
- Rule: `enrichment-rate-limit-mode=WAIT`（默认）时，连续限流只增大退避间隔（优先 Retry-After，否则 2s 起指数翻倍，封顶 `enrichment-max-backoff-ms`，默认 30 分钟），永不主动终止任务；仅 `ABORT` 模式保留原"连续 5 次熔断"行为。
- Applies to: `enrichExistingExperts()` 的退避逻辑；`OpenAlexProperties` 新配置
- Violation consequence: 跨天续跑失败，任务在夜间限流窗口自行退出
- 来源: original

### Invariant I-3: 退避等待必须可暂停
- Rule: 任何 >1s 的退避睡眠必须切成 ≤1s 的切片循环，每片检查 `progressStore.isCancelled("EXPERT_ENRICHMENT")`，命中立即走 CANCELLED 收尾路径。这是去掉熔断后的唯一人工兜底，不可省略。
- Applies to: `enrichExistingExperts()` 内所有 `Thread.sleep`（含退避与常规 delay）
- Violation consequence: 30 分钟退避期间「暂停」无响应，运营失去对任务的控制
- 来源: original

### Invariant I-4: 迭代方式禁止依赖 ES scroll 上下文
- Rule: enrichment 遍历必须改用 `search_after`（按 `orcidId` asc 排序、无状态分页），禁止使用 `scrollExpertsFiltered`。ES scroll keepalive 仅 5m（ExpertSearchService.kt:390/426），任何一次 >5m 的限流退避都会使 scroll 过期、任务 FAILED。
- Applies to: `enrichExistingExperts()` 的遍历入口；`ExpertSearchService` 新方法 `searchAfterExpertsFiltered`
- Violation consequence: 首次长退避后任务必然崩溃，跨天目标不成立
- 来源: original（现状审计发现）

### Invariant I-5: 单次运行的 pending 判定基准冻结
- Rule: `buildEnrichmentFilters()` 的 30 天阈值时间戳必须在任务启动时计算一次并全程复用（含 search_after 每页查询），禁止每页重新取 `LocalDateTime.now()`。同时查询必须排除 `orcidId` 前缀为 `EMAIL-` 的文档（无 ORCID，永远不可能补充成功）。
- Applies to: `buildEnrichmentFilters()`（改为接收冻结时间戳参数）、`getEnrichmentStats()`、search_after 查询
- Violation consequence: 跨天运行中阈值漂移导致新文档不断混入，任务无法终止；EMAIL- 文档每轮重复计失败制造噪音
- 来源: original

### Invariant I-6: enrichedAt 仅成功可写
- Rule: 只有 `EnrichmentOutcome.Success` 且 CANDIDATE 层更新成功才写 `enrichedAt`。NOT_FOUND / API_ERROR / ES_UPDATE_FAILED 不得写入任何 ES 字段，保持 pending 由未来运行重试。
- Applies to: `updateExpertAcademicFields()` 调用点（本计划不改该方法本身）
- Violation consequence: 前端「已补充」徽标失真；30 天重试语义被破坏
- 来源: K-enrichment-write-three-layers（引申）

### Invariant I-7: 异步启动走独立单线程执行器
- Rule: `/enrich` 改为异步后必须提交到**新建的** `enrichmentExecutor`（core=max=1, queue=0），禁止复用 `manualOutreachExecutor`——后者被批量发信（BatchSendControlService）与检查回复（MailAutomationController CHECK_REPLIES）共用，跨天任务会把它们饿死数天。
- Applies to: `ExpertDiscoveryController.enrichExperts()`、`DiscoveryExecutorConfig`
- Violation consequence: enrichment 运行期间批量外发/收信全部排队失效
- 来源: original（现状审计发现）

### Invariant I-8: 终态必须显式写入 progressStore
- Rule: 异步任务体的 try/catch/finally 必须保证：正常完成写 COMPLETED、暂停写 CANCELLED、异常写 FAILED；executor 提交失败（RejectedExecutionException）时 Controller 必须回滚 `tryStartWithToken` 占位（`progressStore.clear`），否则 status 遗留 RUNNING、前端永远"初始化中"。
- Applies to: `ExpertDiscoveryController.enrichExperts()` 异步化改造
- Violation consequence: K-clearExecutionContext-status-leak 描述的状态泄漏复现
- 来源: K-clearExecutionContext-status-leak

## 现状审计

### CANDIDATE/RAW/APPLICATION ES 索引（enrichment 相关字段）
- Mapping: 三索引均 `dynamic: false`（K-es-dynamic-false）；`enrichedAt` 已显式声明为 date（orcid_info_raw.json:37 / candidate.json:40 / application.json:49），`orcidId` keyword 可排序可 term/prefix 查询。**本计划零 mapping 改动**。
- Write paths（enrichment 字段）:
  1. `ExpertDiscoveryService.updateExpertAcademicFields()` — 唯一写 `enrichedAt`/`hIndex`/`citationCount`/`worksCount`/`researchFields`/`recentWorkTitles`/`patentTitles`/`enrichmentSource` 的路径；HEAD 探测后对存在的层 `_update`（K-enrichment-write-three-layers, 来源: K-enrichment-write-three-layers）
- Read paths（enrichment 字段）:
  1. `ExpertDiscoveryService.buildEnrichmentFilters()` — `enrichedAt` not-exists OR <30d，驱动 pending 判定
  2. `ExpertDiscoveryService.getEnrichmentStats()` — 同一 filter 计数，前端启动确认弹窗展示
  3. 前端 app.js:3096/4796 — `enrichedAt` 驱动「已补充」徽标与详情页展示
- Interaction points: 改 `buildEnrichmentFilters()`（加 EMAIL- 排除 + 冻结时间戳）同时影响 `getEnrichmentStats()` 的 pending 数（会略降，属预期修正）与主循环终止条件 → I-5 覆盖

### EXPERT_ENRICHMENT 任务链路
- Write paths:
  1. `ExpertDiscoveryController.enrichExperts()` — `tryStartWithToken` 占位 → **同步** `runAndRecordWithResult` 包裹 `enrichExistingExperts()` → finally `clearExecutionContext` + RUNNING 兜底 clear（controller L211-253）
  2. `ExpertDiscoveryService.enrichExistingExperts()` — `progressStore.update` 每批写进度（内存 + task_progress_log 表）；终态 COMPLETED/CANCELLED/FAILED 均由此写
  3. `TaskExecutionService.runAndRecordWithResult()` — task_execution 表 RUNNING→终态
- Read paths:
  1. 前端 task-modal 轮询 `GET /api/task-progress/EXPERT_ENRICHMENT`（app.js:738 `updateTaskModalFromProgress` 渲染 message/percentage/details.failureReasons 表格）
  2. `TaskProgressController` 执行历史（EXPERT_ENRICHMENT 已在 `allowedTaskTypes` 白名单 L33-35，`parseResultSummary` L161/L211 已有分支，K-allowedTaskTypes-whitelist 已满足，无需改动）
  3. 暂停: 前端 app.js:690 POST `/api/task-progress/{taskType}/cancel` → `requestCancel` 置 CANCELLING + cancellationFlags；app.js:588/778 已把按钮文案改为「暂停」
- Interaction points:
  - 同步 HTTP → 异步化后，前端 `executeEnrichExperts()`（app.js:3822-3860）依赖响应体里的 `result.enriched/failed` 弹完成通知——异步响应不再携带最终结果，需改为依赖既有 watcher 轮询终态（app.js 已有 `markTaskWatcherLaunchSucceeded`/`notifyTaskCompletionOnce` 机制，CHECK_REPLIES 同款模式可参照 MailAutomationController.kt:126-190）
  - `manualOutreachExecutor` 被 3 处共用 → I-7

### OpenAlex 调用层（OpenAlexDataSource）
- `batchEnrichByOrcids()`：50 ORCID 合并 1 次 `/authors?filter=orcid:a|b|...`；整批 429/503 → 全员 RateLimited(retryAfterMs)；works/patents 关闭时每批仅 1 次 API 调用。9 万人 ≈ 1800 次请求，远低于 OpenAlex 10 万/天额度（限流根因待诊断日志确认，K-enrichment-no-ratelimit 注: polite pool 另有 1000 req/5min 窗口限制）。
- 现有缺陷（本计划修复处）: `enrichExistingExperts()` L861-879 整批限流 → `failed += 50` 且 `continue` 跳批不重试；连续 5 次 → CIRCUIT_BREAKER 退出；退避封顶 60s。
- 429 时未记录 Retry-After 头与响应体，无法区分秒级窗口限流与日配额封锁。

## 实现方案

### 阶段 A：迭代与限流核心改造（后端）

**A-1 `ExpertSearchService` 新增 `searchAfterExpertsFiltered`** [I-4]
- 文件: `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt`
- 签名 `fun searchAfterExpertsFiltered(level, filters, batchSize=500, handler: (List<ExpertProfile>) -> Boolean)`：`sort=[{"orcidId":"asc"}]` + `search_after=[lastOrcidId]` 循环，页空即止，handler 返回 false 提前终止。无 scroll 上下文，页间任意长等待安全。复用现有 `sourceFields()`/`toExpertProfile`/`headers()`。
- 注意: 处理成功的文档写入 `enrichedAt` 后自然退出 filter 命中集，但 search_after 按 orcidId 单调前进，不受影响；终态失败文档留在命中集也只会被本轮经过一次。

**A-2 重写 `enrichExistingExperts()` 主循环** [I-1, I-2, I-3, I-5, I-6]
- 文件: `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
- 启动时冻结 `cutoff = now-30d`，`buildEnrichmentFilters(cutoff)` 改为参数化；filter 增加 `must_not: {prefix: {orcidId: "EMAIL-"}}`（`getEnrichmentStats()` 同步使用，pending 口径一致）
- 遍历改用 A-1 的 search_after；批内按 `enrichmentBatchSize` chunk 调 `batchEnrichByOrcids`
- chunk 重试循环：`var retryOrcids = chunk` → 调用后把 outcome 为 RateLimited 的 orcid 收集为下一轮 `retryOrcids`，非空则退避后重试**仅这些**；直到清空或被暂停。RateLimited 不计 failed、不进 failureReasons，`rateLimitWaits++`
- 退避: `retryAfterMs ?: 2000L * 2^(min(n,?)-1)`，封顶 `properties.enrichmentMaxBackoffMs`；WAIT 模式无次数上限，ABORT 模式保留连续 5 次退出（写 FAILED + CIRCUIT_BREAKER 原因，行为兼容旧版）
- 所有睡眠（退避 + `enrichmentDelayMs`）统一走私有 `sleepInterruptible(ms): Boolean`——1s 切片检查 `isCancelled`，返回是否被取消
- 退避进入前 `progressStore.update`：message 形如「限流退避中 300s（第 3 次），已处理 12500/91234，成功 11800，失败 700」；details 增加 `rateLimitWaits`、`currentBackoffMs`、`mode`
- 终态路径保持现状三分支（COMPLETED/CANCELLED/FAILED），统计口径中 RATE_LIMITED 从 failureReasons 移除

**A-3 `OpenAlexProperties` + `application.yml` 新配置** [I-2]
- 文件: `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexProperties.kt`、`src/main/resources/application.yml`
- 新增: `enrichmentRateLimitMode: String = "WAIT"`（env `OPENALEX_ENRICHMENT_RATE_LIMIT_MODE`）、`enrichmentMaxBackoffMs: Long = 1_800_000`（env `OPENALEX_ENRICHMENT_MAX_BACKOFF_MS`）

**A-4 429 诊断日志** 
- 文件: `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OpenAlexDataSource.kt`
- `batchEnrichByOrcids` 捕获 429/503 时 `log.warn` 输出 status、Retry-After 头、响应体前 500 字符。仅加日志，不改控制流。

### 阶段 B：异步启动（后端 + 前端）

**B-1 新增 `enrichmentExecutor`** [I-7]
- 文件: `src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt`
- 追加 `@Bean("enrichmentExecutor")`：core=max=1，queue=0，前缀 `enrichment-`

**B-2 `/enrich` 异步化** [I-7, I-8]
- 文件: `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt`
- 参照 `MailAutomationController.checkReplies` 模式：`tryStartWithToken` 占位 → `enrichmentExecutor.execute { runAndRecordWithResult(...) { enrichExistingExperts() } + finally clearExecutionContext/RUNNING 兜底 }` → HTTP 立即返回 202 `{executionId 未知时返回 pendingToken 占位信息 + message "任务已启动"}`；`RejectedExecutionException` 时 `progressStore.clear` 回滚并返回 409
- executionId 由 `onStarted` 回调经 `bindExecutionId` 绑定（与 CHECK_REPLIES 相同时序）

**B-3 前端适配异步响应**
- 文件: `src/main/resources/static/app.js`
- `executeEnrichExperts()`：POST 返回后不再从响应体取 `enriched/failed` 弹完成通知，改为仅 `markTaskWatcherLaunchSucceeded`，终态通知交由既有 watcher 轮询（进度 modal 已能渲染 message + details.failureReasons 表格，无需新组件）；响应含 executionId 时仍走 `bindTaskModalExecution`

### 阶段 C：测试

**C-1 服务层测试**
- 文件: `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt`（扩展现有类）
- 覆盖: 整批限流后同批重试且不计 failed（I-1）、WAIT 模式退避封顶且不熔断（I-2）、退避期间取消立即生效（I-3）、EMAIL- 排除与冻结 cutoff（I-5）、ABORT 模式保留熔断（兼容）

**C-2 控制器测试**
- 文件: `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt`（扩展现有类）
- 覆盖: 异步 202 立即返回、重复触发 409、RejectedExecution 回滚占位（I-8）

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `expert/service/ExpertSearchService.kt` | 新增 `searchAfterExpertsFiltered` |
| 2 | `discovery/service/ExpertDiscoveryService.kt` | 重写 enrichment 主循环、参数化 filters、sleepInterruptible |
| 3 | `config/OpenAlexProperties.kt` | +2 配置字段 |
| 4 | `resources/application.yml` | +2 配置键 |
| 5 | `discovery/service/OpenAlexDataSource.kt` | 429 诊断日志 |
| 6 | `config/DiscoveryExecutorConfig.kt` | +enrichmentExecutor bean |
| 7 | `discovery/controller/ExpertDiscoveryController.kt` | `/enrich` 异步化 |
| 8 | `resources/static/app.js` | executeEnrichExperts 异步响应适配 |
| 9 | `test/.../ExpertDiscoveryServiceTest.kt` | 新用例 |
| 10 | `test/.../ExpertDiscoveryControllerTest.kt` | 新用例 |

共 10 文件，2 子系统（后端 discovery/task 链路 + 前端 app.js 单函数）。无 DB 迁移，无 ES mapping 变更。

## 验收标准

- I-1: 单测——mock `batchEnrichByOrcids` 首次全 RateLimited、二次成功，断言 `failed == 0`、该 chunk 全部 enriched、`failureReasons` 无 RATE_LIMITED、details.rateLimitWaits == 1
- I-2: 单测——WAIT 模式连续 10 次 RateLimited 不退出且退避不超 maxBackoffMs；ABORT 模式第 5 次退出且 failureReasons 含 CIRCUIT_BREAKER
- I-3: 单测——退避睡眠中置 cancel 标志，任务在 ≤2s 内进入 CANCELLED 终态
- I-4: 代码审查——`enrichExistingExperts` 无 `scrollExpertsFiltered` 调用；`searchAfterExpertsFiltered` 单测验证分页推进与提前终止
- I-5: 单测——filters 含 EMAIL- must_not 且两次分页查询使用同一 cutoff 值；`getEnrichmentStats` 与主循环使用同一 filter 构造器
- I-6: 单测——NotFound/ApiError outcome 不触发 `updateExpertAcademicFields`
- I-7: 代码审查——`/enrich` 提交目标为 `enrichmentExecutor`；grep 确认 `manualOutreachExecutor` 引用数不变
- I-8: 控制器测试——executor 拒绝时 progressStore 状态被清理、响应 409；正常路径 finally 后 store 无 RUNNING 遗留
- 集成场景: 手动触发 → 立即 202 → 进度轮询显示 RUNNING 与批次推进 → 触发暂停 → CANCELLED；再次触发 → 从剩余 pending 续跑（enrichedAt 断点续传）
- 构建: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 全绿

## 运行时预期（供运营参考）

- 9 万专家 ≈ 1800 次 OpenAlex 批量请求；不限流时约 2~3 小时（瓶颈为三层 ES 逐条更新）
- 若持续限流（polite pool 1000 req/5min 或日配额）：WAIT 模式自动等待，最坏跨 1~2 天挂机完成；期间进度弹窗持续显示「限流退避中」与已处理计数
- 应用重启后任务中断（progress 显示 INTERRUPTED），再次手动触发即从剩余待补充继续

## 修正记录

（预留，供 fix-v 回写）
