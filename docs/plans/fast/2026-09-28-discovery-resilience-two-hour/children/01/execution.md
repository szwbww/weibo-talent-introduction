# Child 01 执行报告 — 深度发现：有限延迟续跑与每两小时调度

- 状态：实现完成，命令证据见下（本文件不属于实现提交，留在工作树由控制方提交）
- 计划（批准版，字节冻结）：`docs/plans/2026-09-28/discovery-resilience-two-hour.md`
- 计划 SHA-256：`c94fd0e4a8aaaa54ecf1727d8622812666af1df65a64fbde4635cdacf41db88b`
- EXECUTION_ID：`docs/plans/2026-09-28/discovery-resilience-two-hour.md@c94fd0e4a8aaaa54ecf1727d8622812666af1df65a64fbde4635cdacf41db88b`
- Execution epoch：NEW（此前无同一 identity 的执行证据）
- Child brief：`docs/plans/fast/2026-09-28-discovery-resilience-two-hour/children/01/brief.md`
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour`（`git rev-parse --show-toplevel` 与 `branch --show-current` 均先核对）
- Branch：`fast/2026-09-28-discovery-resilience-two-hour`
- child_base_sha：`f98e27c7538d091bfcdecfcb6ffc10360a35ba04`（seed 提交 `3f167a2820c3f9bdb344f19da2123d6a9ff2f12a` 只加 `docs/plans/**`）
- JDK：所有 Maven 命令均 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`
- 基线并发约束：实现阶段第一条 Maven 命令前先执行 `while pgrep -f 'fastp-baseline-01\.sh' > /dev/null; do sleep 15; done; echo BASELINE_CLEAR`，输出 `BASELINE_CLEAR` 后才开始。

## TDD：先失败断言（改前证据，实现前捕获）

命令（此时工作树只含新增/改写的测试，产品代码仍是 seed 提交的字节）：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoverySchedulerTest,ExpertDiscoveryServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

结果：`exit=1`（BUILD FAILURE），wall 191.18s，`Tests run: 165, Failures: 3, Errors: 0, Skipped: 0`

```
[ERROR] Tests run: 152, Failures: 2, Errors: 0, Skipped: 0, Time elapsed: 67.033 s <<< FAILURE! - in ExpertDiscoveryServiceTest
[ERROR]   ExpertDiscoverySchedulerTest.scheduleDiscovery starts a new run when a scheduled run already succeeded today (I-6):97
[ERROR]   ExpertDiscoveryServiceTest.OpenAlex deferred recovery revisits the same page after the configured delay (O-1, I-1, I-2):3997
        3 次短尝试失败后必须按恢复间隔回访同一页，而不是立刻终止来源 ==> expected: <4> but was: <3>
[ERROR]   ExpertDiscoveryServiceTest.OpenAlex permanently failing page uses every allowed attempt then fails once (I-1):4023
        expected: <12> but was: <3>
```

原代码日志（同一次运行）证明改前行为是「3 次短尝试后立即终止来源」：

```
[OPENALEX] 完成: 耗时 3263ms, API请求 3 次 | ... 失败原因 {SEARCH_FAILED=1}
[OPENALEX] 本次运行结束: stopReason=SEARCH_FAILED, exhausted=false, resumeCursor=C7
[OPENALEX] 完成: 耗时 3407ms, API请求 3 次 | ... 失败原因 {SEARCH_FAILED=1}
[OPENALEX] 本次运行结束: stopReason=SEARCH_FAILED, exhausted=false, resumeCursor=C7
```

改后同一组断言全部转绿（见下方 CMD1）。`ExpertDiscoveryPropertiesTest` 的失败断言无法在改前运行：它引用的 `openAlexSearchRecoveryDelays`
属性在 seed 提交里不存在，Kotlin 编译整个 test source set 会直接失败；因此 T-1 的属性契约以「构造校验 + 绑定」在改后一次性验证，
上面两条恢复行为断言（不依赖任何新 API）承担了 T-1/T-2 的改前红证据。

## 基线对比

控制方在 seed 提交上 fresh 运行的基线（`children/01/baseline.md`）：

| 命令 | 基线结果 |
|---|---|
| CMD1（11 类） | `exit 0`；10 个既有类合计 **337 tests / 0 failures / 0 errors**（`ExpertDiscoveryPropertiesTest` 基线不存在属预期） |
| CMD2 `mvn clean package` | `exit 0`；全量 **4200 tests / 0 failures / 0 errors**、Node 1199/1199 |
| CMD3 `git diff --check` | `exit 0` |

本 worktree 的 `target/surefire-reports`（基线 `mvn clean package` 产物）逐类复核一致：ExpertDiscoveryServiceTest 150、
ExpertDiscoverySchedulerTest 13、DiscoveryCheckpointCodecTest 11、OpenAlexRequestPolicyTest 44、DiscoveryPromotionProgressServiceTest 5、
TaskProgressStoreTest 26、TaskProgressStoreRebindTest 3、TaskProgressControllerTest 13、TaskProgressControllerExecutionsTest 26、
DiscoveryPipelineServiceTest 46 —— 合计 337、failures 0、errors 0。**基线全绿，任何新增失败都算回归。**

## 逐任务实现证据

### T-1 重试策略与可测试时间（I-1/I-4/I-5/I-8）

| 契约 | 文件:行 | 实现 |
|---|---|---|
| 新配置项 `openAlexSearchRecoveryDelays` 默认 30s/120s/300s | `config/ExpertDiscoveryProperties.kt:33`（字段）、`:145-149`（`DEFAULT_OPENALEX_SEARCH_RECOVERY_DELAYS`） | `List<Duration>`，构造绑定 |
| 校验：≤3 项、每项正数、非递减、≤5 分钟，非法值启动失败 | `config/ExpertDiscoveryProperties.kt:98-118`（init）、`:139`（`MAX_OPENALEX_SEARCH_RECOVERY_GROUPS`）、`:142`（`MAX_OPENALEX_SEARCH_RECOVERY_DELAY`） | `require(...)` 抛 `IllegalArgumentException`，报出旋钮名 |
| `SourceStats` 唯一可空对象字段 `retry`（round/maxRounds/nextRetryAt/reason） | `domain/SourceStats.kt:19-24`（`SourceRetryState`）、`:64`（`var retry`） | 同文件类型；ISO-8601 UTC；仅观察 |
| retry 进入 `buildBySourceDetails` | `service/ExpertDiscoveryService.kt:294-300` | 无待恢复项时为 `{}` |
| `buildProgressDetails` 追加 summaryText，不覆盖论文/收录/晋升 | `service/ExpertDiscoveryService.kt:366` + `:317-343` | 运行中 head 为「发现任务进行中」，终态更新随后覆盖 |
| 可注入时间/等待接缝（复用 `PolicyTimeSource`，service 末尾默认 SYSTEM） | `service/ExpertDiscoveryService.kt:120` | deadline 比较 `:635`、短等待 `:2395`、延迟等待 `:1131` 共用同一 clock |
| 等待粒度 ≤100ms + 中断标志恢复 | `service/ExpertDiscoveryService.kt:3646`（`RETRY_WAIT_SLICE_MS=100`）、`:1125-1144`、`:2385-2403` | 绝不 `sleep(300000)` |
| `SourceRunOutcome` 可空待恢复信息（默认 null） | `service/ExpertDiscoveryService.kt:3560-3566`、`:3577-3583` | CORE/ORCID 调用逐字兼容 |
| 原因只允许脱敏码 | `service/ExpertDiscoveryService.kt:1239-1247` | `REMOTE_TLS_HANDSHAKE`/`TIMEOUT`/`NETWORK_IO`/`HTTP_5xx`，不含 URL/密钥 |
| 恢复组上限 3、同页最多 12 次 | `service/ExpertDiscoveryService.kt:1216-1233`（`planDeferredRecovery`）、`:792-806`、`:843-857` | 3 短尝试 + 3 组 × 3 次 |
| 失败分组/轮次只在本次 discover 局部状态 | `service/ExpertDiscoveryService.kt:435`、`:710`、`:1054-1058` | 无 Service 全局 map / 后台线程 |
| YAML 绑定与默认值 | `src/main/resources/application.yml:185` | `${OPENALEX_SEARCH_RECOVERY_DELAYS:30s,120s,300s}` |

### T-2 来源回访、正确计数与可见进度（I-1..I-5、I-7..I-8）

| 契约 | 文件:行 | 实现 |
|---|---|---|
| 首轮来源顺序不变；待恢复只登记，所有来源（含 ORCID）停稳后才消费 | `service/ExpertDiscoveryService.kt:436-458`（登记）、`:460-466`（ORCID 之后消费） | 长等待不饿死其他来源 |
| 恢复时间从最后失败时刻算起；未到期用 100ms 片段等待；到界保留游标+清等待+真实停止原因 | `service/ExpertDiscoveryService.kt:1074-1096`、`:1125-1144` | 已耗过的等待不重复睡 |
| 显式传入 resumeCursor / 固定 runBudget / 已处理基线 | `service/ExpertDiscoveryService.kt:653`、`:667-673`、`:701-703`、`:1099-1101` | 入口不再查库取游标；`resumeCursor=null` 合法为第一页 |
| 恢复组不重新 allocateSourceQuota；请求前检查本源额度/全局/作者/deadline | `service/ExpertDiscoveryService.kt:1099-1101`（沿用 `item.runBudget`）+ `:737-759`（既有循环内检查） | 预算不提高 |
| 批次号唯一递增 | `service/ExpertDiscoveryService.kt` 既有 `stats.nextBatchSeq()`（批次进度） | 恢复回访仍走同一处 |
| `sourceFailureCount` 只在最终停止时决定；全组用尽记一次 SEARCH_FAILED | `service/ExpertDiscoveryService.kt:1026-1032`、`:859-864`（HTTP 分支）、`:886-891`（异常分支） | RETRY_WAIT 不污染最终失败数 |
| 429/额度延期不进网络恢复 | `service/ExpertDiscoveryService.kt:772-780`、`:827-831` | 直接 BUDGET_DEFERRED |
| 安排/回访开始/最终停止各写一次事件（等待期间不刷屏） | `service/ExpertDiscoveryService.kt:457`、`:1148-1153`、`:1111-1116`、`:1180-1196` | `batchNumber=0` ⇒ `batchOnly=true` 规则不变 |
| 等待文案（轮次 + 北京时间） | `service/ExpertDiscoveryService.kt:1198-1211` | `[OPENALEX] 搜索连接中断，等待恢复 1/3，计划重试时间 ...（北京时间）；先处理其他来源` |
| 晋升读投影保留等待提示 | 未修改 `DiscoveryPromotionProgressService`；回归见 CMD1 的 `recovery groups are not reset ... (I-1, I-5, X-3)` | 投影真的把晋升 0→1 且等待文本仍在 |

### T-3 每两小时触发（I-2/I-3/I-6..I-8）

| 契约 | 文件:行 | 实现 |
|---|---|---|
| 删除同步分支「本日已执行」闸门（含无用 import） | `service/ExpertDiscoveryScheduler.kt:72-90`（方法体，已无 `todayStart/countScheduledSince`）；通用 API 未删除（`TaskExecutionService.kt:124`） | 保留 `tryStartWithToken`、`onStarted` 绑定、`finally` 清理、异常 FAILED |
| 默认 cron 两小时 + 显式时区 | `service/ExpertDiscoveryScheduler.kt:72`（`zone = "Asia/Shanghai"`）、`application.yml:182`（`0 0 */2 * * ?`） | 部署覆盖与 `-` 语义不变 |
| 初始化文案显示定时发现 + 实际 cron/时区 + 恢复间隔 | `service/ExpertDiscoveryScheduler.kt:82-85`、`:47-50` | 去掉「初始化 EuropePMC 搜索」 |
| 不新增第二个 @Scheduled / 队列 / 线程池 | 回归断言 `ExpertDiscoverySchedulerTest` 的 `the cron annotation pins the two hour schedule ...`（唯一 `@Scheduled` 方法） | 4 小时单轮 deadline 保留 |
| 修正「每日一次」注释 | `service/ExpertDiscoveryScheduler.kt:17-29`、`:66-71`、`:51-53` | 文档与代码一致 |

## 必需命令（fresh，最终代码状态）

### (a) CMD1 — 定向测试（11 类）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest,ExpertDiscoveryPropertiesTest,DiscoveryCheckpointCodecTest,OpenAlexRequestPolicyTest,DiscoveryPromotionProgressServiceTest,TaskProgressStoreTest,TaskProgressStoreRebindTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest,DiscoveryPipelineServiceTest
```

