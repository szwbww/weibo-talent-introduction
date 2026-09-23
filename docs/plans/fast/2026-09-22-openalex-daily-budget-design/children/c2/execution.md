# Child c2 执行报告：持久化采集队列与全文处理

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design/docs/plans/2026-09-22/02-discovery-paper-queue.md`
- Plan SHA-256: `e2bdbf83bad70799f0a7a94d252a06a35cc1bb5ad3c89729c02648235f6694b8`（执行前后一致，未变更）
- Execution ID: `docs/plans/2026-09-22/02-discovery-paper-queue.md@e2bdbf83…`（epoch NEW；child_base_sha `b96470e4be86408b185c2fbdd2fb0037e8f4fc2c`）
- Executor: C2Implementer
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design`
- Target branch: `fast/2026-09-22-openalex-daily-budget-design`
- Worktree ID: `<root>@fast/2026-09-22-openalex-daily-budget-design@<common>/.git/worktrees/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design`
- Pre-execution code SHA: `6aa09e3de91b705dfb3e423bff71d24d37e4b080`（c1 证据提交，docs-only）
- Post-execution code SHA / Evidence HEAD: `58b96c7b2f66aee703eede6c283ce807ea9d16fe`
- Implementation boundary: `6aa09e3..58b96c7`（单提交，10 个授权文件）

## Commit

```
58b96c7b2f66aee703eede6c283ce807ea9d16fe  feat(fast-p): implement c2
```

`git diff --cached --name-only` 恰好为 10 个授权路径；`docs/plans/fast/**` 未被提交（`ledger.md` 的改动与 `children/c2/execution.md` 保持未暂存，由控制方单独提交证据）。

## Changed files（10/10，无越界）

| # | 文件 | 内容 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V133__create_discovery_paper_queue.sql` | 三张表：`discovery_pipeline`(:43)、`discovery_collection_stream`(:84)、`discovery_paper_job`(:118) |
| 2 | `src/main/kotlin/.../discovery/repository/DiscoveryPaperQueueRepository.kt` | 存储契约 `DiscoveryPaperQueueStore`(:217) + JDBC 实现 `@Repository`(:362) |
| 3 | `src/main/kotlin/.../discovery/service/DiscoveryPipelineService.kt` | `@Service`(:274) 协调者：launch/pause/resume/tick/status/cleanup + 窗口循环 |
| 4 | `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt` | 队列接缝（单页取数 / 抽取 / 消费复用同一门禁） |
| 5 | `src/main/kotlin/.../config/DiscoveryExecutorConfig.kt` | `pipelineCoordinatorExecutor`(:80)、`pipelineCollectionExecutor`(:96)、`pipelineFetchExecutor`(:114) |
| 6 | `src/main/kotlin/.../config/ExpertDiscoveryProperties.kt` | 队列配置 + 启动校验；`PIPELINE_PAGE_SIZE`(:105)、`PIPELINE_RESERVED_RESULT_BYTES`(:114) |
| 7 | `src/main/resources/application.yml`(:191-209) | `pipeline-enabled=false` 等 9 个键 |
| 8 | `src/test/kotlin/.../service/DiscoveryPipelineServiceTest.kt` | 42 个用例 |
| 9 | `src/test/kotlin/.../repository/DiscoveryPaperQueueRepositoryIT.kt` | 32 个用例（真实 MySQL） |
| 10 | `src/main/kotlin/.../config/RestTemplateConfig.kt` | `FulltextRequestGate`(:266) + 工厂逐跳接入 |

## Required commands（本次调用内全部新跑）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=DiscoveryPipelineServiceTest,DiscoveryPaperQueueRepositoryIT,ExpertDiscoveryServiceTest,RestTemplateConfigTest -DmysqlIt=true -Dapi.version=1.40 test` | **exit 0 / BUILD SUCCESS**；`Tests run: 212, Failures: 0, Errors: 0, Skipped: 0`（IT 31、Pipeline 42、ExpertDiscoveryServiceTest 121、RestTemplateConfigTest 18）。IT 真实执行：`Creating container for image: mysql:8.0.36` → `Successfully applied 132 migrations … now at version v133`（24.2 s，未跳过） |
| 2 | `JAVA_HOME=…/zulu-11 mvn -B -Dtest=ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,ExpertAcademicEnrichmentWorkerTest,ExpertAcademicEnrichmentJobRepositoryIT test` | **BUILD SUCCESS**；`Tests run: 39, Failures: 0, Errors: 0, Skipped: 1`（controller 17+3、scheduler 8、worker 10；跳过的 1 个是既有 `ExpertAcademicEnrichmentJobRepositoryIT` —— 它由 `@EnabledIfSystemProperty(named="migrationIt")` 门控，本命令未传该属性） |
| 3 | `node --test src/test/js/*.test.js` | **exit 0**；`tests 1037 / suites 207 / pass 1037 / fail 0 / skipped 0`（与 `baseline-java.txt` 逐字一致） |
| 4 | `JAVA_HOME=…/zulu-11 mvn -B -DskipTests clean package` | **BUILD SUCCESS**；`Building war: target/weibo-talent-introduction-1.0.0-SNAPSHOT.war` |

