# Child 05 independent light verification log


# epoch2 / attempt1 / 2026-10-18T22:43:10+08:00 / boundary a0103c2ea9284c030ad0b0b10c4177474c4f4d47..083fce2f4b97c88b89a226394ebbf1342001a2d4

## Light Verification: LIGHT_FAIL
Child: 05 — docs/plans/2026-10-18/apollo-05-integration.md
Boundary: a0103c2ea9284c030ad0b0b10c4177474c4f4d47..083fce2f4b97c88b89a226394ebbf1342001a2d4
Verifier: ApolloIntegrationVerifierE2 — independent report-only verifier, not ApolloIntegrationImplementerE2; no inherited writer conversation. Four gates only, not a broad/final review.
Approved identity: commit:a58d457ec2921759c455b6f6edae0e6d94b85220; full brief including epoch2, child plan, normative contracts and master read. A5/A6 authorize the three caller-test files and the 13-file exception. Execution report was context, never verification evidence.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Fresh exact-boundary diff lists precisely the 13 authorized src paths (listed below). Other boundary changes are the approved plan-only amendment and controller evidence. HEAD/branch receipt is 083fce2 / fast/apollo-verification-master; no product drift from that HEAD. |
| Plan and invariants | FAIL | F-05-E2-01: reservation freezes a different orcidId from the actual RAW representation and worker commit sees FACT_CHANGED. F-05-E2-02: changed query bypasses the active-window options conflict. Isolated runtime reproduces both. Other direct requirement evidence and fresh integration results below. |
| Required commands | FAIL | Fresh primary backend:410 total/356 passed/0 failures/53 errors/1 skip, exit1. Additional modified MVC classes:7 total/6 passed/1 failure/0 errors/0 skipped, exit1; this reveals patch-introduced F-05-E2-03. Focused Node70 passed; syntax passed. Full Node1694/1653 passed/41 failed matches recorded baseline; not a green result. Actual isolated MySQL ITs and actual changed-path runtime executed. |
| Downstream interfaces | FAIL | Explicit positive execution/context routing reaches pipeline consumer; RegistrationRecovery is consumed by worker ObjectProvider. The snapshot handed from05 to existing04 worker/commit is not the persisted RAW fingerprint (F-05-E2-01), violating the shared fact contract. LaunchOutcome.Conflict.reason is correctly forwarded through PipelineRejection to controller409 code; the missing producer guard is F-05-E2-02. |

### AUTO_FIX
- **F-05-E2-01 — P1 — Freeze the persisted RAW profile, not its pre-write identity representation.** Governing child05 I-2 and master I-6/O-1: immutable input must describe the actual stored facts so an unchanged newly collected profile can reach its qualification result. Patch anchor: `DiscoveryVerificationRegistrationService.kt:81-82` (and reservation callers `ExpertDiscoveryService.kt:1461-1462,1810-1812`). `buildProfile` uses the original ORCID or empty string; `buildOrcidProfile` uses the original ORCID. Both RAW writes use `toIndexMap` (`ExpertDiscoveryService.kt:2466-2467`), which stores `orcidId=esDocId` (an EMAIL-* key on these paths). Snapshot fingerprint explicitly includes orcidId (`DiscoveryVerificationAdmissionGate.kt:91-98`); RAW reread materializes that stored key (`ExpertIndexWriterService.kt:775-777`). `DiscoveryVerificationCommitService.kt:36-37,54-58` therefore sees unequal fingerprints, withholds the outcome and leaves PENDING/FACT_CHANGED without any real fact change. Independent runtime below exercised real queued consumption, registration, recovery, worker processing and commit and reproduced this. **Unique semantic correction:** normalize the frozen reservation profile/fingerprint to the existing RAW representation, preserving the current ES identity convention and real ORCID in its existing provenance fields; do not remove the fingerprint check or change worker/client scope. Add authorized integration coverage through worker commit proving unchanged just-written RAW does not become FACT_CHANGED. All correction files are already authorized (registration/discovery/integration test); no new interface or scope needed.
- **F-05-E2-02 — P2 — Apply verification-option conflict irrespective of academic query equality.** Governing child05 I-4/solution1: same pipeline with active window/backlog must reject an apolloAssist change transactionally with409 VERIFICATION_OPTIONS_CONFLICT. Patch anchor `DiscoveryPaperQueueRepository.kt:408-410` adds `sameQuery &&` to that guard. With a valid owner lease and no jobs yet, changing keywords and the option skips this guard, and the existing query guard only checks backlog; launch overwrites criteria and returns applied=true while the old window is still active. Runtime reproduced `activeWindow=true backlog=0 queryChanged=true optionChanged=true applied=true rejection=null`. **Unique correction:** remove query-equality restriction from the new options-change guard, leaving QUERY_CONFLICT intact for unchanged-option query conflicts; add the authorized integration regression for a live owner with zero backlog. Repository and integration test are authorized.
- **F-05-E2-03 — P2 — Migrate the existing paused-state MVC assertion.** Governing epoch2 caller-test amendment, child05 I-4/A-3/C-7: preserve the required new pause explanation and migrate related existing assertions. Patch anchor `ExpertDiscoveryController.kt:468` changes PAUSED text to `已暂停采集，已入队的学术补全与资格核验可继续；需手动恢复采集`, but authorized `ExpertDiscoveryControllerMvcTest.kt:434` still expects `已暂停，需手动恢复`. Fresh `ExpertDiscoveryContinuousMvcTest` execution fails exactly that assertion. It is a separate top-level class in the modified MVC file, not selected by the filename-matching class selector. **Unique correction:** update that exact stale assertion to the approved new wording, and select both additional MVC classes when verifying. Do not revert required production copy. The test file is explicitly authorized.

