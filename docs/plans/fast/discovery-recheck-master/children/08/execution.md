# Child 08 — Execution log

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/2026-10-24/discovery-recheck-08-api.md`

Plan SHA-256: `47196d02af0f41bd852216230d91ec446020baa6e19b6e5e9a1b0f8c7e1d440a`

Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/2026-10-24/discovery-recheck-08-api.md@47196d02af0f41bd852216230d91ec446020baa6e19b6e5e9a1b0f8c7e1d440a`

Execution epoch: NEW

Approval basis: 当前 child08 实施指令、`children/08/brief.md` 与上述不可变计划；遵循 execute-p。此次未调用 A7 扩文件授权，也未修改计划或 ledger。

Executor: `Recheck08Implementer`（fresh sole implementer）

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`

Target branch: `fast/discovery-recheck-master`

Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master@fast/discovery-recheck-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-recheck-master`

Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-recheck-master`

Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`

Pre-execution code SHA / child base: `f37ff178554cb19b3264f81ed4deac7a6b43bd8c`

Pre-execution governance HEAD: `fca3b4ecb886ebda7b564d9b0a2ef08a60eaf49a`

Post-execution code SHA: `59c34a1b6f04af0669a9276dae7c043bca4164f5`

Evidence HEAD: N/A；本 worker 不创建 fast-p evidence commit。本报告留给 controller 单独纳入证据提交，不混入 product commit。

Implementation boundary: 产品提交 `fca3b4ecb886ebda7b564d9b0a2ef08a60eaf49a..59c34a1b6f04af0669a9276dae7c043bca4164f5`；继承 child07 产品基线 `f37ff178554cb19b3264f81ed4deac7a6b43bd8c`。

Product commit subject: `feat(fast-p): implement 08`

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-1 / A-1 固定名单、幂等、全页 | IMPLEMENTED | Service、Repository、Controller、FlowIT、RepositoryIT | IDS 重新读取 RAW/discovered，单请求最多100；ALL_MATCHING 复用真实 ScanService 的 scroll/filter，每批500冻结且全局去重，无任务人数上限。requestKey 为规范 UUID，固定 canonical spec；同请求返回原 run，不同 spec 为409。真实 HTTP/SQL smoke 冻结1001，批次500/500/1，冻结后新 RAW 不混入，执行1001。 |
| I-2 / A-2 只读准备及默认仅对照 | IMPLEMENTED | Service、Repository、QueryService、FlowIT | 默认 AUDIT/REUSE；prepare 只写核验与 task 审计，不 initializeAdmission，不激活 item，不调用 provider。快照保留 identityHash/factFingerprint/profile，reviewInput 单独保存 null 而非0的缺失 admission revision、前态、refs、证据哈希及基线选择。READY 前 RESERVED、epoch=runId、无步骤；start 的同一短事务才转 RUNNING、激活并初始化步骤。实际 smoke prepareProviderHttp=0、auditProviderHttp=0、auditBusinessWrites=0、auditWouldAdmit=1、auditActualAdmitted=0。 |
| I-3 / A-3 失败、取消、恢复 | IMPLEMENTED | Service、Repository、RepositoryIT、FlowIT | 部分冻结500后异常为 FAILED、inputClosed=false，不能 start；准备 task 被真实标记 INTERRUPTED 后恢复控制为 INTERRUPTED，迟到工作不能改为 READY。PREPARING/READY 可取消；运行取消按 run→item 顺序锁定，只取消活动项、不回滚已提交 receipt。真实 MySQL 竞争分别覆盖 commit-first 与 cancel-first；lease 回收后保留原 hash/requestCount。 |
| I-4 / A-4 权限、状态、旧人工审核 | IMPLEMENTED | Controller、QueryService、ControllerTest、FlowIT | 新端点逐一要求 session，actor 不读客户端；未知枚举400、跨 run/item404、hash/revision/spec 冲突409。列表/manifest 为20/50/100分页，latest 批量查询；真实 run/taskId、互斥 item 状态计数，wouldAdmit/actualAdmitted 与同步状态分离。实际旧人工审核 HTTP 仍可 MANUAL_APPROVED，ENFORCE 重审仍显示 REVIEW_PROTECTED。 |
| prepare/start/cancel/sync-retry/read/manifest 完整 API | IMPLEMENTED | Controller、Service、QueryService、Repository | 已实现 POST prepare、start、cancel、sync-retry；GET runs、latest、run、items、steps、manifest。summary 不嵌全名单/论文正文；逐项 before/proposed/current/applied 取冻结数据、实际步骤、当前 admission 与持久化 receipt，不把建议当已提交。GET 不驱动业务或重算。 |
| 实际 task 绑定与短事务注册 | IMPLEMENTED | Service、Repository、RepositoryIT、FlowIT | 同一事务持久化真实 TaskExecutionRepository task 与 run；绑定 owner/heartbeat/finish，prepare SUCCESS 与核验 COMPLETED 独立。MySQL GET_LOCK 使用 Spring 绑定的同一连接并在事务 afterCompletion 后释放；精确参数化 JSON UUID 查询。并发注册、commit/rollback 释放、另一连接 busy、无孤儿任务、并发 start 均经真实 MySQL 用例。ES scroll/画像/证据读取不放入该注册锁事务。 |
| 原 Worker/Gate/Commit/Trace、投影重试、ordinary 兼容 | IMPLEMENTED | Service、Repository、QueryService、FlowIT；原组件未修改 | start 交回原 Worker，不增第二条规则流水线；步骤读取原 TraceRepository。sync-retry 先核对全体项归属，再只调用原 CommitService 的 project，对已同步项幂等；实际失败投影→retry 成功且 providerCalls=0，不重做资格。既有 Worker/Gate/Commit/Query/Trace 回归56例通过，含 READY/RESERVED 隔离、legacy 字段缺失语义与 frozen rule3/current ordinary 差异证据。 |
| 独立准备执行器与恢复调度 | IMPLEMENTED | DiscoveryExecutorConfig、Service | 专用单线程、容量32队列的 prepare executor；reconcile 刷新本进程拥有的 task 心跳并收敛控制状态，不借用执行器规模改变原 Worker。重复 start 保存原受理 revision，后续 worker revision 变化不破坏同一原请求幂等；过期 hash/revision 仍拒绝。 |

### Commands

下列命令均在目标 worktree 本次执行；最后一次产品/测试编辑之后，两条必需 Maven 命令均重新完整运行，无 mandatory IT skip。JDK 为指定 Zulu11。

| Command | Result | Evidence |
|---|---|---|
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryRecheckControllerTest,DiscoveryRecheckServiceTest,DiscoveryVerificationRepositoryIT,DiscoveryRecheckFlowIT -DmysqlIt=true` | PASS，exit0；JVM 39 / failures0 / errors0 / skipped0；绑定 JS 1827 / pass1827 / fail0 / skipped0；绑定 app.js 与 task-modal-runtime.js 语法检查通过 | 最终新鲜执行 `artifact://1202`，10:41；RepositoryIT 28、ControllerTest 5、ServiceTest 5、FlowIT 1。前两次相同命令 exit1，各39例中1 error（`artifact://1196`、`artifact://1199`），详见下方失败处理。 |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationTraceRepositoryIT -DmysqlIt=true` | PASS，exit0；JVM 56 / failures0 / errors0 / skipped0；绑定 JS 1827 / pass1827 / fail0 / skipped0；两项绑定语法检查通过 | `artifact://1204`，05:28；WorkerTest 17、CommitServiceTest 6、AdmissionGateTest 10、QueryServiceTest 19、TraceRepositoryIT 4。 |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-classpath %classpath com.weibo.talentintroduction.discovery.service.DiscoveryRecheckFlowIT'` | PASS，exit0；独立 forked JVM、非 JUnit 运行，实际 Tomcat/DispatcherServlet/AuthWebConfig + JDK HttpClient + disposable MySQL8.0.36 | `artifact://1213`，07:13；HTTP requests=411；完整 runtime receipts 见下。 |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn exec:java -Dexec.mainClass=com.weibo.talentintroduction.discovery.service.DiscoveryRecheckFlowIT -Dexec.classpathScope=test` | FAIL，工具3600s deadline，未取得正常 CLI exit；不计为通过 | `artifact://1205`。Maven 进程内 exec classloader 造成 Tomcat webapp 找不到 Spring Boot `OnPropertyCondition`，在 servlet 初始化时失败；改用上行真实 forked JVM 的 `%classpath` 启动，无产品/测试改动。 |
| `git diff --check`；`git diff --cached --check` | PASS，exit0 | `artifact://1206`、`artifact://1214`。 |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-24/discovery-recheck-08-api.md` | PASS，注册、staging 前及交接前身份一致 | 初始、`artifact://1206`、`artifact://1214` 及最终 handoff gate；canonical path/hash 与本报告完全一致。 |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-24/discovery-recheck-08-api.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master --expect-branch fast/discovery-recheck-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-discovery-recheck-master` | PASS；add 与 commit 各自前执行，交接再次检查 | `artifact://1214`、`artifact://1215` 及最终 handoff gate。 |
| `git commit -m 'feat(fast-p): implement 08'`；`git rev-parse HEAD refs/heads/fast/discovery-recheck-master`；`git merge-base --is-ancestor HEAD refs/heads/fast/discovery-recheck-master`；`git show --format=fuller --name-only HEAD` | PASS，exit0；HEAD 与目标分支均为 `59c34a1b6f04af0669a9276dae7c043bca4164f5`，ancestor 检查通过 | `artifact://1215`；提交只包含下列授权 product/test 文件，不含 fast-p 证据。 |

