# Child 05 — independent light verification

## Epoch 2 — fresh attempt 1

Plan identity: `commit:6b950d7e0ce1e460e197645a6bcaca485ae83088`; current child-plan SHA256 and the plan bytes from that commit both equal `70976d0211d8e5debd09c5a5d152d5a26a15704dd761af58f3113796664d3ca9`.
Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-access-master`; branch: `fast/expert-access-master`; inspected HEAD: `e4c5d83a580228a92f1dd2c427538829651a40a6`.
Latest epoch2 brief, governing child plan, master invariants/staging amendments, ledger A17/A18 and execution epochs were read. Writer results are baseline only; all results below are this verifier's fresh observations.

## Light Verification: LIGHT_PASS
Child: 05 — docs/plans/2026-10-29/expert-access-05-pending-owner.md
Boundary: a2d4b0e68691d115cff2a6ad3bcc115a06b28d27..e4c5d83a580228a92f1dd2c427538829651a40a6
Verifier: ExpertAccess05Verifier

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact boundary `git diff --stat` (`artifact://2500`) contains precisely the 12 authorized product/test paths from epoch2 brief50–62 / plan71–82; intervening controller planning/evidence files excluded from product scope. Seven production paths and five test paths; no frontend/config/controller/previous migration changes. Product diff `artifact://2503`; adapted-fixture diff `artifact://2512`. |
| Plan and invariants | PASS | V171:1–3 supplies defaultadmin/NOT NULL/single composite index. AutoMailReplyService162–195,295–309,1468–1489,1525–1564 proves real transaction, contact-first lock, both sink allocations and current locking grant read; repository69–77. AccessService75–159 proves contact→grant→processing ordering and atomic reclaim. Repository79–128 preserves narrow field sets and exact reopen triplet. Pending1631–1731 and Unmatched151–260 retain transactions, strict contact locks, reread where returned and expert-wide attention. Fresh MySQL/thread tests and actual production-bean HTTP/JDBC lifecycle below passed. |
| Required commands | PASS | Sequential final owner gate exit0,23/0/0/0 (`artifact://2513`); related gate exit0,310/0/0/0 (`artifact://2514`); all target opt-ins explicitly enabled. Both Maven invocations also run Node1961/0fail/0skip and bound syntax checks. Fresh runtime dependency classpath exit0; javac exit0; production runtime exit0 (`artifact://2522`). Exact commands/counts below. |
| Downstream interfaces | PASS | Domain mapped `pendingOwnerUsername` defaultadmin and SQL `pending_owner_username` produce only admin/user1 through allocation/fallback. No physical mailbox/source or V167 dedup change. New owner persists once; FULL does not migrate historicaladmin; revoke/reopen reclaim is atomic. Whole-expert attention remains owner-independent. Master254 and child7,135 defer personal query/count/SQL/activation/UI to06/08; no early activation or future UI demanded. |

Fresh commands ran in the exact worktree above, on final source, without concurrent Maven/runtime execution. Common test environment:

```bash
env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:32772/talent_introduction?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=verify-owner
```

1. Common environment + `mvn test -Dtest=InboundPendingOwnerIT,ExpertMailReadAccessIT -DmysqlIt=true -DargLine=-Dapi.version=1.41` — exit0,04:49; **23 tests,0 failures,0 errors,0 skipped**. Fresh XML: InboundPendingOwnerIT7; ExpertMailReadAccessIT16.
2. Common environment + `mvn test -Dtest=FlywayMigrationIntegrationTest,AutoMailReplyServiceTest,UnmatchedInboundMailServiceTest,PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,InboundMailProcessingRepositoryTest,ExpertInboundNotificationRepositoryIT,ExpertScopedOperationsIT,AuthFlowIntegrationTest,OperatorStatusWriteSeamGuardTest -DmysqlIt=true -DmigrationIt=true -DargLine=-Dapi.version=1.41` — exit0,14:10; **310 tests,0 failures,0 errors,0 skipped**. Fresh XML parsed independently after command:

```text
FlywayMigrationIntegrationTest                 46/0/0/0
AutoMailReplyServiceTest                      102/0/0/0
UnmatchedInboundMailServiceTest                15/0/0/0
PendingMailOperationServiceTest                42/0/0/0
PendingMailOperationServiceTrustWorkbenchTest  60/0/0/0
InboundMailProcessingRepositoryTest             9/0/0/0
ExpertInboundNotificationRepositoryIT          14/0/0/0
ExpertScopedOperationsIT                       11/0/0/0
AuthFlowIntegrationTest                        10/0/0/0
OperatorStatusWriteSeamGuardTest                1/0/0/0
TOTAL                                        310/0/0/0
```

