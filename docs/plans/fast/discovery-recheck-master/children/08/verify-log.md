# Child 08 — Verification log

## Epoch 1 — Attempt 1 — 2026-10-25T08:50:04+08:00

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 08 — docs/plans/2026-10-24/discovery-recheck-08-api.md
Boundary: f37ff178554cb19b3264f81ed4deac7a6b43bd8c..59c34a1b6f04af0669a9276dae7c043bca4164f5
Verifier: Recheck08Verifier

Authority: exact approved child plan and `children/08/brief.md`; four-gate LIGHT verification only, independent of Recheck08Implementer. Worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master`. Execution log used as baseline/context, not independent proof. No product/test/plan/index/commit/branch changes; only this append-only report written.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Fresh `git diff --name-only f37ff178554cb19b3264f81ed4deac7a6b43bd8c 59c34a1b6f04af0669a9276dae7c043bca4164f5` returned the nine authorized product/test files listed below, plus prior child07 execution/fix/verify evidence and ledger. Those interleaved governance files were excluded from product review as instructed. No migration, frontend, second worker, mail or original manual-service change in this product boundary. |
| Plan and invariants | PASS | Direct service/controller/repository/query inspection and fresh SQL/HTTP execution establish I-1–I-4: fixed canonical roster, read-only preparation/default AUDIT+REUSE, real task/run lifecycle, transactional start/cancel/recovery, fixed-baseline revisions, session authority, paginated read-only history, projection-only retry and manual protection. Detailed anchors and independent runtime receipts below. |
| Required commands | PASS | Both exact Maven test commands and exact forked standalone launcher each executed once after investigation, with specified JDK11 and disposable Docker MySQL. Exit0 for all; JVM39 and JVM56 respectively, zero failures/errors/skips; each test command also ran JS1827/pass1827/fail0/skipped0 and both bound syntax checks. Fresh artifacts `artifact://1221`, `artifact://1223`, `artifact://1225`. No failed result rerun or skipped IT labeled PASS. |
| Downstream interfaces | PASS | Controller:15–78 exposes the locked prepare/start/cancel/sync-retry/runs/latest/run/items/steps/manifest routes. QueryService:126–221,367–389 provides real run/task IDs, READY hash/revision, pageable manifests/history, before/proposed/current/applied, trace eight steps/blocking information/reasons/versions/source refs, mutually exclusive status and separate proposal/admission/projection metrics. Service:61–106,233–266 preserves the explicit prepare→READY→start contract required by09 and the existing-ID recheck/projection-only contract required by10. No09 UI or10 replay implementation/aggregate acceptance claimed. |

### Authorized Product/Test Receipt

The exact boundary filename output contained these product/test paths, matching the approved nine-file roster:

```text
src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckController.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckService.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryRecheckControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckFlowIT.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryRecheckServiceTest.kt
```

Other boundary paths were `children/07/execution.md`, `children/07/fix-log.md`, `children/07/verify-log.md`, and `ledger.md` under `docs/plans/fast/discovery-recheck-master/`; these are earlier interleaved evidence, not extra child08 product scope.

### Direct Plan Evidence

Paths in this section are relative to `src/main/kotlin/com/weibo/talentintroduction/` unless stated otherwise.

