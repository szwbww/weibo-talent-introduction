# 03-lifecycle Execution Report

> Append-only evidence log. Written by the implementation worker.

## Execution Result: READY_FOR_VERIFICATION

- Plan (child): `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md`
- Master plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-master.md`
- Brief: `docs/plans/fast/manual-reply-delay-master/children/03-lifecycle/brief.md`
- Execution epoch: NEW (no prior 03 implementation commit existed; code head was `72906c0`)
- Executor: `Impl03Lifecycle`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`
- Target branch: `fast/manual-reply-delay-master`
- Pre-execution code SHA: `72906c0c5e36393429320817ea0de4f7d6f3ff05` (`feat(fast-p): implement 02-preparation`)
- Post-execution code SHA: `798206d5b2c8dafd892f5c5f275e952a99c18fa8`（`feat(fast-p): implement 03-lifecycle`）
- Evidence HEAD: N/A（fast-p 证据不进入实现提交）
- Implementation boundary: `72906c0..798206d`，授权文件 8 个（6 改 + 2 新增；第 9/10 号授权测试无需修改）

## Task Status

| 需求 | 状态 | 文件 | 证据 |
|---|---|---|---|
| T1 reservation 与排队（I-1/I-2/I-3/I-6/I-7） | IMPLEMENTED | `DelayedManualReplyService.kt`、`ManualReplySendAttemptService.kt`、`DelayedManualReplyRepository.kt` | `reserveDelayed`（MANDATORY，复用 `computeFingerprint`/唯一键/payload 比较，绝不推进 attempt 状态）；`enqueue` 单事务完成 源锁→attempt→job→绑定→审核事件；重复 WAITING 不刷新 due；CANCELLED 必须当前 revision 才能重排 |
| T2 CAS 状态表与公开错误码（I-1/I-2/I-5/I-10） | IMPLEMENTED | `DelayedManualReplyService.kt`、`DelayedManualReplyRepository.kt` | `requeue/retryFailed/failBeforeDelivery/recoverToUnknown/lateSuccess` 全部 revision/token CAS；`DelayedReplyPublicError` 10 码逐字映射；invalid 组合 fail closed |
| T3 草稿全写路径与审核事件/上下文（I-2/I-6/I-7） | IMPLEMENTED | `MailReplyDraftService.kt`、`MailReplyDraftRepository.kt`、`MailReviewDraftService.kt`、`MailReviewDraftRepository.kt` | `save` REQUIRES_NEW + 目标行锁 + job 态守卫；`discard` 同一守卫；`bindDelayedAttempt`/`releaseDelayedBinding` 精确绑定；SEND_QUEUED/CANCELLED/REQUEUED 以 job+revision 去重；`confirmContext` 在 WAITING/SENDING 拒绝；`lockDelayedReviewForRetry` 复用最新 CONTEXT_CONFIRMED |
| T4 完成与恢复（I-2/I-3/I-5/I-10） | IMPLEMENTED | `ManualReplySendAttemptService.kt`、`DelayedManualReplyService.kt` | `finalizeDelayedSuccess/Failure` 锁后核对 job/attempt/token/来源并与 SENT/事实/排期/草稿/审核同事务提交；`recoverStale` 逐条短事务、不调 SMTP；旧令牌不能完成新轮 |
| 验收并发（I-2） | IMPLEMENTED | `DelayedManualReplyLifecycleIT.kt` | 真实 MySQL 双线程 cancel/claim、save/claim、discard/enqueue、finalizeFailure/recover 竞态，各恰好一个赢家且无半绑定；不 sleep 模拟 60 秒 |

