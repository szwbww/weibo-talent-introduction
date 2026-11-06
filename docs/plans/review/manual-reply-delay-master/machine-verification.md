# Aggregate Machine Verification — manual-reply-delay-master

## Epoch 1 — 2026-11-06 — Verification Result: FAIL

- Reviewer: `/root/aggregate_review_epoch1`; phase `aggregate/master`; COMPLETE; fresh fork_turns=none, created after code commit.
- Convergence: INITIAL; prior aggregate report/repair N/A.
- Findings: 3 P1 (V-1/V-2/V-4), 1 mandatory P2 (V-3), plus mandatory browser evidence BLOCKED B-1.
- Manual acceptance: A-1–A-12 PENDING. No human result/sign-off inferred.
- Repair planning: DRAFT_READY; not executed.
- Reviewer modified no product/test/approved plan/review evidence/index/HEAD; wrote only the exact repair.md through repair-p. Controller persists this complete report and evidence.

### Identity and boundary

All relative paths below resolve under W = `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`.

| Item | Identity |
|---|---|
| Branch | fast/manual-reply-delay-master |
| MASTER_BASE_SHA | a224623c8dc17bdad1ce80e1c4391637bf7ee5ca |
| final_code_head | 83ec3e3bdf620f80198f73ae496f78aaf75625f4 |
| evidence_head at review | 17e8563aa2a0d966cb2cb4a7a2f37a0fc657fdd5 |
| Governing master | docs/plans/2026-11-04/manual-reply-delay-master.md |
| Governing SHA256 | 4fa1f45fc2830aea7c138e3da44a60d5ea767fa48a01239ca01ed01989a89a2e |
| Recorded master commit | 8a51021ca463da1e3ea279bf5a94e998267751bf |
| Invoked SHA256 | 9df55143ff99d59e2a958e6703930a8ed69231b9e6b195ed749b7b2c8eb8ff5d |
| Identity state | AMENDMENT_RECORDED |
| Fast ledger | docs/plans/fast/manual-reply-delay-master/ledger.md; SHA256 76dadc5a2039d622ffdb1cd3423bc9f2629a024f4e4dc37b539560528d61a31b |
| Fast handoff | docs/plans/fast/manual-reply-delay-master/human-review-handoff.md; SHA256 82089ede965eb02123c4f26bebb806c1a57c93b1fe18fb0c54141d067d14861c |
| Resolution | DISCOVERED_FROM_GIT_WORKTREES; discovery exit0 SELECTED exactly one matching worktree, 9 terminal children |
| Report | docs/plans/review/manual-reply-delay-master/machine-verification.md |
| Post-repair evidence mode | N/A; no repair execution |

The complete governing master, all ordered child plans, briefs, execution/verify/fix logs, fast ledger/handoff and exact governing diff in selected review ledger were read. Child light PASS is not aggregate proof. Governing A31: Master A-5/I-1/I-2/I-9 and sequential bounded shared-file gates; five-file08 resolves historical06 expiry-closes-confirmation while preserving native409/all12 acceptance items and joint release. Approval HUMAN:2026-11-06「按总 A-5 补齐」. Full A1–A32 before/after/rule/reason/approval records remain in hashed fast ledger.

Child prefix: `docs/plans/2026-11-04/manual-reply-delay-`.

| Order | Child | Approved identity |
|---|---|---|
| 1 | 01-storage.md | 05342b4be63bbc7d9b30416ae73ada2d44b562df |
| 2 | 02-preparation.md | ab3ad56713a74664b0f34f66a138206cb14666ea |
| 3 | 03-lifecycle.md | 0bfe5b07c85e76f85c43a31d30fde8b37f30b9b9 |
| 4 | 04-worker-api.md | de076f6413b65809ca13610c0455965134056dcb |
| 5 | 05-query.md | baa9ba43a9ddf8c39ff2feae1c91c3c76fea2acb |
| 6 | 06-frontend.md | a5c1ddb90e92b5aef7bf8f16b031dd4d51c49b80 |
| 7 | 07-source-contract.md | 525aeeb9e97241d5ad044f5662c75194db1a5944 |
| 8 | 07-activation.md | 8a51021ca463da1e3ea279bf5a94e998267751bf |
| 9 | 08-deadline-confirmation.md | 8a51021ca463da1e3ea279bf5a94e998267751bf |

### Amendment contract matrix

Exact before/after commit identities and original approval text remain authoritative in the hashed fast ledger A1–A32 table. Standing authority is not expanded beyond recorded files.