- **I-1 fixed input / canonical idempotency:** `discovery/service/DiscoveryRecheckService.kt:109–126` validates UUID, IDS request limit and scope/filter/modes, canonicalizes sorted unique IDs and spec. `:131–201` reads actual RAW/discovered sources or existing ScanService scroll in500 batches, deduplicates docIds, freezes profile/identity/facts and `reviewInput` (nullable expected admission revision, before decision, selected baseline evidence/hash/item and fixed paper refs), and only then closes input/READY with persisted hash. Failure `:202–225` cannot publish partial READY. `discovery/repository/DiscoveryVerificationRepository.kt:397–425` uses parameterized exact JSON_EXTRACT requestKey, current read and same-connection GET_LOCK with afterCompletion/failure release; no ES/provider scan inside registration. `:555–561` hashes persisted inputs in stable order, not later ES.
- **I-2 preparation/task separation:** Service`:65–88` registers actual task and frozen run/settings in the short transaction; `:90–104,228–230` handles failed registration/submission and truthful task finish. Freeze does not call initializeAdmission, activate, provider, commit or ES update. Repository`:599–623` reserves run-owned epochs; service`:195–201` sets READY before marking prepare SUCCESS. Dedicated single-thread executor is added in `config/DiscoveryExecutorConfig.kt`; ordinary executor code is unchanged. Actual READY has1001 queued/RESERVED items and zero step rows, business digest unchanged/provider0 (`FlowIT:267–274`). Preparation task SUCCESS is distinct from run READY and later run COMPLETED (`FlowIT:289–291`).
- **Start authority / revision / atomicity:** Service`:233–247` validates hash/revision and checks worker/config before starting READY, leaving READY on rejection. Repository`:483–510` locks run, validates frozen hash and accepted revision, atomically sets RUNNING then activates exactly the persisted roster/initializes steps. Saved accepted startRevision supports idempotent same-request start after worker revision changes; stale hash/revision conflicts. RepositoryIT`:819–844` runs concurrent starts and verifies one activation/eight slots per item, stale conflicts and cancel/lease rejection.
- **I-3 cancellation/recovery:** Repository`:513–549` follows run→item locking, preserves completed receipts, cancels uncommitted active items, recognizes interrupted prepare task with unclosed input, and completes running control only from terminal items independent of projection. Existing worker owns execution/lease recovery (`Service:269–273`). RepositoryIT`:846–874` verifies interrupted/nonstartable preparation, retained rosterHash/requestCount on lease reclaim and exclusive terminal counts. Standalone actual-MySQL races cover both commit-first and cancel-first; admission survives commit-first while cancel-first blocks admission (`FlowIT:370–422`).
- **Projection-only retry / single rules pipeline:** Service`:257–266` validates every requested item's run/epoch ownership before work, requires admitted completed items, and invokes only existing CommitService.project for ERROR/PENDING; synced items are idempotent. `FlowIT:408–419` exercises PENDING→injected ERROR→PROJECTED→idempotent retry, unchanged admission and empty provider attempts. Start only activates persisted work; existing Worker/Gate/Commit/Trace is instantiated and exercised in FlowIT`:113–126,225–232`, not a second qualification pipeline.
- **I-4 auth/actor/error/read semantics:** Controller`:18–78,81–88` guards every new route with session identity; body cannot supply actor. ControllerTest`:49–98` covers all anonymous routes, forged actor/snapshot ignored, AUDIT/REUSE defaults, enum/type400, ownership404/conflict409 and query-only read routing. QueryService`:126–136` enforces exact run/item epoch ownership; `:141–218` reads frozen before, traced proposed, current admission, committed applied receipt, paginated manifest/latest/history without provider/recalculation/write. Repository`:461–474` separates processed/queued/cancelled/errors, wouldAdmit/actualAdmitted and sync outcomes.
- **Original manual / ordinary protection:** No original manual endpoint/service or Worker/Gate/Commit/Rules implementation was modified in this boundary. Actual old manual prepare/confirm HTTP produces MANUAL_APPROVED and subsequent ENFORCE recheck preserves its receipt with REVIEW_PROTECTED (`FlowIT:328–342`). Fresh original-component regression produced the legacy-context, same-chain SQL, AUDIT0/REUSE0, cancellation-both-orders, projection-only and unchanged first-SHA receipts below. This is bounded regression evidence, not a claim that all original production flows or all protected-state combinations were manually exercised.

### Fresh Required Commands

All commands ran in the assigned worktree, serially; no build/lint/test was run during the investigation. No product/test edits occurred in this verification.

1. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryRecheckControllerTest,DiscoveryRecheckServiceTest,DiscoveryVerificationRepositoryIT,DiscoveryRecheckFlowIT -DmysqlIt=true`
   - **Exit0 / BUILD SUCCESS**, finished `2026-10-25T08:37:21+08:00`, elapsed10:42.
   - JVM39, failures0/errors0/skipped0: RepositoryIT28, ControllerTest5, FlowIT1, ServiceTest5.
   - JS1827/pass1827/fail0/skipped0; bound `node-check-app` and `node-check-task-modal-runtime` completed.
   - `artifact://1221:487–491,998–1028,1268–1342,3848–3864`.
2. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationTraceRepositoryIT -DmysqlIt=true`
   - **Exit0 / BUILD SUCCESS**, finished `2026-10-25T08:43:03+08:00`, elapsed05:36.
   - JVM56, failures0/errors0/skipped0: TraceRepositoryIT4, QueryServiceTest19, CommitServiceTest6, WorkerTest17, AdmissionGateTest10.
   - JS1827/pass1827/fail0/skipped0; both bound syntax checks completed.
   - `artifact://1223:747–751,1002–1098,2425–2433,4939–4955`.
3. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn exec:exec -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-classpath %classpath com.weibo.talentintroduction.discovery.service.DiscoveryRecheckFlowIT'`
   - **Exit0 / BUILD SUCCESS**, finished `2026-10-25T08:50:04+08:00`, elapsed06:57.
   - Inspected committed main launcher (`DiscoveryRecheckFlowIT:76–104`) then independently invoked it outsideJUnit in forked Java11, not exec:java.
   - Disposable MySQL8.0.36 at localhost ephemeral port33355; migrated real schema toV161; real Tomcat/DispatcherServlet, session auth and JDK HTTP client, existing Worker/Gate/Commit/Trace. Runtime414 actual servlet HTTP requests (including readiness polling), provider0/production writes0.
   - `artifact://1225:26–35,268–346`; exact receipts below.

Baseline comparison: execution log recorded final39/56 passing, plus earlier implementer-only fixture failures and in-process exec:java classloader failure. This verifier did not reproduce/retry those historical failures, did not use them as proof, and ran only the required forked launcher. All fresh required commands passed without skip or rerun.

### Independently Observed Runtime Receipts

From this verifier's standalone invocation, `artifact://1225:269–336`:

```text
RECHECK08_RUNTIME roster=1001 batches=500,500,1 frozenAfterInsert=true processed=1001 prepareProviderHttp=0 auditProviderHttp=0 auditBusinessWrites=0 realTaskBinding=true
RECHECK08_RUNTIME partialFreeze=FAILED/500/notClosed interruptedPrepare=INTERRUPTED preparingCancel=CANCELLED readyCancel=CANCELLED crossRun404=true
RECHECK08_RUNTIME oldManualHttp=MANUAL_APPROVED protected=REVIEW_PROTECTED auditWouldAdmit=1 auditActualAdmitted=0
RECHECK08_RUNTIME cancelCommitOrder=commit-first state=COMPLETED admitted=true projectionOnlyRetry=true providerCalls=0
RECHECK08_RUNTIME cancelCommitOrder=cancel-first state=CANCELLED admitted=false projectionOnlyRetry=false providerCalls=0
RECHECK08_RUNTIME PASS actualServletHttp=true httpRequests=414 isolatedMySQL=true workerGateCommitTrace=true providerCalls=0 runningRecoveryHashAndRequestCount=true productionWrites=0
```

Fresh second-command compatibility receipts, `artifact://1223:1002,1098`:

```text
CHILD07_LEGACY PASS realWorkerSQL=true rule3=true inputContextAbsent=true evidenceAbsent=true outputContextAbsent=true executionClock=2026-10-20T00:00:00Z liveQualification=NOT_MET frozenQualification=MET crossBirthdayOutcomesDiffer=true explicitOrdinaryFrozen=true
CHILD07_RUNTIME PASS actualRegistrationWorkerRulesGateCommitTrace=true realMySQL=true ordinaryPaper=DIRECT_CLEAR eightSteps=true AUDIT_businessWrites=0 REUSE_HTTP=0 originalFetchedAt=true frozenParity=true freeDetailHTTP=0 lateDiagnostic=once academicCancelWire=1 cancelled429Failures=0 cancelCommitBothOrders=true admittedProjectionRetryProviderHTTP=0 projectionAttempts=3 archiveFirstSHA=unchanged
```

### AUTO_FIX
- N/A — no proven four-gate violation found; no correction made.

### RECORD_ONLY
- O-08-E1-01: Forked runtime emitted Tomcat9.0.83 Java11 reflective-access shutdown warning (`artifact://1225:337–341`); exit0 and all runtime assertions passed. Existing dependency/runtime warning, outside this child's authorized functional correction; recorded only, no investigation or dependency change.

### Limits / Expected Warnings
- Real isolated MySQL, SQL transactions and servlet HTTP were exercised; ES business transport is fixture-backed and scan uses loopback fixture HTTP, not a real ES cluster. Providers are denied/count0; no live/paid provider, production write or production credential was used.
- Candidate HTTP500 in the race test is the inspected intentional projection failure (`FlowIT:411–417`), followed by successful projection-only retry; it is not a failed command or production incident.
- This is child08 four-gate verification only: no full repository JVM suite/WAR build, human/browser acceptance,09 UI,10 historical backfill, live infrastructure or aggregate master review. Existing receipt assertions cover archive/ordinary compatibility; they do not prove production deployment or historical-data repair.

### Required Action
- COMPLETE_CHILD

