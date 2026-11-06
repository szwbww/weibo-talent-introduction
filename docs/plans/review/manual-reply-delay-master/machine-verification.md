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

## Epoch 2 — 2026-11-06 — Verification Result: FAIL

Phase: `aggregate/master`; report ID: `AGGREGATE-20261106-E2`.
Reviewer: `/root/aggregate_review_epoch2`, fresh independent reviewer, no implementation context, distinct from recorded executors. Skills: review-fast-p aggregate reviewer contract → review-p → verify-p; repair-p only after this verification completes.
Convergence: **PROGRESSING**. Manual acceptance: **PENDING** for all A-1–A-12.

V-1/V-2/V-3/V-4 are RESOLVED. B-1's missing native visibility evidence is RESOLVED. One newly exposed mandatory behavioral violation remains: **V-5, P1, hidden/resume leaves an inert cancel confirmation visible**. Its causal function is byte-identical to the pre-repair version; this is not a regression introduced by the repair. No required machine-evidence gap remains after the fresh checks below. Historical unrelated failures retain their actual failing results.

### Identity and implementation boundary

- W: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`.
- Repository common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`; branch `fast/manual-reply-delay-master`.
- Resolution: `EXPLICIT`, from exact `W/docs/plans/fast/manual-reply-delay-master/human-review-handoff.md`. No non-selected worktree ledger consulted.
- Cumulative boundary: `a224623c8dc17bdad1ce80e1c4391637bf7ee5ca` → `fc2750cd7ad4ae5200195036d874aeeb8b5ae400`.
- Post-repair delta: `83ec3e3bdf620f80198f73ae496f78aaf75625f4` → `fc2750cd7ad4ae5200195036d874aeeb8b5ae400`.
- Evidence HEAD: `6a2cc31f60c34819bed7edeeebf9300921d2ed19`; evidence commit changes only `repair-execution.md`.
- Governing plan: `W/docs/plans/2026-11-04/manual-reply-delay-master.md`, SHA256 `4fa1f45fc2830aea7c138e3da44a60d5ea767fa48a01239ca01ed01989a89a2e`, byte-equal to recorded commit `8a51021ca463da1e3ea279bf5a94e998267751bf`.
- Current invoked identity: SAME. State: `AMENDMENT_RECORDED`, A1–A32. The old invoked SHA256 `9df55143ff99d59e2a958e6703930a8ed69231b9e6b195ed749b7b2c8eb8ff5d` and exact governing unified-diff JSON remain in the selected review ledger. Every retroactively authorized file is assessed individually below.
- Fast ledger SHA256 `76dadc5a2039d622ffdb1cd3423bc9f2629a024f4e4dc37b539560528d61a31b`; handoff SHA256 `82089ede965eb02123c4f26bebb806c1a57c93b1fe18fb0c54141d067d14861c`.
- Post-repair evidence mode: `DURABLE_HANDOFF`. Exact `W/docs/plans/review/manual-reply-delay-master/repair-execution.md` SHA256 `527c32c7b6c009e5f1981b65ad05acf8d1cef66bf117ff1e446bfc38dfa421d3`; result READY_FOR_VERIFICATION. Executor: omp coding assistant plus RepairMixedCollation/RepairInboundRestore; optional global parent executor ID unavailable, not a blocker. Exact human execute-p approval is recorded under Identity and authority.
- Reviewed repair artifact: `W/docs/plans/fix/manual-reply-delay-master/repair.md`, old SHA256 `2faeacade4c57d7a03c3cfc211edc85be0da0f3beae00ba904fe1d6d64145e83`. Its implementation changes exactly the nine Authorized Files, no extra path. The next draft at the same path is a new unapproved repair, not a continuation of the prior execution approval.
- At entry: only controller-owned `docs/plans/review/manual-reply-delay-master/ledger.md` dirty; index empty, current src byte-equal to final code head. All 36 child brief/execution/verify/fix-log artifacts hashed; all nine current child plans byte-equal to their recorded identities. See `/tmp/aggregate-epoch2/identity-scope.json`.

Ordered child plans share prefix `W/docs/plans/2026-11-04/manual-reply-delay-`:

| Child | Recorded identity; current equality |
|---|---|
| 01-storage.md | 05342b4be63bbc7d9b30416ae73ada2d44b562df; equal |
| 02-preparation.md | ab3ad56713a74664b0f34f66a138206cb14666ea; equal |
| 03-lifecycle.md | 0bfe5b07c85e76f85c43a31d30fde8b37f30b9b9; equal |
| 04-worker-api.md | de076f6413b65809ca13610c0455965134056dcb; equal |
| 05-query.md | baa9ba43a9ddf8c39ff2feae1c91c3c76fea2acb; equal |
| 06-frontend.md | a5c1ddb90e92b5aef7bf8f16b031dd4d51c49b80; equal |
| 07-source-contract.md | 525aeeb9e97241d5ad044f5662c75194db1a5944; equal |
| 07-activation.md | 8a51021ca463da1e3ea279bf5a94e998267751bf; equal |
| 08-deadline-confirmation.md | 8a51021ca463da1e3ea279bf5a94e998267751bf; equal |

### Fresh environment and commands

All Maven explicitly sets `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` (Zulu 11.0.15). Maven 3.9.11; Node 25.7.0; Docker 29.8.2. Runtime Java is `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java`; actual Chrome `157.0.8103.39`. Patch-level runtime difference is disclosed; optional old-JDK tzdata failures are not relabeled passing.

Exact isolated datasource for required tests needing one:

```text
DB_URL=jdbc:mysql://127.0.0.1:33481/aggregate_review?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=aggregate-local-only
```

Only owned disposable databases/services and `@example.test` recipients. No development/default/production DB. Testcontainers ITs also ran with mysqlIt/migrationIt enabled. No reused historical PASS substitutes. Raw logs and exact argv are under `/tmp/aggregate-epoch2` (E below); runtime evidence under `E/runtime` (R below).

#### C-1

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -DskipTests test-compile
```

Exit **0**; `E/compile.log` empty due quiet success. Compile-only, no test count inferred.

#### C-2

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test '-Dtest=PendingMailOperationServiceTest,ManualReplyDelayEndToEndIT,DelayedManualReplySnapshotTest,DelayedManualReplyControllerTest,DelayedManualReplyWorkerTest,DelayedManualReplyLifecycleIT,DelayedManualReplyQueryIT,MailReplyDraftServiceTest,MailReplyDraftControllerTest,MailReplyDraftRepositoryIT,MailReplyDraftSendIntegrationTest,MailReviewDraftIT,MailReviewSendIT,MailReviewQueryIT,!MailReviewQueryIT#size100*,FlywayMigrationIntegrationTest,MailboxConversationRepositoryIT' -DmysqlIt=true -DmigrationIt=true
```

Exit **1**, 1383.420 s; **358 run, 357 pass, 1 failure, 0 errors, 0 skipped**. Only failure is retained O-1 MailReviewSendIT subject assertion. Flyway really ran 49 tests, 752.497 s. `E/jvm-C2.log` contains 24,501 lines/3,962,771 bytes; fresh XML retained in `E/jvm-C2-reports/`.

#### C-3

