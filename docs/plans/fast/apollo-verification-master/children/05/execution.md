# Child 05 execution log

## Execution Result: PLAN_CONFLICT

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master/docs/plans/2026-10-18/apollo-05-integration.md
Plan SHA-256: ddcb578f31c657cdfbbfef55b6b603c17abc508b0a84e1dbd5323e74cc14c714
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master/docs/plans/2026-10-18/apollo-05-integration.md@ddcb578f31c657cdfbbfef55b6b603c17abc508b0a84e1dbd5323e74cc14c714
Execution epoch: NEW
Approval basis: Current child05 dispatch and frozen brief; no scope amendment granted.
Executor: ApolloIntegrationImplementer
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master
Target branch: fast/apollo-verification-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master@fast/apollo-verification-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-verification-master
Pre-execution code SHA: a0103c2ea9284c030ad0b0b10c4177474c4f4d47
Pre-execution HEAD: 6ff5989012605e810daa5f1d926c359a3563e36d
Post-execution code SHA: N/A — no child05 product commit.
Evidence HEAD: 6ff5989012605e810daa5f1d926c359a3563e36d
Implementation boundary: No retained product changes. Provisional authorized edits were removed surgically after the caller-scope conflict was identified; edit receipts restored the original file snapshot tags (ExpertDiscoveryService #3557, PaperSearchCriteria #AAE3).

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Read exact plan, complete normative appendix, master invariants and audit | IMPLEMENTED | Read-only authority artifacts | Current plan and appendix/master/audit were read from this worktree; supplied child hash matched at preflight and handoff. |
| I-1/I-2 true execution binding and downstream caller migration | CONFLICT | ExpertDiscoveryService.kt; ExpertDiscoveryController.kt; ExpertDiscoveryScheduler.kt; unlisted caller tests | Existing public discover has three arguments and obtains execution identity from a global progress slot. Explicit immutable execution input changes the callable contract; required existing Mockito caller regressions must migrate with it, but their files are not authorized. Detailed receipts below. |
| Startup contexts, registration, recovery, transactional options and permanent acceptance tests | BLOCKED | Authorized child05 implementation/test files | No partial implementation is retained or represented as complete. Authorizing the affected caller-test migration is a prerequisite to an end-to-end clean cutover. |
| Product commit and fresh final regressions/smoke | BLOCKED | N/A | No completed implementation state exists. No tests, build, Node check, Docker test or runtime smoke was run, and no verification success is claimed. |

### Scope Conflict C05-EXECUTION-CALLERS

Governing text:
- Child05 solution 2 requires a positive real execution ID, passing frozen immutable context explicitly, and forbids reading the global “latest execution”.
- Child05 I-1/I-2 and acceptance require true execution binding across the startup contexts.
- Child05 lines 50–65 authorize exactly the listed implementation files and only DiscoveryVerificationIntegrationTest, DiscoveryPipelineServiceTest and ExpertDiscoveryServiceTest for tests; “无清单外授权”.
- Child05 line 69 requires ControllerMvc and Scheduler regressions.
- Brief requires no unlisted modifications. The dispatch additionally forbids duplicate workarounds. The clean-cutover contract requires all callers/tests to migrate without obsolete overload aliases/shims.

Observed implementation:
- ExpertDiscoveryService.kt:410–417 exposes `discover(criteria, triggeredBy, includeRawScan)` and reads `progressStore.getCurrentExecutionId("EXPERT_DISCOVERY")`.
- TaskProgressStore.kt:138 implements that accessor as `store[taskType]?.executionId`; it is the mutable global current slot, not an invocation-bound execution parameter.
- Controller.kt:130–141 and :224–235 and Scheduler.kt:400–406 already have the real positive ID inside the `onStarted`/execution closure; it can be passed directly by changing the discover signature.
- Adding an execution argument with a Kotlin default does not avoid the affected test contract: Kotlin calls resolve to the method containing the defaulted argument, while the existing Mockito invocations register matchers for the original three arguments.

Reference-search receipt (specialized grep, pattern `\\.discover\\(`, paths `src/main;src/test/kotlin/com/weibo/talentintroduction/discovery`, absolute target-worktree paths supplied):
```text
src/main/.../controller/ExpertDiscoveryController.kt:137,231
src/main/.../service/ExpertDiscoveryScheduler.kt:406
src/test/.../controller/ExpertDiscoveryControllerMvcTest.kt:346,525
src/test/.../controller/ExpertDiscoveryControllerTest.kt:112,133,172,195,204,221,230,263
src/test/.../service/ExpertDiscoverySchedulerTest.kt:86,109,119,135,143,175,183,199,232,241,284,313,360,445,486,553,565,574,591,610
src/test/.../service/ExpertDiscoveryServiceTest.kt:395,591,617,2012,2162,2239,2270,2297,2314,2337,2363,2396,2439,2493,2526,2557,2590,2628,2662,2677
```

Source-text receipt, ExpertDiscoverySchedulerTest.kt:109–123:
```kotlin
Mockito.doReturn(DiscoveryResult("SCHEDULED", DiscoveryStats())).`when`(discoveryService).discover(
    Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
    Mockito.anyString(),
    Mockito.anyBoolean()
)
// ...
Mockito.verify(discoveryService).discover(
    Mockito.any(PaperSearchCriteria::class.java) ?: PaperSearchCriteria(),
    Mockito.anyString(),
    Mockito.anyBoolean()
)
```

The other unlisted caller tests similarly stub or verify the old callable with three matchers. [INFERENCE, not an exercised test failure] Appending a fourth defaulted raw null causes Mockito matcher-count failure; renaming/removing the callable instead causes the old references not to compile. Retaining a legacy three-argument wrapper solely to preserve these tests, or branching on optional test-only injection, is not a clean caller migration and is not used.

Smallest missing authority: authorize migration of the affected existing caller tests `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerMvcTest.kt`, `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt`, and `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt` alongside the explicit execution/context API change, with the corresponding child05 scope amendment. No new provider/client/worker scope is requested by this conflict.

No LSP device is mounted in this worker's tool inventory; exported-symbol callers were searched with the specialized grep across production and test sources rather than claiming unavailable LSP references.

### Commands
| Command | Result | Evidence |
|---|---|---|
| `python /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-18/apollo-05-integration.md && python /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py ...` | Infrastructure diagnostic | Exit 127, `python` not installed; retried successfully with python3. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-18/apollo-05-integration.md` | PASS | Exit 0 at preflight and handoff; canonical path and SHA-256 exactly match approval. Final artifact://221. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-18/apollo-05-integration.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master` | PASS | Exit 0; target branch/root/git-directory bound, HEAD 6ff5989012605e810daa5f1d926c359a3563e36d. |
| Same worktree helper with `--expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master --expect-branch fast/apollo-verification-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-verification-master` | PASS | Exit 0 at final handoff, artifact://221. |
| `git status --short && git log -3 --oneline` | Read-only preflight | Exit 0; only controller-owned ledger dirty; current HEAD is later evidence checkpoint through04. |
| `git status --short` | PASS, preservation receipt | Exit 0 at conflict handoff before report append; only ` M docs/plans/fast/apollo-verification-master/ledger.md`; no product changes remain. artifact://221. |
| Required child05 Maven/Node/regression/smoke commands | NOT RUN | Conflict prevents a final implementation state. Historical child01–04 outputs and supplied Node baseline are not reused as current verification. |

### Changed Files
- `docs/plans/fast/apollo-verification-master/children/05/execution.md` — this appended execution/conflict evidence; excluded from any product commit.
- Controller-owned `docs/plans/fast/apollo-verification-master/ledger.md` remains untouched by this worker.

### Deviations / RECORD_ONLY
- No completed child05 product is delivered, no `feat(fast-p): implement 05` commit was made, and no READY_FOR_VERIFICATION is claimed.
- Snapshot-limits investigation was not elevated to a scope requirement: child05 says to record mode/policy/limits in run.settings, while C-1/C-3 and child04 do not explicitly require consuming historical limit settings. Current Worker.kt:90/:136/:198 and OrcidVerificationEvidenceClient.kt:82 use current configured caps; grep of settingsJson/settings_json found only domain/repository persistence. This is an outside05 observation, not authority to modify worker/client or a substitute conflict.
- No unlisted files, plans, provider requests, credentials, shared database, mail, push, merge, deployment or history rewrite were used.

### Freshness
- Plan identity rechecked: YES.
- Worktree identity rechecked: YES.
- Reported product commits reachable from target branch: N/A; no child05 product commit.
- Required commands run this invocation: NO; blocked before completed implementation.
- Historical evidence used only as baseline: YES.

### Remaining Blocker
- C05-EXECUTION-CALLERS: exact file authorization does not include existing caller tests requiring migration for the explicitly bound execution/context interface.

### Next Action
- PLAN_CONFLICT → obtain an explicit child05 caller-test scope amendment or an authoritative alternative contract; then resume under the resulting immutable plan identity. Do not mark child05 complete or advance to child06.

## Epoch 1 — Controller pause closure
- Outcome: PAUSED_FOR_HUMAN
- Product code head: a0103c2ea9284c030ad0b0b10c4177474c4f4d47
- Fix round: 0
- Fix commits: —
- Reason: explicit invocation-bound execution ID requires existing controller/scheduler caller tests to migrate, but those three files are outside05 authorization. No product changes remain.
- Proposed amendment, not approved: authorize ExpertDiscoveryControllerMvcTest.kt, ExpertDiscoveryControllerTest.kt and ExpertDiscoverySchedulerTest.kt solely for the real execution/context callable migration and its existing regression contracts. Child05 becomes13 files (original10 plus3 caller tests), requiring an explicit test-only exception to the master's <=10 per-child cap; production scope unchanged.
- Resume05 in a new epoch after approval; retain completed01–04 and their checkpoint evidence. No05 tests/smoke/independent verifier or later-child dispatch claimed.

## Epoch 2 — Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master/docs/plans/2026-10-18/apollo-05-integration.md`
Plan SHA-256: `c3e9df5daa9009f41c27a85a021af8576f03342f60b7cac8a6b30de7176b7a2d`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master/docs/plans/2026-10-18/apollo-05-integration.md@c3e9df5daa9009f41c27a85a021af8576f03342f60b7cac8a6b30de7176b7a2d`
Execution epoch: NEW (epoch2; epoch1 history above is retained, not replayed)
Approval basis: user-approved C05-EXECUTION-CALLERS, A5/A6 and plan-only identity commit `a58d457ec2921759c455b6f6edae0e6d94b85220`; 13-file test-only exception, unchanged production scope.
Executor: `ApolloIntegrationImplementerE2`, execute-p.
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master`
Target branch: `fast/apollo-verification-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master@fast/apollo-verification-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-verification-master`
Git common directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`
Pre-execution code SHA / product base: `a0103c2ea9284c030ad0b0b10c4177474c4f4d47`
Pre-execution HEAD: `a58d457ec2921759c455b6f6edae0e6d94b85220` (approved plan-only amendment)
Post-execution code SHA: `083fce2f4b97c88b89a226394ebbf1342001a2d4`
Evidence HEAD: `083fce2f4b97c88b89a226394ebbf1342001a2d4`; this appended report is intentionally uncommitted for the controller.
Implementation boundary: `a0103c2ea9284c030ad0b0b10c4177474c4f4d47..083fce2f4b97c88b89a226394ebbf1342001a2d4`; product commit `feat(fast-p): implement 05` contains exactly the 13 authorized product/test files. No plan/evidence files were staged.
Completed children01–04 remain closed; checkpoint `6ff5989012605e810daa5f1d926c359a3563e36d` was not replayed.

### Task Status
| Requirement | Status | Implementation and exercised evidence |
|---|---|---|
| I-1, A-1: four-entry normalization and immutable real execution binding | IMPLEMENTED | Controller body/keyword, scheduler and pipeline normalize once; real positive task execution is passed explicitly. Integration test exercises all four, explicit false/defaults, missing/invalid ID rejection and stale progress slot987654 not consulted. |
| I-1: historical JSON and settings | IMPLEMENTED | Missing historical option is OFF unless an existing run snapshot already exists; persisted run settings record mode, policy and configured nonsecret limits. Existing run snapshot remains authoritative. Limits are recorded only; worker/client unchanged per epoch1 interpretation receipt. |
| I-2: reserve before RAW; no legacy admission/checkpoint on failure | IMPLEMENTED | PAPER, ORCID and queued consumption use the same explicit registration context. Real MySQL failure triggers prove zero RAW/admission on reserve failure and no exhausted checkpoint. Managed RAW activates into NEEDS_REVIEW and waits for worker; enrichment/person registration retain their existing seams. |
| I-2/I-3: crash recovery, identity and protections | IMPLEMENTED | RAW-before-activation failure/replay retains owner, activates once and excludes historical duplicates. Exact RAW GET and identity hash govern recovery; pending source retries remain RESERVED, terminal source jobs or finished synchronous execution without source jobs produce unactivated registration ERROR when RAW is absent. SBIR, contact/alias, APPLICATION and prior admission protection plus unavailable protection reads are exercised. |
| I-3: activation batches | IMPLEMENTED | Run lock serializes first activation and assigns floor(activatedCountBefore/100)+1. Concurrent activation of101 synthetic profiles yields100/1; replay preserves owner/batch/count. |
| I-4: transactional option conflict and resume | IMPLEMENTED | Repository launch compares normalized persisted options while holding pipeline transaction lock. Actual HTTP409 carries code VERIFICATION_OPTIONS_CONFLICT; SQL pipeline snapshot, academic canonical hash and cursor are unchanged; pause/resume reuses stored option; idle next start can update it. Existing QUERY_CONFLICT retained. |
| C-7 and callable migration | IMPLEMENTED | Both controller suites, scheduler and pipeline regressions pass; main and test references migrated with no compatibility overload. All required suites were attempted freshly; exact baseline failures/skips below are not counted as passes. Separate fresh Node checks and a disposable runtime driver were executed. |

### Commands and Evidence
All Maven commands used this worktree and JDK11, with `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` and `-Dapi.version=1.44`. No production/shared business DB or real provider request was used.

Final backend command:
```text
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -q test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationIntegrationTest,ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,DiscoveryPipelineServiceTest,ExpertDiscoveryServiceTest,DiscoveryVerificationContractTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationFlowIT,DiscoveryVerificationRulesTest,DiscoveryPaperQueueRepositoryIT,DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,OperatorStatusWriteSeamGuardTest
```
Result: exit1; 410 total,356 passed,0 assertion failures,53 errors,1 skipped. Final log: `artifact://247` (background result summary `artifact://252`); fresh per-class XML/text under `target/surefire-reports/`.

| Suite | Total | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| DiscoveryVerificationIntegrationTest | 9 | 0 | 0 | 0 |
| ExpertDiscoveryControllerMvcTest | 4 | 0 | 0 | 0 |
| ExpertDiscoveryControllerTest | 20 | 0 | 0 | 0 |
| ExpertDiscoverySchedulerTest | 32 | 0 | 0 | 0 |
| DiscoveryPipelineServiceTest | 49 | 0 | 0 | 0 |
| ExpertDiscoveryServiceTest | 190 | 0 | 53 | 0 |
| DiscoveryVerificationContractTest | 5 | 0 | 0 | 0 |
| DiscoveryVerificationRepositoryIT | 8 | 0 | 0 | 0 |
| DiscoveryVerificationAdmissionGateTest | 6 | 0 | 0 | 0 |
| DiscoveryVerificationCommitServiceTest | 4 | 0 | 0 | 0 |
| DiscoveryVerificationWorkerTest | 3 | 0 | 0 | 0 |
| DiscoveryVerificationFlowIT | 11 | 0 | 0 | 0 |
| DiscoveryVerificationRulesTest | 7 | 0 | 0 | 0 |
| DiscoveryPaperQueueRepositoryIT | 1 | 0 | 0 | 1 |
| DiscoveryAdmissionPolicyTest | 22 | 0 | 0 | 0 |
| DiscoveryReviewServiceTest | 38 | 0 | 0 | 0 |
| OperatorStatusWriteSeamGuardTest | 1 | 0 | 0 | 0 |

Backend baseline classification: all53 errors are `ExpertDiscoveryServiceTest` NPEs at `DiscoveryReviewService.recordAutomaticConclusion(... automatic=null)`. Its pre-existing fixture stubs `admissionPolicy.evaluate(profile, eligibility)` (setup lines201–203, also custom conclusion stub) whereas the closed child03 production review seam calls `policy.evaluate(docId, profile, eligibility)` at `DiscoveryReviewService.kt:849`. Source receipt: `git show a0103c2ea9284c030ad0b0b10c4177474c4f4d47:src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` produced `artifact://244`; grep `when.*admissionPolicy.*evaluate|anyProfile\(\), anyEligibilityResult` shows the same stale overload at baseline lines201–202 and2385–2386. These stubs and the production review seam were not changed by05. [INFERENCE] The observed53-error impact is pre-existing fixture drift, not an execution-context defect; the baseline revision was not executed or modified to establish that attribution. No unrelated baseline repair was made. This is a failed required regression command, not a green suite claim.

`DiscoveryPaperQueueRepositoryIT` remained gated/skipped; not counted as pass. Actual queue launch transactions and concurrency were exercised through the new isolated MySQL8 integration fixture. Optional legacy shared-DB `DiscoveryReviewRepositoryIT` was not selected/enabled, and its prior skip is not fresh PASS evidence.

| Other command | Result and evidence |
|---|---|
| `node --test src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryReview.test.js && node --check src/main/resources/static/app.js` | Fresh final exit0;70 tests passed,0 failed,0 skipped; syntax check exit0. Child07 discoveryVerification.test.js does not yet exist and was not claimed executed. |
| `node --test src/test/js/*.test.js` | Fresh final exit1;1694 total,1653 passed,41 failed,0 skipped; `artifact://251`. Matches supplied baseline, chiefly unrelated recipient-preview DOM stubs and asset cache-token assertion. No frontend/baseline fixes. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=/tmp/apollo-child05-classpath.txt -q` | Exit0, preparation for runtime driver; temporary classpath file removed. |
| JDK11 `javac -cp <target/classes:target/test-classes:test dependency classpath> /tmp/ApolloChild05Smoke.java`, then `java -Dapi.version=1.44 -cp </tmp:that classpath> ApolloChild05Smoke` with the above Docker socket | Both exit0. Actual runtime result detailed below; source/class and diagnostic/classpath scaffolds removed. |
| `git diff --check && git status --short` | Exit0; scope receipt `artifact://249`: authorized product files plus expected controller-owned brief/ledger only before report append. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py <absolute plan path>` | Rechecked before staging/commit; exact unchanged SHA-256 above,9922 bytes (`artifact://257`). |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py <absolute plan path> --worktree <target root> --expect-root <target root> --expect-branch fast/apollo-verification-master --expect-git-dir <exact git-dir above>` | Exit0 before staging/commit; exact retained root/branch/git-dir, HEADa58d457 (`artifact://257`). |
| `git commit -m 'feat(fast-p): implement 05'` | Exit0; `083fce2f4b97c88b89a226394ebbf1342001a2d4`, exactly13 product/test files, no fast artifacts/plans. |
| `git rev-parse HEAD && git merge-base --is-ancestor a0103c2ea9284c030ad0b0b10c4177474c4f4d47 HEAD && git merge-base --is-ancestor a58d457ec2921759c455b6f6edae0e6d94b85220 HEAD && git show --format=fuller --stat HEAD` | Exit0; product base and approved amendment reachable, exact subject and file count confirmed. |

Earlier attempts retained honestly: first full Maven attempt stopped at new test constructor compile errors (`artifact://236`), corrected to real constructors. Next run (`artifact://238`) reached410 tests with2 assertion failures,56 errors,1 skip:53 baseline fixture errors plus new fixture trigger privilege, person mock result and normalized scope defects. These new fixture defects were corrected; a follow-up compile attempt (`artifact://245`) caught the exact scope property name and was corrected to `subjectScope`. Final results above supersede these attempts. No failed command was rerun unchanged.

### Actual Smoke
The disposable Java main executed the real `ExpertDiscoveryService.consumeQueuedItem` → registration → real MySQL ledger path with mocked external ES/provider transport, outside the test runner. An actual MySQL trigger aborted activation after RAW, leaving1 RAW/0 activated. A different positive real execution then replayed the same input twice: one activation, original owner8001 retained, no legacy candidate and no provider call. Closing input persisted true. Observed output:
```text
CHILD05_RUNTIME_SMOKE raw=1 activated=1 owner=8001 replayOwner=8002 legacyCandidates=0 providerRequests=0 inputClosed=true
```
All runtime Java/class files, diagnostic Java/class files and temporary classpath/log files were removed. Testcontainers databases were disposable and stopped; no shared DB cleanup was performed.

### Changed Files
Paths below are relative to this worktree:
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/PaperSearchCriteria.kt` — nullable startup option only.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt` — entry normalization/capability rejection, actual execution context,409 code and honest pause copy.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt` — same normalization and explicit task execution binding.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` — explicit context across synchronous PAPER/ORCID/queued consumption; reserve-before-RAW, activation, retryable registration boundary and duplicate recovery.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt` — persisted option snapshot and positive window execution binding, typed consumption context and input closure.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt` — transactional same-query option conflict/update without cursor reset.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` — run snapshot, protection/reservation/activation batching and existing RegistrationRecovery seam implementation.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationIntegrationTest.kt` — isolated real MySQL registration/launch/failure/concurrency integration coverage.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt` — typed context migration.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` — explicit execution/context callable migration; stale-slot identity assertion.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerMvcTest.kt` — approved callable migration.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt` — approved callable migration.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt` — approved callable migration.

### Reference/Capability Receipt
`read xd://lsp` was attempted as required and reported the LSP device unmounted (mounted devices listed AST/debug/MCP instead). No availability inference was made from the absence of a named function. Fallback reference search covered both main and test discovery trees with regex `\.discover\(|consumeQueuedItem\(`; production controller/scheduler calls explicitly pass invocation execution ID, pipeline passes the immutable context, and migrated test stubs use the new callable signatures. Whole Maven production/test compilation completed in the final command.

### Deviations / Remaining Risks
- No authorized-scope deviation, worker/client edit, production configuration/schema migration, contact/mail sending, or unrelated baseline fix.
- This is not a global green check claim: required backend command has53 statically attributed pre-existing fixture errors; full Node retains41 user-supplied baseline failures; gated shared-DB tests are not passes. Independent verifier must account for these explicitly, not silently omit them.
- Historical epoch1 PLAN_CONFLICT is superseded by the approved epoch2 authority, not erased.

### Freshness
- Plan identity rechecked: YES, unchanged.
- Worktree identity rechecked: YES, exact retained worktree/branch/git-dir.
- Reported product commit reachable from target branch: YES.
- Required commands run freshly in this invocation after final implementation: YES; Node run separately because Maven skipped Node.
- Historical evidence used only as baseline: YES; current counts above are fresh.
- Known in-scope failure: none observed in final integration/controller/scheduler/pipeline/verification suites or actual smoke. Baseline attribution caveat is explicit above.

### Remaining Blocker
- No implementation authority or environment blocker. Required regression baseline failures remain unmodified for controller/independent verification classification.

### Next Action
- Controller: independently light-verify child05 epoch2 against the approved identity and product commit; commit evidence separately. Do not treat this executor result as PASS, reopen children01–04, or run full verify-p/review-p/fix-v inside the fast-p loop.

## Epoch 2 — Automatic repair round 1 closure
- Executor: ApolloIntegrationFixerE2R1; supplied independent AUTO_FIX findings F-05-E2-01/02/03 only; fixer contract, not full fix-v/verify-p/review-p.
- Before:083fce2f4b97c88b89a226394ebbf1342001a2d4; actual repair commit:1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 (`fix(fast-p): repair 05 round 1`), four authorized product/test files, no evidence/plan files committed.
- Repair result: FIXED, awaiting fresh independent verification; this does not supersede LIGHT_FAIL by executor assertion or claim global PASS. Exact fixes, command vectors/exits/counts, attempted-command history and runtime observations are appended in `children/05/fix-log.md`, Epoch2 Round1/3.
- Final covering backend includes ContinuousMvc and PipelineUnavailableMvc as well as the named05/C-7 suites:418 total,364 passed,0 failures,53 recorded pre-existing errors,1 optional legacy DB skip; integration10/10 and all11 MVC tests pass. Separate final Node70/70 and syntax pass; full Node1694/1653 pass/41 recorded failures retained. RECORD_ONLY O-05-E2-01/02/03 unchanged, not silently reclassified as fixes or successful verification.
- Actual isolated MySQL runtime follows consume/RAW activation interruption/replay→claimed worker→commit: fingerprint matches persisted RAW, one activation retains owner9001, genuine PENDING/IDENTITY_EVIDENCE_MISSING rather than FACT_CHANGED; no provider request. Live owner with zero backlog and changed query+option returns409 VERIFICATION_OPTIONS_CONFLICT and leaves pipeline/cursor unchanged. Temporary driver removed and database stopped.
- Product/test writes are complete. Controller owns evidence commit and fresh independent light verification of083fce2..1cef08b; no later-child advancement or closed-child replay claimed.

## Epoch 2 — Controller terminal closing
- Implementation: 083fce2f4b97c88b89a226394ebbf1342001a2d4
- Fix round: 1
- Fix commit: 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848
- Code head: 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848
- Independent verifier: ApolloIntegrationReverifierE2R1; LIGHT_PASS_WITH_NOTES, COMPLETE_CHILD. F-05-E2-01/02/03 CLOSED; report epoch2/attempt2 preserved.
- Fresh independent backend418 total/364 pass/0 assertion failures/53 statically attributed pre-existing fixture errors/1 optional legacy skip; focused Node70 pass and syntax pass, full Node41 existing failures. No global-green claim.
- Actual independent MySQL runtime confirms canonical RAW fingerprint, one activation/original execution owner through replay and real worker commit; active-window/zero-backlog changed-query+option returns409 with pipeline/cursor unchanged.
- Evidence checkpoint follows before06; no whole-system verification or later-child acceptance claimed.

### Controller evidence-format correction 1
- Initial artifact checkpoint rejected only round1 SHA binding format. Original fixer report preserved; controller closing heading was normalized so the appended canonical SHA remains within round1. No product/test changes, new verification or extra repair round. Replacement docs-only evidence commit will bind these corrected closing records.

### Controller evidence-format correction 2
- Corrected the controller-added duplicate action-block formatting in verify-log; original verifier action is untouched. Artifact-only correction, no product changes, repair or new verification.
