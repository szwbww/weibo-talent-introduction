# Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/02-discovery-checkpoint.md
Plan SHA-256: c6b0048406d0e51df9203458684ce05a464dcc899a570865768c531ce68e7e3e
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/02-discovery-checkpoint.md@c6b0048406d0e51df9203458684ce05a464dcc899a570865768c531ce68e7e3e
Execution epoch: NEW
Approval basis: child brief `docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c2/brief.md`（child c2，plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010`，`child_base_sha=147dc953a194a27ea73b7934a8e2bc334beca655`）+ 控制方「实现 c2」的调用
Executor: C2Implementer
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
Target branch: fast/2026-09-21-discovery-enrichment-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master@fast/2026-09-21-discovery-enrichment-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
Pre-execution code SHA: a8a7be6f795e1ad2464984675360ffec3f3290c2（执行开始时的分支 HEAD；`child_base_sha` = 147dc953a194a27ea73b7934a8e2bc334beca655 是 c1 代码头）
Post-execution code SHA: 468df56bf69b4b2f9afc7b4f38ad4791d8331240
Evidence HEAD: N/A（单次 product/test 提交，计划未要求独立证据提交）
Implementation boundary: 147dc953a194a27ea73b7934a8e2bc334beca655..468df56bf69b4b2f9afc7b4f38ad4791d8331240

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Task 1 补回归再修边界（I-1、I-4）：首请求失败不得清空游标；拆分 `SourceRunOutcome(resumeCursor, exhausted, stopReason)`；完整消费页立即持久化；异常/额度延期绝不 `exhausted=true` | IMPLEMENTED | `ExpertDiscoveryService.kt`, `SourceStats.kt`, `ExpertDiscoveryServiceTest.kt` | 基线复现（RED，见 Commands 第 4 行）：147dc95 上首请求 TLS 失败后落盘 `cursor=null`（断言期望 `C7`）；修复后 `first page TLS failure keeps the entering cursor` 断言落盘 envelope 解出 `C7`+ACTIVE、`papersProcessedTotal=0`；`second page failure resumes at the second page entry…` 断言第一页消费完立刻落盘（该 key 落盘次数 ≥2）且第二页失败后停在 `C2`、累计处理恰为 2；`page with failed RAW persistence keeps the entering cursor for replay` 断言页内 RAW 写入失败时检查点不推进；`budget deferred stop keeps the entering cursor and is not a search failure` 断言 `OpenAlexBudgetDeferredException` 保留 `C7`、`exhausted=false`、不计入 `failure_count` |
| Task 2 隔离与结果（I-2、I-3）：`source_name=SOURCE:v2:<24>` + 版本化 envelope（ACTIVE/EXHAUSTED）；旧行不静默挪用；结果与进度共享同一终态决策函数 | IMPLEMENTED | `DiscoveryCheckpointCodec.kt`(新增), `DiscoveryResult.kt`, `DiscoveryStats.kt`, `SourceStats.kt`, `ExpertDiscoveryService.kt`, `DiscoveryCheckpointCodecTest.kt`(新增), `DiscoveryResultTest.kt`(新增), `ExpertDiscoveryServiceTest.kt` | `DiscoveryCheckpointCodecTest` 10/10：key 前缀/24 位十六进制/≤50 字符（七源逐个断言）、同一查询不同页同一 key（cursor 不参与）、关键词/年份/scope/页大小/sources/来源变化 → key 变化、顺序与空白归一、ACTIVE/EXHAUSTED 往返、含 `|` 的游标往返、裸游标/未知版本/未知状态/截断 envelope → null；`different keywords persist to different checkpoint keys`（A/B 各写各的 key 且互不改写）、`legacy plain source name rows stay untouched and are never adopted`（旧行值原样保留且请求不带旧游标）、`v2 key holding a legacy raw cursor value is not adopted`；`DiscoveryResultTest` 7/7 + `V-3 …` 断言全源失败 FAILED、一源失败一源健康 PARTIAL_SUCCESS、空结果 SUCCESS、取消 CANCELLED、预算 pending PARTIAL_SUCCESS、`failure_count` 只按源计一次 |
| I-3：搜索层失败计入任务失败；`DiscoveryResult.taskFinalStatus` 与进度终态同源 | IMPLEMENTED | `DiscoveryResult.kt`（`DiscoveryTerminalStatus.decide/toProgressStatus`）, `ExpertDiscoveryService.kt`（`discover` 进度写入复用同一函数） | 基线复现（RED）：147dc95 上全源失败时 `taskFailureCount=0`；修复后 `V-3 all attempted sources failing yields FAILED…` 断言 `attemptedSources=2`/`failedSources=2`/`sourceFailures=2`/`taskFailureCount=2`/`taskFinalStatus=FAILED`；进度写入用 `toProgressStatus(SUCCESS)=COMPLETED`、其余原样（前端已支持 PARTIAL_SUCCESS，`app.js:1238`、`task-modal-runtime.js:41`，无前端改动） |
| 下游接口保持（brief）：`SourceRunOutcome` 逐字保留；`DiscoveryCheckpointCodec` 为 key/envelope 唯一编码点；`nonPersistableCursorSources` 不再排除 CORE 且保留显式接缝；来源名 ≤50；公开入口与字段名不变（additive-only） | IMPLEMENTED | `ExpertDiscoveryService.kt`, `DiscoveryCheckpointCodec.kt`, `DiscoveryStats.kt`, `SourceStats.kt`, `DiscoveryResult.kt` | `data class SourceRunOutcome(val resumeCursor: String?, val exhausted: Boolean, val stopReason: String)` 顶层公开，两个来源运行器都返回它，所有 catch 分支 `exhausted=false`；`grep -rn "\:v2:"` 仅命中 `DiscoveryCheckpointCodec.kt`（编码唯一）；`nonPersistableCursorSources` 保留为 `Set<String> = emptySet()` 显式接缝且注释标注不得重新加入 CORE；`discover(...)`/keyword/定时入口签名不变；`DiscoveryResult`/`SourceStats` 仅新增字段（`attemptedSources`/`failedSources`/`sourceFailures`/`pendingSources`、`pendingWork`/`sourceFailureCount`/`stopReason`），既有字段名与 `TaskProgress` 读取路径不变 |

## Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q compile` | PASS | exit 0（最终实现状态前的主源码编译） |
| `JAVA_HOME=… mvn -q test-compile` | PASS | exit 0（首次报 2 处 Kotlin 表达式空检查问题 `Unresolved reference: firstValue`，修正测试写法后再编译通过） |
| `JAVA_HOME=… mvn test -Dtest=ExpertDiscoveryServiceTest,DiscoveryCheckpointCodecTest,DiscoveryResultTest`（计划要求的定向命令，最终实现状态后新鲜运行） | PASS | exit 0，BUILD SUCCESS；`Tests run: 96, Failures: 0, Errors: 0, Skipped: 0`（ExpertDiscoveryServiceTest 79 / DiscoveryCheckpointCodecTest 10 / DiscoveryResultTest 7）；同命令 test 阶段由 `exec-maven-plugin` 触发的 JS 回归 `fail 0` |
| 基线复现（RED，临时目录 `/private/tmp/c2-red` = `git archive 147dc95 \| tar -x`，仓库与工作区未被改动）：`mvn test -Dtest=C2RedStateReproTest -DskipNodeTests=true` | FAIL（预期） | `Tests run: 2, Failures: 2`：①`首请求失败后仍必须保留 C7（当前缺陷：被覆盖为 null） ==> expected: <C7> but was: <null>`；②`源终止失败必须进入 failure_count（当前缺陷：为 0） ==> expected: <true> but was: <false>`（该 run 在第二条断言处即失败，未执行到 `taskFinalStatus` 断言）。复现脚本位于临时目录、已删除，未进入仓库 |
| `python3 .agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-09-21/02-discovery-checkpoint.md` | PASS | `sha256=c6b0048…e7e3e`（执行开始与交付前一致） |
| `python3 .agents/skills/execute-p/scripts/worktree_identity.py … --worktree <worktree>` | PASS | root/branch(`fast/2026-09-21-discovery-enrichment-master`)/git-dir 与记录一致；提交后 HEAD 由 a8a7be6 推进到本次提交 |
| `git status --short src/` + `git diff --stat src/` | PASS | 变更恰为 8 个授权文件（5 改 3 新增），无清单外文件；`docs/plans/fast/**` 未纳入提交 |

## Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` — 检查点读写改为 `loadSourceCheckpoint`/`persistSourceCheckpoint`（v2 key + envelope，页面边界立即落盘，delta 累计）；`discoverFromSource`/`discoverFromOrcid` 返回 `SourceRunOutcome` 并在结束/取消/失败/额度延期/限额停止时保留进入该页的游标；新增 `OpenAlexBudgetDeferredException` 与 `RAW_WRITE_INCOMPLETE` 停止分支；`discover` 用共享终态函数写进度并返回结果；`nonPersistableCursorSources` 不再排除 CORE；新增顶层 `SourceRunOutcome`/`DiscoveryStopReason`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodec.kt`（新增）— `CheckpointState`/`SourceCheckpoint`/`DiscoveryCheckpointCodec`：`SOURCE:v2:<24 位 SHA256>` key、条件规范化、envelope 编解码（唯一编码点，供 c4 复用）
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryResult.kt` — 新增 `DiscoveryTerminalStatus.decide/toProgressStatus`（结果与进度共用的终态决策）；`taskFinalStatus` 变为始终非空；`taskFailureCount` 加入 `sourceFailures`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryStats.kt` — 新增派生聚合 `attemptedSources`/`failedSources`/`sourceFailures`/`pendingSources` 及 `refreshGlobalCounts()` 计算
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SourceStats.kt` — 新增 `pendingWork`/`sourceFailureCount`/`stopReason`（单源运行结局）
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` — 两个既有游标用例改为按 envelope 断言；新增 10 例（首请求失败保游标、第二页失败回到第二页入口+页边界落盘、空页带 cursor 继续翻页、空结果即穷尽、不同关键词不同 key、旧行/裸值不采纳、全源失败 FAILED、一源失败 PARTIAL_SUCCESS、额度延期保留游标、RAW 写入失败不推进）
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryCheckpointCodecTest.kt`（新增）— 10 例 key/envelope 契约
- `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryResultTest.kt`（新增）— 7 例终态与 failure_count 契约

## Deviations

计划未唯一确定的实现细节，全部落在授权文件内、未新增文件、未改既有公开签名、未改任何已应用迁移：

- 停止原因取值集合：计划只规定 `stopReason: String` 字段，未给取值。实现为 `DiscoveryStopReason` 常量（`EXHAUSTED`/`SEARCH_FAILED`/`BUDGET_DEFERRED`/`CIRCUIT_BREAKER`/`CANCELLED`/`GLOBAL_PAPER_LIMIT`/`GLOBAL_AUTHOR_LIMIT`/`SOURCE_LIMIT`/`PAGE_PARTIAL`/`PAGE_CONSUMED`/`EMPTY_PAGE`/`RAW_WRITE_INCOMPLETE`），与主方案「每种约束各自给原因」对齐。
- envelope 字面量：`v2|ACTIVE|<cursor>` / `v2|EXHAUSTED|`；`decode` 对任何非本版本形态（裸游标、`v1|…`、未知状态、截断）返回 null，调用方按「无检查点」处理并原样保留旧值（旧行不删不改）。
- 「未完成 RAW 持久化的页不得推进」（I-1）：实现为页内 `rawWriteFailed` 增量 > 0 时不落盘 next cursor 并以 `RAW_WRITE_INCOMPLETE` 结束该来源运行（与既有「部分页即停止并保留入口游标」一致）。既有测试无任何 `rawWriteFailed` 覆盖，故该分支由新增用例锁定。
- 调用方显式给出的 `criteria.cursor`：仅在本次 key 无已存检查点时作为本次运行起点（保持既有入口行为），已存 ACTIVE 检查点优先，EXHAUSTED 视为本次周期重开。
- 来源循环由 `do { … } while (cursor != null)` 改为 `while (true)` + 显式 break：旧结构下「进入游标为 null 时的首请求 429/503 重试」会因 `continue` 触发 `while (cursor != null)` 直接退出（既有用例 `circuit breaker trips after 5 consecutive 429s` 用 `PaperSearchCriteria(cursor="0")` 绕过）。改用显式 break 后重试语义按原意生效，该既有用例仍 5/5 API 请求、`CIRCUIT_BREAKER` 计数为 1。
- ORCID 分页：只在「整页消费完且页内 RAW 写入全部成功」时推进 offset；空页即 EXHAUSTED（下一周期从 offset 0 重开），不再永久停在空 offset；ORCID 未启用时不再写 0 计数游标行。分片 offset 与 CORE offset envelope 仍归 c4。
- CORE 按 brief DP-4 从 `nonPersistableCursorSources` 移除（该集合保留为空集显式接缝）。过渡期影响：c4 落地 offset envelope 之前，CORE 的 `scrollId` 也会被持久化，若该 scrollId 过期则本次运行按 `SEARCH_FAILED` 记录且检查点保持不动（旧行为是永不持久化、每次全新搜索）。c4 的授权文件同样包含 `ExpertDiscoveryService.kt`，由 c4 完成 CORE 的稳定 offset 分页。
- `DiscoveryResult.taskFinalStatus` 由「仅取消时非空」变为始终非空：`TaskExecutionService` 直接采用该终态（旧路径是按 `failure_count>0` 推导），`DiscoveryTerminalStatus.toProgressStatus(SUCCESS)="COMPLETED"` 保证进度侧 `COMPLETED ↔ 记录 SUCCESS`，其余三种终态进度/记录同字面量，前端无需改动。
- 进度/结果 JSON 明细新增 `terminalStatus`、每源 `pendingWork`/`sourceFailureCount`/`stopReason`：additive JSON，不新增列、不改 `task_execution`/`task_progress_log` schema。
- 熔断（连续 5 次 429/503）现在同时记一次终止性源失败（`CIRCUIT_BREAKER` 计数保持 1，未被重复累加，由既有用例 `circuit breaker trips after 5 consecutive 429s/503s` 锁定）。

## Freshness

- Plan identity rechecked: YES（`c6b0048…e7e3e` 未变）
- Worktree identity rechecked: YES（root/branch/git-dir 与记录一致，HEAD 由 a8a7be6 推进到本次提交）
- Reported commits reachable from target branch: YES（本次提交为 `fast/2026-09-21-discovery-enrichment-master` HEAD，仅含 8 个授权文件，未包含 `docs/plans/fast/**`）
- Required commands run this invocation: YES（最终实现状态后新鲜运行，exit 0，96/96 + JS fail 0）
- Historical evidence used only as baseline: YES（基线 RED 复现为独立临时目录运行，未作为通过的证据）

## Remaining Blocker

- None

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