### RECORD_ONLY
- **O-05-E2-01 — Pre-existing policy-overload fixture errors, statically attributed, not baseline-rerun-confirmed.** Fresh190 ExpertDiscoveryServiceTest cases yield137 passes and53 errors; every error is the same null `automatic` at DiscoveryReviewService.recordAutomaticConclusion. I independently inspected baseline source, not just the writer label: `git show a0103c2ea9284c030ad0b0b10c4177474c4f4d47:src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` shows the unchanged two-argument policy stubs at201-202 and2385-2386 (artifact://273); the same base review service already calls `policy.evaluate(docId, profile, eligibility)` at849 (artifact://272). Current53 XML error messages all name null automatic. The callable migration does not introduce that overload mismatch. The recorded original Baseline only executed DiscoveryAdmissionPolicyTest22, not this class; I do NOT claim a successful historical rerun or that53 was a measured original baseline count. This is outside the proven05 delta, not AUTO_FIX, and leaves those53 assertions unverified; no errors are suppressed or counted as passes.
- **O-05-E2-02 — Existing full-Node failures retained.** Fresh1694 total/1653 pass/41 fail/0 skip exactly matches supplied ledger Baseline. Includes resetRecipientPreview/setRecipientPreviewState extraction sandbox references and meetingConfirmationAssets cache-token expectation. Exact-boundary src diff has no frontend changes. Out of05 scope; no unrelated fixes, no whole-suite green claim.
- **O-05-E2-03 — Optional legacy DB coverage excluded, not passed.** The primary command selects `DiscoveryPaperQueueRepositoryIT`, gated by mysqlIt and skipped1; its source uses legacy configured DB wiring, so mysqlIt was never enabled. `DiscoveryReviewRepositoryIT` was not selected/enabled at all. Neither is a pass or replacement for mandatory actual isolated-MySQL execution. IntegrationTest9, RepositoryIT8 and FlowIT11 ran against disposable MySQL8 Testcontainers, zero skips. No shared DB or actual provider requests.

### Authority, exact scope and source receipts
All commands below ran in `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master` after implementation. Fresh `git show -s --format=%H%n%D HEAD` returned `083fce2f4b97c88b89a226394ebbf1342001a2d4` and `HEAD -> fast/apollo-verification-master`. Fresh approved-identity diff for child/master/contracts from a58d457..083fce2 and current-vs083fce2 was empty. `git log` shows the approved plan-only amendment and intervening controller evidence within the exact requested boundary; none was treated as a product deviation. `git diff --name-only 083fce2 -- src` was empty.

Scope receipt command: `git diff --name-only a0103c2ea9284c030ad0b0b10c4177474c4f4d47..083fce2f4b97c88b89a226394ebbf1342001a2d4 -- src` returned:
```text
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperSearchCriteria.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerMvcTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt
```
Fresh patch/source reads are artifact://263,266,271; source boundary receipts artifact://272,273; identity/scope receipt from git show/stat confirms13 product/test files. This verifier changed no product/test/index/branch/commit/plan/other evidence; this report is appended only.

### Direct requirement and routing evidence
- Nullable option only: PaperSearchCriteria.kt:13. Controller normalizes body/no-body and keyword before launch at109,188-192,397-402; keyword adds optional parameter186. Scheduler393-410 shares normalization and passes actual onStarted ID. Registration43-68 resolves default/explicit false, rejects explicit true without capability, honors existing run, rejects missing/nonpositive/nonexistent task when enabled, freezes mode/policy/nonsecret limits. Historical JSON absence is OFF at59-60. No historical-limit-consumption requirement is invented; epoch2 interpretation receipt is preserved.
- Positive immutable context: discovery408-432, pipeline633-644/onStarted, pipeline1083-1084/consumer, context20-24. Existing global task progress slot is not consulted for verification ownership. Integration first two tests exercise four entries, stale slot987654, explicit false/defaults, historical JSON and ID checks.
- Reserve-before-RAW plus activation: discovery1460-1482 and1810-1836; failure maps to retryable consumer flags1860+, pipeline1108-1123 invokes retry rather than success. Integration reserve-failure/ORCID fixtures test no RAW/no checkpoint progression. Managed path skips legacy admit while awaiting worker; registration initializes NEEDS_REVIEW and uses run lock/count for first activation batch105-117. Integration101 activations ->100/1 and repeat unchanged.
- Duplicate recovery remains reserved/source-specific: registration121-157; worker49-56 consumes existing RegistrationRecovery. Recovery exact RAW GET/hash and terminal-source evidence read outside write locks; no history scan. Integration tests verify RAW activation interruption, original owner across run replay, historical duplicate exclusion, missing RAW terminal vs pending, and protected/read-failure cases. Actual smoke independently follows it into worker commit, which exposes F-05-E2-01 beyond those existing tests.
- Query canonical hash excludes option (`DiscoveryCheckpointCodec.kt:65-75`), criteria persisted at pipeline313-328; repository launch transaction386-429 holds pipeline lock and same-query option conflict test passes. Conflict.reason routes repository -> pipeline337-340 -> controller429-437 unchanged by catch-all. Missing different-query guard is F-05-E2-02, not a dropped new reason at consumption.
- Pause only affects collection; new text at controller468 is correct, but stale assertion is F-05-E2-03. Existing scheduler32 and pipeline49 tests pass; focused frontend coexistence/pause/resume contracts70 pass. No05 frontend or mail/contact-send changes. Later06 reporting/07 UI are not claimed implemented or globally verified.

### Fresh commands and outcomes
Environment for every JVM test/runtime: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`, `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`, JVM/Maven `-Dapi.version=1.44`. No credentials or provider accounts used.

Primary exact command (fresh once, no baseline rerun):
```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -q test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationIntegrationTest,ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,DiscoveryPipelineServiceTest,ExpertDiscoveryServiceTest,DiscoveryVerificationContractTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationFlowIT,DiscoveryVerificationRulesTest,DiscoveryPaperQueueRepositoryIT,DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,OperatorStatusWriteSeamGuardTest
```
Exit1;410 total/356 pass/0 failures/53 errors/1 skip. Fresh raw output artifact://267 (end excerpt artifact://276; delivery snapshot artifact://277). Counts independently parsed from fresh Surefire XML; all53 errors have null-automatic message.

| Suite | Total | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| DiscoveryVerificationIntegrationTest |9|9|0|0|0|
| ExpertDiscoveryControllerMvcTest |4|4|0|0|0|
| ExpertDiscoveryControllerTest |20|20|0|0|0|
| ExpertDiscoverySchedulerTest |32|32|0|0|0|
| DiscoveryPipelineServiceTest |49|49|0|0|0|
| ExpertDiscoveryServiceTest |190|137|0|53|0|
| DiscoveryVerificationContractTest |5|5|0|0|0|
| DiscoveryVerificationRepositoryIT |8|8|0|0|0|
| DiscoveryVerificationAdmissionGateTest |6|6|0|0|0|
| DiscoveryVerificationCommitServiceTest |4|4|0|0|0|
| DiscoveryVerificationWorkerTest |3|3|0|0|0|
| DiscoveryVerificationFlowIT |11|11|0|0|0|
| DiscoveryVerificationRulesTest |7|7|0|0|0|
| DiscoveryPaperQueueRepositoryIT |1|0|0|0|1|
| DiscoveryAdmissionPolicyTest |22|22|0|0|0|
| DiscoveryReviewServiceTest |38|38|0|0|0|
| OperatorStatusWriteSeamGuardTest |1|1|0|0|0|

Additional fresh regression command selects the actual other top-level classes in the changed MVC file, using the just-compiled implementation without rerunning the first command:
```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -q surefire:test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=ExpertDiscoveryContinuousMvcTest,ExpertDiscoveryPipelineUnavailableMvcTest
```
Exit1,7 total/6 pass/1 failure/0 errors/0 skip; ContinuousMvc6/5/1 and PipelineUnavailableMvc1/1/0. artifact://280 (end excerpt artifact://279). Failure is exactly old pause string at MVC source434. Combined independent backend selection417 total/362 pass/1 failure/53 errors/1 skip. No repeated error-confirmation run.

Fresh separate C-7 frontend commands (Maven Node intentionally skipped, not omitted):
```sh
node --test src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryReview.test.js && node --check src/main/resources/static/app.js
node --test src/test/js/*.test.js
```
First exit0,70 total/pass,0 failure/skip; syntax also exit0. Second exit1,1694 total/1653 pass/41 fail/0 skip (artifact://269, totals2350-2355). `src/test/js/discoveryVerification.test.js` does not exist until07: explicitly NOT RUN YET, neither failure nor completed acceptance. Browser1366/1920/390 frontend acceptance belongs to07 and was not claimed performed for this backend child.

### Actual changed-path runtime smoke
Independent disposable Java main, not the JUnit runner and not reused writer output. It used only existing compiled test fixtures to provide isolated MySQL8 and mocked ES/provider transports. It invoked the real ExpertDiscoveryService.consumeQueuedItem -> registration -> MySQL ledger -> recovery -> worker process -> commit. The only reflection called the Kotlin internal worker process method on a real claimed lease (the fixture configuration deliberately leaves worker scheduling disabled); it did not modify any product or bypass the commit/fact guard. Temporary source/class used Python TemporaryDirectory and were removed automatically, including after the initial driver-only compile failure. No permanent scaffold or test change.

Driver launch: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock python3 -c <independent temporary Java compilation/launch harness>`. Harness derives CP directly from property `java.class.path` in the fresh `target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest.xml` (not historical test results), creates `IndependentChild05Smoke.java` in a temporary directory TMP and runs these subprocess argument vectors:
```text
/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/javac -cp CP TMP/IndependentChild05Smoke.java
/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dapi.version=1.44 -cp TMP:CP IndependentChild05Smoke
```
Fresh javac exit0; runtime exit0; raw artifact://284. Runtime started actual MySQL8.0.36 at localhost:32839/verification_registration via Testcontainers, migrated through158, finally stopped it. Trigger injected activation failure after RAW, replayed the same source twice under different actual task9002, closed inputs, then ran worker to actual terminal persistence. Assertions checked interruption, zero activated before replay, successful replay, single activation and original owner9001. Observed exact output:
```text
VERIFIER05_SMOKE interrupted=true activatedBeforeReplay=0 replay=true raw=1 activated=1 owner=9001 fingerprintMatches=false finalOutcome=PENDING reasons=["FACT_CHANGED"] providerRequests=0 inputClosed=true
VERIFIER05_OPTIONS activeWindow=true backlog=0 queryChanged=true optionChanged=true applied=true rejection=null
SMOKE_JAVAC_EXIT=0
SMOKE_JAVA_EXIT=0
```
The javac marker appears before JVM startup in raw output. First line demonstrates valid recovery but wrong no-change fingerprint (F-05-E2-01). Second line uses a valid pipeline owner_until2030-01-01 and zero backlog, then a real transactional launch changing both keywords and option, demonstrating F-05-E2-02. Runtime exit0 means the diagnostic driver completed and its recovery assertions passed, NOT that these defect observations pass the plan.

Driver-only first attempt: javac exit1 from JSON quoting in the temporary Java source (artifact://281); no JVM/DB started. Corrected the throwaway source quoting and removed an invalid mutable-properties approach before retry; no product/test edit. Corrected successful run above supersedes that harness failure, not any product failure. A malformed specialized artifact-range grep also failed once and was retried against the whole numeric artifact; no evidence fabricated.

### Disposition
No authority/evidence/mandatory-command execution blocker remains. Three findings are proven explicit gate violations with a unique semantic correction entirely inside approved files; they are AUTO_FIX, not requests to reopen completed01-04 or enlarge scope. O-items are recorded only and must not be silently promoted into unrelated repairs. Preserve F-05-E2-01, F-05-E2-02, F-05-E2-03 in subsequent repair/verification records. Controller owns routing; no fixes performed and no advancement/final-review claim.

### Required Action
- AUTO_FIX


# epoch2 / attempt2 / 2026-10-18T23:13:09+08:00 / boundary a0103c2ea9284c030ad0b0b10c4177474c4f4d47..1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 05 — docs/plans/2026-10-18/apollo-05-integration.md
Boundary: a0103c2ea9284c030ad0b0b10c4177474c4f4d47..1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848
Verifier: Independent05RepairVerifierAttempt2 — fresh independent report-only verifier, distinct from ApolloIntegrationImplementerE2, ApolloIntegrationFixerE2R1 and ApolloIntegrationVerifierE2. No inherited writer conversation. Four gates only; not a whole-system or final master review.
Approved identity: commit:a58d457ec2921759c455b6f6edae0e6d94b85220. Full brief including epoch2, complete child plan/contracts/master, downstream06 plan and historical execution/fix/verification reports read. Historical reports are context, not current verification.
Implementation:083fce2f4b97c88b89a226394ebbf1342001a2d4; repair1:1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact-boundary src diff contains precisely the13 authorized paths below; repair1 touches4 of them. Fresh HEAD is1cef08bd on fast/apollo-verification-master; current src differs from review head by nothing. Approved child/master/contracts diff from a58d457 to HEAD and working tree is empty. Other boundary changes are approved plan/controller evidence, not product deviations. |
| Plan and invariants | PASS | F-05-E2-01/02/03 all CLOSED, direct source/test/runtime evidence below. Full explicit05 scope checked, not only repair hunks. Immutable contexts, reservation ordering, replay ownership, batches, protection, defaults and transactional options have direct exercised evidence. |
| Required commands | PASS with recorded baseline notes | Fresh covering Maven exit1:418 total/364 pass/0 failures/53 pre-existing fixture errors/1 optional skip. All three top-level MVC classes ran:11/11; Integration10/10; other specified new/regression suites below. Focused Node70/70 and syntax exit0; full Node1694/1653 pass/41 fail matches supplied baseline. No baseline-only confirmation rerun. Independent actual runtime and real MySQL launch transaction succeeded. This gate classifies results; it is not an all-green Maven/full-suite claim. |
| Downstream interfaces | PASS | Conflict.reason reaches pipeline when-dispatch then controller409 code. Positive immutable execution/run context reaches queued consumer. RegistrationRecovery is consumed by worker ObjectProvider. Persisted snapshot now matches real RAW reread through04 worker/commit;06 receives stable owner/run/settings/revision, activation-only batches and honest terminal reason/outcome. No later06 implementation is claimed. |

### AUTO_FIX
- N/A. No remaining proven, uniquely authorized four-gate violation observed; no product/test repair performed by this verifier.

### RECORD_ONLY
- **O-05-E2-01 — retained, not reopened.** Fresh ExpertDiscoveryServiceTest190 total/137 pass/53 errors. All53 fresh XML error messages are exactly `Parameter specified as non-null is null: method com.weibo.talentintroduction.discovery.service.DiscoveryReviewService.recordAutomaticConclusion$weibo_talent_introduction, parameter automatic`. Recorded independently sourced baseline attribution is unchanged: old fixture two-argument policy stubs versus the already-three-argument base review-service call. Attribution is static, NOT baseline-rerun-confirmed. Required suite executed freshly once; no unrelated stub repair and no rerun merely to confirm baseline.
- **O-05-E2-02 — retained.** Fresh full Node1694 total/1653 pass/41 fail/0 skip, exit1, matches ledger Baseline. Output includes resetRecipientPreview/setRecipientPreviewState extraction sandbox errors and existing meetingConfirmationAssets expectation. No frontend path changes in review boundary; outside05 authority. No global green claim.
- **O-05-E2-03 — retained, skip is not pass.** Selected DiscoveryPaperQueueRepositoryIT is optional legacy configured-DB coverage and reports1 skipped. mysqlIt was not enabled. Legacy shared-DB DiscoveryReviewRepositoryIT was not selected/enabled and is not represented as fresh coverage or pass. Mandatory Integration10, VerificationRepositoryIT8 and VerificationFlowIT11 actually ran on disposable MySQL8 with zero skips. No shared/production DB or actual provider requests.

### Finding status after repair1
| Exact ID | Status | Direct evidence and fresh confirmation |
|---|---|---|
| F-05-E2-01 | CLOSED | RegistrationService.kt:81-84,98 freezes `profile.copy(esDocId=docId,orcidId=docId)` matching unchanged RAW toIndexMap2463-2488, while retaining externalIds/proof. Gate.snapshot83-98 and CommitService.prepare31-45 still enforce the full real RAW fingerprint; guard was not weakened. Integration218-252 exercises PAPER crash/replay through actual worker/commit, matches fingerprint and yields genuine IDENTITY_EVIDENCE_MISSING instead of FACT_CHANGED. ORCID integration409-450 uses real source identity conversion, preserves externalIds.orcid/boundOrcid/valid identity and ends QUALIFICATION_EVIDENCE_MISSING with offline ORCID reads, no Apollo. Independent standalone smoke below confirms unchanged RAW fingerprint and terminal worker commit. |
| F-05-E2-02 | CLOSED | Repository.kt:398-410 checks optionsChanged AND (backlog OR activeWindow) before sameQuery branching; query equality no longer gates conflict. Integration373-406 exercises live owner/zero backlog/different query, HTTP409 code, unchanged row/cursor, and preserved QUERY_CONFLICT when only query changes with backlog. Independent actual transactional smoke below confirms different-query option conflict and HTTP409 with unchanged persisted state. |
| F-05-E2-03 | CLOSED | Existing paused assertion at ExpertDiscoveryControllerMvcTest.kt:434 now matches required Controller.kt:468 wording without reverting product text. Fresh required command explicitly selects ExpertDiscoveryControllerMvcTest4, ExpertDiscoveryContinuousMvcTest6 and ExpertDiscoveryPipelineUnavailableMvcTest1:11 passed,0 failures/errors/skips. |

### Exact scope and identity receipts
Fresh commands, exit0:
```sh
git show -s --format=%H%n%D HEAD
git diff --name-only a0103c2ea9284c030ad0b0b10c4177474c4f4d47 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 -- src
git diff 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 -- src
git diff a58d457ec2921759c455b6f6edae0e6d94b85220 -- docs/plans/2026-10-18/apollo-05-integration.md docs/plans/2026-10-18/apollo-contracts.md docs/plans/2026-10-18/apollo-verification-master.md
git show --format=fuller --stat 083fce2f4b97c88b89a226394ebbf1342001a2d4
```
HEAD receipt:1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 / HEAD -> fast/apollo-verification-master. Both current product drift and approved plan diff empty. Scope/repair receipts artifact://320; service patch artifact://322; caller migration patch artifact://324. Authorized src output:
```text
src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperSearchCriteria.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerMvcTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt
```
No product/tests/index/commits/branch/plans/other evidence changed by verifier; only this report appended. Standalone source/class were outside the tree in TemporaryDirectory and removed automatically.

### Remaining explicit05 requirements and downstream06 seams
- **I-1/four entries:** PaperSearchCriteria13 adds nullable option only. Controller109,186-192,397-402 normalizes body/no-body and keyword and rejects explicit true if unavailable; scheduler393-408 shares registration normalization and actual onStarted ID. Pipeline313-328 serializes normalized criteria; window639-644 binds actual positive execution before constructing context. Registration43-68 handles defaults, historical JSON OFF, existing immutable run, enabled missing/nonpositive/nonexistent task rejection and nonsecret mode/policy/limits. Integration159-196 exercises four distinct positive owners/runs, stale progress slot987654 never used, explicit false and historical/default cases. Historical limits consumption remains outside05 per approved interpretation, not a new requirement.
- **I-2 ordering/failure:** Service1457-1487 and1807-1841 reserve before RAW, activate after RAW/enrichment, skip old admit for managed items, retain person registration seam. Registration errors become incomplete/retryable consumption, not legacy admission; pipeline1083-1128 processes success/error flags without silent discard. Integration reserve-trigger tests202-211 and420-423 verify zero RAW/admission and no exhausted checkpoint. `begin` fails before new RAW without actual task identity. Both synchronous and window finally close input without cancelling existing registered verification.
- **I-3 recovery/protection:** Registration123-160 recovers existing source/key-matching RESERVED only via exact RAW GET and identity hash; missing RAW waits for pending source, terminal missing source becomes unactivated ERROR. Worker49-56 consumes RegistrationRecovery. Integration255-297 verifies recovery and terminal missing RAW;218-252 verifies original owner across replay and historical duplicate exclusion;300-318 verifies concurrent101 activations ->100/1, replay unchanged;321-340 verifies SBIR/APPLICATION/contact-alias protections and fail-closed read failure. Registration107-118 serializes first activation under owner run lock and initializes NEEDS_REVIEW; original owner never migrates.
- **I-4/query/pause/coexistence:** Repository launch386-451 is transactional and compares frozen options independently of query. Pipeline330-340 explicitly routes Conflict.reason; Controller429-437 forwards code/reason to409. CanonicalCriteria65-75 excludes apolloAssist; Integration343-370 verifies equal hash, saved cursor, persisted pause/resume and idle option update. Scheduler32/Pipeline49 existing regressions and Node70 cover original scheduling/coexistence/pause contracts. Source filters/platform query/cursor strategy are not replaced.
- **06 data boundary:** Registration snapshot retains docId/name/email/institution/external IDs/source key/time/admission revision, stable execution owner and policy. Existing repository createRun/reserve/activate/closeInput bumps revision, excludes RESERVED from activated denominator and leaves ERROR/outcome semantics intact. Close only input; worker terminal result is independent from collection task status. Canonical persisted RAW now survives the04 prepare/commit consumer unchanged. 06 may read these rows and current admission using its approved API contract; this child does not implement or claim testing future reporting endpoints/F123 UI/browser acceptance.
- Reference receipt: specialized grep `\.discover\(|consumeQueuedItem\(|recoverReserved\(|VERIFICATION_OPTIONS_CONFLICT|LaunchOutcome.Conflict` over src/main/kotlin and discovery tests located production Controller137/220, Scheduler408, Pipeline1084, Worker51, repository410 and controller response routing; those consumers were read, rather than inferring producer correctness. Caller-test migration is confined to authorized callable context changes; entire production/test compilation completed in the fresh command.

### Fresh required commands and counts
All commands ran in `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master`, after repair1. Every JVM runtime used JDK11 and isolated OrbStack Docker socket; no real provider/shared DB. Required test command executed once, not a baseline-only rerun:
```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -q test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationIntegrationTest,ExpertDiscoveryControllerMvcTest,ExpertDiscoveryContinuousMvcTest,ExpertDiscoveryPipelineUnavailableMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,DiscoveryPipelineServiceTest,ExpertDiscoveryServiceTest,DiscoveryVerificationContractTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationFlowIT,DiscoveryVerificationRulesTest,DiscoveryPaperQueueRepositoryIT,DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,OperatorStatusWriteSeamGuardTest
```
Exit1;418 total/364 passed/0 assertion failures/53 errors/1 skip. Raw artifact://316, completion artifact://325/bg_69. Independently parsed the selected fresh Surefire XML files using Python ElementTree and summed tests/failures/errors/skipped; all53 error messages agree with O01. Exact selected per-suite results:

| Suite | Total | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| DiscoveryVerificationIntegrationTest |10|10|0|0|0|
| ExpertDiscoveryControllerMvcTest |4|4|0|0|0|
| ExpertDiscoveryContinuousMvcTest |6|6|0|0|0|
| ExpertDiscoveryPipelineUnavailableMvcTest |1|1|0|0|0|
| ExpertDiscoveryControllerTest |20|20|0|0|0|
| ExpertDiscoverySchedulerTest |32|32|0|0|0|
| DiscoveryPipelineServiceTest |49|49|0|0|0|
| ExpertDiscoveryServiceTest |190|137|0|53|0|
| DiscoveryVerificationContractTest |5|5|0|0|0|
| DiscoveryVerificationRepositoryIT |8|8|0|0|0|
| DiscoveryVerificationAdmissionGateTest |6|6|0|0|0|
| DiscoveryVerificationCommitServiceTest |4|4|0|0|0|
| DiscoveryVerificationWorkerTest |3|3|0|0|0|
| DiscoveryVerificationFlowIT |11|11|0|0|0|
| DiscoveryVerificationRulesTest |7|7|0|0|0|
| DiscoveryPaperQueueRepositoryIT |1|0|0|0|1|
| DiscoveryAdmissionPolicyTest |22|22|0|0|0|
| DiscoveryReviewServiceTest |38|38|0|0|0|
| OperatorStatusWriteSeamGuardTest |1|1|0|0|0|

Separate fresh C-7 frontend commands because Maven intentionally skipped Node:
```sh
node --test src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryReview.test.js && node --check src/main/resources/static/app.js
node --test src/test/js/*.test.js
```
First exit0,70 tests/70 passed/0 failed/0 skipped; syntax exit0 (bg_70). Second exit1,1694 total/1653 pass/41 fail/0 skip (bg_71, artifact://318:2350-2355). Supplied ledger Baseline and baseline-command.log read; baseline observations not rerun merely to confirm. `discoveryVerification.test.js` source availability check returns path not found: future07 file, explicitly NOT EXECUTED, not pass. Backend05 has no frontend change; future07 real-DOM1366/1920/390 browser acceptance is not claimed performed here.

### Fresh independent actual runtime smoke
Not a JUnit invocation or reused fixer output. A new disposable Java main used only compiled fixture infrastructure for isolated MySQL/transport setup, then called real consumeQueuedItem -> registration -> actual MySQL reserve/activation interruption -> different-execution source replay twice -> actual lease claim -> real worker -> transactional commit persistence. Reflection selected only the existing one-VerificationItem worker process method; it bypassed scheduler timing, not policy/commit/fact/lease checks. Fixture providers and ES were mock transports throughout.

Exact outer launch: `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock python3 -c <Independent05RepairVerification temporary-source harness>`; exit0. CP was extracted from `properties/property[name=java.class.path]` in the freshly produced `target/surefire-reports/TEST-com.weibo.talentintroduction.discovery.service.DiscoveryVerificationIntegrationTest.xml`. The harness used `tempfile.TemporaryDirectory(prefix="independent-child05-")` and ran these exact subprocess vectors with CP/TMP resolved at runtime:
```text
/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/javac -cp CP TMP/Independent05RepairVerification.java
/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dapi.version=1.44 -cp TMP:CP Independent05RepairVerification
```
Fresh javac exit0; JVM exit0, first attempt. Actual disposable MySQL8.0.36 at localhost:32861/verification_registration, migrated through158, then stopped in finally. Raw artifact://329 records startup, assertions, final output and automatic temporary-directory cleanup. No permanent scaffold or classpath file created.

Observed exact terminal markers:
```text
SMOKE_JAVAC_EXIT=0
INDEPENDENT05_SMOKE interrupted=true activatedBeforeReplay=0 replay=true raw=1 activated=1 owner=9101 fingerprintMatches=true state=COMPLETED outcome=PENDING reasons=["IDENTITY_EVIDENCE_MISSING"] providerRequests=0 inputClosed=true
INDEPENDENT05_OPTIONS activeWindow=true backlog=0 queryChanged=true optionChanged=true applied=false rejection=VERIFICATION_OPTIONS_CONFLICT http=409 pipelineUnchanged=true cursorUnchanged=true
SMOKE_JAVA_EXIT=0
SMOKE_TEMPORARY_DIRECTORY_REMOVED=true
```
The driver asserts genuine reasons, terminal SQL state, owner9101 rather than replay9102, one activated denominator, no provider requests and closed original input. It separately creates an actual pipeline/stream, persists a live owner lease with zero backlog, saves a cursor, changes both keywords and apolloAssist, invokes real transactional launch and controller, and asserts409/VERIFICATION_OPTIONS_CONFLICT plus exact pipeline-row/cursor preservation. This is actual runtime consumption/worker commit and transactional conflict, not source inspection masquerading as smoke.

### Disposition
All three exact F-IDs CLOSED; no new AUTO_FIX or evidence/authority blocker. O-05-E2-01/02/03 remain RECORD_ONLY, not suppressed, repaired or counted as successful coverage. Completion applies only to approved child05 four gates and downstream seams at this boundary; no claim of globally green regressions, implemented06/07, rollout readiness or reopening closed01–04. Controller owns next-child routing and evidence commit.

### Required Action
- COMPLETE_CHILD

### Controller evidence-format correction 1
- Round1 closing metadata formatting corrected after artifact-only gate rejection; this does not change or repeat the independent epoch2/attempt2 verdict or its exercised commands.

### Required Action
- COMPLETE_CHILD