### Actual Standalone Runtime Receipts

证据为 `artifact://1213:269-336`；不是 MockMvc，也不是只引用测试成功摘要。运行在独立 Java11 JVM，真实 loopback Tomcat servlet HTTP、会话拦截与参数转换、真实 MySQL事务/SQL、原 Worker/Gate/Commit/Trace；HTTP 请求计数包括等待异步准备的轮询，因此可随准备速度变化。

```text
RECHECK08_RUNTIME roster=1001 batches=500,500,1 frozenAfterInsert=true processed=1001 prepareProviderHttp=0 auditProviderHttp=0 auditBusinessWrites=0 realTaskBinding=true
RECHECK08_RUNTIME partialFreeze=FAILED/500/notClosed interruptedPrepare=INTERRUPTED preparingCancel=CANCELLED readyCancel=CANCELLED crossRun404=true
RECHECK08_RUNTIME oldManualHttp=MANUAL_APPROVED protected=REVIEW_PROTECTED auditWouldAdmit=1 auditActualAdmitted=0
RECHECK08_RUNTIME cancelCommitOrder=commit-first state=COMPLETED admitted=true projectionOnlyRetry=true providerCalls=0
RECHECK08_RUNTIME cancelCommitOrder=cancel-first state=CANCELLED admitted=false projectionOnlyRetry=false providerCalls=0
RECHECK08_RUNTIME PASS actualServletHttp=true httpRequests=411 isolatedMySQL=true workerGateCommitTrace=true providerCalls=0 runningRecoveryHashAndRequestCount=true productionWrites=0
```

兼容命令 `artifact://1204` 同时重新产生原组件的下列 receipts（本次执行，不借用历史 PASS）：

```text
CHILD07_LEGACY PASS realWorkerSQL=true rule3=true inputContextAbsent=true evidenceAbsent=true outputContextAbsent=true executionClock=2026-10-20T00:00:00Z liveQualification=NOT_MET frozenQualification=MET crossBirthdayOutcomesDiffer=true explicitOrdinaryFrozen=true
CHILD07_RUNTIME PASS actualRegistrationWorkerRulesGateCommitTrace=true realMySQL=true ordinaryPaper=DIRECT_CLEAR eightSteps=true AUDIT_businessWrites=0 REUSE_HTTP=0 originalFetchedAt=true frozenParity=true freeDetailHTTP=0 lateDiagnostic=once academicCancelWire=1 cancelled429Failures=0 cancelCommitBothOrders=true admittedProjectionRetryProviderHTTP=0 projectionAttempts=3 archiveFirstSHA=unchanged
```

### Changed Files

由 `git diff --cached --name-only`（`artifact://1214`）及提交后的 `git show --format=fuller --name-only HEAD`（`artifact://1215`）核对：

- `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckController.kt` — 新建认证 API；typed request、错误状态、查询与 mutation 路由。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt` — 新建固定名单与 task 生命周期协调；canonical spec、异步冻结、start/cancel/sync-retry、reconcile。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` — 同连接 GET_LOCK 注册、精确幂等读取、锁序与原 revision 幂等启动、取消/恢复、分页与真实统计；更新控制 JSON 时保留 root 元数据。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` — 只读 run/items/steps/latest/manifest DTO，分离 before/proposed/current/applied 和同步结果。
- `src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt` — 专用有界准备执行器。
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckControllerTest.kt` — 新建 session/default/actor/枚举/状态/归属 API 契约测试。
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckServiceTest.kt` — 新建 canonical/default/校验/启动前置/全体归属先验证的契约测试。
- `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt` — 追加实际 MySQL 注册锁/事务/并发启动/取消/恢复用例，保留原有回归。
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt` — 新建真实 servlet HTTP + MySQL 全流程；同一验收体可由 standalone `main` 在 JUnit 外启动。

