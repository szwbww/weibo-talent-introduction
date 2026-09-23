# Child c3 执行报告：统一入口、持续运行与可见状态

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design/docs/plans/2026-09-22/03-discovery-continuous-run.md`
- Plan SHA-256: `a87bf07245ab2ae0688dbee69160d6f5bf73d638836f43791664da93979c3623`（执行前后一致，未变更；计划身份 = brief 的 `commit:ee1dfcd5439de54475c12ff51c9c713e984a82ec`，`git diff ee1dfcd -- <plan>` 为空）
- Execution ID: `docs/plans/2026-09-22/03-discovery-continuous-run.md@a87bf072…`（epoch NEW；`child_base_sha` `ef1eb2b8c1e17aabaf04178f5f7931852872a42b`）
- Executor: C3Implementer
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design`
- Target branch: `fast/2026-09-22-openalex-daily-budget-design`
- Worktree ID: `<root>@fast/2026-09-22-openalex-daily-budget-design@<common>/.git/worktrees/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design`
- Pre-execution code SHA: `ee7a45fa23c166c4e235cb3121c4fc10b402f265`（c2 证据提交，docs-only；HEAD 起点）
- Post-execution code SHA / Evidence HEAD: `13b82fde5d74836977d12e97b7f794a98803429d`（单一实现提交 `feat(fast-p): implement c3`；fast-p 证据由控制方另行提交）
- Implementation boundary: `ee7a45f..13b82fde5d74836977d12e97b7f794a98803429d`（单提交，恰好 10 个授权文件）

## Commit

```
13b82fde5d74836977d12e97b7f794a98803429d  feat(fast-p): implement c3
```

`git show --name-only` 恰好为 10 个授权路径；`docs/plans/fast/**`（含本报告）未被提交，保持未暂存由控制方单独提交证据。

## Changed files（10/10，无越界）

| # | 文件 | 内容 |
|---|---|---|
| 1 | `src/main/kotlin/.../discovery/controller/ExpertDiscoveryController.kt` | 模式分支（202/409/503）、`GET /pipeline`、`POST /pipeline/resume`、两个 POST 的统一规范化、`RND_TARGET` 来源禁用、等待原因文案 |
| 2 | `src/main/kotlin/.../discovery/service/ExpertDiscoveryScheduler.kt` | 新模式 cron 只 `tick()`、`SchedulingConfigurer` 注册恢复 tick（interval = `pipeline-tick`）、旧模式 cron 逐字保留 |
| 3 | `src/main/kotlin/.../task/controller/TaskProgressController.kt` | `EXPERT_DISCOVERY` 取消 = 先持久化 `pause()` 再请求窗口停止；其他任务类型不变 |
| 4 | `src/test/kotlin/.../discovery/controller/ExpertDiscoveryControllerMvcTest.kt` | 旧模式 4 例（新增 LEGACY 契约）+ 连续模式 6 例 + 装配缺失 1 例 |
| 5 | `src/test/kotlin/.../discovery/service/ExpertDiscoverySchedulerTest.kt` | 连续模式 cron/tick/暂停/间隔/装配缺失/fail-soft 共 5 例 |
| 6 | `src/test/kotlin/.../task/controller/TaskProgressControllerTest.kt` | 暂停持久化/窗口间隙/其他任务类型/503 共 4 例 |
| 7 | `src/main/resources/static/app.js` | 按请求任务的运行锁、启动冻结与 15s 超时、pipeline 轮询与诚实呈现、暂停/恢复、来源加载状态、`api()` 有限超时 |
| 8 | `src/main/resources/static/index.html` | 11 项资源缓存键同步 bump 为 `20260922-discovery-continuous`（无 DOM/inline 样式改动） |
| 9 | `src/test/js/discoveryContinuousRun.test.js`（新增） | 20 例：真实调用前端函数 + DOM stub + Promise/计时控制 |
| 10 | `src/test/js/taskActivityCenter.test.js`（新增） | 6 例：缓存键从发布文件派生（不写死字面量）的资源一致性契约 |

未授权文件保持零改动：`styles.css`（`git diff --stat` 无该项）、`ExpertDiscoveryControllerTest.kt`（17/17 通过、未修改）、`TaskProgressControllerExecutionsTest.kt`（26/26 通过、未修改）、`TaskProgressStore.kt`、`TaskExecutionRepository.kt`。