| Amendment | Rule/reason/authorized scope | Human approval | Result |
|---|---|---|---|
| A1 | I-5; move RAG persistence to finalizeSuccess; RagSendBridgeTest seam adaptation | 2026-11-04 授权修订02 | PASS;11 tests |
| A2/A3/A4 | I-3/I-6;03 owns review cancellation revision DTO,07 consumes; MailReviewDraftModels | 2026-11-05「在 03 补齐审核请求代次」and continuous execution | PASS |
| A5/A6 | I-2/I-6; RepositoryIT raw writes use real READ_COMMITTED |「授权必要测试兼容修订并持续执行」| PASS;8 tests; production transactions unchanged |
| A7/A8 | I-4/I-6/I-7; Models persists original LEGACY input; old frozen delivery retained, missing restore input422 |「补齐原输入并继续」| PASS |
| A9/A10 | A5/I-6; SnapshotTest original request/meeting fixtures | A5 applied to concrete mismatch | PASS;14 tests |
| A11/A12 | I-7/I-10; readVerifiedFile private→internal only, body unchanged |「授权本次及同类内部接口修订」| PASS;attachment28/QueryIT |
| A13/A14 | A11/I-7/I-10; two repositories bounded read-only findForDelayedPage,max100,original mapper | A11 specific missing interfaces | PASS |
| A15/A16 | I-6/I-7; Controller/Test restore-only validated inbound source |「授权本次及同类契约补齐」| PASS;Controller16/native restore |
| A17/A18 | A5/A15/S-4;two JS fixtures clock/SVG/cleanup; actual resource count19→20 | A5/A15 | PASS |
| A19/A20 | A5/06T3;five fixture dependencies/single-turn drain; retire obsolete READ no-refresh assertion | A5 concrete failures | PASS;affected445 |
| A21/A22 | A5/06T3;auth Map;remove obsolete review focus-only-access assertion | A5 concrete failures | PASS;auth/content/CAS/reload retained |
| A23/A24 | O-1/I-3/I-4/I-6/I-7;ten-file source prerequisite before activation;all acceptance retained | A15/continuous execution | Authority PASS;V-4 separate |
| A25/A26 | A5/S-C2;remove old06 cache key coupling,retain20refs/DOM/CSS | A5 | PASS |
| A27/A28 | I-1/I-3/I-5/I-7/I-11;strict review revision,pure attempt reader,defaultfalse queued-read | A15/continuous execution | PASS;E2E29/Pending49 |
| A29/A30 | A5;WorkerTest uses real defaultfalse final config,override precedence | A5 concrete Worker mismatch | PASS;9 tests |
| A31/A32 | MasterA-5/I-1/I-2/I-9;five-file08 supersedes historical expiry close,retains native409/all12/joint release | 2026-11-06「按总 A-5 补齐」| PASS;three-host native59→61 POST409→GET |

### Cumulative 58-file scope matrix

Complete combined diff inspected. All58 product/test paths match governing authorized lists; no unauthorized file. PASS here means authorization/scope only, not erasure of behavioral findings.

Prefixes: K=`src/main/kotlin/com/weibo/talentintroduction/`; T=`src/test/kotlin/com/weibo/talentintroduction/`; S=`src/main/resources/static/`; J=`src/test/js/`.

| File | Authorized child/amendment | Result |
|---|---|---|
| Kconfig/ManualReplyDelayProperties.kt | 01 | PASS |
| Kmail/controller/DelayedManualReplyController.kt | 04,06 A16,07-source | PASS |
| Kmail/controller/MailReviewDraftController.kt | 07-activation A28 | PASS |
| Kmail/controller/MailboxConversationController.kt | 05,07-activation | PASS |
| Kmail/controller/UnmatchedInboundMailController.kt | 07-activation | PASS scope;V-3 |
| Kmail/repository/DelayedManualReplyRepository.kt | 01,03,05 | PASS scope;V-1 |
| Kmail/repository/MailReplyDraftRepository.kt | 03,05 A14 | PASS |
| Kmail/repository/MailReviewDraftRepository.kt | 03,05 A14 | PASS |
| Kmail/repository/MailboxConversationRepository.kt | 05 | PASS |
| Kmail/service/DelayedManualReplyModels.kt | 01,02,04 A8,07-source | PASS scope;V-2 |
| Kmail/service/DelayedManualReplyService.kt | 03,07-source | PASS |
| Kmail/service/DelayedManualReplyWorker.kt | 04 | PASS |
| Kmail/service/ExpertMailPermissionGuard.kt | 04 | PASS |
| Kmail/service/MailReplyDraftService.kt | 03 | PASS |
| Kmail/service/MailReviewDraftModels.kt | 03 A3 | PASS |
| Kmail/service/MailReviewDraftService.kt | 03,07-activation A28 | PASS |
| Kmail/service/MailReviewSendService.kt | 07-activation | PASS |
| Kmail/service/MailboxConversationService.kt | 05 | PASS |
| Kmail/service/ManualReplySendAttemptService.kt | 02,03,07-activation A28 | PASS |
| Kmail/service/OutboundAttachmentService.kt | 05 A12 | PASS;internal visibility only |
| Kmail/service/PendingMailOperationService.kt | 02,04,07-source,07-activation | PASS scope;V-2 |
| src/main/resources/application.yml | 07-activation | PASS |
| src/main/resources/db/migration/V174__create_delayed_manual_reply.sql | 01 | PASS |
| Sapp.js | 06 | PASS scope;V-4 chain |
| Sindex.html | 06,07-source,08 | PASS |
| Smailbox-chat.css | 06 | PASS |
| Smailbox-chat.js | 06,07-source,08 | PASS scope;V-4 |
| Smailbox-readonly.js | 06 | PASS |
| JauthFlow.test.js | 06 A22 | PASS |
| JexpertAccessWorkspace.test.js | 08 | PASS |
| JmailReviewAdmin.test.js | 06,08 | PASS |
| JmailReviewReader.test.js | 06 A22 | PASS |
| JmailboxChatBehavior.test.js | 06 A18 | PASS |
| JmailboxChatStyle.test.js | 06,07-source A26 | PASS |
| JmailboxDelayedReply.test.js | 06,07-source,08 | PASS scope;missing V-4 discriminator |
| JmailboxMailReadAccess.test.js | 06 A20 | PASS |
| JmailboxOutboundAttachments.test.js | 06 A20 | PASS |
| JmailboxReadOnly.test.js | 06 A18/A20 | PASS |
| JmailboxReplyIdentity.test.js | 06 A20 | PASS |
| JmailboxServerDrafts.test.js | 06 | PASS |
| JmailboxSuspension.test.js | 06 A20 | PASS |
| JmeetingConfirmationIntegration.test.js | 06 A20 | PASS |
| Tcampaign/OperatorStatusWriteSeamGuardTest.kt | 05 | PASS scope;mandatory V-3 |
| Tcampaign/repository/FlywayMigrationIntegrationTest.kt | 01 | PASS |
| Tmail/RagSendBridgeTest.kt | 02 A1 | PASS |
| Tmail/controller/DelayedManualReplyControllerTest.kt | 04,06 A16,07-source | PASS |
| Tmail/repository/DelayedManualReplyQueryIT.kt | 05 | PASS scope;V-1 mixed rules untested |
| Tmail/repository/MailReplyDraftRepositoryIT.kt | 03 A6 | PASS |
| Tmail/repository/MailboxConversationRepositoryIT.kt | 05 | PASS |
| Tmail/service/DelayedManualReplyLifecycleIT.kt | 03,07-source | PASS |
| Tmail/service/DelayedManualReplySnapshotTest.kt | 02,04 A10,07-source | PASS |
| Tmail/service/DelayedManualReplyWorkerTest.kt | 04,07-activation A30 | PASS |
| Tmail/service/ExpertMailPermissionGuardTest.kt | 04 | PASS |
| Tmail/service/ExpertMailReadAccessIT.kt | 01 | PASS |
| Tmail/service/InboundPendingOwnerIT.kt | 01 | PASS scope;optional O-3 |
| Tmail/service/ManualReplyDelayEndToEndIT.kt | 07-activation | PASS |
| Tmail/service/ManualReplySendAttemptServiceTest.kt | 02 | PASS |
| Tmail/service/PendingMailOperationServiceTest.kt | 02,07-activation | PASS |