`exit=0`，BUILD SUCCESS，**354 tests / 0 failures / 0 errors / 0 skipped**（基线 337 + 17 新增）。

| 类 | 基线 | 本次 | Failures |
|---|---:|---:|---:|
| ExpertDiscoveryServiceTest | 150 | 160 | 0 |
| ExpertDiscoverySchedulerTest | 13 | 15 | 0 |
| ExpertDiscoveryPropertiesTest（新增） | — | 5 | 0 |
| DiscoveryCheckpointCodecTest | 11 | 11 | 0 |
| OpenAlexRequestPolicyTest | 44 | 44 | 0 |
| DiscoveryPromotionProgressServiceTest | 5 | 5 | 0 |
| TaskProgressStoreTest | 26 | 26 | 0 |
| TaskProgressStoreRebindTest | 3 | 3 | 0 |
| TaskProgressControllerTest | 13 | 13 | 0 |
| TaskProgressControllerExecutionsTest | 26 | 26 | 0 |
| DiscoveryPipelineServiceTest | 46 | 46 | 0 |

与基线逐类一致（除新增用例数），**无既有用例被改写为「迎合新实现」**；被改写的既有用例只有两个，均为计划明确要求：
`OpenAlex three timeouts fail once and retain the same cursor`（改为显式空恢复列表，语义仍是计划要求的「3 次短尝试后终止」）
与 `scheduleDiscovery skips when scheduled discovery already ran today`（按 I-6 改为断言「仍会启动新一轮」，旧断言在改前是红的）。
其余既有用例只新增断言（如 `OpenAlex certificate failures...` 不动、`page with failed RAW persistence...` 追加「消费错误不进恢复组」）。
`git diff -U0 -- src/test` 中被删除的测试函数仅 `scheduleDiscovery skips when scheduled discovery already ran today` 与闸门脚手架 `setUp/stubNoScheduledRunToday`。