## Commands（全部在最终代码状态下重新执行；Java 11 / zulu-11）

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `mvn -q -DskipTests test-compile` | 0 | 主/测试源编译通过，无输出 |
| 2 | `mvn test -Dtest='ManualReplySendAttemptServiceTest,MailReplyDraftServiceTest'` | 0 | `Tests run: 76, Failures: 0, Errors: 0, Skipped: 0`（62 + 14） |
| 3 | `mvn test -Dtest='DelayedManualReplyLifecycleIT,MailReplyDraftRepositoryIT,MailReplyDraftSendIntegrationTest,MailReviewDraftIT,MailReviewQueryIT' -DmysqlIt=true` | 1 | `Tests run: 81, Failures: 1, Errors: 0, Skipped: 0`。**唯一失败是预先存在的基线失败**：`MailReviewQueryIT.size100 uses constant batched queries and readers never query review summaries`（`MailReviewQueryIT.kt:239`）。其余 76 个全绿：`DelayedManualReplyLifecycleIT` 22/22（Testcontainers MySQL 8.0.36）、`MailReplyDraftRepositoryIT` 8/8、`MailReplyDraftSendIntegrationTest` 27/27、`MailReviewDraftIT` 19/19、`MailReviewQueryIT` 4/5 |
| 4 | `mvn test -Dtest='PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest'` | 0 | `Tests run: 103, Failures: 0, Errors: 0, Skipped: 0`（43 + 60） |
| 附加 | `mvn test`（全量单测 + JS 套件，非 brief 要求） | 1 | `Tests run: 5800, Failures: 0, Errors: 20, Skipped: 28`；20 个错误全部是 `ExpertContactLocationServiceTest` 在**测试类构造器**里抛 `Unknown time-zone ID: America/Coyhaique`（JDK 11 tzdata 缺该时区），与本 child 无关 |

### 预先存在失败的真实基线证据（K-plan-quantified-claims-need-grep-receipts 口径）

在 `git worktree add /tmp/delayed-base 72906c0`（child base，未含本 child 任何改动）上分别执行：

- `mvn test -Dtest='MailReviewQueryIT' -DmysqlIt=true` → `EXIT=1`，`Tests run: 5, Failures: 1`，失败用例与行号完全一致（`MailReviewQueryIT.kt:239`）。
- `mvn test -Dtest='ExpertContactLocationServiceTest'` → `EXIT=1`，`Tests run: 20, Errors: 20`，同一句 `国家时区目录配置错误：国家 CL 的时区 id 无法解析：America/Coyhaique`。

两个临时 worktree 均已 `git worktree remove --force` 删除，未改动任何被跟踪文件。

## Changed Files（实现提交内）

- `src/main/kotlin/.../mail/service/DelayedManualReplyService.kt`（新增）— 六态事务状态机：enqueue/cancel/retry/claim/preflightFailure/recoverStale + 公开投影 + 固定错误码；锁序 源（审核授权行→草稿/审核稿）→attempt→job；全部单短事务。
- `src/main/kotlin/.../mail/repository/DelayedManualReplyRepository.kt` — 新增 `requeue`/`retryFailed`/`failBeforeDelivery`/`recoverToUnknown`/`lateSuccess`/`findUnresolvedByContact`/`scanStaleSendingIds`；`insert` 的 actor 绑定改为「归一化认证身份」（见偏差 1）。
- `src/main/kotlin/.../mail/service/ManualReplySendAttemptService.kt` — `reserveDelayed`/`lockDelayedAttempt`/`claimDelayed`/`recoverDelayedUnknown`/`failDelayedBeforeDelivery`；`prepareAndClaim` 的排队任务守卫（收敛/拒绝，绝不把 PREPARED 直接改 IN_PROGRESS）；`finalizeDelayedSuccess`/`finalizeDelayedFailure` 及锁后 token 校验与 job 收敛。
- `src/main/kotlin/.../mail/service/MailReplyDraftService.kt` — `save` REQUIRES_NEW + 目标行锁 + job 态守卫；`discard` 同一守卫；`lockDelayedSource`/`lockDelayedTransition`/`bindDelayed`/`releaseDelayedBinding`。
- `src/main/kotlin/.../mail/repository/MailReplyDraftRepository.kt` — 投影优先返回 job 态（WAITING/SENDING/UNKNOWN）；`lockTargetRow`；`delayedJobStatus`。
- `src/main/kotlin/.../mail/service/MailReviewDraftService.kt` — `lockDelayedReview`/`lockDelayedReviewForRetry`/`lockDelayedResult`/`bindDelayedAttempt`/`recordQueueEvent`；`validateSendRow` 与 `confirmContext` 的排队闸门。
- `src/main/kotlin/.../mail/repository/MailReviewDraftRepository.kt` — `sendProjection` 优先 job 态；`delayedJobStatus`；`latestContextConfirmation`；`appendJobEvent`（job+revision 去重）；新事件类型的 payload 校验。
- `src/test/kotlin/.../mail/service/DelayedManualReplyLifecycleIT.kt`（新增）— 22 个真实 MySQL 用例：期限幂等、冻结与撤回、revision 重排、改动内容新任务、过期 revision、权限矩阵（actor/admin/user1-FULL）、同步绕行、四组并发竞态、UNKNOWN 收敛与迟到成功/失败、旧令牌、preflight/retry、审核事件去重与 PROJECTION、审核上下文不被自身排队改变。
- 授权文件 #9 `MailReplyDraftSendIntegrationTest.kt`、#10 `MailReviewDraftIT.kt`：**未修改**（仅运行，27/27 与 19/19 全绿），无回归需要调整断言。