## Required commands（本次调用内全部新跑，逐字命令）

| # | 命令 | 结果 |
|---|---|---|
| 1 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest test` | **exit 0 / BUILD SUCCESS**；`Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`（Mvc 4、ControllerTest 17、Scheduler 13、TaskProgressControllerTest 12、TaskProgressControllerExecutionsTest 26 —— 后两个未授权文件未修改且全绿） |
| 1b | `JAVA_HOME=… mvn -B -Dtest=ExpertDiscoveryContinuousMvcTest,ExpertDiscoveryPipelineUnavailableMvcTest test`（额外：计划 T-1 要求 MockMvc 断言 202/409/503，这两个上下文与旧模式上下文互斥，故无法并入命令 1 的 `-Dtest` 单类名） | **exit 0 / BUILD SUCCESS**；`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`（Continuous 6、Unavailable 1） |
| 2 | `JAVA_HOME=… mvn -B -Dtest=DiscoveryPipelineServiceTest,OpenAlexRequestPolicyTest,ExpertDiscoveryServiceTest test`（c1/c2 回归） | **exit 0 / BUILD SUCCESS**；`Tests run: 204, Failures: 0, Errors: 0, Skipped: 0`（OpenAlexRequestPolicyTest 41、ExpertDiscoveryServiceTest 121、DiscoveryPipelineServiceTest 42） |
| 3 | `node --check src/main/resources/static/app.js` | **exit 0**（无输出） |
| 4 | `node --test src/test/js/*.test.js` | **exit 0**；`tests 1063 / suites 215 / pass 1063 / fail 0 / skipped 0`（基线 1037 + 本次新增 26 例：`discoveryContinuousRun.test.js` 20、`taskActivityCenter.test.js` 6） |
| 5 | `JAVA_HOME=… mvn -B -DskipTests clean package` | **exit 0 / BUILD SUCCESS**；`Building war: target/weibo-talent-introduction-1.0.0-SNAPSHOT.war` |

## Per-invariant evidence

| 不变量 | 实现位置 | 测试 |
|---|---|---|
| I-1 所有入口只使用一份已保存配置 | `ExpertDiscoveryController.kt`（`src/main/kotlin/com/weibo/talentintroduction/discovery/controller`）:106（`/run` 新模式分支）、:188（`/run/by-keyword`）、:407 `normalizeDiscoveryCriteria`（强制 `scope=RND_TARGET` + 清游标）、:94（`GET /sources` 用同一套 `excludedSources(RND_TARGET)`）；`ExpertDiscoveryScheduler.kt`（`src/main/kotlin/com/weibo/talentintroduction/discovery/service`）:47 `configureTasks`、:56 `scheduleDiscovery`（新模式不查「今天已跑一次」）、:110 `recoverPipelineTick` | `both manual entries are accepted with the same normalized RND_TARGET query and never run legacy sync (I-1, I-2)`、`continuous cron only dispatches the tick and ignores the once-per-day gate (I-1, I-4)`、`a manually paused pipeline is never resumed by cron or the recovery tick (I-1, I-3)`、`pipeline endpoints report LEGACY while the feature switch is off` |
| I-2 HTTP 受理与完成严格分开 | `ExpertDiscoveryController.kt`（`src/main/kotlin/com/weibo/talentintroduction/discovery/controller`）:412 `launchContinuousPipeline`（只有 `launch` 成功才 202；异常 503）、:432 `continuousLaunchResponse`（`!applied` → 409；成功 202 + `mode/pipelineId/phase/state/executionId(nullable)`）、:278 `getPipelineStatus`（`configured` 由 `queryHash` 决定，无配置不返回历史 `currentExecutionId`）；`app.js`:982 `applyContinuousLaunch`、:950 `renderDiscoveryPipelineStatus`（只绑定非空 `currentExecutionId`） | `both manual entries …`、`a different query is 409 while a repeated identical query is idempotent (I-2)`、`a configuration that cannot be persisted is 503 and the screen stays retryable (I-2)`、`pipeline status without a saved query is PAUSED and never impersonates a historical task (I-2, I-5)`、JS `renders acceptance for 202 and keeps tracking the pipeline instead of notifying`、`binds only a non-null currentExecutionId from the status snapshot` |
| I-3 暂停持久化且可恢复 | `TaskProgressController.kt`（`src/main/kotlin/com/weibo/talentintroduction/task/controller`）:57 `cancelTask`、:75 `pauseDiscoveryPipeline`（先 `pause()` 再 `progressStore.requestCancel`，窗口间隙 `requestCancel=false` 仍 200 PAUSED，未持久化则 503 且不取消窗口）；`ExpertDiscoveryController.kt`（`src/main/kotlin/com/weibo/talentintroduction/discovery/controller`）:304 `resumePipeline`（显式恢复，无配置 409）；`app.js`:1421 `handleCancelTask`（同一按钮暂停/恢复） | `cancelTask persists the pause first and then requests the window stop (I-3)`、`cancelTask during a window gap answers 200 PAUSED instead of 409 (I-3)`、`cancelTask of another task type keeps the in-process semantics (I-3, I-6)`、`cancelTask returns 503 without cancelling a window when the pause is not persisted (I-3)`、`explicit resume continues only a saved query (I-3)`、JS `pauses a running pipeline…` / `resumes an explicitly paused pipeline…` |
| I-4 后台窗口不占调度线程 | `ExpertDiscoveryScheduler.kt`（`src/main/kotlin/com/weibo/talentintroduction/discovery/service`）:47（tick 注册 interval = `pipelineTick`）、:115 `dispatchTick`（只调用 02 `tick()`，异常不逃出调度线程）、:129 `continuousPipeline`（开关关闭 → 旧语义；开关打开却无协调者 → 明确失败） | `the recovery tick is registered with the configured interval exactly when the pipeline is enabled (I-4)`、`continuous cron only dispatches the tick…`、`a tick failure never kills the scheduler thread and never falls back to legacy (I-4)`、`an enabled switch without the coordinator fails loudly (I-1)` |
| I-5 预算/进度/等待/故障诚实呈现 | `ExpertDiscoveryController.kt`（`src/main/kotlin/com/weibo/talentintroduction/discovery/controller`）:463 `pipelineStateText`、:478 `waitReasonTexts`（七个固定原因 + 终止原因文案，`DAILY_BUDGET` 用真实 `resetAt` 格式化北京时间；未知值原样透出）；`app.js`:871 `discoveryPipelineMetricsText`、:859 `discoveryBudgetText`（未同步 → “待同步”）、:829/837 状态 class/标签映射、:902 `renderDiscoverySourceDetail`（逐源明细，局部错误不放大为整体失败） | `pipeline status exposes the 02 snapshot with honest texts and no legacy binding (I-2, I-5)`、JS `maps every state onto the existing status classes and hides the daily percentage`、`shows real numbers, 待同步 for unsynced budget and tolerates unknown wait reasons` |
| I-6 只放开确认过的互斥组合 | `app.js`:1207 `progressStoreHasRunningTask(requestedTaskType)` + :803 `COEXISTING_TASK_TYPES`（仅 `EXPERT_DISCOVERY`↔`CHECK_REPLIES`）、:1231 `fetchTaskRunningOrThrow`（失败显式抛错）、:6302 `executeCheckReplies`、:7079 `executeDiscover`（状态查询失败可见且可重试）；后端检查回复实现与任务锁未改 | JS `allows deep discovery and check replies to coexist in both directions`、`still blocks the same type and every other combination`、`surfaces a failed state query instead of reporting an idle system`、`keeps legacy no-argument callers tolerant of a failed query` |
| I-7 界面生命周期与旧记录隔离 | `app.js`:7079 `executeDiscover`（切视图前冻结关键词/来源/includeRawScan；来源未 ready 拒绝启动）、:7145 `postDiscoveryLaunch`（新模式 15s 超时、旧模式不设超时）、:7043 `loadDiscoverySources`（10s 超时 + 错误状态）、:943 `fetchDiscoveryPipelineInfo`（10s 超时）、:1019 `startDiscoveryPipelinePolling`（3s、单次最多一请求、generation 校验、失败可见）、:1054 `restoreLaunchConfigAfterFailure`（409/503/超时后回到可重试配置态）、:1950 `api()`（可选 `timeoutMs`）、:1388 `closeTaskModal`（新模式不启动旧 watcher）、:1123 `pollTaskWatcher`（连续模式下窗口终态不发完成通知） | `freezes the operator selections before switching views and posts with a finite timeout`、`restores the retryable configuration when a different query is refused`、`refuses to start when the source list never loaded`、`never renders into a closed or switched dialog`、`reports a load failure with a retry hint and leaves no stale selection`、`initializes the same nodes for continuous mode and restores the legacy display afterwards` |
| S-1 只更新现有状态节点 | `index.html`:1090-1105 既有节点逐字保留；`app.js`:829 `pipelineStatusClass`（QUEUED/RUNNING/WAITING→running、PAUSED→cancelled、FAULTED→failed、DRAINED→completed/failed）、:837 `pipelineStatusLabel`（有失败时“已排空，存在失败”）、:950 只用 `textContent`/`hidden`/既有 class，连续模式写“持续运行”并隐藏既有 track | JS `maps every state onto the existing status classes and hides the daily percentage`、`keeps using the existing status nodes and the existing track element`、`keeps using the existing status nodes…`（id 源文本断言）；`styles.css` 零 diff |
| S-2 配置与暂停/恢复操作 | `ExpertDiscoveryController.kt`（`src/main/kotlin/com/weibo/talentintroduction/discovery/controller`）无新增按钮/DOM；`app.js`:1421 `handleCancelTask`（同一 `#taskModalCancelBtn` 在“暂停发现/恢复发现”间切换，pending 时 `disabled`，finally 恢复）、`:6657` `openTaskLaunchModal`（`#taskLaunchDesc` 显示持续模式说明或来源加载错误，错误时 `#taskLaunchRunBtn` disabled，不新增重试组件） | JS `maps every state…`（按钮文本）、`disables the button while the pause request is pending`、`keeps the legacy cancel wording for other task types` |

## Deviations from the plan's stale audit snapshot（全部为已论证的落地决定）

1. **`scope 强制 RND_TARGET` 在本 base 并不存在**（计划审计称“已有 RND_TARGET 修复”，brief 已声明其陈旧）。已实现：`ExpertDiscoveryController.normalizeDiscoveryCriteria`（:407）在两个 POST 的**新模式分支**统一 `copy(subjectScope=RND_TARGET, cursor=null)`；旧模式分支（开关关闭）逐字保留原同步语义与既有 `criteria` 透传，因为计划 I-1 的规则以 `pipeline-enabled=true` 为前提、且要求“false 走原同步逻辑”。因此“两个 POST 规范化输出相同”在开关打开时成立（同输入得到同一 `PaperSearchCriteria`，由 MockMvc 断言 `captured[0] == captured[1]`）。
2. **`GET /sources` 的医学源禁用规则**（计划审计声称“按相同 scope 禁用医学源”，base 未实现）：`ExpertDiscoveryController`:94 现在按 `SubjectScopeCatalog.excludedSources(RND_TARGET)` 把 `EUROPE_PMC`/`PMC_OA` 标为 `enabled=false`，使前端不再提交永远不参与的两源（服务端同一规则）。既有的两个 sources 断言（`ExpertDiscoveryControllerTest`，未授权文件）依旧通过。
3. **缓存键**：base 为 `20260920-manual-material-upload` 且无固定键测试。11 项资源同一提交 bump 为 `20260922-discovery-continuous`（`index.html`，仅键）；`taskActivityCenter.test.js` 按 brief 要求新建为**派生键**契约测试（从 `index.html` 派生，不写死字面量，并断言 triad 同键、资源数 11、历史键零命中、键只允许出现在 `index.html`）。
4. **恢复 tick 的注册方式**：计划写“新增 `fixedDelay` 读取 02 的 `pipeline-tick` 默认 30000ms”，但该属性是 Spring `Duration`（`30s`），而 Spring 5.3.31 的 `fixedDelayString` 只接受毫秒或 ISO-8601（`ScheduledAnnotationBeanPostProcessor.toDuration` 走 `isDurationString`+`Duration.parse`，已用 `javap` 复核），直接写会启动即 `NumberFormatException`。故改用仓库既有先例（`DailyCountResetScheduler`/`BounceCollectionScheduler`）的 `SchedulingConfigurer.addFixedDelayTask(runnable, duration.toMillis())`，默认仍是 `pipelineTick=30s`；未新增配置键（`application.yml` 不在授权清单内）。
5. **依赖注入形式**：协调者以**可空直接依赖** `DiscoveryPipelineService?`（末尾带默认值）注入，而不是 `ObjectProvider<…>`。原因：本项目 `@MockBean` 对 `ObjectProvider` 注入点的替换不生效（实测：连续模式上下文里协调者仍为 null），而 `@MockBean` 直接替换 `DiscoveryPipelineService` 才可测；`ObjectProvider` 在本仓测试里只承担“满足构造参数”的历史作用。既有构造调用（含未授权的 `ExpertDiscoveryControllerTest`、`TaskProgressControllerExecutionsTest`）因末尾默认值而逐字兼容。
6. **逐源明细 renderer**：计划 S-1 说“沿用现有来源明细 renderer”，但 02 的逐源行字段（排队/已处理/在途/失败/游标/来源错误）与 `renderBySourceTable` 的列语义（论文/邮箱/有效/收录/晋升）不同，直接复用会把流水线数据打上错误列名（违反 I-5 的诚实呈现）。因此在**同一既有容器 `#taskModalBySource`/`#taskModalBySourceContent`** 内渲染与既有 renderer 同构（同一 `<table>` + 同样的 inline 风格写法，所有来源名/错误串经 `escapeHtml`）的诚实列。未新增 DOM 节点、未改 `index.html` 内联样式。
7. **重新打开弹窗的落点**：已有配置（`queryHash != null`）时直接展示状态区（PAUSED 显示“恢复发现”），符合 I-2/T-3“有已有配置即展示状态；PAUSED显示恢复按钮”；同一查询重复启动由 c2 幂等（PAUSED 视为显式恢复），不同查询由 c2 拒绝为 409 并在配置区显示原因（可调整后重试），不静默覆盖旧积压。
8. **执行标识绑定**：`bindTaskModalExecution` 只接受 `status().currentExecutionId`（非空），202 响应中的 `executionId` 不用于绑定；状态查询失败（超时/非 2xx）在弹窗内可见并自动重试（I-6/I-7）。
9. **`pollTaskWatcher` 的连续模式判定用 `typeof` 兜底**：本仓既有惯例（app.js 内 I1-5 注释同款），因为 vm 沙箱单测按函数抽取源码；未注册该变量时按旧模式处理，不影响既有 watcher 语义。
10. **来源加载成为两个模式共用的前置步骤**：计划 I-7/T-3 要求“来源 10 秒超时、加载失败不得按空列表启动、错误可见且按钮禁用”，这条没有限定新模式，因此旧模式的启动弹窗也改为 `preload` 内 await 加载（旧实现是 fire-and-forget + `console.error`）。服务端旧同步逻辑零改动；仅前端在来源不可用时不再静默继续。
11. **计划 S-1 的 `styles.css` 行号**在此 base 与计划快照不同；本次完全不改 `styles.css`（`git diff` 无该文件），仅按选择器复用。

## Blockers

- 无阻塞项。计划“人工验收清单 A-1..A-6”（浏览器鉴权实测启动/暂停/刷新/回复检查、隔离环境 mock 额度耗尽、Tomcat 部署目录等）属于人工门禁：本 worktree 没有运行中的 MySQL/Tomcat 与登录凭据，本 child 交付的自动化证据覆盖 I-1..I-7 与 S-1/S-2 的可断言部分，浏览器实测留待人工验收。

## Freshness

- Plan identity rechecked: YES（`a87bf072…`，执行前后一致）
- Worktree identity rechecked: YES（`--expect-root/--expect-branch/--expect-git-dir` 校验后提交；提交后仅剩未暂存的 fast-p 证据文件）
- Reported commits reachable from target branch: YES（`13b82fde5d74836977d12e97b7f794a98803429d` 为 `fast/2026-09-22-openalex-daily-budget-design` HEAD，父提交 `ee7a45fa`）
- Required commands run this invocation: YES（5 条必跑 + 1 条额外 MockMvc 上下文命令，均在最终实现状态之后新跑）
- Historical evidence used only as baseline: YES（`baseline-java.txt`、`children/c1|c2/verify-log.md` 仅用于对比；JS 基线 1037/1037 与本次 1063/1063 的差值恰为新增 26 例）

---

## Controller evidence note (fast-p finalization, 2026-09-23)

No automatic fix round was dispatched for this child (`fix_round = 0`); `fix-log.md` records that. The controller re-recorded this child's evidence commit so that it contains all four required child artifacts, because the first evidence commit `798dd80` predated the empty fix log.

No product, test, plan or verification content above was changed.