`RestTemplateConfigTest` 未修改且 18/18 通过 —— brief 中「必须保持原行为」的约束成立。

## Per-invariant evidence

| 不变量 | 实现位置 | 测试 |
|---|---|---|
| I-1 query 与处理位置 | V133:84（唯一 `(query_hash,source,epoch)`）+ `ExpertDiscoveryService.queueQueryHash`:1415 / `queueLegacySeedCursor`:1435 / `collectQueuePage`:1466；`Repository.enqueuePage`:775（整页 + cursor 同事务）、`seedStreamCursorIfPristine`:686 | `per-source query hash ignores other sources order and omission (I-1)`、`a whole page commits together with the cursor advance (I-1, I-5)`、`a capacity-blocked page keeps the cursor and reports QUEUE_FULL (I-1, I-5)`、`EXHAUSTED survives a new window and a day rollover (I-1, I-7)`、`conflicting legacy v2 cursors replay conservatively (I-1)`、`a matching legacy cursor seeds only a pristine stream (I-1)`、`a source that never advances its cursor becomes a source error instead of spinning (I-1, I-6)`；IT：`an explicitly rolled back page transaction leaves the cursor and the queue untouched (I-1)`、`a page whose lease was taken over is never written and never advances the cursor (I-1)`、`EXHAUSTED is a persisted cursor state that a later page cannot silently reopen (I-1)`、`a legacy cursor only seeds a pristine stream (I-1)`、`a source level error is observable and does not masquerade as exhaustion (I-1, I-6)` |
| I-2 工作唯一性与版本 | V133:118（唯一 `(stream_id,item_key)` + `payload_version` + `identity_quality` CHECK）+ `ExpertDiscoveryService.paperIdentity`:1719 / `envelopeForPaper`:1664 / `envelopeForOrcid`:1689；`Repository.insertFailedItem`:1252 | `normalized duplicate DOI yields one identity (I-2)`、`records without an identifier are hashed from canonical metadata, never title-merged (I-2)`、`ORCID records are records keyed by the full ORCID (I-2, I-6)`、`oversized metadata becomes an observable FAILED item (I-2, I-5)`、`an unknown payload version is never consumed (I-2)`；IT：`the unique key keeps one row per work identity and makes replay free of new capacity (I-2)`、`job identity is unique per stream and the same key in another stream is not merged (I-2)`、`an unknown payload version is stored observably and still repairable (I-2)`、`unidentifiable or oversized items become observable FAILED rows with intact identity (I-2, I-5)` |
| I-3 租约与状态机 | `Repository.claimJob`:965（含 `desired_state='RUNNING'` + generation 谓词）、`saveExtraction`:1009、`completeJob`:1052、`completeJobWithExperts`:1119、`scheduleRetry`:1183、`returnToPending`:1211；`Service.processJob`:951、`handleRetryableFailure`:1085 | `budget deferral and host busy never consume attempts (I-3, I-6)`、`retryable failures back off and the fifth failure terminates (I-3)`、`a non-retryable failure goes straight to FAILED (I-3)`、`a manual pause lets a claimed job save its extraction but never consume (I-3, I-7)`；IT：`two connections cannot claim the same job (I-3)`、`an expired lease is recoverable and an old token cannot touch the new attempt (I-3)`、`a stale generation can save an extraction but can never complete (I-3, I-7)`、`retry backoff and terminal failure are persisted with their reason (I-3)`、`a budget wait returns the job to PENDING without consuming attempts (I-3)` |
| I-4 先保存再消费 | `ExpertDiscoveryService.consumeOutcomeInternal`:1223（门禁唯一实现）+ `consumeQueuedItem`:1616 / `extractQueuedItem`:1547；`Service.processJob`:951（已存抽取直接消费、generation 门控消费） | `an already-saved extraction is consumed without downloading again (I-4)`、`a RAW write failure never completes the job while no-email may succeed (I-4)`、`replay re-creates the missing enrichment task without rewriting the expert (I-4)`、`a first-time consumer writes RAW then enqueues enrichment before succeeding (I-4)`、`a failed enrichment enqueue keeps the job out of SUCCEEDED (I-4)` |
| I-5 有界容量 | V133:43（`active_count`/`payload_bytes`/`reserved_result_bytes`/`capacity_paused`）+ `Repository.enqueuePage`:775（数量或字节任一满→整页拒绝）、`Service.capacityGateOpen`/低水位 + `guardAgainst…`；`Service.collectPage`:801 | `a capacity-blocked page keeps the cursor and reports QUEUE_FULL (I-1, I-5)`、`capacity only resumes below the low-water mark (I-5)`、`in-flight result space is reserved at enqueue and released at terminal (I-5)`、`terminal cleanup releases bytes without touching counters or cursors (I-8)`、`runtime validation rejects an inconsistent queue configuration (I-5)`；IT：`a page is refused as a whole when the count high-water would be crossed (I-5)`、`a page is refused as a whole when the byte budget would be crossed (I-5)`、`saving an extraction converts the reservation into actual bytes without deadlock (I-5)`、`concurrent enqueuers cannot exceed the high-water mark (I-5)`、`the 32 kilobyte result reservation… (I-5)`、`terminal cleanup releases bytes and never touches counters or cursors (I-8)` |
| I-6 公平与外部请求边界 | `Service.windowLoop`:635/`producePages`:765/`consumeJobs`:890/`claimNextJob`:922（来源轮转 + 每 10 个高优先取 1 个最老普通）、`guardAgainstNonAdvancingSource`:747；`RestTemplateConfig.FulltextRequestGate`:266（每域 2 个在飞、逐跳检查、HOST_BUSY）+ `DeadlineBoundedRequestFactory.execute`（逐跳） | `a suspended source does not block the others (I-6)`、`real requests hold per-host permits and a third same-host hop is refused (I-6)`、`the queue scope refuses metered destinations and strips cross-origin credentials (I-6)`、`the oldest ordinary task is claimed within ten priority claims (I-6)`、`budget deferral of one source does not stop the other sources (I-6)`；IT：`a suspended collection lease lets the other source proceed (I-6)`、`the due queries respect status priority and stream scope (I-3, I-6)` |
| I-7 持久化控制与窗口交接 | V133:43 + `Repository.launch`:388/`pause`:437/`markDrained`:475/`claimOwner`:493/`releaseStaleOwner`:553；`Service.launch`:301/`pause`:354/`resume`:377/`tick`:414/`runWindowSafely`:604/`publishProgress`:1199 | `pause is persisted and survives restart and a day rollover (I-7)`、`resume without a configured query is rejected (I-7)`、`the same query is idempotent while a different query with backlog is rejected (I-7)`、`a rejected dispatch keeps QUEUED and creates no duplicate window (I-7)`、`a live owner suppresses a second window and a stale owner is recovered once (I-7)`、`a one-minute window continues the same queue afterwards (I-7)`、`drained pipeline is SUCCESS and all sources exhausted is DRAINED (I-7, I-8)`、`the pipeline never creates empty task_execution records (I-7)`；IT：`the pipeline starts PAUSED and launch pause resume keep their persisted meaning (I-7)`、`resume without a configured query is refused (I-7)`、`the window owner lease is exclusive and a stale owner only clears itself (I-7)`、`a new window refreshes an expired window deadline instead of reusing it (I-7)`、`task history binding and raw scan marking require the current owner (I-7)`、`draining requires exhausted sources and an empty active queue (I-7)` |
| I-8 计数与清理 | `Repository.completeJobWithExperts`:1119/`clearTerminalPayloads`:1351/`deleteTerminalJobs`:1391；`Service.status`:517/`cleanup`:586/`PipelineWindowResult.toDetails` | `repeated completion does not double count (I-8)`、`paper ORCID and expert counters stay separate (I-8)`、`window result progress and status agree on the termination reason (I-8)`、`status derives PAUSED over phase and reports per-source detail (I-8)`、`terminal cleanup releases bytes without touching counters or cursors (I-8)`；IT：`each counter is incremented at most once per CAS and units stay separate (I-8)`、`terminal cleanup releases bytes and never touches counters or cursors (I-8)` |