### (b) CMD2 — `mvn clean package`

`exit=0`，BUILD SUCCESS，Total time 06:30 min（`/tmp/cmd2-final.log`）。

- 全量 surefire：**4217 tests / 0 failures / 0 errors / 13 skipped**（基线 4200 / 0 / 0 / 13；+17 即本 child 新增用例数）。
- 绑在 test 阶段的 Node 前端套件（`exec-maven-plugin`）：**1199 pass / 0 fail**（基线 1199 / 1199）。

### (c) CMD3 — `git diff --check`

`exit=0`（提交前对完整工作树 diff）；提交后复查同样 `exit=0`（工作树只剩未跟踪的报告目录）。

## 提交与身份复核

- 实现提交：`8700a605427aaedb4c31be63a72e22a657b94208`（`feat(fast-p): implement 01`），是 `fast/2026-09-28-discovery-resilience-two-hour` 的 HEAD。
- `git rev-list --count 3f167a2..HEAD` = **1**（seed 之后恰好一个实现提交）。
- `git diff --name-status 3f167a2..HEAD` = 恰好 8 个授权文件（5 产品 + 1 yml + 3 测试，其中 `ExpertDiscoveryPropertiesTest.kt` 为新增 A）。
  以 `child_base_sha=f98e27c` 为左端的 `git diff --name-status f98e27c..HEAD` 会额外列出 seed 提交自带的 `docs/plans/**`（10 个文档/证据文件，非本实现提交内容）。