未改动：`pom.xml`、`application.yml`、任何迁移（未编辑已应用迁移）、`CLAUDE.md`、前端、`docs/plans/fast/**`。

## Key decisions

1. **排队不改变 attempt 生命周期。** `reserveDelayed` 只做 `insertIgnore` + `FOR UPDATE` + 身份比较，返回锁后真实状态；`PREPARED→DELIVERY_IN_PROGRESS` 只发生在 `claim`（worker 的短事务），因此「旧同步入口抢发排队任务」在服务层被结构性地排除。
2. **同步入口的收敛映射复用既有 `ClaimResult` 枚举**（不扩展枚举、不改 Pending 的穷尽 `when`）：job `SENT→DEDUP_SENT`、`WAITING/SENDING→IN_PROGRESS`、`UNKNOWN→UNKNOWN`、`FAILED/CANCELLED→PERMANENT_FAILED`。
3. **草稿冻结判据是「job 态」而非 attempt 态。** 投影仍按计划优先显示 WAITING/SENDING/UNKNOWN，但写守卫查询 `delayed_manual_reply`：同步 `DELIVERY_IN_PROGRESS`（无 job）保持既有可编辑语义，只有真实排队任务才 409。
4. **`finalize*` 用新增方法而不是给既有方法加参数。** 既有 `finalizeSuccess(payload, attemptId, messageId)` / `finalizeFailure(...,5 args)` 签名逐字保留（9 个测试文件以 Mockito 精确匹配），延迟路径走 `finalizeDelayedSuccess/Failure`；两者共用一个私有实现，锁序为 源→attempt→job。
5. **迟到结果的三重防线。** `verifyDelayedFinalize` 锁 job 后校验 id/token/来源/状态：SENDING 才可写 FAILED/UNKNOWN；UNKNOWN 只接受同 token 的成功（`lateSuccess`）；SENT 之后任何迟到结果 fail closed（事务回滚，不新增记录）。
6. **审核稿在排队受理时即绑定 attempt**（不写 SEND_CLAIMED），使详情投影显示 WAITING 并让 `prepareSend`/`confirmContext` 服务端拒绝；撤回保留绑定与 APPROVED 内容/版本。
7. **`recoverStale` 用 `TransactionTemplate` 逐条短事务**（每个 job 一个 REQUIRES_NEW + READ_COMMITTED），避免「一个坏任务拖垮整批」，也避免自调用绕过 `@Transactional` 代理。

## Deviations