下游接口（c3 消费，形状与计划一致）：`launch(criteria,triggeredBy,includeRawScan)`:301、`pause()`:354、`resume()`:377、`tick()`:414、`status()`:517；等待原因常量 `PipelineWaitReason`（DAILY_BUDGET/QUEUE_FULL/RATE_LIMIT/ENRICHMENT_RESERVE/BUDGET_SYNC/OWNER_RECOVERY/SOURCE_ERROR + MANUAL_PAUSE/WINDOW_END/SOURCE_EXHAUSTED 作为终止原因）；窗口任务记录经 `TaskExecutionService.runAndRecordWithResult` + `onStarted` 绑定 executionId，结果实现 `TaskExecutionSummaryProvider`（`taskSuccessCount` = 本窗口新增专家）。生产入口未切换：`pipeline-enabled` 默认 `false`。

## Deviations（全部为本文件内、已论证的设计决定）

1. **`DiscoveryPaperQueueStore` 接口**（与 c1 的 `OpenAlexBudgetStore` 同构）：让窗口/公平/暂停语义可在不依赖真实 MySQL 的情况下确定性验证；唯一生产实现仍是 `DiscoveryPaperQueueRepository`（`DiscoveryPaperQueueStore` 由它实现，JDBC 细节不外泄）。它落在清单文件 2 内，未新增生产类。
2. **`discovery_paper_job.priority` 列**（计划列举列之外）：I-6「来源内优先公开可下载」必须在 SQL 层选行，无法从 `metadata_json` 便宜推导。
3. **`NO_PROGRESS` 来源错误**：来源反复返回同一个 `nextCursor`（既不前进也不穷尽）时按 `SOURCE_ERROR` + 退避处理，而不是让窗口在同一页上空转（会持续消耗来源限速额度）。这是我在测试中发现并修掉的空转风险。
4. **修掉 MySQL `SET` 求值顺序缺陷**：`pause()` 原先在同一条 UPDATE 里先写 `desired_state` 再算 `generation`，而 MySQL 的 SET 从左到右求值，`CASE WHEN desired_state='RUNNING'` 读到的是新值 → generation 永不递增。已改为先算 generation（`Repository.pause`:437），并由 IT 断言。
5. **新增条数不再依赖 `ON DUPLICATE KEY UPDATE` 的 affected-rows**：MySQL 对「同值更新」的计数不可依赖，`enqueuePage` 用事务内预查结果、`insertFailedItem` 增加同键预查（`Repository`:775/1252）。
6. **`ExpertDiscoveryService.runRawScan()`**:1129：把 `discover()` 内联的 RAW 扫描两段 try/catch 收成一个公共接缝，旧流程与窗口共用同一门禁（行为逐字不变，`ExpertDiscoveryServiceTest` 121/121 仍绿）。
7. **队列查询固定 `scope=RND_TARGET`**（T-2 要求）：`ExpertDiscoveryService.queueCriteria`:1407；因此 EUROPE_PMC/PMC_OA 不再是队列来源，而 ORCID（不在 `resolveEnabledSources` 注册表内、走独立分页协议）由 `orcidQueueSourceNames`:1392 按同一套规则显式纳入。
8. **`PipelineTimeSource`**（与 c1 `PolicyTimeSource` 同风格）与 `PipelineWindowResult.capacityBlocked` 等 DTO 形状：授权文件内的可测性设计。
9. **`launch` 的 RAW 扫描语义**：`rawScanDone = !includeRawScan`，即只有显式要求扫描的启动才会重开扫描，之后窗口不再重扫（I-7）。
10. **抽取结果的「可靠」定义（解释）**：适配器**正常返回**的抽取（含下载/解析失败原因）视为可靠结果并持久化，job 可 `SUCCEEDED` 但新增 0 —— 与旧流程「下载失败不停留」一致，并满足 I-4「不再下载」；只有适配器**抛异常**才是可重试失败。
11. **409 映射留在控制层**：服务返回 `PipelineRejection(reason=QUERY_CONFLICT|NOT_CONFIGURED)`，`GlobalExceptionHandler` 不在本 child 授权文件内，需 c3 的控制层映射为 409（计划明确把 409 记在下游接口表里）。
12. **`ExpertDiscoveryProperties` 新增 `init {}` 启动校验**（计划要求 `0<low<high`、容量可容一页、并发为正），由 `runtime validation rejects an inconsistent queue configuration (I-5)` 覆盖。

## Blockers

无。备注（非本 child 引入）：既有 `ExpertAcademicEnrichmentJobRepositoryIT` 由 `migrationIt` 门控，brief 的命令 2 未传该属性，故按预期 skipped；命令 1 已真实覆盖 MySQL 集成路径（Flyway → v133）。

## Freshness

- Plan identity rechecked: YES（`e2bdbf83…`，执行前后一致）
- Worktree identity rechecked: YES（提交前用 `--expect-root/--expect-branch/--expect-git-dir` 校验；提交后 worktree 仅剩未暂存的 fast-p 证据文件）
- Reported commits reachable from target branch: YES（`58b96c7` 是目标分支 HEAD，父提交 `6aa09e3`）
- Required commands run this invocation: YES（4/4，均为最终实现状态之后新跑）
- Historical evidence used only as baseline: YES（`baseline-java.txt`、`children/c1/verify-log.md` 仅作对比）
- `RestTemplateConfigTest` / `ExpertDiscoveryServiceTest` 未被修改：YES（18/18、121/121 通过）