- `git show --stat HEAD`：8 files changed, 1244 insertions(+), 70 deletions(-)；**不含** `docs/plans/**`。
- 报告文件 `docs/plans/fast/2026-09-28-discovery-resilience-two-hour/children/01/execution.md` 保持未跟踪（`git status --short` 仅 `?? docs/plans/fast/...`）。
- 计划 identity 复核：`sha256sum docs/plans/2026-09-28/discovery-resilience-two-hour.md` = `c94fd0e4a8aaaa54ecf1727d8622812666af1df65a64fbde4635cdacf41db88b`（与开始时一致，未变）。
- Worktree identity 复核：root `.../weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour`、branch `fast/2026-09-28-discovery-resilience-two-hour`、git-dir `<main>/.git/worktrees/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour`、common-dir `<main>/.git`；主工作区 `/Users/lukai/IdeaProjects/weibo-talent-introduction` 全程未读写改动。
- 未执行 push / merge / rebase / amend / reset；未修改已应用 Flyway 迁移（本计划无 DB 变更）；未修改 `docs/plans/**` 的既有文件。

## 显式偏差

1. **T-1 属性契约的改前红证据形态不同**：`ExpertDiscoveryPropertiesTest` 引用的新属性在 seed 提交里不存在，
   Kotlin 一次编译整个 test source set，故它无法先以「断言失败」形式变红（只能以编译失败形式）。T-1/T-2 的改前红证据
   由两条不依赖新 API 的行为断言承担（`OpenAlex deferred recovery ...` / `OpenAlex permanently failing page ...`，
   见上文 `expected: <4> but was: <3>`、`expected: <12> but was: <3>`），属性契约在实现后以构造校验 + Binder 绑定一次验证。