Machine-readable mapping `/tmp/aggregate-epoch1-scope.json`, SHA256 `2a7c13a0912531194e3730734ba925c9cea6f09962a0e15908adcbf3d15a45a9`.

### Environment and fresh commands

All Maven explicitly uses `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`: Zulu11.0.15, Maven3.9.11, Node25.7.0, Docker29.8.2. Actual runtime uses newer installed JDK11 `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java`, not default Java25. Patch difference retained; optional old-tzdata full JVM suite not claimed successful.

Explicit disposable test datasource:

```text
DB_URL=jdbc:mysql://127.0.0.1:33461/aggregate_review?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=aggregate-local-only
```

No default development/production DB. ITs also use actual Testcontainers; mysqlIt/migrationIt enabled.

#### C-1 compile

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -DskipTests test-compile
```

Exit0. `/tmp/aggregate-epoch1-compile.log` is empty due quiet success; no tests executed/count inferred.

#### C-2 original combined gate

```sh
mvn test '-Dtest=PendingMailOperationServiceTest,ManualReplyDelayEndToEndIT,DelayedManualReplySnapshotTest,DelayedManualReplyControllerTest,DelayedManualReplyWorkerTest,DelayedManualReplyLifecycleIT,DelayedManualReplyQueryIT,MailReplyDraftServiceTest,MailReplyDraftControllerTest,MailReplyDraftRepositoryIT,MailReplyDraftSendIntegrationTest,MailReviewDraftIT,MailReviewSendIT,MailReviewQueryIT,!MailReviewQueryIT#size100*,FlywayMigrationIntegrationTest,MailboxConversationRepositoryIT' -DmysqlIt=true -DmigrationIt=true
```

Exit1;355 run/354 pass/1 failure/0 error/0 skipped;1462.56s. Only failure MailReviewSendIT:347 expected Subject,actual Re: Inbound (O-1). Original explicit size100 exclusion retained; later individually executed, not represented as skip or pass.

| Class | Run | Failure/error/skip |
|---|---:|---|
| DelayedManualReplyQueryIT | 8 | 0/0/0 |
| MailReviewQueryIT except size100 | 4 | 0/0/0 |
| MailboxConversationRepositoryIT | 41 | 0/0/0 |
| MailReplyDraftRepositoryIT | 8 | 0/0/0 |
| MailReplyDraftControllerTest | 6 | 0/0/0 |
| DelayedManualReplyControllerTest | 16 | 0/0/0 |
| ManualReplyDelayEndToEndIT | 29 | 0/0/0 |
| DelayedManualReplyWorkerTest | 9 | 0/0/0 |
| PendingMailOperationServiceTest | 49 | 0/0/0 |
| DelayedManualReplySnapshotTest | 14 | 0/0/0 |
| MailReviewDraftIT | 19 | 0/0/0 |
| MailReplyDraftSendIntegrationTest | 27 | 0/0/0 |
| DelayedManualReplyLifecycleIT | 44 | 0/0/0 |
| MailReplyDraftServiceTest | 14 | 0/0/0 |
| MailReviewSendIT | 18 | 1/0/0 |
| FlywayMigrationIntegrationTest | 49 | 0/0/0 |

Flyway49/49,792.343s,actual execution. Log `/tmp/aggregate-epoch1-gates/command-1.log`, SHA256 `920950713f06a064451bb4f6f50af75a6a79819425332ab1b841e3d8c9ecb54d`;16 XML in reports-1/.

#### C-3 uncovered required ranges plus disclosed optional diagnostics

Requested selection:

```sh
mvn test '-Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTrustWorkbenchTest,ExpertMailPermissionGuardTest,MailboxConversationRepositorySqlCompatTest,OperatorStatusWriteSeamGuardTest,OutboundAttachmentFlowTest,OutboundAttachmentServiceTest,MailboxReadAccessProjectionTest,MailboxAttachmentServiceTest,RagSendBridgeTest,InboundPendingOwnerIT,ExpertMailReadAccessIT,MailReviewQueryIT#size100*' -DmysqlIt=true
```

Actual command:

```sh
mvn surefire:test exec:exec@node-test exec:exec@node-check-app exec:exec@node-check-task-modal-runtime '-Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTrustWorkbenchTest,ExpertMailPermissionGuardTest,MailboxConversationRepositorySqlCompatTest,OperatorStatusWriteSeamGuardTest,OutboundAttachmentFlowTest,OutboundAttachmentServiceTest,MailboxReadAccessProjectionTest,MailboxAttachmentServiceTest,RagSendBridgeTest,InboundPendingOwnerIT,ExpertMailReadAccessIT,MailReviewQueryIT#size100*' -DmysqlIt=true
```

Exit1;225 run/220 pass/3 failure/2 error/0 skipped;110.48s.

| Class | Run | Failure/error/skip |
|---|---:|---|
| MailReviewQueryIT#size100* | 1 | 1/0/0;O-2 |
| MailboxConversationRepositorySqlCompatTest | 2 | 0/0/0 |
| OutboundAttachmentFlowTest | 9 | 0/0/0 |
| OutboundAttachmentServiceTest | 28 | 0/0/0 |
| ExpertMailReadAccessIT | 16 | 0/0/0 |
| ManualReplySendAttemptServiceTest | 62 | 0/0/0 |
| MailboxReadAccessProjectionTest | 9 | 0/0/0 |
| ExpertMailPermissionGuardTest | 11 | 0/0/0 |
| InboundPendingOwnerIT | 8 | 1/2/0;O-3 |
| MailboxAttachmentServiceTest | 7 | 0/0/0 |
| PendingMailOperationServiceTrustWorkbenchTest | 60 | 0/0/0 |
| RagSendBridgeTest | 11 | 0/0/0 |
| OperatorStatusWriteSeamGuardTest | 1 | 1/0/0;V-3 |

Lifecycle equivalence: C-1 completes current compile/test-compile; C-2 normal test lifecycle compiles current unchanged source again. C-3 reuses current outputs, no parallel Maven target writers. pom.xml:181–240 maps Surefire and three exec goals. C-2/C-3 stop at Surefire failure, so the later Node goals were NOT executed; standalone fresh Node/app/task-modal syntax below supplies those gates. No duplicate Flyway rerun solely to replay child acceptance. Combined C-2+C-3:580 run/574 pass/4 failure/2 error/0 framework skips. Optional InboundPendingOwnerIT/ExpertMailReadAccessIT full classes do not grant repair scope.

Log `/tmp/aggregate-epoch1-gates/equivalent-2.log`, SHA256 `0fb3f7851d3d15af95cb03e38d74fb4bafd818393170aef48826e084f34622b1`;13 XML in equivalent-reports-2/.

#### Required command coverage mapping

| Original required range | Fresh evidence |
|---|---|
| 01 compile | C-1 |
| 01 Flyway migrationIt=true | C-2;49/49 |
| 02 Pending/Trust/Attempt/Snapshot | C-2 49/14;C-3 60/62 |
| 02 DraftSend mysqlIt | C-2;27/27 |
| 03 Attempt/DraftService | C-3 62;C-2 14 |
| 03 Lifecycle/DraftRepo/DraftSend/ReviewDraft/fullReviewQuery mysqlIt | C-2 44/8/27/19/4;C-3 size1001 actual failure |
| 03 Pending/Trust | C-2 49;C-3 60 |
| 04 Worker/Controller/PermissionGuard/Snapshot | C-2 9/16/14;C-3 11 |
| 04 Lifecycle mysqlIt | C-2 44 |
| 05 Query mysqlIt | C-2 8 |
| 05 MailboxRepo mysqlIt/explicit disposable datasource | C-2 41 |
| 05 SqlCompat/WriteSeam/attachment/read-only | C-3;V-3 retained |
| 06 Controller/focused/affected/fullJS/syntax | C-2 16;N groups |
| 07-source Snapshot/Controller/Worker/Pending/Lifecycle/focused/fullJS/syntax | C-2 14/16/9/49/44;N groups |
| 07-activation E2E/originaldraft-review/Flyway/Mailbox/opt-in | C-2 full combined,C-3 missing ranges |
| 08 exactsix/fullJS/syntax/three-hostnative | N-2/N-5/N-6/R-3 |

#### Node gates

| ID | Exact command | Exit;run/pass/fail/skip |
|---|---|---|
| N-1 | `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailReviewAdmin.test.js src/test/js/mailboxChatStyle.test.js` | 0;185/185/0/0 |
| N-2 | `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailReviewAdmin.test.js src/test/js/expertAccessWorkspace.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxServerDrafts.test.js` | 0;238/238/0/0 |
| N-3 | `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailboxChatStyle.test.js` | 0;106/106/0/0 |
| N-4 | `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxMailReadAccess.test.js src/test/js/mailboxOutboundAttachments.test.js src/test/js/mailboxReplyIdentity.test.js src/test/js/mailboxSuspension.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/authFlow.test.js src/test/js/mailReviewReader.test.js` | 0;445/445/0/0 |
| N-5 initial sandbox | `node --test src/test/js/*.test.js` | 1;2103/2102/1/0;target output EPERM |
| N-5 approved escalation rerun | `node --test src/test/js/*.test.js` | 0;2103/2103/0/0 |
| N-6 | individual `node --check` on all17 changed JS paths above, plus src/main/resources/static/trust-reply-workbench.js | 18 commands exit0;no test count |
| N-7 | `node --check src/main/resources/static/task-modal-runtime.js` | exit0 |

Initial wrong diagnostic path src/main/resources/static/js/task-modal-runtime.js returned MODULE_NOT_FOUND; exact pom path N-7 then passed. Initial error retained.

Exact syntax commands/counts `/tmp/aggregate-epoch1-node-receipts.json`. Full approved JS log SHA256 `23d69c48577267bc553b610713fe29fc149aecfd93c7b0d086415085596a299d`.

### 真实运行证据与条件

原始环境为 MySQL 8.0.46，server/default utf8mb4 为 utf8mb4_0900_ai_ci；aggregate_runtime 用 utf8mb4_unicode_ci 创建后运行原样 Flyway。原样结构出现 V-1，不能记为运行通过。

为验证其余路径，另建 aggregate_runtime_coherent，仅在该测试夹具统一 email_suppression 排序规则。下列成功结论均受此条件约束，没有将 V-1 改写为 PASS。产品和实际静态资源字节未修改。

真实应用18161、SMTP25461/control25462、MySQL33461；后补本地 ES7.17.26（19261）、GreenMail2.1.3（IMAP31461/注入SMTP30261）。测试收件仅 @example.test。

| 证据 | 命令与实际结果 | 所证范围 |
|---|---|---|
| R-1 原始结构 | 原生脚本及 collation-repro，exit1 | 入队200后消息GET500；V-1 |
| R-2 超限 | `python3 /tmp/aggregate-epoch1-runtime/oversize-probe.py` | 实际400与零副作用；V-2。探针完成不等于合同通过 |
| R-3 三宿主 | `node /tmp/aggregate-epoch1-runtime-coherent/native-retry/native.cjs`，exit0 COMPLETE | ordinary/review/delegated真实59秒打开、61秒各一次cancel POST409及一次权威GET，无假DOM/clock/响应 |
| R-4 失败展示 | `node /tmp/aggregate-epoch1-runtime-coherent/error-surfaces.cjs`，exit0 | 实际550和DATA断链；FAILED/UNKNOWN无重试；17px/#c53e32/tooltip，无横向溢出 |
| R-5 重启 | `python3 /tmp/aggregate-epoch1-runtime-coherent/restart-proof.py`，exit0 | DATA后真实终止进程；WAITING保留due并发一次；SENDING真实等待600000ms转UNKNOWN且不重投；flag=false新请求同步、旧任务继续 |
| R-6 OUTBOUND恢复 | `node /tmp/aggregate-epoch1-runtime-coherent/legacy-native-complete/legacy-native.cjs`，exit0 | 刷新后原正文、UUID、actual anchor、当前revision、无draftRef，同job重新60秒 |
| R-7 INBOUND恢复 | `node /tmp/aggregate-epoch1-runtime-coherent/legacy-inbound-complete/legacy-native.cjs`，exit0 | 原processingId打开旧编辑器，原输入/当前revision/无伪draft，同job重排 |
| R-8 撤回/451/会议 | `node /tmp/aggregate-epoch1-runtime-coherent/final-runtime3.cjs`，exit0 | 40秒打开并取消，50秒确认撤回，过due仍无投递；451取消重试无动作，再确认重新60秒，最终一条SENT；会议与两附件真实SMTP |
| R-9 MIME | 本轮Python MIME诊断，exit0 | job22恰一MIME；ICS哈希与预览/下载一致；两份二进制原字节、逆序提交顺序一致；SMTP在due后969ms |
| R-10 UI生命周期 | `node /tmp/aggregate-epoch1-runtime-coherent/ui-lifecycle-final.cjs`，exit0 | 真断网恢复、延迟真实GET后切专家、退出清理；不包含hidden分支成功证明 |
| R-11 审核组合 | `python3 /tmp/aggregate-epoch1-runtime-coherent/remaining-review3.py`，exit0 | READ作者不能cancel；admin撤回仍APPROVED；真实IMAP新来信→REVIEW_CONTEXT_CHANGED、SMTP0；确认当前上下文→retry→SENT，冻结版本/正文不变 |
| R-12 非目标/RAG | `python3 /tmp/aggregate-epoch1-runtime-coherent/remaining-nontarget.py`，exit0 | RAG顺序、QA零混写、人工额度不变；专家模板SENT；真实IMAP自动会议邀请1；ES批量success1/failure0/skipped0；非目标delay行0 |
| R-13 分页 | remaining-browser3.cjs的A9段通过，整个脚本后续A12夹具失败exit1 | 原生50→100→114条，无重复键；第二账号筛选；历史失败无retry。独立回执remaining-browser3/pagination.json |
| R-14 A12 | remaining-conflict.cjs断言失败exit1；独立conflict-confirm.cjs诊断exit0 | 两浏览器实际保存/取消/恢复，确认V-4，不是PASS |
| R-15 visibility | visibility-final.cjs诊断exit0 | 隔离headful、真实最小化/恢复、关闭focus emulation后仍visible；B-1 |

R-3打开确认距due约859/861/860ms，确认晚于due约1149/1163/1139ms，各SMTP一封。review保持冻结APPROVED；delegated在390px以FULL带附件；READ无操作、直接POST403；打开确认后撤权正确清理。

以下早期失败全部保留，不冒充产品缺陷或成功：

- 初次coherent native选择器同时命中article与按钮；限定article后R-3完成。
- LEGACY先用旧mc-chip；后有成功重排后读取已卸载editor超时、合法null/undefined断言失配。修正临时脚本后R-6/R-7完成。
- 会议第一次把无subject的preview直接作请求；第二次遗漏普通draft会议context，被正确409拒绝。最终合法LEGACY路径R-8/R-9完成；普通draft关闭另由E2E及普通入口证明。
- A9首次在首屏渲染前读空DOM；再次同源入队被正确409拒绝。沿用既有任务并等待实际首屏后，114条证明完成。
- GreenMail首次配置格式错误退出；据错误修正。首次IMAP抓取只初始化游标，使用新来信后R-11/R-12完成。
- A12旧编辑器起初未展开；展开后运行实际场景，并由独立最小诊断确认V-4。

### 完整合同矩阵

下列O/N/I/S/C及机器门禁均为必需项；人工结果单独PENDING。来源：总计划需求、不变量与验收章节；S来自06，C来自07-source。C/N/R证据对应上文精确命令和真实运行，源码链路见下一节。

| ID | 要求 | 判断与新鲜证据 |
|---|---|---|
| O-1 | 三人工入口服务端60秒 | PASS；E2E29、R-3/5/8/11，无提前SMTP |
| O-2 | footer撤回/重试二次确认 | FAIL V-1；原始结构卡片读取失败；条件环境确认/取消/451/late409通过 |
| O-3 | 列表/卡片公开失败，UNKNOWN禁重发 | PASS；R-4/8、Node、实际图标/tooltip |
| O-4 | 普通原稿恢复、审核冻结 | FAIL V-4；无冲突恢复及review冻结通过，新普通稿冲突无提示 |
| N-1 | 批量/自动/模板/旧会议路径保持 | PASS；R-12真实ES/IMAP/SMTP，非目标delay0，完整调用边界 |
| N-2 | 权限、原件归属、真实来源、审核上下文 | PASS；Controller16、Guard11、Lifecycle44、E2E29、R-3/11 |
| N-3 | 正文、安全、QA/RAG、附件/ICS、人工额度 | PASS；E2E与R-9/12真实MIME及额度 |
| N-4 | 仅SENT产生成功、关闭/排期，HTTP超时不重复 | PASS；Attempt62、Lifecycle44、E2E回滚/丢响应、R-5/8 |
| N-5 | 筛选/计数/分页/只读/移动布局 | FAIL V-1；其他真实SQL、114条分页、READ与390px通过 |
| I-1 | DB时间60000、重复due不变、重试新期限 | PASS；Lifecycle/E2E及R-3/5/8/11 |
| I-2 | 取消/领取短事务互斥、截止CAS、SMTP事务外 | PASS；Lifecycle44真实MySQL竞争、E2E、三宿主late409 |
| I-3 | 指纹/UUID/Message-ID、actor/source/revision | PASS；Attempt62、E2E、R-6/7，并发旧请求拒绝 |
| I-4 | 完整白名单冻结、重启、4MiB及错误合同 | FAIL V-2；其余快照/ICS/附件/重启内容通过 |
| I-5 | 原子成功、安全失败/UNKNOWN分类 | PASS；最终事务链、Lifecycle/E2E注入、451/550/断链/真实kill |
| I-6 | 草稿全部写保护、精确恢复、审核APPROVED | FAIL V-4；其余锁定、关闭、版本和冻结通过 |
| I-7 | 当前HTTP/持久actor、来源/抑制/文件校验 | PASS；Guard/Controller/Query/Lifecycle/E2E、真实READ/撤权、R-11 |
| I-8 | 稳定job身份/rank/createdAt、统计、原始reviewContext | FAIL V-1；其余Query8、Mailbox41、114条分页、重试单卡通过 |
| I-9 | 服务端权威、异步归属、清理、浏览器生命周期 | FAIL V-1/V-4；hidden子项BLOCKED B-1；其他Node/R-3/10通过 |
| I-10 | 六新状态、旧attempt不变、固定错误、历史禁重试 | PASS；Flyway49、Controller16、Query8、R-4/13 |
| I-11 | 默认false、独立有界worker、旧任务继续、恢复UNKNOWN、无Rabbit依赖 | PASS；配置/worker源码、R-5真实600000ms恢复、运行时queue关闭 |
| S-1 | 原footer与可换行确认 | PASS；逐字CSS/DOM、实际1440/390 |
| S-2 | 17px/#c53e32、tooltip/aria、移动44px | PASS；真实computed style、截图及JS |
| S-3 | 复用组件、冲突默认保留并提示 | FAIL V-4；其余样式复用通过 |
| S-4 | 20资源统一版本、实际服务代码一致 | PASS；served/worktree SHA、style tests |
| C-1/C-2 | nullable原输入与实际anchor分离、互斥typed来源 | PASS；Snapshot14、E2E、R-6/7 |
| C-3 | 换UUID不能绕来源锁，actor/anchor/account分离 | PASS；Lifecycle44、E2E、真实同源409 |
| C-4 | restore权限、实际SENT anchor、无私密字段 | PASS；Controller16、Query8、R-6 |
| C-5 | OUTBOUND原renderer、无伪draft、冲突保留 | PASS；source-focused106、R-6；INBOUND见V-4 |
| C-6 | text+HTML不变才复用raw、原UUID/actual anchor/附件顺序 | PASS；Snapshot、JS消费链、R-6/9 |
| C-7 | owner/target/epoch/revision、关闭开关旧任务权威 | 已执行范围PASS；JS、E2E、R-5/10 |
| 05必跑守卫 | SQL兼容、WriteSeam及附件/只读门禁 | FAIL V-3；其他指定类通过 |
| AUTH-SCOPE | 累计及逐次修订授权 | PASS；58文件表 |
| NON-GOALS | 无自定义预约、自动重试、SMTP召回、历史批量补发、新推送 | PASS；完整diff |
| HUMAN A1–A12 | 人工结果与同一SHA签字 | PENDING；机器运行不转为人工PASS |

### 写入、读取与跨阶段链路审查

1. 三HTTP入口经原准备、安全确认、actor/source与附件校验，进入PendingMailOperationService.kt:978共用入队。
2. factory/codec冻结完整载荷；生命周期按来源/授权→attempt→job锁序在短事务中写入，普通稿绑定、审核事件与任务一致。
3. DelayedManualReplyWorker.kt:43/70重验当前来源和持久actor；DB到期才claim，SMTP在领取事务外。
4. ManualReplySendAttemptService.kt:630/637成功事务使record、QA/RAG、排期、attempt、draft/review、job原子提交；afterCommit:1031不会把已提交SENT降级。
5. MailboxConversationService.kt:430–431批量投影；READ不读私有快照/附件元数据/磁盘；原始reviewContext独立于UI隐藏linked record。
6. 前端保持job身份、revision、owner/session/target/epoch清理和确认回读；V-4为INBOUND提前分支绕过普通稿冲突检查。
7. 新增独立表，旧attempt枚举不扩大；开关只管新请求，旧任务worker不依赖Rabbit。

未发现其他已证必须修复的问题；该结论不覆盖B-1缺失的原生分支。

### Finding Lineage

| Finding | State | Severity |
|---|---|---|
| V-1 | NEW | P1 |
| V-2 | NEW | P1 |
| V-3 | NEW | P2，明确强制门禁 |
| V-4 | NEW | P1 |

首次aggregate，因此INITIAL；不以子阶段修复次数推导STALLED/DIVERGING。

### Findings

#### V-1 — P1：合法新迁移数据库中，延迟邮件详情读取500

要求：O-2/N-5/I-8/I-9、05T2。位置：src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt:103。

```sql
EXISTS(SELECT 1 FROM email_suppression es
       WHERE es.email=LOWER(TRIM(c.expert_email)))
```

实际库按unicode_ci创建并运行原样迁移后，expert_contact.expert_email为utf8mb4_unicode_ci，email_suppression.email为utf8mb4_0900_ai_ci。比较触发MySQL1267。真实普通入队200后，消息GET反复500，撤回卡片无法正常使用；worker仍可后台发送。

证据：/tmp/aggregate-epoch1-runtime/ordinary-network.json、collations.tsv、collation-repro.log、原始应用日志及截图。QueryIT的MySQL8.0.36库两列均0900，因此8项通过未覆盖此组合，不能否认真实故障。

最小修复：仓储局部比较及现有QueryIT判别回归；禁止全局DDL/统一生产库排序规则。

#### V-2 — P1：超4MiB快照返回400，违反413合同

要求：01计划:80、04计划:96明确UTF-8快照总量4MiB，超限413且不入队；I-4。位置：DelayedManualReplyModels.kt:252–253,652–654；PendingMailOperationService.kt:978–988。

真实合法conversation请求可见正文Hello，HTML后接大注释，htmlBytes=4200019。实际HTTP400 BAD_REQUEST，错误“Delayed manual reply snapshot exceeds 4194304 bytes”，而非413。job、attempt、mail_record均0，没有声称错误入队。

证据：/tmp/aggregate-epoch1-runtime/oversize-probe.json、oversize-no-effects.tsv。最小修复：Models可区分超限、共用Pending入队seam精确映射、既有E2E真实HTTP回归；不能全局把IllegalArgumentException改成413。

#### V-3 — 强制P2：写入点守卫未随controller行号同步

要求：05计划:105及brief Required commands #4明确必跑。位置：src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt:53,68。

MASTER_BASE两个只读DTO位置236/1163；本次07新增import/handler后为237/1169，guard旧钉导致必跑失败。不是新增业务写入口，仍为同一只读DTO。最小修复仅同步两个NoiseSite精确行号及必要原因；禁止扩大白名单、删除守卫。

#### V-4 — P1：INBOUND LEGACY恢复绕过普通新稿冲突提示

要求：master A-12/I-6/O-4、06T1。mailbox-chat.js:1158–1171先进入INBOUND adapter并return；普通稿检查在:1179–1184，仅OUTBOUND可到达。app.js:228–245 adapter仅检查旧#manualRichReplyEditor。

两真实浏览器及独立复验：另一浏览器保存普通新稿后，取消旧LEGACY并恢复，直接切旧编辑器填原正文：

```text
ordinaryEditorBefore = "Newer ordinary browser draft retained."
legacyEditorAfter = "Original legacy content retained."
warningPresent = false
dialogOpen = 0
serverDraftPreserved = true
```

服务器新普通稿版本和正文仍保留；未认定数据丢失。违反的是规定的恢复冲突提示及当前输入保护流程。

证据：/tmp/aggregate-epoch1-runtime-coherent/conflict-confirm.json、conflict-before.png、conflict-after.png、初始双浏览器脚本及新稿保存API回执。

最小修复：mailbox-chat在INBOUND挂载/写入前检查普通稿冲突，既有JS消费链回归，index按S-4统一资源键；无需改backend或新增UI。

### B-1 — 必需原生visibility证据受浏览器设施限制

master验收I-9要求浏览器页面隐藏/显示；06T3及验收要求visibility/focus恢复与资源清理。

已取得新鲜Node的visibility/unmount/owner/迟到回包覆盖，以及真断网、切专家、延迟真实GET、退出清理。尝试headless、隔离headful真实tab切换、原生Chrome最小化/恢复、关闭focus emulation、去掉occluded-window默认参数后，浏览器仍报告：

```json
{"hidden":{"visibility":"visible","hidden":false},"visible":{"visibility":"visible","hidden":false}}
```

因此不能证明原生hidden→visible被触发。未改DOM属性、伪造visibilityState或dispatch假事件。证据：visibility-final.json、ui-lifecycle-final.json、remaining-browser3/real-visibility.json及脚本。

该项为机器必需证据BLOCKED，不是人工PENDING，也不是产品缺陷。需可产生真实后台事件的浏览器环境补验，不进入repair产品任务。

### Observations

| ID | 观察与归因 |
|---|---|
| O-1 | 当前MailReviewSendIT Subject失败真实复现。MASTER_BASE测试整文件及create/targetSubject因果块相同，为既有期望冲突。未在base运行整套，仅静态因果对照，不虚报动态基线通过。 |
| O-2 | size100当前失败于:239“reader绝不查询review表”。MASTER_BASE ownDraftRows已union review草稿；该块逐字相同，SHA b61e1301f89ee8b9da8e6725c29df71620cf3bca8e57f46ce49e7ded723f591c。旧断言冲突，保留失败，不扩repair。 |
| O-3 | 额外InboundPendingOwnerIT全类8项：反射mock新增final配置导致1error及1等待断言failure；历史target172留下旧结构，使后续查询缺新表1error。01brief仅compile/Flyway明确必跑，narrower可选。恢复完整当前结构后，`mvn surefire:test '-Dtest=InboundPendingOwnerIT#real*' -DmysqlIt=true`单独重跑exit0、1/1、0skip。夹具污染不作为产品缺陷，不入repair。 |
| O-4 | 可选全JVM的旧JDK America/Coyhaique tzdata问题未重跑全套；真实应用用较新JDK11 patch，环境差异明确保留。 |
| O-5 | `git diff --check BASE CODE -- src/main src/test` exit0；全diff exit2，689行涉及19个docs路径空格/EOF问题，不在产品修复范围。 |
| O-6 | 一份text/plain附件旧SMTP传输发生LF→CRLF；SMTP文件与base相同。未把其wire字节称原字节一致；后续二进制附件与ICS真实MIME字节/顺序独立通过。 |
| O-7 | 旧08散文index哈希笔误已按实际served/current字节核对。正确完整SHA为fef64dba60702dd505c3d1ec4b4aed6849da07cd4ec5b463087096a5b93d9048，以native-served-sources.json为准；无产品字节失配。 |

### Fast-P RECORD_ONLY重新评估

| 来源 | Master映射 | 本轮结果与证据 |
|---|---|---|
| 02 O-1未使用RAG依赖/O-2参数拼写 | 无已证行为违反 | Observation，不修 |
| 02 O-3无draft来源限制 | O-1/I-3/I-4/I-6 | governing source-contract补足；Snapshot/E2E/R-6真实无draft OUTBOUND证明 |
| 02 O-4 rehydration | I-4/I-5/I-7 | Worker/E2E、真实MIME及重启证明，不继承旧PASS |
| 03 O-1/07 O07E2-01 size100 | N-5/I-8 | 新鲜失败O-2；master-base因果对照，不豁免查询矩阵 |
| 03/04可选tzdata | 环境 | O-4；未跑可选全套不报PASS |
| 04 O-1 component-only | O-1/I-5/I-11 | 新鲜公共HTTP、真SMTP、60秒与进程kill恢复 |
| 06 O06-01 activation/restart | O-1/I-11 | R-3/5/8/11实际闭环；原始结构仍有V-1 |
| 07-source O07SC-01同步guard500含422 | I-3/兼容链 | 历史记录不改；当前E2E29及真实LEGACY当前revision重排证明 |
| 07 O07E2-02 Subject | 原审核回归 | 新鲜C-2失败O-1，未删断言 |
| 07 O07E2-03 native A5 | A31/A32/I-1/I-2/I-9 | 当前三宿主真实59→61各一次POST409→GET通过，历史负例保留 |
| 08 O08-01哈希笔误 | S-4/证据身份 | 实际served/current哈希匹配，metadata观察 |
| 延期人工验收 | Master A1–A12 | 全PENDING，机器证据不制造human结果 |

### 证据归档、清理与最终状态

本轮原始归档 `/tmp/aggregate-epoch1-evidence.tar.gz`：384文件，6,844,137 bytes；SHA256 `f1385a1d71ad5fa4406c1deb26acf0beda844aecf850e451e1a7033e3102faa1`。

逐文件大小/SHA：`/tmp/aggregate-epoch1-evidence-manifest.json`。包括原始失败、成功重跑、精确脚本、JUnit XML、HTTP、MIME、截图、数据库dump、环境及清理回执；没有把旧PASS复制为新证据。

| 回执 | SHA256 |
|---|---|
| remaining-review3-final.json | 1adaff4102ce9699b9cdb80e7a6841caf84fa86c6be1ca66e1ab0e22e725fc83 |
| remaining-nontarget-final.json | 89ab2e94fd665125c4affb8a6d924ee832e4d0ff43a7e18d388033b041938342 |
| remaining-browser3/pagination.json | 819a9ff46fc927d63515834b28e44b65df8d99747c2ecd933c4ee4d9e73863db |
| conflict-confirm.json | 7bdcce01db65df3188889284d93208b4d7148ed6ad3d221120496a702133f917 |
| visibility-final.json | bcf69f41c27621ed32affa07d5045bf410b83b485bc44c15991dcee94d1476a9 |

旧归档均本轮独立读取并计算匹配哈希，仅复用设施/夹具设计，不复用旧PASS：

- verify07-activation-epoch2attempt1fix0-evidence.tar.gz：03a7d61560a67172a35fe6b178471ded5c6db9adf90ed5ae6b918efaef5306ee
- verify08-deadline-confirmation-epoch1attempt1fix0-evidence.tar.gz：c8ce6017fe1edaf0ce44758caec551e76232f739fb57d5a12c4545a8517a0e53
- resume07-activation-epoch1-final-evidence.tar.gz：46581d5c44bbcd39710a1c973637d97775a05386f173d7f3a23cc3b4a3ab9d2a

本轮应用/SMTP/MySQL/ES/IMAP已按确切PID/container停止移除，先dump数据库，保留cleanup.json。其他任务Maven、工作树、用户浏览器未操作。

审查员完成时：HEAD=17e8563aa2a0d966cb2cb4a7a2f37a0fc657fdd5；tracked工作树及index diff空；产品/测试相对final_code_head空。仅未跟踪controller review目录与精确repair文件。主控在证据提交前另核查。

### Repair Planning Result: DRAFT_READY

Baseline为上述governing master；Verification FAIL/INITIAL。Included：V-1/V-2/V-3/V-4。Excluded：B-1、O-1～O-7、人工验收；没有把缺证据包装为产品修复。

修复草案：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/fix/manual-reply-delay-master/repair.md`。

SHA256：`2faeacade4c57d7a03c3cfc211edc85be0da0f3beae00ba904fe1d6d64145e83`。

四根因、九个精确文件，含判别回归、禁止事项、限定执行门禁、完整aggregate回审要求；不顺手修两个既有失败。一次批准交接包含：

- 产品提交主题：`fix(mail): restore delayed reply aggregate contracts`。
- 仅handoff证据提交主题：`docs(review-fast-p): record aggregate repair execution`。
- 精确repair-execution.md、master、report路径。
- 用户请求修复后自动回审时，同聊天回到新的独立reviewer，无需用户转述执行器元数据。
- 当前无修复执行授权，未执行修复。

Required Human Decision / Next Action：通过人类原始调用 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/fix/manual-reply-delay-master/repair.md` 批准并执行限定修复，连同计划授权本地提交、持久repair-execution.md交接，并请求同聊天自动返回aggregate review。回审仍须补B-1真实visibility证据；人工A1～A12保持PENDING。

No product code was modified.