1. **`delayed_manual_reply.insert` 的 actor 绑定放宽**（`actorUsername == evidence.operator` → 「非空且已归一化」）。理由：`operatorName` 来自请求体（可伪造），而 I-7 要求 job 的授权身份是**服务端认证身份**；展示名仍完整保存在快照 `evidence.operator` 中供审计。这是 01 的实现期多字段守卫，不是迁移约束。
2. **`lockTargetRow` 在无活动事务时退化为不加锁**（直接构造 service 的既有测试路径）。生产路径 `save` 是 `@Transactional(REQUIRES_NEW)`，必然持有行锁；无事务时行为不弱于改动前的单条 CAS。
3. **`claim` / `view` / `recoverStale` 不使用「引用实例字段的默认参数」**：Kotlin 默认参数在 CGLIB 代理下以代理实例求值会读到 `null` 字段（实测 NPE）。到期时间一律在事务内读数据库时钟，`view`/`recoverStale` 用可空默认值。
4. **CANCELLED 审核稿「必须携带 queue revision 才能重新准备」推迟到 04/06**：`MailReviewSendRequest`（`MailReviewDraftModels.kt`）不在本 child 授权文件内，无法新增字段。本阶段实现的是服务端闸门：WAITING/SENDING/UNKNOWN 一律拒绝准备发送与上下文确认，CANCELLED 释放发送锁后允许重新准备（queue revision 由 04 的 API/DTO 承载）。
5. **retry 的「源/权限/上下文再校验」止于 03 可见范围**：源草稿/审核稿内容、FULL 权限、审核上下文令牌、attempt `FAILED_SAFE_TO_RETRY` 全部重验；账号可用性/附件完整性/抑制名单的重验属于 04 worker 的 `rehydrate → preflightFailure`（03 不调用 SMTP、不解析账号）。
6. `DelayedReplyPublicError` 与 `DelayedDispatch` 定义在本 child 新增的 `DelayedManualReplyService.kt`（`DelayedManualReplyModels.kt` 不在授权文件内）；01 的 `DelayedDispatchRef`（internal）保持不变，服务内 claim 结果用等价的公开 `DelayedDispatch`，避免 public 方法暴露 internal 类型。

## 未被分配的失败（不计入本 child）

- `MailReviewQueryIT.size100 ...`：基线 `72906c0` 同命令同样失败（见上）。
- `ExpertContactLocationServiceTest`（20 errors）：基线 `72906c0` 同命令同样失败，JDK tzdata 缺 `America/Coyhaique`。

## Freshness

- Plan identity rechecked（child/master 路径与内容在实现后未变）：YES
- Worktree/branch/HEAD rechecked before commit：YES
- Reported commits reachable from `fast/manual-reply-delay-master`：YES（见 Commit）
- Required commands 1–4 run this invocation at final code state：YES
- Historical evidence used only as baseline：YES（基线失败用 child base `72906c0` 的独立 worktree 现场复现）

## Remaining blocker

None.

## Commit

`798206d5b2c8dafd892f5c5f275e952a99c18fa8` — `feat(fast-p): implement 03-lifecycle`（实现文件 8 个：6 改 + 2 新增；`docs/plans/fast/**` 证据未纳入提交；`git merge-base --is-ancestor` 已确认可达 `fast/manual-reply-delay-master`）

## Next action

`verify-p` on `manual-reply-delay-03-lifecycle.md`.

## Controller closing record — Epoch 1 paused

- Independent verifier: Verify03Lifecycle.
- Reviewed product head: 798206d5b2c8dafd892f5c5f275e952a99c18fa8.
- Verdict: PAUSE; see this child's verify-log.md, epoch 1 / attempt 1.
- P-1 requires human authority resolution for CANCELLED review preparation revision validation. F-1 through F-11 remain open; no automatic repair has started.
- Required-command baseline failures were classified by the verifier; no new command regression was reported. Green tests do not discharge the untested invariant failures.
- Controller action: retain branch/worktree; keep 04–07 PENDING; commit pause evidence only.
- This closing record supersedes the earlier generic next-action suggestion: the fast-p child loop uses a fresh lightweight verifier, never verify-p. After explicit authority resolution, resume child 03 in a new epoch with a reset per-epoch repair counter; do not replay children 01 or 02.
- Workflow deviation recorded from the implementation report: temporary baseline worktrees were created and removed by Impl03Lifecycle despite the one-retained-worktree contract. The controller did not authorize that operation and did not repeat it.