2. **等待文案带脱敏原因码**：计划 T-2 的示例消息为「搜索连接中断，等待恢复 1/3，…」。实现按原因码生成文案
   （`REMOTE_TLS_HANDSHAKE`/`NETWORK_IO` → 「连接中断」，与示例逐字一致；`TIMEOUT` → 「超时」；`HTTP_5xx` → 「服务不可用（HTTP_5xx）」），
   以便运营端区分超时与服务端 5xx。轮次/北京时间/「先处理其他来源」与示例一致。
3. **运行中 summaryText 的 head 文案**：计划要求 `buildProgressDetails` 追加 summaryText。终态 head 逐字不变
   （`发现任务完成[<status>]: 总耗时 Nms`），运行中 head 用「发现任务进行中」而不是伪造耗时/终态。
4. **测试 profile 的 `src/test/resources/application.yml:86`** 仍是旧默认 cron（该 profile `expert-discovery.enabled=false`，
   无任何断言依赖），它不在 8 个授权文件内，按范围纪律未改动（见「观察」）。
5. 计划 T-4 的发布/上线/服务器核对步骤不在本 child 范围，未执行；R-0 只读探针亦未执行（属上线阶段）。

## 观察（不属于本 child 范围，未处理）

- `src/test/resources/application.yml:86` 仍是旧默认 `cron: ${EXPERT_DISCOVERY_CRON:0 0 2 * * ?}`（测试 profile 的
  `expert-discovery.enabled=false`，该值不参与任何断言）。它不在 8 个授权文件内，故未改动；若后续要求测试 profile 与仓内默认一致，需单独授权该文件。
- 生产实际生效的 cron 仍取决于外部 PropertySource（计划 E-1 已指出），本 child 只改仓内默认值与注解时区；
  上线前仍需按 T-4 核对有效配置。