### Failure Handling / Deviations

- 产品范围无偏离；无未列文件修改，无 schema/migration、09 UI、10 backfill、ordinary流水线重构、provider付费调用、生产写入、push/merge/history rewrite。
- 最初两轮39例命令均在新增 FlowIT 的正例 wouldAdmit 断言报错。第一轮添加具体诊断并让 READY 等待真实 prepare task 终态，第二轮明确得到 `SOURCE_ID_CONFLICT`：fixture 把有效身份凭证的 ORCID 错改成了合成 docId。只修正授权 FlowIT 的 fixture，保留真实 `ruleSnapshot().profile.orcidId`；未放宽任何身份、hard blocker、AUDIT 或 wouldAdmit 断言，未修改 Gate/Rules/Worker。最终两条必需命令均在此最后编辑后重新通过。
- standalone 首次进程内 Maven launcher 失败后，改 forked Java launcher解决 classloader 问题，不改实现或测试语义；最终独立运行 exit0。失败进程所属 MySQL 容器已由 Testcontainers 清除（inspect 为 No such container），其唯一遗留 `discovery-recheck-http-*` 临时目录已删除；成功运行关闭 Tomcat/HTTP server/executors/application context/container 后，相同前缀 glob 无残留。
- 日志中的 fixture candidate projection HTTP500 为刻意注入的投影失败，随后通过真实 sync-retry 恢复；非生产 ES 故障。Java11/Tomcat shutdown 出现 reflective-access warning，未影响 exit0。
- 环境最初 `python` 不存在（exit127），所有 identity gate 改用已安装的 `python3` 成功完成。
- 未创建 throwaway 源码或脚本；本报告是用户明确要求的 execution evidence，并故意排除于产品提交。

### Limits

- 使用 disposable MySQL8.0.36 和当前迁移到 v161，未连接生产库。业务 ES transport 使用既有 `VerificationFlowFixture` 的严格 fixture；ALL_MATCHING scan 另经真实 loopback HTTP scroll。未声称真实 ES 集群发布、真实邮件/浏览器交互或生产环境验证。
- provider HTTP 被拒绝并计数为0；未调用任何 live provider、paid detail 或生产凭证。新增运行体验证 cached evidence/无可用原文分支，不声称以此替代外部 provider 或新论文原文解析验收；原组件回归的 archive/ordinary/legacy receipts 另列如上。
- 未跑全仓 JVM 测试或 WAR package；已跑两条计划必需 mysqlIt Maven 命令以及其绑定的全量 JS/语法检查、额外 standalone HTTP runtime。没有 full review/verify/repair/fix skill 自审。

### Freshness

- Plan identity rechecked: YES；SHA-256 保持 `47196d02af0f41bd852216230d91ec446020baa6e19b6e5e9a1b0f8c7e1d440a`。
- Worktree identity rechecked: YES；canonical root、branch、git-dir 未变化。
- Reported commits reachable from target branch: YES；产品提交为目标 worktree HEAD 且为目标分支 ancestor，`artifact://1215`。
- Required commands run this invocation: YES；最终编辑后39例与56例，均0 failures/errors/skips，另 standalone exit0。
- Historical evidence used only as baseline: YES；没有使用 child07 旧报告替代本次执行；上述 child07 receipts 来自本次兼容命令。
- Product/index cleanliness: 产品提交后 product/test 无未提交修改，index 干净；仅保留 controller 原有 ledger 修改与 child08 evidence 目录。本报告不修改 ledger、不纳入产品提交。

### Remaining Blocker

None known within the approved child08 implementation scope.

### Next Action

Controller 对 `59c34a1b6f04af0669a9276dae7c043bca4164f5` 进行 fresh child08 独立轻量核验并按 fast-p 管理证据。此结果为 READY_FOR_VERIFICATION，不是独立 PASS，不授权09 UI或10 backfill。

## Controller closing record — Epoch1
- Independent verifier Recheck08Verifier returned LIGHT_PASS_WITH_NOTES/COMPLETE_CHILD on f37ff178554cb19b3264f81ed4deac7a6b43bd8c..59c34a1b6f04af0669a9276dae7c043bca4164f5; all four gates PASS. Fresh required commands39/56JVM and each1827JS, zero failures/errors/skips; separate actualHTTP414/MySQL/originalpipeline invocation passed. O-08-E1-01 records existing Tomcat reflective-access shutdown warning only. Actual repair count0; this closing record does not claim controller product verification or whole-system review.