Original missing-range selection is `mvn test` with the same `-Dtest` selection below. Actual lifecycle-equivalent command:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn surefire:test exec:exec@node-test exec:exec@node-check-app exec:exec@node-check-task-modal-runtime '-Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTrustWorkbenchTest,ExpertMailPermissionGuardTest,MailboxConversationRepositorySqlCompatTest,OperatorStatusWriteSeamGuardTest,OutboundAttachmentFlowTest,OutboundAttachmentServiceTest,MailboxReadAccessProjectionTest,MailboxAttachmentServiceTest,RagSendBridgeTest,InboundPendingOwnerIT,ExpertMailReadAccessIT,MailReviewQueryIT#size100*' -DmysqlIt=true
```

Exit **1**, 124.650 s; **225 run, 221 pass, 2 failures, 2 errors, 0 skipped**. O-2 size100 contributes one failure. Optional O-3 InboundPendingOwnerIT contributes one failure/two errors. **OperatorStatusWriteSeamGuardTest now 1/1 PASS**. `E/jvm-C3.log` contains 1,576 lines/249,901 bytes; fresh XML in `E/jvm-C3-reports/`.

C-1 and C-2 compiled the exact current unchanged sources; C-3 used those outputs. No concurrent Maven target writers. C-2/C-3 stop at Surefire failures, so their later bound Node exec goals did **not** execute. Fresh standalone N-5/N-6/N-7 provide those exact gates. No hidden test exclusion: the original C-2 `!size100*` remains explicit; C-3 executes size100 and retains its failure. Combined **583 executed / 578 passed / 3 failures / 2 errors / 0 framework skips**. `E/jvm-receipts.json` preserves command, duration, exit and class counts. Complete log scans and original outputs retained; failing results not rewritten as green.

#### Fresh JVM class counts

| Gate / class | Run | Failure / error / skip |
|---|---:|---|
| C-2 DelayedManualReplyLifecycleIT | 44 | 0/0/0 |
| C-2 MailReviewQueryIT | 4 | 0/0/0 |
| C-2 DelayedManualReplyControllerTest | 16 | 0/0/0 |
| C-2 DelayedManualReplySnapshotTest | 14 | 0/0/0 |
| C-2 ManualReplyDelayEndToEndIT | 30 | 0/0/0 |
| C-2 DelayedManualReplyWorkerTest | 9 | 0/0/0 |
| C-2 MailReviewSendIT | 18 | 1/0/0 |
| C-2 PendingMailOperationServiceTest | 49 | 0/0/0 |
| C-2 MailReplyDraftControllerTest | 6 | 0/0/0 |
| C-2 MailboxConversationRepositoryIT | 41 | 0/0/0 |
| C-2 MailReplyDraftServiceTest | 14 | 0/0/0 |
| C-2 MailReplyDraftSendIntegrationTest | 27 | 0/0/0 |
| C-2 FlywayMigrationIntegrationTest | 49 | 0/0/0 |
| C-2 DelayedManualReplyQueryIT | 10 | 0/0/0 |
| C-2 MailReplyDraftRepositoryIT | 8 | 0/0/0 |
| C-2 MailReviewDraftIT | 19 | 0/0/0 |
| C-3 OperatorStatusWriteSeamGuardTest | 1 | 0/0/0 |
| C-3 InboundPendingOwnerIT | 8 | 1/2/0 |
| C-3 MailReviewQueryIT | 1 | 1/0/0 |
| C-3 MailboxReadAccessProjectionTest | 9 | 0/0/0 |
| C-3 RagSendBridgeTest | 11 | 0/0/0 |
| C-3 ManualReplySendAttemptServiceTest | 62 | 0/0/0 |
| C-3 MailboxAttachmentServiceTest | 7 | 0/0/0 |
| C-3 OutboundAttachmentFlowTest | 9 | 0/0/0 |
| C-3 OutboundAttachmentServiceTest | 28 | 0/0/0 |
| C-3 PendingMailOperationServiceTrustWorkbenchTest | 60 | 0/0/0 |
| C-3 ExpertMailReadAccessIT | 16 | 0/0/0 |
| C-3 ExpertMailPermissionGuardTest | 11 | 0/0/0 |
| C-3 MailboxConversationRepositorySqlCompatTest | 2 | 0/0/0 |

#### Node gates

| ID | Exact command | Exit; run/pass/fail/skip | Evidence |
|---|---|---|---|
| N-1 | `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailReviewAdmin.test.js src/test/js/mailboxChatStyle.test.js` | 0; 197/197/0/0; cancelled0 | E/node-1.log |
| N-2 | `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailReviewAdmin.test.js src/test/js/expertAccessWorkspace.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxServerDrafts.test.js` | 0; 250/250/0/0; cancelled0 | E/node-2.log |
| N-3 | `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailboxChatStyle.test.js` | 0; 118/118/0/0; cancelled0 | E/node-3.log |
| N-4 | `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxMailReadAccess.test.js src/test/js/mailboxOutboundAttachments.test.js src/test/js/mailboxReplyIdentity.test.js src/test/js/mailboxSuspension.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/authFlow.test.js src/test/js/mailReviewReader.test.js` | 0; 445/445/0/0; cancelled0 | E/node-4.log |
| N-5 | `node --test src/test/js/*.test.js` | 0; 2115/2115/0/0; cancelled0 | E/node-5.log |

N-6: each of the following exact individual commands exit0. No test count applies. The first17 are every changed JS file; trust-reply-workbench is the supplied additional syntax gate. N-7 is task-modal-runtime. Exact argv/exits/times: E/node-receipts.json, logs node-6.log–node-24.log.

```sh
node --check src/main/resources/static/app.js
node --check src/main/resources/static/mailbox-chat.js
node --check src/main/resources/static/mailbox-readonly.js
node --check src/test/js/authFlow.test.js
node --check src/test/js/expertAccessWorkspace.test.js
node --check src/test/js/mailReviewAdmin.test.js
node --check src/test/js/mailReviewReader.test.js
node --check src/test/js/mailboxChatBehavior.test.js
node --check src/test/js/mailboxChatStyle.test.js
node --check src/test/js/mailboxDelayedReply.test.js
node --check src/test/js/mailboxMailReadAccess.test.js
node --check src/test/js/mailboxOutboundAttachments.test.js
node --check src/test/js/mailboxReadOnly.test.js
node --check src/test/js/mailboxReplyIdentity.test.js
node --check src/test/js/mailboxServerDrafts.test.js
node --check src/test/js/mailboxSuspension.test.js
node --check src/test/js/meetingConfirmationIntegration.test.js
node --check src/main/resources/static/trust-reply-workbench.js
node --check src/main/resources/static/task-modal-runtime.js
```

All Node gates ran once freshly in this epoch; no prior failed sandbox run is reused as current evidence.

#### Required command coverage mapping

The approved child briefs and plans were independently resolved; ranges are combined once against the master, not reaccepted child by child.

| Original required range | Fresh evidence |
|---|---|
| 01 compile / Flyway migrationIt=true | C-1; C-2 Flyway49 |
| 02 Pending / Trust / Attempt / Snapshot, A1 RagSendBridge | C-2 Pending49/Snapshot14; C-3 Trust60/Attempt62/Rag11 |
| 02 DraftSend mysqlIt | C-2 27 |
| 03 Attempt / DraftService | C-3 62; C-2 14 |
| 03 Lifecycle / DraftRepo / DraftSend / ReviewDraft / full ReviewQuery mysqlIt | C-2 44/8/27/19/4; C-3 size1001, actual O-2 failure |
| 03 Pending / Trust | C-2 49; C-3 60 |
| 04 Worker / Controller / Guard / Snapshot / Lifecycle | C-2 9/16/14/44; C-3 Guard11 |
| 05 Query mysqlIt / MailboxRepo mysqlIt isolated datasource | C-2 10/41 |
| 05 SqlCompat / WriteSeam / attachment / read projection | C-3 2/1/9/28/9/7, all pass |
| 06 Controller / focused / affected / full JS / each changed JS syntax | C-2 Controller16; N-1/N-3/N-4/N-5/N-6 |
| 07-source six prescribed ranges | C-1; C-2 Snapshot14/Controller16/Worker9/Pending49/Lifecycle44; N-3 includes full DelayedReply; N-5/N-6 |
| 07-activation E2E/original draft-review/Flyway/Mailbox/enabled ITs | C-2 358 plus C-3 uncovered ranges; no opted-out IT |
| 08 exact six-file group / full JS / chat syntax / native surfaces | N-2/N-5/N-6; R-2 below; hidden behavior fails V-5 |
| Prior repair exact backend class range / frontend groups / syntax / live repair probes | C-2 plus C-3 WriteSeam; N-2/N-3/N-5/N-6; R-1/R-5/R-2 |
| Optional broad InboundPendingOwnerIT and ExpertMailReadAccessIT diagnostics retained from supplied command | C-3 8 (O-3),16 pass; no expanded repair scope |

### Fresh real runtime evidence

Owned MySQL8.0.46 container `aggregate-manual-reply-epoch2-db`, port33481; application database `aggregate_runtime`; app18181; controlled real SMTP25481/control25482; Greenmail IMAP31481/SMTP30281; ES7.17.26 port19281. Runtime launches exact current compiled classes plus Maven dependency classpath, with `spring.web.resources.static-locations=file:W/src/main/resources/static/`. Current static responses equal current W bytes; no old target static assets substituted. `R/starts.json`, runtime logs, seed SQL, MIME files, HTTP/network logs and scripts preserve the exact launch and proof.

Default production delay config remains false. Only local runtime enables it. Scheduling needed for delay worker remains active; unrelated scheduling, outbound queues, LLM/discovery/translation and Rabbit-dependent work disabled. SMTP uses real socket protocol and actual JavaMail; modes250/451/550/DATA disconnect/kill-after-DATA. No real recipients; no service/cloud deployment. Original MySQL mixed collations preserved throughout—no normalization/DDL workaround. Testcontainers race tests use DB manipulation only where the approved deterministic test requires it; every runtime60-second and600-second observation uses unmodified due/claim timestamps and clock.

| ID | Exact fresh command or script | Exit and observed result |
|---|---|---|
| R-1 | `python3 R/repair-backend-corrected.py` | 0. Original server/suppression0900 vs contact unicode; messages GET200, unrelated suppression allows action, actual suppression disables, READ no action/private data. Two legal4,200,019-byte raw snapshot requests (INBOUND/OUTBOUND) both413 SNAPSHOT_INVALID; jobs/attempts/sent/calendar unchanged. `repair-backend-final.json`, HTTP logs. |
| R-2 | `node R/native-retry/native.cjs` | 0. Actual current ordinary/admin REVIEW/delegated FULL native clicks. Open due−323/−877/−566ms; confirm due+1191/+1145/+1136ms; each exactly one cancel POST with captured revision→409→one action-owned GET; actual SENDING/SENT. Held genuine GET only, no response/DOM/clock fabrication. READ direct mutation403/no controls; observed FULL→READ denial clears; expired dismiss cannot reopen. 1440/390, keyboard and frozen review/attachments retained. `native-final.json` plus per-surface network/screenshots/served-source evidence. |
| R-3 | `python3 R/restart-proof.py` | 0. Real SMTP DATA kills owned app; restart same DB with enabled=false. Waiting job9 delivers once at unchanged due1793949950058. New disabled request90903 returns synchronous SENT with delayedReply null/job0. Claimed job8 stays SENDING until actual fixed600000ms recovery→UNKNOWN; retry409; same Message-ID DATA exactly1, no fake successful mail_record. `restart-before/after-crash/progress/http/final.json`. |
| R-4 | `node R/final-runtime3.cjs` | 0;129.063s. Refresh/switch preserves due;40s cancel-open+dismiss,50s explicit cancel, no delivery past due. Real451→FAILED; retry dismiss unchanged, confirm fresh ≥59s remaining→SENT1; stable card/error cleared. Real meeting preview2030, two reversed binary files, calendar0 before/1 after, downloads match. |
| R-5a | `node R/repair-native.cjs` | 1 only at final harness assertion expecting null instead of documented empty draft DTO. Earlier actual two independent Chrome processes prove legacy original/new ordinary text+two ordered files conflict warning before legacy mount, exact before/after draft equality, original restore readable; `repair-native/conflict.json`. Original failure retained. |
| R-5b | `node R/repair-native-clean-proof.cjs` | 0. Only unfinished no-conflict check repeated: old editor restores original content, GET target has id/content null/version0, no synthetic persisted draft. `repair-native-clean-proof/final.json`. |
| R-6 | `node R/error-surfaces.cjs` | 0;65.540s. Actual550 FAILED and DATA-disconnect UNKNOWN, fixed public reason, no cancel/retry/confirm; real icon17px/color rgb(197,62,50), hover tooltip and no desktop overflow. |
| R-7 | `node R/legacy-native-complete/legacy-native.cjs`; `node R/legacy-inbound-complete/legacy-native.cjs` | 0/0. Native cancel→reload→restore→requeue same job/new revision/new full due; OUTBOUND same UUID/verified old anchor/no draftRef; INBOUND original processing/no fake anchor; originals retained. |
| R-8 | `python3 R/remaining-review4.py` | 0. Fresh91507 READ author→admin APPROVED→send/cancel/stale requeue409/current revision requeue; real Greenmail inbound makes REVIEW_CONTEXT_CHANGED at due with no send; body/version/APPROVED preserved; explicit context confirmation→retry→another real60s→SENT. User cannot cancel REVIEW. `remaining-review4-final.json`. |
| R-9 | `python3 R/remaining-nontarget.py` then `python3 R/nontarget-tail.py` | 1/0. First command's RAG real60s/fact order/no QA/manual quota unchanged and direct template SENT all pass before the isolated mailbox fixture failure; no rerun of those completed checks. Corrected tail actual IMAP automatic meeting invitation1 and ES/manual batch success1; zero delay jobs for non-targets. `nontarget-final.json`, original HTTP/script prove passed prefix. |
| R-10 | `node R/ui-lifecycle-final.cjs` | 0;21.367s. Real offline/online recovery, held genuine response then switch expert, logout clears cards; stale response cannot repaint wrong contact. Its Playwright visibility stays visible and is explicitly NOT hidden evidence. |
| R-11 | `node R/pagination.cjs` | 0;12.056s. Actual mixed two-account same-second outbound+inbound plus WAITING/CANCELLED rows: load50→100→133 unique source/id keys; filtered secondary65 all correct account; historical FAILED has no retry. Date/direction/count/rank/cursor and retry→SENT union invariants additionally covered by fresh Query10/Mailbox41 SQL. |
| R-12 | `python3 R/mime-proof.py`; `python3 R/meeting-mime-proof.py` | 0/0. Three native surfaces each one MIME with final subject/HTML/text/thread matching frozen snapshot and no premature DATA. Text attachment legacy LF→CRLF transport normalization disclosed O-6. Separate binary proof: second.bin5 bytes then first.bin6 bytes exact ordered SHA/file/wire equality; ICS exact preview/download/wire hash; meeting DATA due+702ms, one MIME. |
| R-13 | `node R/visibility-app2.cjs`; `node R/visibility-app4.cjs` | First independently proves actual hidden/no poll→visible GET but fails confirm cleanup; second independently confirms native stale click0POST while visible confirmation remains. Second later exits1 only because helper tries JSON on logout204, after complete admin behavior proof. Full raw `visibility-app4-failure.json.results[0]` and event stream retained. V-5, not PASS. |
| R-14 | `node R/visibility-read.cjs` | 0. Separate fresh native Chrome READ; actual hidden samples, zero conversation polling while hidden; activation promptly GET access/list/messages; no confirmation/actions. `visibility-read-final.json`. B-1 evidence gap closed. |
| R-15 | `node R/source-anchor-corrected/source-native.cjs` | 0. Fresh no-draft OUTBOUND omits optional anchor; enqueue binds real930020. After cancel insert newer SENT930021, reload/native restore/requeue still sends930020, original UUID/raw HTML/raw text whitespace/ordered binary IDs exact, no draftRef, same job/new revision;1440/390 no overflow. |
| R-16 | `node R/list-only.cjs`; `node R/list-only-complete.cjs`; `node R/list-keyboard.cjs` | 1/1/0. Original actual451/full60s showed failure icon by live polling with0 selected messages; first helper incorrectly expected card-specific reason instead of approved generic list text. Second programmatic focus did not enter keyboard focus-visible. Final real Tab/Shift+Tab proves parent-button focus shows exact approved generic tooltip, with0 selected messages; no60s/send repeated. Original network/job/failures plus list-only-final.json retained. |

`post-receipts.json` and `supplement-receipts.json` retain exact argv, per-command exits/durations. Wrapper exit0 is not substituted for child command outcomes. Separate command/session exits are retained in `runtime-extra-receipts.json`.

#### Failed diagnostic/probe lineage, retained rather than erased

- Runtime initial launch wrapper watched an incorrect Maven marker; terminated only that owned wrapper and corrected to observed Surefire marker. No test/build restarted for that issue.
- First backend diagnostic queried nonexistent attempt.expert_contact_id; corrected fixture inspection to recipient before behavioral assertions; first script/log retained.
- Initial native visibility probe had another browser moving focus during its interval: ten requests, so not accepted as a hidden interval. `visibility-app2` then independently records32 hidden samples and confirms V-5. Raw Chrome same-window background/activation is required; focus emulation is not used.
- `visibility-app3` clicked a stale document's navigation during Page.navigate; timed out before behavior. `visibility-app4` waits for new complete document and visible hit area; admin hidden/resume and actual click evidence complete before separate logout204 JSON parsing failure. Separate READ-only command completes missing branch without rerunning the admin proof.
- R-5a's null-versus-empty-DTO assertion is a harness error; all product conflict assertions and native clean restore already occurred. R-5b completes exact empty target representation checks.
- First real inbound probes used local IMAP username `unused`, whose inbox was empty, while SMTP injected To `sender@example.test`. `imap-diagnostic.json` reads both actual inboxes. Only disposable DB account username changed to the intended inbox; original failures retained. Fresh91507 and91416 actual inbound scenarios then complete. No product fix or mock result substitution.
- First source-anchor probe collided with auto-created batch contact92002. It stopped at fixture creation before behavior; new unused93002 fixture completes R-15. No product mutation.
- Original list-only probe reached actual FAILED and displayed list icon through polling with no selected conversation, then incorrectly expected card-specific reason. Approved06 S-2:103 explicitly requires fixed list text「有 N 封邮件发送异常，打开会话查看」. A completion probe used programmatic focus (not keyboard modality); final native Tab/Shift+Tab proves focus-visible tooltip. Both original failures retained, no second60s send.
- `restart-final.json.disabledResponse` is a harness labeling error: variable was reused for later retry409. Its complete `restart-http.json` independently records90903 synchronous200/SENT/delayedReply-null; the assertion and DB job0 ran immediately. Do not read the mislabeled field as synchronous response.

### Contract Matrix

All machine rows below are mandatory unless explicitly marked otherwise. Source authority: governing master Requirements/Invariants/Acceptance; style S-1–S-4 from06; C-1–C-7 from07-source;08 as recorded A31/A32. Human acceptance remains separate PENDING; machine exercise never supplies a human signature.

| ID | Requirement / required evidence | Verdict | Fresh evidence |
|---|---|---|---|
| O-1 | Three manual entry paths, durable server60s | PASS | C-2 E2E30, Lifecycle44; R-2/R-3/R-4/R-7/R-8/R-15 |
| O-2 | Footer cancel/retry confirmation and real explicit action | FAIL V-5 | Ordinary cancel/dismiss/retry/late409 pass; after actual hidden/resume confirmation remains visible but inert R-13 |
| O-3 | List/card fixed public failure; UNKNOWN never retry | PASS | R-4/R-6/R-16; Query10; Node full |
| O-4 | Preserve ordinary original/attachments and frozen APPROVED | PASS | R-5 conflict+clean native, R-8; ordinary/review binding and restore source inspected |
| N-1 | Bulk/auto/expert template/old meeting paths unchanged | PASS | R-9 real ES/IMAP/SMTP; no non-target delay jobs; full write-entry diff and unchanged meeting service path |
| N-2 | Permissions, files/source/current thread, review context | PASS | Guard11, Controller16, Lifecycle44, E2E30; real READ/403/context R-2/R-8/R-15 |
| N-3 | Final HTML/text, safety, ordered QA/RAG, bytes/ICS, quota | PASS | C-2/C-3; R-9 manual quota/facts, R-12 exact binary/ICS; O-6 text-wire normalization disclosed |
| N-4 | Only SENT success/close/calendar; no HTTP timeout duplication | PASS | Attempt62/Lifecycle44 atomic failure injection and idempotence; R-3 actual kill+unknown; R-4 |
| N-5 | Filters/counts/pagination/account isolation/READ/mobile | PASS | Query10, Mailbox41, R-11 133 unique rows/two accounts; R-2/R-14/R-15; V-1 repaired without collation alteration |
| I-1 | DB+60000ms; no early claim; original repeated due; retry full round | PASS | Service.kt:116/287/364; Lifecycle real CAS; R-2/R-3/R-4/R-8 actual time |
| I-2 | Cancel/claim exclusive, deadline CAS, SMTP after commit | PASS | Service.kt:241/364; Lifecycle races; worker transaction separation; actual late409 three hosts |
| I-3 | Original fingerprint, UUID/Message-ID, origin/actor/revision | PASS | AttemptService.kt:185/248; Attempt62; Lifecycle44; R-7/R-15 same job/UUID/new revision; HTTP stale403/409 |
| I-4 | Complete frozen whitelist payload, restart/bytes/size contract | PASS | Models.kt:255/659, Pending.kt:978/990/1028; Snapshot14, E2E30; R-1 exact413 and zero effects; R-3/R-12 |
| I-5 | Atomic success, definite fail vs UNKNOWN, no auto retry | PASS | AttemptService.kt:630/803; Lifecycle trigger-induced QA/RAG/calendar/job/review rollback; R-3/R-4/R-6/R-8 |
| I-6 | All draft writers guarded; precise release/restore; review APPROVED | PASS | DraftService/Repo, ReviewService/Repo, Service enqueue/cancel; C-2; R-5 preserves new version/body/ordered files, R-8 frozen review |
| I-7 | Current HTTP permission, persisted actor preflight, no null admin | PASS | Guard, Controller, Pending rehydrate1028, Query; suppression mixed rules R-1, real READ/403 R-2, context R-8; post-SMTP finalize independent of later grant |
| I-8 | Stable rank3/job.id/createdAt; one union row/count; raw review context | PASS | MailboxRepo.kt:290 and UI/raw union; Service.kt:280/424; Query10/Mailbox41; R-4 retry→SENT one record/card; R-11/R-8 |
| I-9 | Server-authoritative UI, owner/generation, polling lifecycle cleanup | FAIL V-5 | Source chat.js:1239/1242/1293/10028–10037; R-13 real hidden/resume visible inert confirmation. R-10 stale/offline/logout, R-14 READ, R-16 list-only pass; B-1 resolved |
| I-10 | Six job states, unchanged attempts, fixed public errors/historical no retry | PASS | Models/codec/DTO; Flyway49; R-6 fixed errors/UNKNOWN, R-11 historical failed no action |
| I-11 | Defaultfalse, independent bounded worker, old jobs survive, no Rabbit | PASS | Worker.kt:30–41/108; properties/application.yml; R-3 true600000ms crash/restart and disabled-new sync path; no Rabbit service |
| S-1 | Existing footer and wrap-capable inline confirmation | PASS | Literal CSS/DOM tests; actual1440/390 R-2/R-15. V-5 concerns lifecycle removal, not style values |
| S-2 | 17px/#c53e32, tooltip/aria, keyboard/mobile44px | PASS | R-2/R-6/R-16 computed styles/native focus; no horizontal overflow |
| S-3 | Existing components and default-preserve conflict warning | PASS | R-5 native warning before legacy mount, exact draft preserved; no overlay/new editor introduced |
| S-4 |20 original resources/same key, source actually served | PASS | Fresh served bytes/source manifest, index current repair key; N-1/N-2/N-3 style/DOM test; task runtime unversioned |
| C-1 | Nullable raw OUTBOUND input separate from verified actual anchor | PASS | Models factory/codec; Snapshot14/E2E30; R-15 omitted input→930020 and later930021 does not replace it |
| C-2 | Typed mutually exclusive legacy source, old snapshot compatibility | PASS | Models codec/source predicates; Snapshot14, Controller16, Lifecycle44; R-7 both types |
| C-3 | UUID changes cannot bypass source lock/actor/account identity | PASS | Lifecycle44 real source locking; E2E30; source delta checked; no new identity scheme |
| C-4 | Restore only with permission/current source; no private public projection | PASS | Controller restore validation, READ Query path; R-1/R-2/R-5/R-15 |
| C-5 | Existing renderer/no fake draft, newer content conflict preservation | PASS | R-5 INBOUND two independent browsers; R-7/R-15 OUTBOUND native; correct null-id/version0 target |
| C-6 | Raw reuse only if text+HTML unchanged; retain original UUID/ordered files | PASS | N-3 direct format-only/edited-input regressions; R-15 actual unchanged raw HTML/text whitespace/ordered files/old anchor exact; R-12 MIME |
| C-7 | Owner/target/epoch/revision lifecycle; disabled switch only admission | FAIL V-5 (UI cleanup subpart) | Server/identity/disabled portions pass C-2/R-3/R-7/R-15; shared confirmation hidden cleanup fails R-13 |
| 08 I-1 | Admission before due distinct from continuing already open confirmation | PASS | R-2 three native late409, expired dismiss no reopen; N-2 fresh expired canCancel=false vs stale true |
| 08 I-2 | Writable/owner/job/revision/state/known-error checks retained | PASS | N-2; real READ/revocation403 cleanup R-2; no authority inferred from stale canCancel |
| 08 I-3 | One captured POST409, action-owned GET, no false cancel/no replay | PASS | R-2 per-surface request logs and actual states; node busy/network/401/403 tests |
| 08 I-4 | Hidden/switch/logout/unmount cleanup, original timers and locked content | FAIL V-5 | Actual hidden clears closure but not displayed confirmation; stale click0POST leaves inert buttons. Other lifecycle branches R-2/R-10/R-14 and N-2 pass |
| 08 I-5 / S-1/S-2 | Original CSS/DOM/resources, actual current assets | PASS | Current repair changed only20 version values; literal styles/resources tests; native surfaces |
| Required command gate | Every prescribed range freshly executed, results honestly classified | PASS execution; raw C-2/C-3 FAIL | Exact mapping and original logs above; O-1/O-2/O-3 not silently waived/passed |
| 05 mandatory WriteSeam | Exact owner write-site guard | PASS | C-3 1/1, repaired exact controller line pins237/1169; no whitelist expansion |
| AUTH-SCOPE | Cumulative58 and repair9 files inside explicit authority | PASS | Complete diff/identity-scope; per-file amendment table below |
| NON-GOALS | No arbitrary scheduling/autoretry/SMTP recall/bulk-recall/push | PASS | Complete cumulative diff; no new shared-table fields/enums or unrelated feature introduced |
| HUMAN A-1–A-12 | Named human/date/results on exact final boundary | PENDING | All twelve retained; no machine result substituted for human acceptance |

### Write/read and cross-plan chain audit

Complete combined source and test diff inspected, including all1,239 lines of LifecycleIT, all new tests and every old-test adjustment. Review follows the combined master behavior rather than treating child reports as acceptance.

1. Actual inbound/conversation/review handlers preserve existing safety/source/permission/file validation, then `PendingMailOperationService.kt:978` is the shared enqueue branch. Snapshot factory and codec size exceptions now map only proven sizeLimitExceeded to413; other invalid snapshot semantics remain unchanged.
2. Durable snapshot stores final delivery authority and separate original restoration inputs, with explicit whitelists, no credentials/service/file bytes/absolute paths. Transactional source→attempt→job locking and revision CAS preserve source/actor identity; current source binding/APPROVED state is not inferred from a frontend cache.
3. Worker has fixed one-thread/SynchronousQueue no-backlog executor; scheduler submits only. Independent stale recovery never queues a resend. Rehydrate rereads current files/entities, verifies SHA/size/fingerprint, and does not rerender the frozen letter. Claim commits before real SMTP.
4. Delayed finalize atomically writes record+QA/RAG+unique meeting+attempt+exact draft close/review result+job. Real transaction-trigger tests demonstrate rollback, including late success/recovery token races. After-commit audit/ES failure cannot rewrite committed SENT.
5. Read projection batches current page IDs (≤100) with canonical mappers; original mixed SQL collations are handled only in suppression comparison. READ does not load restore snapshots/private file metadata/disk. UI union preserves job identity while raw original-mail union governs review context; new inbound really invalidates it.
6. Frontend current-owner/generation callbacks and exact numeric/method reader allowlist remain. Repair moved INBOUND legacy conflict handling before the old-editor adapter and after current-target read. Fresh two-browser proof retains both content copies; all server draft before/after fields and ordered files equal.
7. Confirm lifecycle has one remaining mismatch: hidden clears internal state and aborts old reads but does not update existing footer DOM. Resume's unchanged-item comparison excludes serverNow, so no repaint occurs. This exact narrow chain is V-5.

### Amendment authority and individual amended-file assessment

A1–A32 exact before/after identities, master rule, concrete reason and original approval text are in the unchanged hashed fast ledger. Specific authority: A1 RAG success transaction; A2–A4 review revision moved into03; A5/A6 necessary existing-test compatibility; A7/A8 original legacy inputs; A11 internal read-only interfaces; A15 source/response contract completion; A31/A32 explicit HUMAN:2026-11-06「按总 A-5 补齐」. Standing approvals do not authorize unrecorded paths or broaden behavior. A31's five-file08 follows07 and preserves every original manual item, production defaultfalse and combined release gate; no authority conflict or unapproved amendment found.

Prefixes below are relative to exact W: K=`src/main/kotlin/com/weibo/talentintroduction/`; T=`src/test/kotlin/com/weibo/talentintroduction/`; S=`src/main/resources/static/`; J=`src/test/js/`. Every row is mandatory and assessed individually; shared-file rows retain every applicable amendment. PASS in this table means the change follows the recorded amendment's rule; behavioral V-5 remains separately FAIL.

| Amended file | Authority / master rule | Individual assessment and current proof |
|---|---|---|
| Tmail/RagSendBridgeTest.kt | A1 / I-5 | PASS. Assertions follow fact persistence into final transaction, not after-commit success; C-3 11/11. |
| Kmail/service/MailReviewDraftModels.kt | A2/A3/A4 / I-3,I-6 | PASS. Only already-approved delayExpectedRevision DTO handoff/strict current round; no new user capability. C-2 review/E2E. |
| Tmail/repository/MailReplyDraftRepositoryIT.kt | A5/A6 / I-2,I-6 | PASS. Real READ_COMMITTED transaction wrapper satisfies production lock contract; existing state/content assertions retained,8/8. |
| Kmail/service/DelayedManualReplyModels.kt | A7/A8,A23/A24 / I-4,I-6,I-7 | PASS. White-listed original request/meeting restore inputs and typed nullable OUTBOUND source preserve frozen sending/old schema compatibility; missing old restore input fails closed. Snapshot14/R-15; repair size marker is narrower than generic invalid snapshot. |
| Tmail/service/DelayedManualReplySnapshotTest.kt | A9/A10,A23/A24 / I-4,I-6 | PASS. Prepared fixtures now carry required original input; both legacy source/old compatibility/normalization/limits retain real expectations;14/14. |
| Kmail/service/OutboundAttachmentService.kt | A11/A12 / I-7,I-10 | PASS. readVerifiedFile visibility private→internal only, body unchanged; no new file access authority. Attachment28 and live bytes. |
| Kmail/repository/MailReplyDraftRepository.kt | A13/A14 / I-7,I-10 | PASS. Bounded current-contact/account/page-id read API reuses canonical mapper; no locks/writes/new lifecycle. Query10, Repo8. |
| Kmail/repository/MailReviewDraftRepository.kt | A13/A14 / I-7,I-10 | PASS. Same bounded read-only mapper reuse for review; existing events/state intact; Review19/Query10. |
| Kmail/controller/DelayedManualReplyController.kt | A15/A16,A23/A24 / I-6,I-7 | PASS. Validated original processing/actual anchor only on privileged restore response; public READ projection unchanged; Controller16/R-5/R-15. |
| Tmail/controller/DelayedManualReplyControllerTest.kt | A15/A16,A23/A24 / I-6,I-7 | PASS. Direct positive/negative source restoration, permissions and status contract;16/16; not fixture-only source claims. |
| JmailboxChatBehavior.test.js | A17/A18 / I-9,S-4 | PASS. Monotonic clock/SVG namespace/teardown supports added production timer; business assertions retained; N-4. |
| JmailboxReadOnly.test.js | A17/A18,A19/A20 / I-7,I-9 | PASS. Timer/clock fixture updates and retirement of obsolete no-refresh expectation follow required focus refresh; content/scroll/permission/coalescing remain. N-2/N-4/R-14. |
| JmailboxMailReadAccess.test.js | A19/A20 / I-7,I-9 | PASS. Single macrotask drain/clock avoids infinite fake polling while preserving permission semantics; N-4. |
| JmailboxOutboundAttachments.test.js | A19/A20 / I-4,I-6 | PASS. Same bounded timer compatibility; attachment order/download/ownership assertions retained; N-4/R-5/R-12. |
| JmailboxReplyIdentity.test.js | A19/A20 / I-3,I-9 | PASS. Supplies new closure dependencies without loosening owner/source identity assertions; N-4. |
| JmailboxSuspension.test.js | A19/A20 / I-9 | PASS. Clock/single-turn fixture supports periodic read; original suspension assertions retained; N-4. |
| JmeetingConfirmationIntegration.test.js | A19/A20 / N-3,I-4 | PASS. Timer/clock support only; preview/content/ICS integration assertions retained; N-4 and actual R-12. |
| JauthFlow.test.js | A21/A22 / I-7,I-9 | PASS. Supplies closure-local legacy map to extracted-function sandbox; auth/logout behavior preserved; N-4/R-10. |
| JmailReviewReader.test.js | A21/A22 / I-7,I-9 | PASS. Removes only old focus-only-access request assumption, retains editor identity/body/CAS/reload assertions; N-4/R-14. |
| Kmail/service/DelayedManualReplyService.kt | A23/A24 / I-3,I-6,I-7 | PASS. Existing predicates/source identity accept typed legacy source while preserving transaction and locks; Lifecycle44/R-15. |
| Kmail/service/PendingMailOperationService.kt | A23/A24 / O-1,I-4,I-6 | PASS. Captures actual source separately from optional raw input and restores raw text/HTML only under unchanged-content guard; no alternate delivery path. E2E30/R-7/R-15. |
| Smailbox-chat.js | A23/A24,A31/A32 / I-6,I-7,I-9,A-5 | Authority PASS. Narrow existing renderer/source consumer and shared pre-open vs admitted-confirm predicate are within rule. Native raw/anchor/late409 pass; mandatory hidden cleanup FAIL V-5 remains. |
| Sindex.html | A23/A24,A31/A32 / S-4 | PASS. Existing20 version values updated together, same order/URLs; unversioned task runtime and DOM intact. Current repair cache key also limited to same set. |
| Tmail/service/DelayedManualReplyLifecycleIT.kt | A23/A24 / I-3,I-6,I-7 | PASS. Real dual-source exclusion, identity collisions, retry/recovery/token and transaction regressions retain all previous invariants;44/44. |
| JmailboxDelayedReply.test.js | A23/A24,A31/A32 / I-6,I-9,A-5 | Authority PASS. Native-source consumer and narrowed late-confirm changes plus repair conflicts are directly relevant; N-1/N-2/N-3 all pass. Current hidden test only asserts stale POST0 and misses visible DOM cleanup; V-5 discriminator required. |
| JmailboxChatStyle.test.js | A25/A26 / S-4 | PASS. Retires old06 cache-key coupling while preserving20 refs/same key/literal CSS/real DOM; no new key pinned; N-1/N-2/N-3. |
| Kmail/controller/MailReviewDraftController.kt | A27/A28 / I-3,I-7 | PASS. Revision allowlist/strict value forwarding and exact delayed exception status only; no permission expansion. E2E30/R-8. |
| Kmail/service/ManualReplySendAttemptService.kt | A27/A28 / I-1,I-3,I-5 | PASS. Internal pure existing-payload lookup and explicit queued-read preparation preserve original strict sync claim and identity; Attempt62/E2E30. |
| Kmail/service/MailReviewDraftService.kt | A27/A28 / I-3,I-6,I-7 | PASS. Defaultfalse queued-read mode enabled only in two approved preparation callers; UNKNOWN and source/context permission remain strict; Review19/R-8. |
| Tmail/service/DelayedManualReplyWorkerTest.kt | A29/A30 / I-11 | PASS. Only final properties use real defaultfalse instance, overrides win, remaining mocks/assertions unchanged;9/9. |
| JmailReviewAdmin.test.js | A31/A32 / A-5,I-6,I-9 | PASS. Shared late-confirm direct consumer/local clock fixture retains APPROVED read-only/body/version restrictions; N-2 and actual REVIEW native R-2. |
| JexpertAccessWorkspace.test.js | A31/A32 / A-5,I-7,I-9 | PASS. Existing delegated FULL/READ host tests directly prove late-confirm and revocation boundaries; native delegated/READ R-2/R-14. |

### Complete cumulative file scope

All58 changed product/test files were read and compared with exact current authorized tables; all permitted. The nine repair changes are a subset explicitly authorized by the reviewed repair artifact. No unauthorized code, test, migration, route, shared table or knowledge-file change. The table's PASS is scope, not erasure of V-5.

| File | Exact governing child table(s) | Scope result |
|---|---|---|
| Kconfig/ManualReplyDelayProperties.kt | 01-storage | PASS |
| Kmail/controller/DelayedManualReplyController.kt | 04-worker-api,06-frontend,07-source-contract | PASS |
| Kmail/controller/MailReviewDraftController.kt | 07-activation | PASS |
| Kmail/controller/MailboxConversationController.kt | 05-query,07-activation | PASS |
| Kmail/controller/UnmatchedInboundMailController.kt | 07-activation | PASS |
| Kmail/repository/DelayedManualReplyRepository.kt | 01-storage,03-lifecycle,05-query | PASS |
| Kmail/repository/MailReplyDraftRepository.kt | 03-lifecycle,05-query | PASS |
| Kmail/repository/MailReviewDraftRepository.kt | 03-lifecycle,05-query | PASS |
| Kmail/repository/MailboxConversationRepository.kt | 05-query | PASS |
| Kmail/service/DelayedManualReplyModels.kt | 01-storage,02-preparation,04-worker-api,07-source-contract | PASS |
| Kmail/service/DelayedManualReplyService.kt | 03-lifecycle,07-source-contract | PASS |
| Kmail/service/DelayedManualReplyWorker.kt | 04-worker-api | PASS |
| Kmail/service/ExpertMailPermissionGuard.kt | 04-worker-api | PASS |
| Kmail/service/MailReplyDraftService.kt | 03-lifecycle | PASS |
| Kmail/service/MailReviewDraftModels.kt | 03-lifecycle | PASS |
| Kmail/service/MailReviewDraftService.kt | 03-lifecycle,07-activation | PASS |
| Kmail/service/MailReviewSendService.kt | 07-activation | PASS |
| Kmail/service/MailboxConversationService.kt | 05-query | PASS |
| Kmail/service/ManualReplySendAttemptService.kt | 02-preparation,03-lifecycle,07-activation | PASS |
| Kmail/service/OutboundAttachmentService.kt | 05-query | PASS |
| Kmail/service/PendingMailOperationService.kt | 02-preparation,04-worker-api,07-source-contract,07-activation | PASS |
| src/main/resources/application.yml | 07-activation | PASS |
| src/main/resources/db/migration/V174__create_delayed_manual_reply.sql | 01-storage | PASS |
| Sapp.js | 06-frontend | PASS |
| Sindex.html | 06-frontend,07-source-contract,08-deadline-confirmation | PASS |
| Smailbox-chat.css | 06-frontend | PASS |
| Smailbox-chat.js | 06-frontend,07-source-contract,08-deadline-confirmation | PASS; V-5 behavioral finding retained |
| Smailbox-readonly.js | 06-frontend | PASS |
| JauthFlow.test.js | 06-frontend | PASS |
| JexpertAccessWorkspace.test.js | 08-deadline-confirmation | PASS |
| JmailReviewAdmin.test.js | 06-frontend,08-deadline-confirmation | PASS |
| JmailReviewReader.test.js | 06-frontend | PASS |
| JmailboxChatBehavior.test.js | 06-frontend | PASS |
| JmailboxChatStyle.test.js | 06-frontend,07-source-contract | PASS |
| JmailboxDelayedReply.test.js | 06-frontend,07-source-contract,08-deadline-confirmation | PASS; V-5 behavioral finding retained |
| JmailboxMailReadAccess.test.js | 06-frontend | PASS |
| JmailboxOutboundAttachments.test.js | 06-frontend | PASS |
| JmailboxReadOnly.test.js | 06-frontend | PASS |
| JmailboxReplyIdentity.test.js | 06-frontend | PASS |
| JmailboxServerDrafts.test.js | 06-frontend | PASS |
| JmailboxSuspension.test.js | 06-frontend | PASS |
| JmeetingConfirmationIntegration.test.js | 06-frontend | PASS |
| Tcampaign/OperatorStatusWriteSeamGuardTest.kt | 05-query | PASS |
| Tcampaign/repository/FlywayMigrationIntegrationTest.kt | 01-storage | PASS |
| Tmail/RagSendBridgeTest.kt | 02-preparation | PASS |
| Tmail/controller/DelayedManualReplyControllerTest.kt | 04-worker-api,06-frontend,07-source-contract | PASS |
| Tmail/repository/DelayedManualReplyQueryIT.kt | 05-query | PASS |
| Tmail/repository/MailReplyDraftRepositoryIT.kt | 03-lifecycle | PASS |
| Tmail/repository/MailboxConversationRepositoryIT.kt | 05-query | PASS |
| Tmail/service/DelayedManualReplyLifecycleIT.kt | 03-lifecycle,07-source-contract | PASS |
| Tmail/service/DelayedManualReplySnapshotTest.kt | 02-preparation,04-worker-api,07-source-contract | PASS |
| Tmail/service/DelayedManualReplyWorkerTest.kt | 04-worker-api,07-activation | PASS |
| Tmail/service/ExpertMailPermissionGuardTest.kt | 04-worker-api | PASS |
| Tmail/service/ExpertMailReadAccessIT.kt | 01-storage | PASS |
| Tmail/service/InboundPendingOwnerIT.kt | 01-storage | PASS |
| Tmail/service/ManualReplyDelayEndToEndIT.kt | 07-activation | PASS |
| Tmail/service/ManualReplySendAttemptServiceTest.kt | 02-preparation | PASS |
| Tmail/service/PendingMailOperationServiceTest.kt | 02-preparation,07-activation | PASS |

Repair delta independently verified, exactly nine paths:

- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
- `src/main/resources/static/index.html`
- `src/main/resources/static/mailbox-chat.js`
- `src/test/js/mailboxDelayedReply.test.js`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyQueryIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplyDelayEndToEndIT.kt`

### Finding Lineage

| Finding | Prior state | Current state | Fresh evidence |
|---|---|---|---|
| V-1 P1 mixed-collation messages500 | OPEN | RESOLVED | Query10/10 includes both original and unified metadata cases; R-1 actual original mixed schema GET200, suppression semantics/READ intact; no DDL normalization |
| V-2 P1 oversized snapshot400 instead of413 | OPEN | RESOLVED | E2E30/30 includes new paired endpoint limit case; R-1 both real oversized requests413, exact side-effect counts unchanged; explicit size marker only |
| V-3 mandatory P2 write-site guard | OPEN | RESOLVED | C-3 OperatorStatusWriteSeamGuardTest1/1; only exact line pins changed, no guard bypass |
| V-4 P1 INBOUND restore bypasses newer ordinary draft conflict | OPEN | RESOLVED | New twelve JS discriminators plus R-5 two independent browser conflict/no-conflict; warning before adapter mount, entire saved draft/ordered attachments unchanged, original remains readable |
| B-1 required native hidden/show evidence | BLOCKED | RESOLVED evidence gap | R-13 actual Chrome hidden32 samples/no polling and visible immediate GET; R-14 READ native hidden/resume. New behavior failure V-5 retained separately |
| V-5 P1 visible stale confirmation after hidden/resume | N/A; latent path previously lacked native evidence | NEW | R-13 plus exact unchanged causal stopDelayPolling and no-repaint refresh chain; actual stale click0POST and visible confirmation remains |

Convergence **PROGRESSING** is based on root-cause and behavioral evidence: the two backend runtime defects, required guard and actual content-preservation defect materially close; the old environment gap is resolved. V-5 is an unchanged pre-repair lifecycle cause newly exposed by real visibility evidence, not an equal/higher repair regression. `E/causal-provenance.json` proves pre-repair/current stopDelayPolling SHA256 `d587f1a187e5eb33ad9cdb8df31ca749a3548d24354c0a99f3e748726d69f261` equal. No reopened/renamed persistent finding or superficial line-change claim; required repair scope narrows to one frontend root cause.

### Findings

#### P1 — V-5: Hidden/resume leaves the old cancellation confirmation visible and unusable

- Requirement: master I-9 lifecycle cleanup and O-2 actions;08 must-not-change and I-4 explicitly require hidden to clear confirmation and terminate old reads. C-7 shares the same current-owner cleanup contract. Hidden cleanup is outside the A31 expiry exception.
- Source: `W/src/main/resources/static/mailbox-chat.js:1239` stopDelayPolling clears `delayState.confirm` at1242 and aborts GET controllers, but never synchronizes the existing footer. Resume at1293 calls delayTick, which only processes a non-null confirmation. Unchanged server messages are compared excluding `serverNowMs` at10028/10037, so no timeline render removes the old controls. `delayAction` at1080–1088 returns immediately when the closure confirmation is null; dismiss similarly returns without rendering.
- Reproduction: fresh real WAITING, open native inline confirmation, create another real Chrome tab in same window, observe32 actual hidden samples with0 conversation requests, reactivate original page, observe actual fresh GET and unchanged job. Confirmation still visible. Native Input mouse press/release on visible confirm causes0 cancel POST and leaves the same visible group. `R/visibility-app4-failure.json.results[0]`: hidden.confirm1; visible.preClickConfirm1/postClickConfirm1/staleClickCancelPosts0. Original raw sample/event stream/screenshots retained. The later logout helper204 parsing failure does not invalidate these completed observations. Independent earlier `visibility-app2` records the same DOM residual.
- Impact: after routine tab switching the user sees two inactive cancel-confirmation controls and cannot reopen the normal cancel affordance until another job/timeline transition or navigation. No unauthorized cancellation or duplicate SMTP occurred. It is a proven required UI lifecycle defect, not missing evidence or optional polish.
- Smallest product scope: shared `mailbox-chat.js` confirmation/footer lifecycle. Direct existing regression file `mailboxDelayedReply.test.js`; `index.html` existing20-resource cache key. No backend/schema/worker/CSS/permission/draft change needed.
- Existing test blind spot: `mailboxDelayedReply.test.js:123` hidden case clicks a retained detached/stale reference and asserts only POST0; it never asserts visible confirmation removal or usable current affordance after unchanged resume. The test being green does not prove this contract.

#### P2

No new blocking P2. V-3 mandatory P2 is RESOLVED. No optional hygiene or old fixture repair included.

#### Observations

| ID | Fresh result, causal scope and limit |
|---|---|
| O-1 | C-2 MailReviewSendIT:347 still expected Subject/actual Re: Inbound. Whole test file is byte-identical to MASTER_BASE; actual create block that derives subject from inbound also byte-identical (hashes/excerpts in causal-provenance.json). Unrelated pre-existing expectation conflict; not dynamically rerun on base and no claim base tests pass. Original failure remains in358. |
| O-2 | C-3 MailReviewQueryIT size100 still fails “reader never query review table”; the whole test and causal ownDraftRows block are byte-identical to MASTER_BASE; that block already unions review rows. Current required SQL/count/paging checks still run; this assertion is not waived as a product pass or repaired out of scope. |
| O-3 | Supplied optional full InboundPendingOwnerIT8 retains1failure/2errors: consumer reflection mocks final ManualReplyDelayProperties; a latch assertion times out downstream of that fixture; historical migration test leaves explicit aggregate_review at172, so later optional owner query cannot find delayed_manual_reply. The full class is not01's mandatory Flyway gate. Flyway49 and every required current-schema Query/Lifecycle/Repository matrix ran independently first and pass. No extra optional rerun claimed this epoch; previous epoch's restored-schema narrow PASS not reused. |
| O-4 | Optional all-JVM old-JDK America/Coyhaique tzdata suite not run; no result inferred. Required Maven uses exact specified11.0.15; live runtime uses available11.0.32, disclosed rather than obscured. |
| O-5 | Fresh `git diff --check BASE CODE -- src` exits0. Previous whole-document whitespace observations remain outside product/repair scope; no docs cleanup or optional full-diff gate added. |
| O-6 | Real native text/plain attachment wire normalizes LF toCRLF through existing SMTP path. Explicit text-normalized comparison only; never described as exact original wire bytes. SMTP implementation unchanged by cumulative diff. R-12 separate binary attachments and ICS prove exact bytes/order/hash. |
| O-7 | Historical08 prose index-hash typo remains metadata-only. Current served/current source hashes independently match; current repair cache key differs from historical08, so historical SHA is not presented as current bytes. Native served-source manifest is authoritative. |

### Fast-P RECORD_ONLY re-evaluation

All retained entries re-evaluated; historical overwritten child stages are not treated as current PASS. Final child evidence is read as an unverified claim and mapped to fresh combined evidence.

| Entry | Master rule | Epoch2 decision |
|---|---|---|
| 01 final N/A; historical optional full owner IT boundary | Mandatory compile/Flyway, optional fixture diagnostics | C-1/C-2 Flyway49 pass; C-3 optional O-3 disclosed, no fabricated mandatory waiver |
| 02 O-1 unused injected RAG repo | No proven mandatory behavior | Observation only; dead constructor dependency retained, no cleanup |
| 02 O-2 claimedAttempt parameter spelling | Positional API unchanged | Observation only; no named-argument contract break |
| 02 O-3 no-draft conversation snapshot gap | O-1/I-3/I-4/I-6 | A23 governing source contract and fresh Snapshot/E2E/R-7/R-15 close actual nullable-source path |
| 02 O-4 later worker rehydration dependency | I-4/I-5/I-7 | Current worker/Pending/source inspected; Worker9, real frozen MIME, actual crash restart prove present implementation |
| 03 O-1 /04 O-2 /07 O07E2-01 size100 baseline | N-5/I-8 | Fresh C-3 failure retained O-2 with exact base causal identity; full required query range executed |
| 03 O-2 /04 O-2 optional tzdata | Environment | O-4; no optional full JVM PASS asserted |
| 04 O-1 component SMTP only | O-1/I-5/I-11 | Fresh full app/socket SMTP/MySQL, real60s and600s process kill/restart R-2/R-3/R-4; no component result substituted |
| 05 final N/A, disclosed deferred runtime boundary | I-8/query/API | Query10/Mailbox41 plus original mixed live GET and133-row native paging; deferment closed by fresh evidence |
| 06 O06-01 activation/restart later stage | O-1/I-11/I-9 | Fresh public three-entry and restart complete; visibility gap resolved but mandatory V-5 promoted from actual behavior |
| 07-source O07SC-01 synchronous guard500 containing422 | I-3/compatibility | Historical result retained; current activated native both legacy requeues and optional-source later-anchor chain pass R-7/R-15 |
| 07-source F06-01 closed, preserved | I-6/source input | Not reopened by label. Current INBOUND V-4 resolved through repair; raw/source/native proof rechecked independently |
| 07 O07E2-02 Subject | Existing review regression range | Fresh C-2 failure O-1, unchanged-base causal proof, no test exclusion |
| 07 O07E2-03 native A5 unresolved before08 | A31/A32/A-5/I-1/I-2/I-9 | Fresh three hosts each late native POST409→action GET pass R-2. Historical negative remains unchanged; V-5 concerns separate hidden cleanup |
| 08 O08-01 index hash prose typo | S-4/evidence identity | Current actual network/source hashes match; metadata observation O-7 |
| Deferred human whole-plan acceptance | A-1–A-12 | All PENDING, no approval/signature inferred |

### Evidence boundaries and manual acceptance

No unavailable mandatory machine evidence remains. One confirmed mandatory implementation violation takes precedence: FAIL. P1 is not used as a label for a missing environment check. All required ranges, including enabled MySQL/Flyway and real native runtime, were freshly exercised. Optional global executor identity, optional all-JVM suite, production deployment and real-mail delivery remain outside this machine review.

Human A-1–A-12 remain PENDING at final product head. Machine exercise coverage: A1→R-4/R-5; A2→R-2/R-4/R-12; A3→R-4/R-16; A4→R-6/R-3; A5→R-2; A6→R-8; A7→R-2 plus required real IT matrix; A8→R-3; A9→R-11; A10→R-9; A11→R-2/R-6/R-16; A12→R-5. These are evidence mappings, not human acceptance results. Current shared hidden cleanup violation must be repaired before machine PASS and deferred human gate.

### Evidence archive, cleanup and final state

- Raw evidence archive: `/tmp/aggregate-epoch2-evidence.tar.gz`; SHA256 **a4ee31aac42b484cd5ab3a4b2dd9441fa35a4bc7c0f411de8f3ef4a1c1f34964**; 6,767,500 bytes;373 files. Includes all commands/scripts/raw logs/JUnit/HTTP/network/MIME/screenshots, failed probes, source/diff/identity receipts, original reviewed repair plus new draft, final DB snapshots, cleanup and repository state.
- Manifest: `E/manifest.json`, SHA256 **be71355f895e15853882e6419f77fa6f53634ee5dd6a9eb6a960a5f1c067133a**. Browser profile caches omitted from archive, not used as behavioral evidence; remain transient `/tmp`. Final report is returned separately to avoid self-hash recursion. Archive receipt: `/tmp/aggregate-epoch2-archive-receipt.json`.
- Exact `aggregate_runtime` and `aggregate_review` SQL dumps succeeded before cleanup. Only app82435/SMTP65298 and this epoch's three named Docker containers removed; no owned Chrome remains. `E/cleanup.json` exact args/exits and process identities; unrelated services untouched. Runtime fixtures disappear with owned containers; no scaffolding written into product tree.
- Final `E/final-repository-state.json`: HEAD remains `6a2cc31f60c34819bed7edeeebf9300921d2ed19`; branch unchanged; index empty; `git diff fc2750cd7ad4ae5200195036d874aeeb8b5ae400 -- src` empty.49 original plan/evidence files retain entry hashes. Only dirty files: controller's selected review ledger and this review-p-authorized single repair.md. Reviewer did not edit review report/ledger, product, tests, approved plans, fast evidence or knowledge; no stage/commit/branch operation.
- Return destination only: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/review/manual-reply-delay-master/machine-verification.md`; controller appends this complete report. Reviewer writes no review evidence there.

### Repair Planning Result: DRAFT_READY

Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-master.md` (identity frozen above).
Verification result: **FAIL / PROGRESSING**, `AGGREGATE-20261106-E2`, exact boundary above.
Repair artifact: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/fix/manual-reply-delay-master/repair.md`.
Current draft SHA256: **f96501759344c97dd27cc6e7bce2cbc3f842c703ddd376a03f7996e15652ef03**;11,162 bytes.

#### Included Findings

- **V-5 — REPAIRABLE**. One root cause, R-1: synchronize existing confirmation footer with hidden lifecycle cleanup, preserving server authority and all draft/review/permission behavior. Three exact files: `src/main/resources/static/mailbox-chat.js`, `src/test/js/mailboxDelayedReply.test.js`, `src/main/resources/static/index.html`.
- One discriminating existing-suite behavior regression: open confirmation→actual hidden event→resume with same job/revision/status/capability and only serverNow changed; old visible controls absent, stale click0POST, legal new open+explicit confirm exactly1POST. No fixture-forced repaint or source-string test. Actual current-source native ordinary/delegated/READ proof and existing late409 behavior retained; full prescribed frontend groups/JS/syntax required.

#### Excluded Findings

- V-1/V-2/V-3/V-4 RESOLVED; no repeat repair.
- B-1 resolved evidence gap, not product repair.
- O-1/O-2 unrelated baseline assertions; O-3 optional fixtures; O-4–O-7 environment/metadata/unchanged semantics. No optional cleanup or expanded matrix.
- Human A-1–A-12 remain PENDING.

#### Self-review and Required Human Decision

The current draft replaces the prior implemented repair at the same exact path; old approval is not transferred. One root cause/three files, explicit completion criteria and must-not-change list; no new plan, split, continuation artifact, backend change or automatic repair loop. Reviewed implementation remains untouched. No authority ambiguity or new scope is required.

Human must approve the **current** draft before execution. This pause is explicitly required by `/Users/lukai/.agents/skills/repair-p/SKILL.md`: “Execution is prohibited until the human explicitly approves this plan.” `/Users/lukai/.agents/skills/review-p/SKILL.md` recognizes a later exact human `$execute-p` invocation as approval. Agent/controller handoff and the prior artifact's execute approval do not authorize this new draft. The draft includes exact subsequent product/evidence commit subjects and repair-execution append path so approval is concrete and reviewable.

No implementation was performed.

### Next Action

To approve and execute this repair, send:

```text
$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/fix/manual-reply-delay-master/repair.md
```

No product code was modified.


## Aggregate review — Epoch 3 — AGGREGATE-20261106-E3

## Verification Result: PASS

Plan: `W/docs/plans/2026-11-04/manual-reply-delay-master.md`
Implementation boundary: MASTER_BASE→`60f4a5389e032aac50d1a8f0d8eab0519edc3ad7`; exact three-file post-repair delta checked separately.
Manual acceptance: PENDING (A-1–A-12).
Convergence: **PROGRESSING**. V-1…V-5 resolved; B-1 evidence gap resolved. Raw C-2/C-3 failures remain disclosed observations, not successful test results. No mandatory machine row remains blocked; no new product defect proven.
Reviewer: `/root/aggregate_review_epoch3`; independent aggregate/master review. Prior reviewers `/root/aggregate_review_epoch1` and `/root/aggregate_review_epoch2` are distinct. No product implementation by this reviewer. Full `review-p` → `verify-p` workflow applied; eligible `repair-p` decision follows actual verification result.

### Identity and exact boundary

W = `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`; E = `/tmp/aggregate-epoch3`; R = `E/runtime`. Paths below are relative to W unless prefixed E/R or absolute. All fresh evidence in this report belongs to Epoch 3; prior reports supply lineage and authorization history only.

- Repository common root `/Users/lukai/IdeaProjects/weibo-talent-introduction`; common Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Selected branch `fast/manual-reply-delay-master`; MASTER_BASE_SHA `a224623c8dc17bdad1ce80e1c4391637bf7ee5ca`; current product head `60f4a5389e032aac50d1a8f0d8eab0519edc3ad7`; evidence HEAD `39bfd10890e94b531f46e54c251f35d9ecd9fc4f`.
- EXPLICIT exact handoff invocation; invoked master identity SAME as current governing handoff. No discovery or nearest-file inference. Master `docs/plans/2026-11-04/manual-reply-delay-master.md`, SHA256 `4fa1f45fc2830aea7c138e3da44a60d5ea767fa48a01239ca01ed01989a89a2e`, identical to commit `8a51021ca463da1e3ea279bf5a94e998267751bf`.
- AMENDMENT_RECORDED A1–A32. Exact before/after/master-rule/reason/approval in selected fast ledger; prior invoked→governing exact unified JSON diff in selected review ledger under Governing master diff. Recorded approval is respected. A31/A32 human2026-11-06「按总 A-5 补齐」supersedes historical06 expiry-close behavior through five-file08, preserving all12 manual items and joint sequential release gate.
- Durable repair handoff latest section explicitly records human exact execute-p invocation and READY_FOR_VERIFICATION. Executor identities `omp coding assistant`, `HiddenConfirmRegression`, `NativeVisibilityRepair` differ from reviewer; optional global parent executor identity unavailable, not invented or treated as a mandatory blocker.
- Prior product `fc2750cd7ad4ae5200195036d874aeeb8b5ae400`→current code changes exactly three approved repair files: `src/main/resources/static/mailbox-chat.js`, `src/test/js/mailboxDelayedReply.test.js`, `src/main/resources/static/index.html`. Full base→current combined product/test boundary is58 files. Current worktree product matches code; index initially clean; only controller's selected review ledger dirty.

| Artifact | SHA256 at review entry |
|---|---|
| `docs/plans/2026-11-04/manual-reply-delay-01-storage.md` | `60d362e4c9c5155120f1f21c06008a434238bc83b4b8902d11ba441b929dd2a6` — equal recorded commit `05342b4be63bbc7d9b30416ae73ada2d44b562df` |
| `docs/plans/2026-11-04/manual-reply-delay-02-preparation.md` | `2849fccb0b6b864773506061745c779a9c78ce094715a20b52d2992ec564b951` — equal recorded commit `ab3ad56713a74664b0f34f66a138206cb14666ea` |
| `docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md` | `d3c5cdddd4daf0c3a58fef12036b0d6dd27b6546c70048b1e4528c6a00ac7b82` — equal recorded commit `0bfe5b07c85e76f85c43a31d30fde8b37f30b9b9` |
| `docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md` | `56da95916445b1137b6c1127f8d64209f6e58b24bd8c981fa668e7ebd37a4878` — equal recorded commit `de076f6413b65809ca13610c0455965134056dcb` |
| `docs/plans/2026-11-04/manual-reply-delay-05-query.md` | `e3c61cd9704326828a92b16a48e38eccd5c877ee9d3bb05a0c74c37c3b56f52f` — equal recorded commit `baa9ba43a9ddf8c39ff2feae1c91c3c76fea2acb` |
| `docs/plans/2026-11-04/manual-reply-delay-06-frontend.md` | `554afdd838dbdd9adf7f8fd1f5249b0233293f625a10042a6548b22d012103d8` — equal recorded commit `a5c1ddb90e92b5aef7bf8f16b031dd4d51c49b80` |
| `docs/plans/2026-11-04/manual-reply-delay-07-source-contract.md` | `109d6996fb968afc2e698a6fd667beb1ecad9075f17f35f831606523bf668a77` — equal recorded commit `525aeeb9e97241d5ad044f5662c75194db1a5944` |
| `docs/plans/2026-11-04/manual-reply-delay-07-activation.md` | `b2d00f1e370e4307644c336a3c4c63d66c9c44b00a0a44c3e47347bcbc180e60` — equal recorded commit `8a51021ca463da1e3ea279bf5a94e998267751bf` |
| `docs/plans/2026-11-04/manual-reply-delay-08-deadline-confirmation.md` | `991aa66575c7e22efe2143b57a3571ec4a8c4a153e392152f80d827486977072` — equal recorded commit `8a51021ca463da1e3ea279bf5a94e998267751bf` |
| `docs/plans/2026-11-04/manual-reply-delay-master.md` | `4fa1f45fc2830aea7c138e3da44a60d5ea767fa48a01239ca01ed01989a89a2e` |
| `docs/plans/fast/manual-reply-delay-master/ledger.md` | `76dadc5a2039d622ffdb1cd3423bc9f2629a024f4e4dc37b539560528d61a31b` |
| `docs/plans/fast/manual-reply-delay-master/human-review-handoff.md` | `82089ede965eb02123c4f26bebb806c1a57c93b1fe18fb0c54141d067d14861c` |
| `docs/plans/fix/manual-reply-delay-master/repair.md` | `f96501759344c97dd27cc6e7bce2cbc3f842c703ddd376a03f7996e15652ef03` |
| `docs/plans/review/manual-reply-delay-master/repair-execution.md` | `edbd6ae2185512489366681af0a7aa6025f510725e8453f777047b27cbb6bb14` |

All36 child `brief.md`, `execution.md`, `verify-log.md`, `fix-log.md` artifacts read as unverified claims; ordered children01,02,03,04,05,06,07-source-contract,07-activation,08. Every plan matches its recorded identity commit; exact hashes, artifact byte lengths and full authority mapping: `E/identity-scope.json`. No child verdict substituted for this review. Stable V-1…V-5/B-1/O lineage retained.

### Environment and fresh execution boundary

Maven3.9.11 and all required compile/JVM commands use exact Zulu11.0.15 (`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`), Node25.7.0, Docker29.8.2, macOS27.2 aarch64. Real app uses installed Zulu11.0.32.1+1-LTS at `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home/bin/java`; this patch difference is explicit. Default Java25 is not used for review gates. `E/environment-and-diff-check.json`, `E/docker-version.json` preserve actual outputs.

Owned MySQL8.0.46 `aggregate-manual-reply-epoch3-db`:33501, isolated `aggregate_review` for Maven and `aggregate_runtime` for runtime. Explicit JDBC `jdbc:mysql://127.0.0.1:33501/aggregate_review?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai`; root/local-only disposable credential. Opt-in mysqlIt/migrationIt enabled. Testcontainers execute real independent database cases. No development/production DB used. C-2 and runtime overlap only on separate databases; C-3 reuses completed current compile boundary, no parallel Maven target writers.

Owned app18201, socket SMTP25501/control25502, Greenmail2.1.3 IMAP31501/injectionSMTP30301, ES7.17.26:19301. Only `@example.test` recipients; no external delivery, deployment or real recipient. Original mixed MySQL collations retained. Production feature defaultfalse unchanged; local app explicitly enabled except disabled-restart case. Other mail queues/discovery/LLM/translation disabled. Exact launch `R/starts.json`; current classes after compilation and explicit current source static directory. No fake SMTP service method, altered clock, shortened60000ms delay, shortened600000ms recovery, mutated runtime due/claimed timestamps, or prior screenshots reused.

### Fresh mandatory commands

All exact commands below run from W; Maven uses the explicit Java11.0.15 and disposable datasource above. Raw command failure is retained, never relabeled green by the aggregate classification.

#### C-1 compile

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -DskipTests test-compile
```

Exit0; quiet log `E/compile.log`, receipt `E/compile.exit`. No test count inferred.

#### C-2

```sh
mvn test '-Dtest=PendingMailOperationServiceTest,ManualReplyDelayEndToEndIT,DelayedManualReplySnapshotTest,DelayedManualReplyControllerTest,DelayedManualReplyWorkerTest,DelayedManualReplyLifecycleIT,DelayedManualReplyQueryIT,MailReplyDraftServiceTest,MailReplyDraftControllerTest,MailReplyDraftRepositoryIT,MailReplyDraftSendIntegrationTest,MailReviewDraftIT,MailReviewSendIT,MailReviewQueryIT,!MailReviewQueryIT#size100*,FlywayMigrationIntegrationTest,MailboxConversationRepositoryIT' -DmysqlIt=true -DmigrationIt=true
```

Exit1; 358 run/357 pass/1 failures/0 errors/0 skipped; elapsed1307.313s. Raw `E/jvm-C2.log`; fresh XML `E/jvm-C2-reports/`.

| Class | Run | Fail/error/skip |
|---|---:|---|
| DelayedManualReplyLifecycleIT | 44 | 0/0/0 |
| MailReviewQueryIT | 4 | 0/0/0 |
| DelayedManualReplyControllerTest | 16 | 0/0/0 |
| DelayedManualReplySnapshotTest | 14 | 0/0/0 |
| ManualReplyDelayEndToEndIT | 30 | 0/0/0 |
| DelayedManualReplyWorkerTest | 9 | 0/0/0 |
| MailReviewSendIT | 18 | 1/0/0 |
| PendingMailOperationServiceTest | 49 | 0/0/0 |
| MailReplyDraftControllerTest | 6 | 0/0/0 |
| MailboxConversationRepositoryIT | 41 | 0/0/0 |
| MailReplyDraftServiceTest | 14 | 0/0/0 |
| MailReplyDraftSendIntegrationTest | 27 | 0/0/0 |
| FlywayMigrationIntegrationTest | 49 | 0/0/0 |
| DelayedManualReplyQueryIT | 10 | 0/0/0 |
| MailReplyDraftRepositoryIT | 8 | 0/0/0 |
| MailReviewDraftIT | 19 | 0/0/0 |
#### C-3

```sh
mvn surefire:test exec:exec@node-test exec:exec@node-check-app exec:exec@node-check-task-modal-runtime '-Dtest=ManualReplySendAttemptServiceTest,PendingMailOperationServiceTrustWorkbenchTest,ExpertMailPermissionGuardTest,MailboxConversationRepositorySqlCompatTest,OperatorStatusWriteSeamGuardTest,OutboundAttachmentFlowTest,OutboundAttachmentServiceTest,MailboxReadAccessProjectionTest,MailboxAttachmentServiceTest,RagSendBridgeTest,InboundPendingOwnerIT,ExpertMailReadAccessIT,MailReviewQueryIT#size100*' -DmysqlIt=true
```

Exit1; 225 run/221 pass/2 failures/2 errors/0 skipped; elapsed9102.648s. Raw `E/jvm-C3.log`; fresh XML `E/jvm-C3-reports/`.

| Class | Run | Fail/error/skip |
|---|---:|---|
| OperatorStatusWriteSeamGuardTest | 1 | 0/0/0 |
| InboundPendingOwnerIT | 8 | 1/2/0 |
| MailReviewQueryIT | 1 | 1/0/0 |
| MailboxReadAccessProjectionTest | 9 | 0/0/0 |
| RagSendBridgeTest | 11 | 0/0/0 |
| ManualReplySendAttemptServiceTest | 62 | 0/0/0 |
| MailboxAttachmentServiceTest | 7 | 0/0/0 |
| OutboundAttachmentFlowTest | 9 | 0/0/0 |
| OutboundAttachmentServiceTest | 28 | 0/0/0 |
| PendingMailOperationServiceTrustWorkbenchTest | 60 | 0/0/0 |
| ExpertMailReadAccessIT | 16 | 0/0/0 |
| ExpertMailPermissionGuardTest | 11 | 0/0/0 |
| MailboxConversationRepositorySqlCompatTest | 2 | 0/0/0 |

C-2 only assertion failure O-1 Subject. C-3 O-2 size100 and optional O-3 owner fixture failures/errors; required write-site guard1/1 passes. Combined583 run/578 pass/3 assertion failures/2 errors/0 framework skips. Flyway49/49 completes in694.771s. C-3's9102.648s includes host sleep; ExpertMailReadAccessIT16 passed but its elapsed8977.549s is explicitly affected. No test outcomes inferred from elapsed time.

Requested C-3 selection uses `mvn test` with the same `-Dtest` and `-DmysqlIt=true`. Actual lifecycle-equivalent command shown above reuses unchanged C-1/C-2 compiled outputs and invokes Surefire plus exact bound Node goals from pom. C-2/C-3 both stop at Surefire failures, so subsequent Maven Node goals do NOT execute. Fresh standalone N-5 and app/task-modal syntax supply those gates. No other Maven target writer, opt-in omission, or optional all-JVM expansion. Failures and full traces: `E/jvm-failures.json`; unchanged-base causal receipts: `E/causal-provenance.json`. No dynamic base run claimed.

#### Node and syntax gates

| ID | Exact command | Exit;run/pass/fail/skip |
|---|---|---|
| N-1 | `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailboxServerDrafts.test.js src/test/js/mailReviewAdmin.test.js src/test/js/mailboxChatStyle.test.js` | 0;198/198/0/0 |
| N-2 | `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailReviewAdmin.test.js src/test/js/expertAccessWorkspace.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxServerDrafts.test.js` | 0;251/251/0/0 |
| N-3 | `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailboxChatStyle.test.js` | 0;119/119/0/0 |
| N-4 | `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxMailReadAccess.test.js src/test/js/mailboxOutboundAttachments.test.js src/test/js/mailboxReplyIdentity.test.js src/test/js/mailboxSuspension.test.js src/test/js/meetingConfirmationIntegration.test.js src/test/js/authFlow.test.js src/test/js/mailReviewReader.test.js` | 0;445/445/0/0 |
| N-5 initial sandbox | `node --test src/test/js/*.test.js` | 1;2116/2115/1/0 |
| N-5 authorized rerun | `node --test src/test/js/*.test.js` | 0;2116/2116/0/0 |
| N-6 | Individual commands below | All19 exit0; syntax checks, no test count |

Initial N-5 failed only because sandbox denied writing generated `target/discovery-plan-acceptance/08.html`;2115 other tests passed. Original failed log/receipt retained. Narrow authorized full rerun passed2116,0skip; `E/node-5-authorized.log` and `.exit`. No test/product edit. N-1…N-4 counts198/251/119/445 all pass,0skip. Exact argv/times/logs in `E/node-receipts.json`.

```sh
node --check src/main/resources/static/app.js
node --check src/main/resources/static/mailbox-chat.js
node --check src/main/resources/static/mailbox-readonly.js
node --check src/test/js/authFlow.test.js
node --check src/test/js/expertAccessWorkspace.test.js
node --check src/test/js/mailReviewAdmin.test.js
node --check src/test/js/mailReviewReader.test.js
node --check src/test/js/mailboxChatBehavior.test.js
node --check src/test/js/mailboxChatStyle.test.js
node --check src/test/js/mailboxDelayedReply.test.js
node --check src/test/js/mailboxMailReadAccess.test.js
node --check src/test/js/mailboxOutboundAttachments.test.js
node --check src/test/js/mailboxReadOnly.test.js
node --check src/test/js/mailboxReplyIdentity.test.js
node --check src/test/js/mailboxServerDrafts.test.js
node --check src/test/js/mailboxSuspension.test.js
node --check src/test/js/meetingConfirmationIntegration.test.js
node --check src/main/resources/static/trust-reply-workbench.js
node --check src/main/resources/static/task-modal-runtime.js
```

#### Required command coverage mapping

The approved child briefs and plans were independently resolved; ranges are combined once against the master, not reaccepted child by child.

| Original required range | Fresh evidence |
|---|---|
| 01 compile / Flyway migrationIt=true | C-1; C-2 Flyway49 |
| 02 Pending / Trust / Attempt / Snapshot, A1 RagSendBridge | C-2 Pending49/Snapshot14; C-3 Trust60/Attempt62/Rag11 |
| 02 DraftSend mysqlIt | C-2 27 |
| 03 Attempt / DraftService | C-3 62; C-2 14 |
| 03 Lifecycle / DraftRepo / DraftSend / ReviewDraft / full ReviewQuery mysqlIt | C-2 44/8/27/19/4; C-3 size1001, actual O-2 failure |
| 03 Pending / Trust | C-2 49; C-3 60 |
| 04 Worker / Controller / Guard / Snapshot / Lifecycle | C-2 9/16/14/44; C-3 Guard11 |
| 05 Query mysqlIt / MailboxRepo mysqlIt isolated datasource | C-2 10/41 |
| 05 SqlCompat / WriteSeam / attachment / read projection | C-3 2/1/9/28/9/7, all pass |
| 06 Controller / focused / affected / full JS / each changed JS syntax | C-2 Controller16; N-1/N-3/N-4/N-5/N-6 |
| 07-source six prescribed ranges | C-1; C-2 Snapshot14/Controller16/Worker9/Pending49/Lifecycle44; N-3 includes full DelayedReply; N-5/N-6 |
| 07-activation E2E/original draft-review/Flyway/Mailbox/enabled ITs | C-2 358 plus C-3 uncovered ranges; no opted-out IT |
| 08 exact six-file group / full JS / chat syntax / native surfaces | N-2/N-5/N-6; R-2 below; fresh actual hidden/resume R-13/R-14 passes |
| Prior repair exact backend class range / frontend groups / syntax / live repair probes | C-2 plus C-3 WriteSeam; N-2/N-3/N-5/N-6; R-1/R-5/R-2 |
| Optional broad InboundPendingOwnerIT and ExpertMailReadAccessIT diagnostics retained from supplied command | C-3 8 (O-3),16 pass; no expanded repair scope |

### Fresh real runtime evidence

All R rows use current source/current compiled code and this epoch's fixtures. Full argv/exits/durations: `R/chain-receipts.json`, `R/post-receipts.json`, `R/supplement-fresh-receipts.json`, `R/recovery-receipts.json`; direct-CDP `.exit` files record separate commands. A wrapper exit0 never overrides a child failure. Failed probes remain present; successful prefixes and narrowly completed tails are explicitly distinguished.

| ID | Exact fresh script(s), all relative to R | Exit and observations |
|---|---|---|
| R-1 | `python3 repair-backend-corrected.py` | 0;6.847s. Original0900 suppression/server versus unicode contact metadata; real messages GET200. Unrelated suppression allows actions, matching normalized suppression disables, READ no private/action authority. Two legal4,200,019-byte INBOUND/OUTBOUND snapshots return413 SNAPSHOT_INVALID; job/attempt/record/calendar counts unchanged. `repair-backend-final.json` and HTTP records. |
| R-2 | `node native-retry/native.cjs` | 0;269.029s. Actual ordinary/admin REVIEW/delegated FULL: open due−913/−867/−846ms, confirm due+1151/+1165/+1129ms. Each native trusted click causes exactly1 POST captured revision→409 DELAYED_REPLY_TOO_LATE→1 action-owned GET; actual SENT, no false cancellation. Holds genuine GET only, never fabricates responses/clock/DOM. READ direct mutations403/no controls, FULL→READ403 clears, expired dismiss cannot reopen; frozen REVIEW/body/version and attachments intact. |
| R-3 | `python3 restart-proof.py` | 0;673.624s. Real SMTP DATA kills owned process. Restart same DB flag=false: WAITING job8 retains original due and sends exactly once; new90903 synchronous SENT/delayedReply null/job0. Claimed job7 stays SENDING until real600000ms recovery→UNKNOWN, retry409, exact1 DATA for its Message-ID, no fake successful record/no resend. Complete before host sleep. `restart-final.json`, SQL/HTTP/MIME. |
| R-4 | `node final-runtime3.cjs`; `node final-runtime-tail.cjs` | 1/0;177.399s/4.553s. Completed prefix: actual ordinary DRAFT with txt attachment; refresh/switch same due;40s open+dismiss and50s confirm cancel; original body/context/ordered attachment/version retained and editable, zero record after due. Real451 FAILED→dismiss unchanged→explicit retry new≥59s WAITING. Meeting ordinary DRAFT: real preview, reverse-ordered binary files, calendar0→1 only SENT, original draft SENT/contentnull/version+1, exact ICS/binary downloads. Initial final retry read encountered WAITING during host sleep; tail checks same unchanged-due job eventually SENT exactly1, same card/error removed; no new retry or repeated send. |
| R-5 | `node repair-native.cjs` | 0;22.798s. Two independent Chrome processes: legacy old editor versus newer ordinary saved body/two ordered attachments. Cancel/reload/native restore warns before legacy mount; exact before/after ordinary draft equality; old original readable. Clean no-conflict path restores original old editor, target id/contentnull/version0, no synthetic persisted draft. `repair-native/conflict.json`, `repair-native/final.json`. |
| R-6 | `node error-surfaces.cjs`; `node error-recovery.cjs` | 1/0;first interrupted by sleep/HTTP timeout, fresh replacement66.705s. Actual550→FAILED PERMANENT_DELIVERY_FAILURE and real DATA-disconnect→UNKNOWN DELIVERY_UNKNOWN, both no retry/cancel/confirmation. Icon17×17px rgb(197,62,50), role/img/aria/keyboard target, hover tooltip, no1440 overflow. `native-error-recoverys.json`, current screenshots; interrupted originals retained. |
| R-7 | `node legacy-native-complete/legacy-native.cjs`; `node legacy-inbound-complete/legacy-native.cjs` | Initial sleep-era launch failures1/1 before behavior; same unfinished commands0/0 in6.455s/6.181s after wake. Native cancel→reload→restore→requeue same job/new revision/new full due; OUTBOUND original UUID/real anchor/no draftRef; INBOUND original processing/no fake anchor. Ordered restore/raw semantics separately R-15. |
| R-8 | `python3 remaining-review4.py` | Initial1 at login timeout; unfinished scenario0,126.609s after wake. Fresh READ author/admin APPROVED; enqueue/cancel keeps body/version/state; stale requeue409/current revision works; actual Greenmail inbound triggers REVIEW_CONTEXT_CHANGED with no send; explicit context confirmation→retry→real second60s→SENT. READ cannot cancel REVIEW. `remaining-review4-final.json`. |
| R-9 | `python3 remaining-nontarget.py`; `python3 nontarget-tail.py` | 1/0;tail3.947s. First completed real60s RAG delivery, exact ordered facts, QA0 and unchanged manual quota, direct template SENT, then first actual IMAP request only initialized cursor and fetched0. Tail uses a new real inbound after cursor initialization: automatic meeting invitation1; ES/manual batch SUCCESS1/failure0/skip0; non-target delayed rows0. Completed RAG/direct checks not rerun; raw original prefix retained. `nontarget-final.json`. |
| R-10 | `node ui-lifecycle-final.cjs` | Initial1 during host interruption; unfinished scenario0,23.282s. Real offline/online refresh, genuinely held GET→switch expert→old response cannot repaint, logout removes cards. Its Playwright visibility remained visible and is NOT hidden evidence. `ui-lifecycle-final.json`. |
| R-11 | `node pagination.cjs` | 0;15.072s. Actual mixed two-account same-second outbound/inbound+WAITING/CANCELLED:50→100→133 unique source/id keys (one intermediate100 render observation), secondary account65 exact; historical FAILED no retry. Fresh Query10/Mailbox41 cover counts/date/direction/rank/cursor and retry→SENT union. `pagination/final.json`. |
| R-12 | `python3 mime-proof.py`; `python3 meeting-mime-proof.py` | Native MIME0; meeting first1 due temporary MySQL-client output charset decode, corrected evidence reader0. Three native surfaces exactly1 actual MIME each, subject/HTML/text/thread equal frozen snapshot, no premature DATA. Text/plain LF→CRLF normalization separately disclosed O-6. Meeting ordinary DRAFT13: exact ICS preview/download/wire hash; second.bin5 bytes then first.bin6 bytes, exact stored/download/wire SHA/order; SMTP due+966ms and1 MIME. `native-mime-proof.json`, `meeting-mime-final.json`. |
| R-13 | `node visibility-direct.cjs`; `node visibility-direct-rest.cjs` | Core command1 only after completed admin/FULL evidence when separate READ setup interval failed; final tail0. Actual raw-CDP headed Chrome same-window tabs, no Playwright attachment/focus emulation/visibility or clock overrides. Admin1440/FULL390 each32 hidden samples: confirmation0, hidden conversation GET0, automatic mutation POST0; visible fresh GET; job unchanged except serverNow; editor/attachment unchanged; old node detached; legal reopen+explicit confirm exactly1 current-revision POST200 each. Tail retry32 samples has same cleanup/readback, legal fresh reopen exactly1 retry POST200. `visibility-direct-progress.json` (two complete results), `visibility-direct-rest-final.json` (retry), `E/native-visibility-consolidated.json`. |
| R-14 | `node visibility-direct-rest.cjs` | 0. Separate READ branch uses actual readonly renderer `[data-delay-id]`, waits current content before hiding.32 genuine hidden samples/no conversation GET/no actions or confirmation; visible immediate GET, unchanged job apart from serverNow, no automatic mutation. `visibility-direct-rest-final.json` READ result. |
| R-15 | `node source-anchor-corrected/source-native.cjs` | 0;6.679s. No-draft OUTBOUND omits optional raw anchor; server binds930020. Insert newer SENT930021 after cancellation, then reload/native restore/requeue retains actual930020, original UUID/raw HTML/raw text whitespace/ordered binary IDs/no draftRef, same job/new revision;1440/390 no overflow. |
| R-16 | `node list-only.cjs` | 0;62.887s. Real451 after full60s, icon appears by live list polling while0 selected messages. Native Tab/Shift+Tab makes parent focus-visible and approved generic tooltip「有1封邮件发送异常，打开会话查看」display; no card-specific reason substituted. `list-only-final.json`, screenshot/network. |

Actual Chrome157.0.8103.39. Desktop1440/mobile390 computed layout: existing footer,12px action gap,7px confirmation gap,11px/16.5px status, keyboard2px blue outline/2px offset, mobile buttons44px, no inline styles/dialog/overflow. `native-retry/desktop1440-keyboard-layout.json`, `mobile390-keyboard-layout.json`; R-6 actual17px/red error icon. Current screenshots independently inspected: ordinary/delegated late-confirm, raw hidden/resume admin/FULL, fixed public error. UI preserves editor and frozen review rather than introducing a separate form.

Actual network-served/current-source SHA256 equality on ordinary/REVIEW/FULL/READ/revocation/dismissal surfaces:

| Asset | Current served and source SHA256 |
|---|---|
| index.html | `29f20cf6ee25d9edd3c155dc4785b08c09f378fa8ebf5505cacfbcbcfd018d45` |
| mailbox-chat.js | `a9a112eeaef082280837949292cb083e9d9f8c935e38bbeafb19dd98d36be07d` |
| mailbox-chat.css | `9ac18fb00fcf5d565cb05489c4c77bde963f45a016ff614191f82569de38df24` |
| styles.css | `464ebaf5206af9e699a850758568e1f776f5fc785564bbb596e6b02a13b122b7` |

Manifest/raw bodies: `R/native-retry/native-served-sources.json`, `browser-served-*`. All20 original versioned references use `20261106-manual-reply-visibility`, same order/URLs; task runtime remains unversioned. No historical08 SHA treated as current.

#### Failed probes and environmental interruptions — preserved

- Initial N-5 sandbox target-write EPERM retained alongside successful authorized rerun; no code changed to make it pass.
- macOS17:31:33 Clamshell Sleep, repeated Sleep/DarkWake,20:00:31 FullWake: `E/host-sleep-wake.log`. Runtime Hikari43m37s/34m17s/1h10m54s starvation/clock-leap warnings and stale MySQL connections match the pause. C-3 actual elapsed9102.648s retained. R-4 final read WAITING, original R-6 timeout, R-7 Chrome launch timeouts, R-8 login timeout, R-10 closed-browser failure are not PASS. Only unfinished/invalidated branches were completed after wake; earlier three-host late409,673.624s real crash recovery and conflict proof were not rerun or borrowed. Temporary owned `caffeinate -i -t3600` used for completion, then terminated; no permanent power setting changed.
- R-9's first IMAP call initialized the real mailbox cursor without importing history, so fetched0. RAG/direct prefix had already passed. New inbound tail completed the missing automatic/batch scenarios; original assertion1 remains. The disposable account had been corrected to actual `sender@example.test` inbox before probes; an initial diagnostic used a nonexistent `email_accounts` table and made no change, followed by exact `mail_sender_account` fixture correction. No product or real mailbox changed.
- `visibility-fresh.cjs` exited1 before scenario because initial unauthenticated logout was401 (helper only allowed200/204). `visibility-recovery.cjs` then timed out waiting actual hidden under Playwright-attached Chrome, which remained visible/continued polling; neither is hidden evidence. Raw CDP replaced that facility.
- `visibility-direct.cjs` completes admin/FULL and preserves every sample/readback/native action before READ's selection-to-render setup produced an interrupted hidden interval. Separate tail initially failed before behavior on a rewritten evidence-reader path (`ENOENT`); corrected tail then waited for a writable-host `data-message-key` that the independent readonly renderer does not have. These failures remain in `visibility-direct-tail*` and `visibility-direct-complete*`. Final `visibility-direct-rest.cjs` uses observed readonly `data-delay-id`, waits current card, and completes only READ/retry. No admin/FULL repetition, fabricated visibility or substituted response; wrapper1 is never called0. Final consolidated successful intervals contain128 actual hidden samples across four branches.
- Meeting MIME reader initially let mysql client output use its default charset; a Chinese draft meeting-context field caused UTF-8 decode failure before assertions. Added only `--default-character-set=utf8mb4` to temporary SQL reader, preserving initial helper/log and DB bytes. Exact wire binary/ICS proof then passes; no data normalization.
- Current R-5 helper initially prepared to expect documented empty DTO id/contentnull/version0, and current list-only helper uses approved generic list text/native keyboard modality before running. Historical epoch2 failures from those helpers are not replayed or presented as fresh evidence. All reused harness source identities/adaptations are in `E/harness-reuse.json`; copies of unused old diagnostic scripts are not executed evidence.
- Read-only diagnostic attempts at nonexistent `src/main/java` paths were corrected to this Kotlin repository; no source mutation. Application optional ES profile404 warnings before fixture indices are nonfatal; actual ES non-target scenario later succeeds. Native late409 surfaces report pageErrors0; asynchronous observer response reads after page closure are harness lifecycle errors, not product script exceptions.

`E/interrupted-probes/` preserves pre-completion copies of JSON/logs/screenshots where a rerun uses the same result filename. Original command logs, failed exits, assertions and event streams remain in the archive. No mandatory check is left dependent solely on a failed probe.

### Contract Matrix

All machine rows below are mandatory unless explicitly marked otherwise. Source authority: governing master Requirements/Invariants/Acceptance; style S-1–S-4 from06; C-1–C-7 from07-source;08 as recorded A31/A32. Human acceptance remains separate PENDING; machine exercise never supplies a human signature.

| ID | Requirement / required evidence | Verdict | Fresh evidence |
|---|---|---|---|
| O-1 | Three manual entry paths, durable server60s | PASS | C-2 E2E30, Lifecycle44; R-2/R-3/R-4/R-7/R-8/R-15 |
| O-2 | Footer cancel/retry confirmation and real explicit action | PASS | N-1/N-2/N-3; R-2 late409, R-4 dismiss/cancel/retry; R-13 actual hidden clears stale controls and permits new explicit action |
| O-3 | List/card fixed public failure; UNKNOWN never retry | PASS | R-4/R-6/R-16; Query10; Node full |
| O-4 | Preserve ordinary original/attachments and frozen APPROVED | PASS | R-5 conflict+clean native, R-8; ordinary/review binding and restore source inspected |
| N-1 | Bulk/auto/expert template/old meeting paths unchanged | PASS | R-9 real ES/IMAP/SMTP; no non-target delay jobs; full write-entry diff and unchanged meeting service path |
| N-2 | Permissions, files/source/current thread, review context | PASS | Guard11, Controller16, Lifecycle44, E2E30; real READ/403/context R-2/R-8/R-15 |
| N-3 | Final HTML/text, safety, ordered QA/RAG, bytes/ICS, quota | PASS | C-2/C-3; R-9 manual quota/facts, R-12 exact binary/ICS; O-6 text-wire normalization disclosed |
| N-4 | Only SENT success/close/calendar; no HTTP timeout duplication | PASS | Attempt62/Lifecycle44 atomic failure injection and idempotence; R-3 actual kill+unknown; R-4 |
| N-5 | Filters/counts/pagination/account isolation/READ/mobile | PASS | Query10, Mailbox41, R-11 133 unique rows/two accounts; R-2/R-14/R-15; V-1 repaired without collation alteration |
| I-1 | DB+60000ms; no early claim; original repeated due; retry full round | PASS | DelayedManualReplyService.kt:116/287/364; Lifecycle real CAS; R-2/R-3/R-4/R-8 actual time |
| I-2 | Cancel/claim exclusive, deadline CAS, SMTP after commit | PASS | DelayedManualReplyService.kt:241/364; Lifecycle races; worker transaction separation; actual late409 three hosts |
| I-3 | Original fingerprint, UUID/Message-ID, origin/actor/revision | PASS | ManualReplySendAttemptService.kt:185/248; Attempt62; Lifecycle44; R-7/R-15 same job/UUID/new revision; HTTP stale403/409 |
| I-4 | Complete frozen whitelist payload, restart/bytes/size contract | PASS | Models.kt:255/659, Pending.kt:978/990/1028; Snapshot14, E2E30; R-1 exact413 and zero effects; R-3/R-12 |
| I-5 | Atomic success, definite fail vs UNKNOWN, no auto retry | PASS | ManualReplySendAttemptService.kt:630/803; Lifecycle trigger-induced QA/RAG/calendar/job/review rollback; R-3/R-4/R-6/R-8 |
| I-6 | All draft writers guarded; precise release/restore; review APPROVED | PASS | DraftService/Repo, ReviewService/Repo, Service enqueue/cancel; C-2; R-5 preserves new version/body/ordered files, R-8 frozen review |
| I-7 | Current HTTP permission, persisted actor preflight, no null admin | PASS | Guard, Controller, Pending rehydrate1028, Query; suppression mixed rules R-1, real READ/403 R-2, context R-8; post-SMTP finalize independent of later grant |
| I-8 | Stable rank3/job.id/createdAt; one union row/count; raw review context | PASS | MailboxRepo.kt:290 and UI/raw union; DelayedManualReplyService.kt:280/424; Query10/Mailbox41; R-4 retry→SENT one record/card; R-11/R-8 |
| I-9 | Server-authoritative UI, owner/generation, polling lifecycle cleanup | PASS | Current stopDelayPolling renders only existing action footer after clearing confirmation; N-2 discriminators; R-10 stale/offline/logout; R-13/R-14 actual hidden/resume and unchanged job; R-16 list polling |
| I-10 | Six job states, unchanged attempts, fixed public errors/historical no retry | PASS | Models/codec/DTO; Flyway49; R-6 fixed errors/UNKNOWN, R-11 historical failed no action |
| I-11 | Defaultfalse, independent bounded worker, old jobs survive, no Rabbit | PASS | Worker.kt:30–41/108; properties/application.yml; R-3 true600000ms crash/restart and disabled-new sync path; no Rabbit service |
| S-1 | Existing footer and wrap-capable inline confirmation | PASS | Literal CSS/DOM and actual1440/390 R-2/R-15; R-13 current footer-only lifecycle cleanup, editor unchanged |
| S-2 | 17px/#c53e32, tooltip/aria, keyboard/mobile44px | PASS | R-2/R-6/R-16 computed styles/native focus; no horizontal overflow |
| S-3 | Existing components and default-preserve conflict warning | PASS | R-5 native warning before legacy mount, exact draft preserved; no overlay/new editor introduced |
| S-4 |20 original resources/same key, source actually served | PASS | Fresh served bytes/source manifest, index current repair key; N-1/N-2/N-3 style/DOM test; task runtime unversioned |
| C-1 | Nullable raw OUTBOUND input separate from verified actual anchor | PASS | Models factory/codec; Snapshot14/E2E30; R-15 omitted input→930020 and later930021 does not replace it |
| C-2 | Typed mutually exclusive legacy source, old snapshot compatibility | PASS | Models codec/source predicates; Snapshot14, Controller16, Lifecycle44; R-7 both types |
| C-3 | UUID changes cannot bypass source lock/actor/account identity | PASS | Lifecycle44 real source locking; E2E30; source delta checked; no new identity scheme |
| C-4 | Restore only with permission/current source; no private public projection | PASS | Controller restore validation, READ Query path; R-1/R-2/R-5/R-15 |
| C-5 | Existing renderer/no fake draft, newer content conflict preservation | PASS | R-5 INBOUND two independent browsers; R-7/R-15 OUTBOUND native; correct null-id/version0 target |
| C-6 | Raw reuse only if text+HTML unchanged; retain original UUID/ordered files | PASS | N-3 direct format-only/edited-input regressions; R-15 actual unchanged raw HTML/text whitespace/ordered files/old anchor exact; R-12 MIME |
| C-7 | Owner/target/epoch/revision lifecycle; disabled switch only admission | PASS | C-2/R-3 server/source/disabled contract; R-7/R-15 original sources; R-13/R-14 cleanup without new action authority |
| 08 I-1 | Admission before due distinct from continuing already open confirmation | PASS | R-2 three native late409, expired dismiss no reopen; N-2 fresh expired canCancel=false vs stale true |
| 08 I-2 | Writable/owner/job/revision/state/known-error checks retained | PASS | N-2; real READ/revocation403 cleanup R-2; no authority inferred from stale canCancel |
| 08 I-3 | One captured POST409, action-owned GET, no false cancel/no replay | PASS | R-2 per-surface request logs and actual states; node busy/network/401/403 tests |
| 08 I-4 | Hidden/switch/logout/unmount cleanup, original timers and locked content | PASS | Fresh native R-13 admin/FULL/retry plus R-14 READ:128 real hidden samples total; R-10 switch/logout; R-2 review locked content; N-2 stale reference and current affordance regression |
| 08 I-5 / S-1/S-2 | Original CSS/DOM/resources, actual current assets | PASS | Current repair changes only helper, regression test and20 existing version values; exact served hash equality; literal style/resource tests; native layout |
| Required command gate | Every prescribed range freshly executed; actual failures retained | PASS execution; raw C-2/C-3 FAIL | C-1; C-2/C-3 combined583 run/578 pass/3fail/2error/0skip; O-1/O-2 unchanged-base causal identity, O-3 optional fixture boundary; N-1…N-6 freshly pass after disclosed sandbox rerun |
| 05 mandatory WriteSeam | Exact owner write-site guard | PASS | C-3 1/1, repaired exact controller line pins237/1169; no whitelist expansion |
| AUTH-SCOPE | Cumulative58 and current repair3 files inside explicit authority | PASS | Complete diff/identity-scope; per-file amendment table below |
| NON-GOALS | No arbitrary scheduling/autoretry/SMTP recall/bulk-recall/push | PASS | Complete cumulative diff; no new shared-table fields/enums or unrelated feature introduced |
| HUMAN A-1–A-12 | Named human/date/results on exact final boundary | PENDING | All twelve retained; no machine result substituted for human acceptance |


### Write/read and cross-plan chain audit

The full58-file combined diff, direct callers/writers and all changed tests were inspected independently, including complete new snapshot/lifecycle/worker/query/controller/end-to-end/frontend regressions. Tests are evidence, not inferred correctness from green counts.

1. Three existing HTTP paths retain preparation/safety/current actor/current source/account/attachment validation before shared admission. Defaultfalse preserves synchronous admission. Typed frozen source carries raw nullable OUTBOUND input separately from verified real anchor; no invented draft, alternate identity or fallback synchronous path when enabled.
2. Snapshot factory/strict codec freeze whitelisted final HTML/text/thread/QA/RAG/ordered file and ICS data, excluding secrets. UTF-8 encoded4MiB limit uses a dedicated size marker for413; malformed input remains distinct. Rehydration verifies original bytes/hash/source and persisted actor without rendering current templates again. R-1 real oversize zero-side-effect and R-12 exact wire proof supplement Snapshot14/E2E30.
3. Lock order remains source→attempt→job, DB time60000ms, exact revision/token/state CAS. First draft admission rejects stale version; requeue/retry refresh only the authorized current source and excludes another unresolved job. Cancel and worker claim are mutually exclusive; SMTP runs after commit, outside short transaction. Lifecycle44 and actual three-host late409 cover these paths.
4. Every ordinary/review draft writer is guarded while queued; cancel releases only matching binding and preserves original content. Success transaction includes mail_record, ordered QA/RAG, meeting calendar, attempt, ordinary/review state, and job. Fault-injection transaction tests establish rollback boundaries; after-commit work cannot downgrade committed SENT. Actual ordinary meeting draft closes only on SENT, calendar0→1; crash never creates false success.
5. Independent worker uses bounded one-thread/SynchronousQueue execution, no Rabbit dependency; old jobs run even with admission disabled, stale SENDING recovers after600000ms to UNKNOWN without resend. Real process kill/restart R-3 confirms DB recovery and unchanged due/Message-ID behavior.
6. Query projection batches at most100 current page/contact/account ids, preserves stable rank3/job.id/createdAt and suppresses the linked record exactly once. Raw review-context rows are separate from display union. READ avoids private snapshot/file authority. Mixed-collation suppression comparison coerces only the parameter expression without altering indexed column/schema; source audit plus Query10 and live original mixed metadata prove the repair.
7. Frontend uses current server job/revision/capabilities, owner/session/target/generation, readback after403/409, and cleanup on hidden/switch/logout/unmount. A31 permits continuing an already admitted late confirmation, while fresh expired opening remains forbidden. Current repair only synchronizes existing footer when clearing non-null confirmation in stopDelayPolling; no timeline/editor repaint.89 added regression lines prove DOM removal, detached stale click0POST, unchanged job/serverNow-only resume, usable new confirmation and retry. R-13/R-14 supplies independent actual native behavior.

Current repair diff:3 files,113 insertions/21 deletions (`E/current-repair-0.txt`, full diff `E/current-repair-1.txt`). `mailbox-chat.js` adds conditional confirm clearing plus `renderDelayActions()`; test gains89 lines; index changes20 existing cache values. No backend, schema, worker, CSS, permission, polling interval, countdown/deadline or draft semantics change in this repair.

### Amendment authority and individual amended-file assessment

A1–A32 exact before/after identities, master rule, concrete reason and original approval text are in the unchanged hashed fast ledger. Specific authority: A1 RAG success transaction; A2–A4 review revision moved into03; A5/A6 necessary existing-test compatibility; A7/A8 original legacy inputs; A11 internal read-only interfaces; A15 source/response contract completion; A31/A32 explicit HUMAN:2026-11-06「按总 A-5 补齐」. Standing approvals do not authorize unrecorded paths or broaden behavior. A31's five-file08 follows07 and preserves every original manual item, production defaultfalse and combined release gate; no authority conflict or unapproved amendment found.

Prefixes below are relative to exact W: K=`src/main/kotlin/com/weibo/talentintroduction/`; T=`src/test/kotlin/com/weibo/talentintroduction/`; S=`src/main/resources/static/`; J=`src/test/js/`. Every row is mandatory and assessed individually; shared-file rows retain every applicable amendment. PASS in this table means the change follows the recorded amendment's rule; all current behavioral decisions are independently established by the fresh matrix above; V-5 is now resolved.

| Amended file | Authority / master rule | Individual assessment and current proof |
|---|---|---|
| Tmail/RagSendBridgeTest.kt | A1 / I-5 | PASS. Assertions follow fact persistence into final transaction, not after-commit success; C-3 11/11. |
| Kmail/service/MailReviewDraftModels.kt | A2/A3/A4 / I-3,I-6 | PASS. Only already-approved delayExpectedRevision DTO handoff/strict current round; no new user capability. C-2 review/E2E. |
| Tmail/repository/MailReplyDraftRepositoryIT.kt | A5/A6 / I-2,I-6 | PASS. Real READ_COMMITTED transaction wrapper satisfies production lock contract; existing state/content assertions retained,8/8. |
| Kmail/service/DelayedManualReplyModels.kt | A7/A8,A23/A24 / I-4,I-6,I-7 | PASS. White-listed original request/meeting restore inputs and typed nullable OUTBOUND source preserve frozen sending/old schema compatibility; missing old restore input fails closed. Snapshot14/R-15; repair size marker is narrower than generic invalid snapshot. |
| Tmail/service/DelayedManualReplySnapshotTest.kt | A9/A10,A23/A24 / I-4,I-6 | PASS. Prepared fixtures now carry required original input; both legacy source/old compatibility/normalization/limits retain real expectations;14/14. |
| Kmail/service/OutboundAttachmentService.kt | A11/A12 / I-7,I-10 | PASS. readVerifiedFile visibility private→internal only, body unchanged; no new file access authority. Attachment28 and live bytes. |
| Kmail/repository/MailReplyDraftRepository.kt | A13/A14 / I-7,I-10 | PASS. Bounded current-contact/account/page-id read API reuses canonical mapper; no locks/writes/new lifecycle. Query10, Repo8. |
| Kmail/repository/MailReviewDraftRepository.kt | A13/A14 / I-7,I-10 | PASS. Same bounded read-only mapper reuse for review; existing events/state intact; Review19/Query10. |
| Kmail/controller/DelayedManualReplyController.kt | A15/A16,A23/A24 / I-6,I-7 | PASS. Validated original processing/actual anchor only on privileged restore response; public READ projection unchanged; Controller16/R-5/R-15. |
| Tmail/controller/DelayedManualReplyControllerTest.kt | A15/A16,A23/A24 / I-6,I-7 | PASS. Direct positive/negative source restoration, permissions and status contract;16/16; not fixture-only source claims. |
| JmailboxChatBehavior.test.js | A17/A18 / I-9,S-4 | PASS. Monotonic clock/SVG namespace/teardown supports added production timer; business assertions retained; N-4. |
| JmailboxReadOnly.test.js | A17/A18,A19/A20 / I-7,I-9 | PASS. Timer/clock fixture updates and retirement of obsolete no-refresh expectation follow required focus refresh; content/scroll/permission/coalescing remain. N-2/N-4/R-14. |
| JmailboxMailReadAccess.test.js | A19/A20 / I-7,I-9 | PASS. Single macrotask drain/clock avoids infinite fake polling while preserving permission semantics; N-4. |
| JmailboxOutboundAttachments.test.js | A19/A20 / I-4,I-6 | PASS. Same bounded timer compatibility; attachment order/download/ownership assertions retained; N-4/R-5/R-12. |
| JmailboxReplyIdentity.test.js | A19/A20 / I-3,I-9 | PASS. Supplies new closure dependencies without loosening owner/source identity assertions; N-4. |
| JmailboxSuspension.test.js | A19/A20 / I-9 | PASS. Clock/single-turn fixture supports periodic read; original suspension assertions retained; N-4. |
| JmeetingConfirmationIntegration.test.js | A19/A20 / N-3,I-4 | PASS. Timer/clock support only; preview/content/ICS integration assertions retained; N-4 and actual R-12. |
| JauthFlow.test.js | A21/A22 / I-7,I-9 | PASS. Supplies closure-local legacy map to extracted-function sandbox; auth/logout behavior preserved; N-4/R-10. |
| JmailReviewReader.test.js | A21/A22 / I-7,I-9 | PASS. Removes only old focus-only-access request assumption, retains editor identity/body/CAS/reload assertions; N-4/R-14. |
| Kmail/service/DelayedManualReplyService.kt | A23/A24 / I-3,I-6,I-7 | PASS. Existing predicates/source identity accept typed legacy source while preserving transaction and locks; Lifecycle44/R-15. |
| Kmail/service/PendingMailOperationService.kt | A23/A24 / O-1,I-4,I-6 | PASS. Captures actual source separately from optional raw input and restores raw text/HTML only under unchanged-content guard; no alternate delivery path. E2E30/R-7/R-15. |
| Smailbox-chat.js | A23/A24,A31/A32 / I-6,I-7,I-9,A-5 | PASS. Narrow existing renderer/source consumer and admitted-confirm predicate follow A31. Native raw/anchor/late409 pass; current bounded footer cleanup closes V-5 under I-9, with actual admin/FULL/retry R-13. |
| Sindex.html | A23/A24,A31/A32 / S-4 | PASS. Existing20 version values updated together, same order/URLs; unversioned task runtime and DOM intact. Current repair cache key also limited to same set. |
| Tmail/service/DelayedManualReplyLifecycleIT.kt | A23/A24 / I-3,I-6,I-7 | PASS. Real dual-source exclusion, identity collisions, retry/recovery/token and transaction regressions retain all previous invariants;44/44. |
| JmailboxDelayedReply.test.js | A23/A24,A31/A32 / I-6,I-9,A-5 | PASS. Native-source/late-confirm/conflict regressions remain relevant; current89-line repair regression also asserts visible DOM cleanup, detached stale POST0, unchanged-job resume and usable new confirmation/retry. N-1/N-2/N-3 and independent R-13/R-14 pass. |
| JmailboxChatStyle.test.js | A25/A26 / S-4 | PASS. Retires old06 cache-key coupling while preserving20 refs/same key/literal CSS/real DOM; no new key pinned; N-1/N-2/N-3. |
| Kmail/controller/MailReviewDraftController.kt | A27/A28 / I-3,I-7 | PASS. Revision allowlist/strict value forwarding and exact delayed exception status only; no permission expansion. E2E30/R-8. |
| Kmail/service/ManualReplySendAttemptService.kt | A27/A28 / I-1,I-3,I-5 | PASS. Internal pure existing-payload lookup and explicit queued-read preparation preserve original strict sync claim and identity; Attempt62/E2E30. |
| Kmail/service/MailReviewDraftService.kt | A27/A28 / I-3,I-6,I-7 | PASS. Defaultfalse queued-read mode enabled only in two approved preparation callers; UNKNOWN and source/context permission remain strict; Review19/R-8. |
| Tmail/service/DelayedManualReplyWorkerTest.kt | A29/A30 / I-11 | PASS. Only final properties use real defaultfalse instance, overrides win, remaining mocks/assertions unchanged;9/9. |
| JmailReviewAdmin.test.js | A31/A32 / A-5,I-6,I-9 | PASS. Shared late-confirm direct consumer/local clock fixture retains APPROVED read-only/body/version restrictions; N-2 and actual REVIEW native R-2. |
| JexpertAccessWorkspace.test.js | A31/A32 / A-5,I-7,I-9 | PASS. Existing delegated FULL/READ host tests directly prove late-confirm and revocation boundaries; native delegated/READ R-2/R-14. |

### Complete cumulative58-file scope

Every path below is in current MASTER_BASE→product diff and independently reconciled to governing child Files/amendment authority. All58 are authorized; no extra product/test file. Scope PASS does not relabel old optional/baseline test failures. K=`src/main/kotlin/com/weibo/talentintroduction/`; T=`src/test/kotlin/com/weibo/talentintroduction/`; S=`src/main/resources/static/`; J=`src/test/js/`.

| File | Governing Files authority | Scope verdict |
|---|---|---|
| Kconfig/ManualReplyDelayProperties.kt | 01-storage | PASS |
| Kmail/controller/DelayedManualReplyController.kt | 04-worker-api,06-frontend,07-source-contract | PASS |
| Kmail/controller/MailReviewDraftController.kt | 07-activation | PASS |
| Kmail/controller/MailboxConversationController.kt | 05-query,07-activation | PASS |
| Kmail/controller/UnmatchedInboundMailController.kt | 07-activation | PASS |
| Kmail/repository/DelayedManualReplyRepository.kt | 01-storage,03-lifecycle,05-query | PASS |
| Kmail/repository/MailReplyDraftRepository.kt | 03-lifecycle,05-query | PASS |
| Kmail/repository/MailReviewDraftRepository.kt | 03-lifecycle,05-query | PASS |
| Kmail/repository/MailboxConversationRepository.kt | 05-query | PASS |
| Kmail/service/DelayedManualReplyModels.kt | 01-storage,02-preparation,04-worker-api,07-source-contract | PASS |
| Kmail/service/DelayedManualReplyService.kt | 03-lifecycle,07-source-contract | PASS |
| Kmail/service/DelayedManualReplyWorker.kt | 04-worker-api | PASS |
| Kmail/service/ExpertMailPermissionGuard.kt | 04-worker-api | PASS |
| Kmail/service/MailReplyDraftService.kt | 03-lifecycle | PASS |
| Kmail/service/MailReviewDraftModels.kt | 03-lifecycle | PASS |
| Kmail/service/MailReviewDraftService.kt | 03-lifecycle,07-activation | PASS |
| Kmail/service/MailReviewSendService.kt | 07-activation | PASS |
| Kmail/service/MailboxConversationService.kt | 05-query | PASS |
| Kmail/service/ManualReplySendAttemptService.kt | 02-preparation,03-lifecycle,07-activation | PASS |
| Kmail/service/OutboundAttachmentService.kt | 05-query | PASS |
| Kmail/service/PendingMailOperationService.kt | 02-preparation,04-worker-api,07-source-contract,07-activation | PASS |
| src/main/resources/application.yml | 07-activation | PASS |
| src/main/resources/db/migration/V174__create_delayed_manual_reply.sql | 01-storage | PASS |
| Sapp.js | 06-frontend | PASS |
| Sindex.html | 06-frontend,07-source-contract,08-deadline-confirmation | PASS |
| Smailbox-chat.css | 06-frontend | PASS |
| Smailbox-chat.js | 06-frontend,07-source-contract,08-deadline-confirmation | PASS |
| Smailbox-readonly.js | 06-frontend | PASS |
| JauthFlow.test.js | 06-frontend | PASS |
| JexpertAccessWorkspace.test.js | 08-deadline-confirmation | PASS |
| JmailReviewAdmin.test.js | 06-frontend,08-deadline-confirmation | PASS |
| JmailReviewReader.test.js | 06-frontend | PASS |
| JmailboxChatBehavior.test.js | 06-frontend | PASS |
| JmailboxChatStyle.test.js | 06-frontend,07-source-contract | PASS |
| JmailboxDelayedReply.test.js | 06-frontend,07-source-contract,08-deadline-confirmation | PASS |
| JmailboxMailReadAccess.test.js | 06-frontend | PASS |
| JmailboxOutboundAttachments.test.js | 06-frontend | PASS |
| JmailboxReadOnly.test.js | 06-frontend | PASS |
| JmailboxReplyIdentity.test.js | 06-frontend | PASS |
| JmailboxServerDrafts.test.js | 06-frontend | PASS |
| JmailboxSuspension.test.js | 06-frontend | PASS |
| JmeetingConfirmationIntegration.test.js | 06-frontend | PASS |
| Tcampaign/OperatorStatusWriteSeamGuardTest.kt | 05-query | PASS |
| Tcampaign/repository/FlywayMigrationIntegrationTest.kt | 01-storage | PASS |
| Tmail/RagSendBridgeTest.kt | 02-preparation | PASS |
| Tmail/controller/DelayedManualReplyControllerTest.kt | 04-worker-api,06-frontend,07-source-contract | PASS |
| Tmail/repository/DelayedManualReplyQueryIT.kt | 05-query | PASS |
| Tmail/repository/MailReplyDraftRepositoryIT.kt | 03-lifecycle | PASS |
| Tmail/repository/MailboxConversationRepositoryIT.kt | 05-query | PASS |
| Tmail/service/DelayedManualReplyLifecycleIT.kt | 03-lifecycle,07-source-contract | PASS |
| Tmail/service/DelayedManualReplySnapshotTest.kt | 02-preparation,04-worker-api,07-source-contract | PASS |
| Tmail/service/DelayedManualReplyWorkerTest.kt | 04-worker-api,07-activation | PASS |
| Tmail/service/ExpertMailPermissionGuardTest.kt | 04-worker-api | PASS |
| Tmail/service/ExpertMailReadAccessIT.kt | 01-storage | PASS |
| Tmail/service/InboundPendingOwnerIT.kt | 01-storage | PASS |
| Tmail/service/ManualReplyDelayEndToEndIT.kt | 07-activation | PASS |
| Tmail/service/ManualReplySendAttemptServiceTest.kt | 02-preparation | PASS |
| Tmail/service/PendingMailOperationServiceTest.kt | 02-preparation,07-activation | PASS |

All individual amendment-rule assessments above are mandatory, including retroactively authorized tests; original explicit approvals and exact before/after identities remain in hashed fast ledger A1–A32. Current three-file repair has its own exact human approval/durable handoff and does not transfer future repair authority.

### Finding Lineage

| Finding | Prior state | Current state | Independent fresh evidence |
|---|---|---|---|
| V-1 P1 mixed-collation messages500 | RESOLVED in epoch2 | RESOLVED | C-2 Query10 includes original/unified metadata; R-1 original mixed server/contact/suppression schema GET200, matching/unrelated suppression and READ semantics; no schema normalization. |
| V-2 P1 oversized snapshot400 instead of413 | RESOLVED in epoch2 | RESOLVED | C-2 E2E30; R-1 two actual legal oversized endpoint bodies413 SNAPSHOT_INVALID and unchanged side-effect counts. Dedicated size marker, no generic malformed-input broadening. |
| V-3 mandatory P2 write-site guard | RESOLVED in epoch2 | RESOLVED | C-3 exact OperatorStatusWriteSeamGuardTest1/1; unchanged strict write-site checking, no bypass. |
| V-4 P1 INBOUND restore skips conflict prompt for newer ordinary draft | RESOLVED in epoch2 | RESOLVED | Current discriminating JS suite plus R-5 two independent Chrome processes: native warning before mount, exact new body/context/two ordered files unchanged; clean old restore succeeds without fake draft. |
| V-5 P1 old confirmation remains visible/inert after hidden/resume | OPEN in epoch2; current authorized repair | RESOLVED | Current stopDelayPolling clears state and renders only action footer. R-13 actual admin/FULL/retry hidden removes old DOM, preserves editor/files/job, resumes with actual GET and allows exactly1 fresh legal POST. N-1/N-2/N-3 added discriminator also checks detached stale reference0POST and only-serverNow change. |
| B-1 required native hidden/show evidence | RESOLVED in epoch2 | RESOLVED; freshly re-established | R-13/R-14 raw CDP actual same-window hidden/resume across admin/FULL/READ/retry,128 samples; every accepted interval hidden,0conversation GET/0automatic mutation, visible prompt GET. Playwright visible-only attempts explicitly excluded. |

Convergence **PROGRESSING** rests on root-cause/behavioral closure of the sole prior open P1 and fresh preservation of earlier repairs, not finding count. `E/causal-provenance.json` records previous helper SHA256 `d587f1a187e5eb33ad9cdb8df31ca749a3548d24354c0a99f3e748726d69f261` versus current `d4fc59751cd762945317757ce4fdcdecff9d7617cadfab17ebdfbe27dbab326c`: the missing footer synchronization is now present. No persistent finding renamed, no repair-induced equal/higher-severity regression established, no mandatory evidence gap remains.

### Findings

#### P1

N/A — V-1,V-2,V-4,V-5 resolved. No new confirmed mandatory implementation violation.

#### P2

N/A — required V-3 resolved. Optional hygiene/fixtures are not promoted to blocking findings or repair scope.

#### Observations

| ID | Current evidence, classification and limits |
|---|---|
| O-1 | C-2 MailReviewSendIT18 has1 assertion failure expected Subject/actual Re: Inbound. Entire test SHA identical to MASTER_BASE; actual create block already derives subject from inbound and is byte-identical, hash95aed274…. Exact base/current text and hashes in causal-provenance.json. Pre-existing adjacent expectation conflict by unchanged causal source, not dynamically rerun on base and not a successful test. Included in358, not edited/excluded. |
| O-2 | C-3 required size100 query case runs and fails “reader never query review table.” Whole test and actual ownDraftRows block are byte-identical to MASTER_BASE, where the block already unions review rows. Fresh Query10/Mailbox41 and current native133-row paging pass. Existing assertion failure retained, no waiver of execution/no out-of-scope repair/no claim of dynamic base reproduction. |
| O-3 | Supplied optional full InboundPendingOwnerIT8 has1failure/2errors: reflection mock cannot mock final ManualReplyDelayProperties; latch failure downstream of fixture; historical migration leaves aggregate_review at172 so later owner projection cannot find delayed_manual_reply. Exact current failure traces in jvm-failures.json. Mandatory current-schema Flyway49/Query10/Lifecycle44/Repository checks independently ran and pass. Optional full owner/ExpertReadAccess selections do not expand repair authority; no optional rerun or restored-schema success borrowed. |
| O-4 | Required Maven11.0.15 versus live runtime11.0.32.1 explicitly disclosed. Optional all-JVM old America/Coyhaique timezone suite not run; no result claimed. |
| O-5 | Current `git diff --check MASTER_BASE CODE -- src` exit0. Whole-document whitespace cleanup remains outside product/repair scope; no docs cleanup or optional all-diff gate added. |
| O-6 | Existing SMTP transport normalizes text/plain attachment LF toCRLF. Native text attachment comparison is explicitly text-normalized, never exact raw bytes. SMTP implementation unchanged by cumulative diff. Separate R-12 binary attachments and ICS prove exact bytes/order/hash, including ordinary meeting DRAFT closure. |
| O-7 | Historical08 prose index hash typo remains metadata-only. Current network/source manifest independently matches exact current hashes and current cache key; no old hash passed off as current. |
| O-8 NEW environment | Actual17:31:33–20:00:31 host clamshell/maintenance sleep interrupted runtime and inflated C-3 timing. Full power/runtime logs, original failures and only necessary completions preserved. Real600000ms crash proof completed before sleep. No clock/timestamp manipulation, product repair or permanent power setting. |
| O-9 NEW harness | Initial logout401 assumption, Playwright visible-only facility, direct READ initial render race, tail evidence path, readonly selector and mysql output charset diagnostics are retained in raw reports. Current successful native/model/SMTP evidence completes each required branch; failed wrapper statuses remain visible. These are not unexplained product failures or missing mandatory evidence. |

### Fast-p RECORD_ONLY re-evaluation

Every retained RECORD_ONLY entry and repeated historical occurrence was re-evaluated against current master, not accepted from a child verdict. Historical F/P lineage remains historical; no prior negative evidence erased.

| Entry | Governing master rule | Epoch3 disposition |
|---|---|---|
| 01 final N/A; historical optional owner boundary | Mandatory compile/Flyway versus optional fixture range | C-1 and C-2 Flyway49 pass; optional O-3 actual failures disclosed, no invented mandatory waiver. |
| 02 O-1 unused injected RAG repository | No proven mandatory behavior violation | Observation only. Constructor compatibility retained; no cleanup. |
| 02 O-2 deliverPrepared parameter name claim/claimedAttempt | Positional preparation/delivery contract | Observation only; identical positional API, no caller/named-argument break. |
| 02 O-3 no-draft conversation snapshot gap | O-1/I-3/I-4/I-6/I-7 | A23/A24 governing typed legacy source; Snapshot14/E2E30 and R-7/R-15 actual nullable-input/real-anchor recovery close the gap. |
| 02 O-4 later rehydration dependency | I-4/I-5/I-7 | Full current Pending/worker/source inspected; Worker9, real frozen MIME, actual process restart prove implemented rehydration. |
| 03 historical P-1 DTO authority pause | I-3/I-6; A2/A3/A4 | Recorded authorization places revision DTO in03 and consumption in07. Current strict revision validation/forwarding and current tests/native stale/current requests pass; not unapproved now. |
| 03 O-1;04 O-2;07 O07E2-01 size100 | N-5/I-8 and required original range | Fresh C-3 actual O-2 failure retained with exact unchanged-base causal identity. Full required query selection executed. |
| 03 O-2;04 O-2 optional tzdata | Environment/optional full-suite boundary | O-4; no optional whole JVM result asserted. |
| 04 O-1 component-only SMTP at child stage | O-1/I-5/I-11 | Full current application/socket SMTP/MySQL R-2/R-3/R-4/R-6 and real60s/600s completed. Component test not substituted. |
| 05 final N/A; deferred integrated query/runtime | I-8/API/query | Query10/Mailbox41, original mixed live GET and133-row/two-account actual browser paging complete deferment. |
| 06 O06-01 activation/restart later stage | O-1/I-9/I-11 | Three actual public surfaces, restart/disabled/UNKNOWN and current raw-CDP hidden/resume complete required master matrix. |
| 07-source O07SC-01 pre-activation sync500 containing422 | I-3/source compatibility | Historical result retained; actual current activated INBOUND/OUTBOUND requeue and original-null-anchor→newer-anchor chain pass R-7/R-15. No old sync exception mapping changed out of scope. |
| 07-source F06-01 closed | I-6/C-6 original input versus edited content | Complete current app.js/delay consumer audit and focused JS unchanged/text-edited/format-only regressions; R-7/R-15 actual restore/raw use and current MIME remain valid. V-4 separately resolved; no label-based reopening. |
| 07 O07E2-02 Subject | Original review regression range | Fresh C-2 O-1 failure and unchanged-base causal proof, no exclusion. |
| 07 O07E2-03 native A-5 gap before08 | A31/A32/A-5/I-1/I-2/I-9 | Current ordinary/REVIEW/FULL each actual59→61s one POST409→action GET R-2. API-only409 not used. Original negative history retained. |
| 08 O08-01 index-hash prose | S-4/evidence identity | Actual current served/source hash equality; metadata O-7 only. |
| Deferred whole-plan human acceptance | A-1…A-12 | All PENDING; machine exercises are not human approval. |

### Evidence Boundaries and manual gate

No unavailable mandatory machine evidence remains. PASS applies to the exact reviewed product boundary and governing approved contract; it does not claim all raw commands green. O-1/O-2 are evidenced pre-existing causal paths; O-3 optional fixture diagnostics; every required command was executed with actual counts and opt-ins. Native hidden evidence uses real Chrome document.hidden/visibilityState; Playwright visible-only probes are excluded. Runtime SMTP/database/IMAP/ES are disposable local systems; no production deployment or real-mail acceptance inferred. Optional global executor identity, optional all-JVM suite and human acceptance are not manufactured.

| Human item | Machine exercise mapping | Human status |
|---|---|---|
| A-1 ordinary draft/attachment, refresh/switch,40/50s cancellation | R-4 ordinary DRAFT body/txt/unchanged version and due; R-5 restore preservation | PENDING |
| A-2 meeting/ICS/two ordered files/final close | R-2 ordinary delivery; R-4 ordinary meeting DRAFT SENT/contentnull/calendar0→1; R-12 exact MIME/download | PENDING |
| A-3 temporary failure/retry/dismiss/new60s | R-4 original451+explicit retry and same-job completion; R-13 retry cleanup; R-16 list | PENDING |
| A-4 permanent failure/UNKNOWN/no duplicate retry | R-6 real550/DATA disconnect; R-3 real crash/UNKNOWN | PENDING |
| A-5 three-host native59→61s late409 | R-2 ordinary/REVIEW/FULL native clicked POST409→GET; exact timings retained | PENDING |
| A-6 review frozen/body/version/context change | R-8 real new IMAP→context error→confirm/retry/60s SENT; R-2 frozen view | PENDING |
| A-7 READ/FULL/revoke/current permission | R-1/R-2/R-14; Controller16/Guard11/Lifecycle44/E2E30 | PENDING |
| A-8 restart/disabled-new/old jobs/no duplicate | R-3 actual process kill and unchanged600000ms recovery | PENDING |
| A-9 list/filter/count/pagination/account | R-11 actual133 unique rows/secondary65; Query10/Mailbox41 | PENDING |
| A-10 non-target/manual quota/QA-RAG | R-9 real RAG/direct template/IMAP automatic/ES batch, no non-target delay jobs | PENDING |
| A-11 desktop/mobile/keyboard/fixed errors | R-2/R-6/R-16 current screenshots/computed styles/keyboard; R-13 mobile hidden resume | PENDING |
| A-12 cross-browser newer draft conflict | R-5 two independent Chrome processes exact body/context/ordered files preserved and original recoverable | PENDING |

Machine exercise mapping is not a human step-by-step execution signature. All12 require human-originated results at product SHA `60f4a5389e032aac50d1a8f0d8eab0519edc3ad7`. No human approval, merge/release acceptance, publish or branch completion recorded by this reviewer.

### Archive, cleanup and final state

- Raw archive: `/tmp/aggregate-epoch3-evidence.tar.gz`; SHA256 **9c67763c19e923d982eaa1c9b3d77127a363dec21f1691aaf4f024afc36ed83a**; 12,499,211 bytes; 554 files. Commands/scripts/JUnit/logs/HTTP/network/MIME/current screenshots/failed probes/source-and-identity receipts/DB dumps/cleanup included.
- Manifest: `E/manifest.json`; SHA256 **c4cb2fb61de1f8de9c47e73395004498b71bcb9343afe3c82d95fb33f95c02a2**. Chrome profile caches and report-drafting files excluded, not behavioral evidence. Complete report returned separately to avoid self-hash recursion. Archive receipt `/tmp/aggregate-epoch3-archive-receipt.json`.
- Both `aggregate_runtime` and `aggregate_review` SQL dumps completed before cleanup. Exact owned application/SMTP/caffeinate processes terminated; three epoch3 containers removed; no owned Chrome/server process remains. `E/cleanup.json`, `E/owned-processes-before-cleanup.txt`. Unrelated services untouched; no persistent power-setting change, product scaffolding or real recipient.
- Final `E/final-repository-state.json`: HEAD `39bfd10890e94b531f46e54c251f35d9ecd9fc4f`, branch `fast/manual-reply-delay-master`, index empty, `git diff 60f4a5389e032aac50d1a8f0d8eab0519edc3ad7 -- src` empty. All50 protected master/child/fast/repair/handoff artifact hashes equal entry. Only dirty path is controller's selected review ledger. Reviewer did not edit product/tests/approved plans/fast evidence/tracked review report/ledger/repair; no staging, commit, HEAD or branch operation.
- Return destination, controller-only: `W/docs/plans/review/manual-reply-delay-master/machine-verification.md`. Reviewer complete return saved `/tmp/aggregate-epoch3/report.md`; controller can persist it verbatim. No evidence write to that tracked destination by reviewer.

Repair planning: N/A — verify-p PASS; no repair-p run, no repair artifact update, no new approval or implementation loop.

### Next Action

Perform deferred human A-1–A-12 acceptance for exact product SHA `60f4a5389e032aac50d1a8f0d8eab0519edc3ad7`. All human statuses remain PENDING; machine PASS supplies no human acceptance or release approval.

No product code was modified.