Each Maven gate's bound Node result:1961 tests/352 suites/1961 pass/0 fail/0 cancelled/0 skipped/0 todo; app/task-modal-runtime syntax checks exit0. Actual opt-ins include AuthFlow's migrationIt. No historical writer fixture failures reproduced on the final required commands. Before these final gates, an initial invocation omitted external datasource overrides; verifier cancelled it during Kotlin compile, before test execution or datasource initialization (cancelled `bg_275` receipt). It is not counted as a test result; both final gates used the owned disposable database explicitly.

Direct permanent behavioral evidence: InboundPendingOwnerIT99–121 exercises both real sinks, NONE/READ/FULL, unmatched and physical UID dedup;124–200 exercises both grant/receipt lock orders for FULL/READ/NONE with independent threads/barriers;203–240 exercises both stale completion consumers with real downgrade and whole-expert attention;243–280 exercises binding, reclassification and reopen with concurrent access changes, field retention and exact cleared resolution triplet;283–294 injects a real MySQL trigger failure proving downgrade/revoke grant+queue rollback;297–310 upgrades V170 legacy rows with divergent dates and FULL to defaultadmin. ExpertMailReadAccessIT adds grant/reclaim lifecycle and retains actual authorization/upgrade/idempotence gates. Existing fixture edits retire only approved save-count/copy pins and move failure injection to actual reclassification SQL; trust-workbench original60 cases remain exercised.

Actual changed-path runtime beyond tests:

- Fresh `env JAVA_HOME=/tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=/tmp/expert05-independent-runtime/classpath` — exit0.
- Reviewed/reconstructed the prior disposable driver from recorded source as a fixture, not its results; added JDBC assertions for reopened triplet/null resolution and unchanged physical source. Compiled fresh with Java11.0.33 `javac -cp <target/classes>:<fresh dependencies> /tmp/expert05-independent-runtime/OwnerRuntime.java` — exit0. No test classes, expandedWAR, Mockito or replacement production beans in the runtime classpath.
- Fresh `env SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:32772/owner_runtime?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=verify-owner MAIL_ATTACHMENT_STORAGE_BASE_PATH=/tmp/expert05-independent-runtime/attachments /tmp/zulu11.92.109-ca-jdk11.0.33-macosx_aarch64/Contents/Home/bin/java @/tmp/expert05-independent-runtime/java.args` — exit0,16.83s, `artifact://2522`.
- Production TalentIntroductionApplication/beans/JDBC/AuthWebConfig started at HTTP57216, pid5159; loopback ES57140 and real SMTP connection collector only. Real PasswordEncoder hashes and genuine cookie logins: admin200/user1200; no synthetic session. Separate disposable owner_runtime schema migrated throughV171.
- Real pipeline created two pre-FULL admin rows, then three FULL user1 rows through both sink branches; duplicate UID retained exactly five rows and owner, unmatched remainedadmin. Physical source/UIDVALIDITY/folder unchanged.
- Real user1 FULL resolve200 ignored forged operatorName=admin; JDBC resolved_by=user1. READ downgrade200 reclaimed only unfinished user1 rows; completed row stayeduser1. user1 reopen403; admin reopen200 changed owneradmin and restored MANUAL_REVIEW/MANUAL_REOPENED/null reasonType/null resolvedBy/null resolvedAt.
- READ messages200; FULL plus legacy empty-body PUT both200 retainedFULL without moving historicaladmin. New receiptuser1; DELETE200 reclaimed it; NONE user1 resolve403; unrelated global sender-account403. Expert needsManualAttention remainedtrue. SMTP collector0 connections; OUTBOUND mail_record count0.
- Terminal receipt `OWNER_RUNTIME_OK both_sinks=true duplicate=true legacy_admin=true downgrade=true revoke=true reopen=true session_audit=true no_smtp=true downstream_personal_query=DEFERRED_TO_06`.
- ES fixture was not an Elasticsearch persistence implementation; expected local startup mapping/HEAD warnings do not constitute ES acceptance. No frontend files changed, so no browser or screenshot claimed. Required concurrent lifecycle/race evidence is real MySQL/thread behavior above, not an invented browser demonstration.
- Cleanup complete: production context/Tomcat/Hikari/ES/SMTP closed in finally; owned Docker `expert05-verifier-mysql` removed exit0; temporary Java/class/classpath/argfile removed exit0 and empty runtime directory removed. No product/test/index/commit/plan/ledger/brief mutation by verifier.

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD
