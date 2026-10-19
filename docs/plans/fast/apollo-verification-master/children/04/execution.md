# Child 04 execution log

## Execution Result: PLAN_CONFLICT

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master/docs/plans/2026-10-18/apollo-04-worker.md
Plan SHA-256: 8507e21e882539d54f908732dc58095129f9a3f2538f3b4c8b8a003926fa487a
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master/docs/plans/2026-10-18/apollo-04-worker.md@8507e21e882539d54f908732dc58095129f9a3f2538f3b4c8b8a003926fa487a
Execution epoch: NEW
Approval basis: current child04 dispatch and immutable brief.
Executor: ApolloWorkerImplementer
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master
Target branch: fast/apollo-verification-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master@fast/apollo-verification-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-verification-master
Pre-execution code SHA: 98c7b78fde54f69b72f175b94b2ab0eed25d86ab (controller-supplied product boundary)
Pre-execution HEAD: ccdaad239792d33089e0dfea5f048e33acc3c26a
Post-execution code SHA: N/A — no implementation changes or product commit.
Evidence HEAD: ccdaad239792d33089e0dfea5f048e33acc3c26a (report remains uncommitted for controller).
Implementation boundary: no product changes.

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Exact plan, complete appendix/master invariants and audit | IMPLEMENTED | Read-only plan/context | Exact hash matched at start and handoff; complete reads performed. |
| Existing gate producer/consumer inspection | IMPLEMENTED | DiscoveryVerificationAdmissionGate.kt; domain/DiscoveryVerification.kt | Gate consumes VerificationAdmissionEvidence and shared snapshot; progress is separate, unknown JSON fields allowed. No changes made. |
| I-1 restart-safe existing-client orchestration | CONFLICT | OrcidVerificationEvidenceClient.kt (not authorized) | Aggregate-only read API cannot resume successful endpoint results or expose completion before subsequent HTTP calls; details below. |
| I-2 atomic commit, I-3 projection recovery, I-4 fencing and required tests | BLOCKED | Authorized child04 implementation/test files | Execution stopped at missing required seam; no partial implementation claimed. |

### Commands
| Command | Result | Evidence |
|---|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-18/apollo-04-worker.md` | PASS | Exit 0, run twice; SHA-256 matches approved bytes. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-18/apollo-04-worker.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master` | PASS | Exit 0, run twice; expected root/branch/git-dir; HEAD unchanged. |
| `git status --short` | PASS | Exit 0, before report edit: only controller-owned ledger.md dirty. |
| Required Maven/MySQL/Node commands and runtime smoke | BLOCKED / NOT RUN | No implementation established: scope conflict gate stops execution before implementation and tests. No test or smoke PASS claimed. |

### Changed Files
- docs/plans/fast/apollo-verification-master/children/04/execution.md — appended this execution report; excluded from product commits.
- No product/test files changed. Controller-owned ledger preserved.

### Deviations
- Child04 implementation and required checks did not begin because completing the restart contract requires a currently unauthorized existing-client seam.

### Remaining Blocker
- **PLAN_CONFLICT — ORCID per-request persistence/resume seam absent from authorized scope.**
- Child04 implementation step 1 requires persisted request reservations, durable progress, and restart reading the same attempt results rather than restarting the query. Appendix C-3 requires bounded cumulative requests, persisted attempts and progress, and safe restart recovery.
- Current `OrcidVerificationEvidenceClient.kt:58-99` exposes `read(snapshot, reserve: (String) -> RequestPermit?)`. It always creates a fresh queue `person, employments, educations` at line 61, performs HTTP internally at line 72, accumulates successful documents in a private local list at lines 75-76, and returns that list only on an aggregate return path. The worker cannot observe a completed response before the next request. A crash during a later endpoint therefore leaves earlier response data inaccessible to the worker, although its budget reservation is durable.
- On restart, the API has no saved-document/cursor input or endpoint-read method. Returning null from reserve for a cached endpoint stops the whole read at line 68; returning a permit repeats the real HTTP request. Supplying a prior permit is not replay: transport consumes it and still performs HTTP. Repeated failure at a later endpoint consequently repeats earlier successful requests and consumes the item budget without completing remaining evidence.
- Read-only search receipt: functions.grep pattern `OrcidEvidenceRead|OrcidEvidenceDocument|fun read\(snapshot: VerificationSnapshot|VerificationHttp\(` over `src/main/kotlin/com/weibo/talentintroduction/discovery` returned the aggregate read/transport declarations in OrcidVerificationEvidenceClient.kt and ApolloPeopleSearchClient.kt, plus QualificationEvidenceParser.parse consuming the completed aggregate. The relevant client code was then read at lines 58-99 and transport/permit at lines 22-35,65-105.
- A worker-owned replacement HTTP implementation or copied ORCID orchestration would violate the explicit requirement to use the existing dedicated client interfaces and avoid workaround/duplicate behavior. Weakening recovery to restart all ORCID reads would silently narrow I-1. Editing the client is outside the seven authorized files.
- Smallest missing authority: authorize an existing ORCID-client seam that supports durable endpoint completion and resuming previously completed endpoint results (with corresponding client regression coverage), then resume child04 under the amended approved scope. No client, domain, gate, rules or config changes were made.

### Freshness
- Plan identity rechecked: YES
- Worktree identity rechecked: YES
- Reported commits reachable from target branch: N/A — no new commit
- Required commands run this invocation: NO — conflict preflight only
- Historical evidence used only as baseline: YES — Node baseline supplied by controller, not exercised here

### Next Action
- PLAN_CONFLICT → obtain a human decision or amended plan authorizing the missing existing-client seam; no push, merge, deployment, later-child work or independent verification performed.

## Epoch 1 — Controller pause closure
- Outcome: PAUSED_FOR_HUMAN
- Current product code head: 98c7b78fde54f69b72f175b94b2ab0eed25d86ab
- Fix round: 0
- Fix commits: —
- Reason: ORCID client aggregates multiple requests without per-endpoint completion/resume hooks; mandatory durable request recovery cannot be implemented within child04's seven authorized files.
- Proposed amendment, not yet approved: add OrcidVerificationEvidenceClient.kt and OrcidVerificationEvidenceClientTest.kt to child04 (7→9 files); expose per-endpoint completion and saved-result/cursor resume through the existing dedicated client, preserving endpoint allowlist, request permits, caps and no-provider-production-call boundary.
- Resume child04 in a new epoch after human approval; no child04 product changes, no later-child dispatch, no tests or independent verification fabricated.

## Epoch 2 — controllerEpoch2

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master/docs/plans/2026-10-18/apollo-04-worker.md
Plan immutable commit: bb9eda7df6fde5f4eb0a5d433a1058f008d42b16
Plan SHA-256: 9b8d742b37fefd332cf9799db5f479c9b24653f5e3ce945d6b01727591e25819
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master/docs/plans/2026-10-18/apollo-04-worker.md@9b8d742b37fefd332cf9799db5f479c9b24653f5e3ce945d6b01727591e25819
Execution epoch: NEW — amended plan identity; historical Epoch1 conflict retained above.
Approval basis: human “批准该计划并执行”, ledger A3/A4, amended brief Epoch2 and current dispatch.
Executor: ApolloWorkerImplementerE2
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master
Target branch: fast/apollo-verification-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master@fast/apollo-verification-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-verification-master
Pre-execution code SHA: 98c7b78fde54f69b72f175b94b2ab0eed25d86ab
Pre-execution HEAD: a01b687978e6ee275cff8dee115d465ecd983451
Post-execution code SHA: a0103c2ea9284c030ad0b0b10c4177474c4f4d47
Evidence HEAD: a0103c2ea9284c030ad0b0b10c4177474c4f4d47 — this report remains uncommitted for the controller's separate evidence commit.
Implementation boundary: 98c7b78fde54f69b72f175b94b2ab0eed25d86ab..a0103c2ea9284c030ad0b0b10c4177474c4f4d47; child04 product commit contains only its authorized implementation/test paths.
Product commit subject: feat(fast-p): implement 04

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Current plan, appendix, master invariants and audit | IMPLEMENTED | Read-only authority artifacts | Full current-plan/appendix/master/audit reads; exact SHA matched at preflight, before staging and handoff. Worktree helper matched root/branch/git directory throughout. |
| I-1 finite independent worker and restart recovery | IMPLEMENTED | DiscoveryVerificationWorker.kt; DiscoveryVerificationRepository.kt; DiscoveryVerificationWorkerTest.kt; DiscoveryVerificationFlowIT.kt | Scheduled finite batch, single active tick, optional existing RegistrationRecovery, independent 30-second lease renewal, durable dispatch permits, cumulative SQL caps, retry scheduling, bounded page/query progress and candidate union. Expired RUNNING and due RETRY_WAIT use existing claim fencing. |
| Approved ORCID amendment | IMPLEMENTED | OrcidVerificationEvidenceClient.kt; OrcidVerificationEvidenceClientTest.kt; worker/repository/FlowIT | Existing client exposes saved endpoint results and response acknowledgement. Sanitized successful/missing endpoint results, attempt completion and cursor commit before another request. New client/worker instances skip completed endpoints and reconstruct detail reads. Injected SQL attempt-completion failure leaves DISPATCH_RESERVED/RUNNING and issues no later HTTP. Binding/allowlist/dedicated transport/permits/caps retained; no replacement HTTP implementation. |
| I-1 rule and provider semantics | IMPLEMENTED | worker; client regressions; FlowIT | Reliable persisted evidence and ORCID Professor take DIRECT without Apollo. Full broad-page union survives page-2 429/restart without replaying page 1. Zero results remain PENDING. Page/item caps remain SEARCH_INCOMPLETE, malformed response and 403 are ERROR, 403 stops later claims, 429 preserves transient count, third transient failure persists count=3 and ERROR. |
| I-2 atomic evidence and admission | IMPLEMENTED | DiscoveryVerificationCommitService.kt; DiscoveryReviewService.kt; repository; commit tests; FlowIT | External RAW/layer reads finish before the commit transaction. Fixed SQL lock order and lease/revision checks; current policy/gate evaluates the proposed structured evidence, shared review CAS confirms a real row, admission revision attaches in the same transaction. SQL trigger failure after admission write rolls back both evidence and admission. Strict seam rejects missing-row revision-zero fallback while accepting a confirmed real revision-zero row. |
| I-2 AUDIT | IMPLEMENTED | commit service; FlowIT | AUDIT persists its suggestion without admission or ES writes; auto count remains zero. No manual review items are manufactured. |
| I-3 projection and recovery | IMPLEMENTED | commit service; repository; commit tests; FlowIT | SQL admission event survives ES500; projection-only restart reaches PROJECTED without provider replay or another admission event. ALREADY_PRESENT is distinct from PROJECTED. NOT_ADMITTED is ERROR despite legacy sync.ok. Current admission revision/identity checked before and after projection; later HOLD preserves admission history and stops recovery with SUPERSEDED_BY_REVIEW. |
| I-4 cancellation, fencing and protection | IMPLEMENTED | worker; commit service; repository; FlowIT | Collection SUCCESS/input_closed do not cancel queued verification or create a task. Disabled worker does not claim. Replaced/expired token cannot commit; source identity/fact races become PENDING; protection read failure becomes ERROR. HOLD, REJECTED, MANUAL_APPROVED, LEGACY_APPROVED, SBIR, contact alias and APPLICATION fixtures preserve admission and create no candidate. |
| N-1/N-2, prohibited side effects and downstream seam | IMPLEMENTED | Authorized production files only | Worker has no TaskProgressStore/task-execution writer or mail/sending dependency. RAW is read rather than rewritten. Candidate synchronization uses existing shared projector and does not overwrite existing candidate/application sources. Repository exposes persisted attempts/projection recovery for subsequent read-only reporting. |
| Required regressions and runtime smoke | IMPLEMENTED | Authorized test files and existing regressions | Final JDK11 matrix and standalone external-JVM real-MySQL runtime smoke succeeded; Node ran separately with exact pre-existing baseline retained. Details below. |

### Commands

All commands ran in the exact target worktree. Final matrix command:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationFlowIT,OrcidVerificationEvidenceClientTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationRulesTest,QualificationEvidenceParserTest,ApolloPeopleSearchClientTest,DiscoveryReviewServiceTest,DiscoveryAdmissionPolicyTest,OperatorStatusWriteSeamGuardTest,DiscoveryReviewRepositoryIT,DiscoveryReviewAllPagesTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest,ExpertIndexWriterServiceTest,BatchRecipientSelectionServiceTest
```

Standalone runtime smoke command:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn exec:exec -Dapi.version=1.44 -Dexec.executable=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java -Dexec.classpathScope=test '-Dexec.args=-Dapi.version=1.44 -classpath %classpath com.weibo.talentintroduction.discovery.service.DiscoveryVerificationFlowIT'
```

| Command | Result | Evidence |
|---|---|---|
| Final Maven matrix above | PASS with explicitly recorded legacy skip | Exit 0; tests 264, failures 0, errors 0, skipped 1. FlowIT 11/0/0/0; worker 3/0/0/0; commit 4/0/0/0; ORCID client 6/0/0/0; ledger MySQL IT 8/0/0/0. Final raw output artifact://191; completed 2026-10-18T21:38:56+08:00. |
| Standalone external-JVM smoke above | PASS | Exit 0; fresh disposable MySQL8 with real transaction proxies, real worker/clients and mocked HTTP fixtures. Output: `RUNTIME_SMOKE auto=1 projection=PROJECTED apolloRequests=2 no-provider-replay=true candidates=1`. ES500 was injected first; a reconstructed worker repaired projection only. artifact://191; completed 2026-10-18T21:39:18+08:00. |
| `node --test src/test/js/discoveryContinuousRun.test.js src/test/js/discoveryScheduleSetting.test.js src/test/js/discoveryReview.test.js` | PASS | Exit 0; tests 70, pass 70, fail 0, skipped 0. artifact://177. |
| `node --check src/main/resources/static/app.js` | PASS | Exit 0, between focused and full Node commands. artifact://177 chained command reached the full suite. |
| `node --test src/test/js/*.test.js` | Existing baseline FAIL, unchanged | Exit 1; tests 1694, pass 1653, fail 41, skipped 0, exactly the supplied baseline. Missing extraction-sandbox helpers/old recipient-preview expectations and preview cache-key rejection remain out of scope. No JS/product repair performed. artifact://177. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-18/apollo-04-worker.md` | PASS | Exit 0 at preflight and final handoff; canonical path and SHA-256 unchanged. artifact://194 final receipt. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-18/apollo-04-worker.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master --expect-root /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master --expect-branch fast/apollo-verification-master --expect-git-dir /Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-apollo-verification-master` | PASS | Exit 0 before each staging/commit and at handoff; final helper HEAD is the product SHA. artifact://192/193/194. |
| `git diff --check`; `git diff --cached --check`; `git diff --cached --name-only` | PASS | Exit 0; staging receipt lists exactly the nine authorized files and excludes all fast-p artifacts. artifact://192. |
| `git commit -m 'feat(fast-p): implement 04'` | PASS | Exit 0; a0103c2ea9284c030ad0b0b10c4177474c4f4d47; nine files in commit. artifact://193. |
| `git rev-parse HEAD`; `git merge-base --is-ancestor a0103c2ea9284c030ad0b0b10c4177474c4f4d47 refs/heads/fast/apollo-verification-master`; `git status --short` | PASS | Exit 0; target HEAD/branch reachability confirmed. After product commit only controller-owned ledger was dirty; this separately allowed report is appended afterward. artifact://193/194. |

#### Execution diagnostics retained, not substituted for final evidence
- Initial matrix failed test compilation because the Spring Mock HTTP response builder uses `headers(...)`, not `header(...)`; corrected in authorized FlowIT. artifact://178, exit 1; no test PASS claimed.
- Next expanded matrix: 261 tests, zero failures, two errors, one legacy skip. FlowIT needed its isolated MySQL trigger-creator setting; a unit-test-only snapshot read needed the shared snapshot builder instead of strict deserialization of computed profile properties. Corrected within authorized tests. artifact://180, exit 1.
- Next matrix: 264 tests, one failing pagination assertion, zero errors, one legacy skip. Assertion substring `page=1` also matched `per_page=100`; changed to exact query-token comparison without weakening expected no-replay behavior. artifact://184, exit 1.
- Subsequent matrices passed (artifact://187, artifact://189). Final code also removes repeated attempt-response reads/recursive whitelist allocation and durably records the terminal transient count; artifact://191 is fresh after those final changes.
- Earlier in-process `exec:java` smoke reached all assertions and exit 0 but emitted a Testcontainers shutdown classloader warning (artifact://187). The final smoke uses an external JDK11 JVM and does not have that classloader warning.

### Changed Files
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` — bounded asynchronous orchestration, durable progress, retries, fencing and lease renewal.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt` — external preflight, atomic evidence/admission commit, independent projection and recovery.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` — worker progress/attempt atomicity, commit locks, actual revision attachment, terminal failure accounting and projection recovery reads.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` — strict persisted-row confirmation using existing CAS/protection logic; old callers unchanged.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidVerificationEvidenceClient.kt` — existing-client endpoint completion/resume and bounded parser-selected persisted response shape.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` — disabled/optional recovery and scheduling boundaries.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitServiceTest.kt` — lease/manual protection, strict actual-row confirmation and NOT_ADMITTED projection.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt` — isolated MySQL/Mock HTTP fixture, fault/race/restart acceptance and standalone runtime entry.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidVerificationEvidenceClientTest.kt` — endpoint persistence/resume, reconstructed detail cursor, selected-field persistence and acknowledgement failure.
- `docs/plans/fast/apollo-verification-master/children/04/execution.md` — this appended evidence only; excluded from product commit. Controller-owned ledger preserved.

### Deviations / observations
- No product-scope or semantic amendment beyond approved A3/A4. No unlisted implementation/test/doc edits, real provider traffic, shared/production DB access, mail, push, merge, history rewrite or deployment.
- `DiscoveryReviewRepositoryIT` is a pre-existing `mysqlIt=true` test targeting localhost/shared DB and performing DELETEs. It remains gated/skipped; it is not represented as a MySQL PASS. New required FlowIT and ledger IT executed against disposable Testcontainers MySQL without skips.
- `src/test/js/discoveryVerification.test.js` is not present in this child (glob receipt); no fake future-test execution. Existing C-7 Node files and full suite ran separately, permitting `-DskipNodeTests=true` in Maven while retaining the known baseline.
- Browser widths/visual checks belong to the later frontend implementation; no frontend changed here and no browser PASS claimed.
- Approved permanent fixture/runtime entry is inside the authorized FlowIT test file. No throwaway driver or scaffold file was left behind. Defaults remain disabled in the unchanged configuration.
- Real provider credentials/account limits and rollout acceptance remain human rollout prerequisites; application caps are not claimed as provider guarantees.

### Freshness
- Plan identity rechecked: YES
- Worktree identity rechecked: YES
- Reported product commit is target HEAD and reachable from target branch: YES
- Product worktree/index clean after commit: YES; only separately allowed controller ledger/report artifacts remain dirty
- Required commands run this invocation: YES
- Historical evidence used only as baseline: YES
- Independent verification performed: NO — READY_FOR_VERIFICATION is execution readiness, not PASS

### Remaining Blocker
- None for the authorized child04 implementation. Existing full-Node baseline and legacy shared-DB skip are explicitly retained observations, not silently repaired or represented as success.

### Next Action
- Controller runs the independent fast-p four-gate light verifier for Epoch2 and commits evidence separately. Do not invoke full verify-p/review-p/repair-p/fix-v inside this loop or advance to a later child before the required checkpoint.

## Epoch 2 — Controller terminal closure
- Implementation: a0103c2ea9284c030ad0b0b10c4177474c4f4d47
- Writer: ApolloWorkerImplementerE2
- Independent verifier: ApolloWorkerVerifierE2
- Terminal verdict: LIGHT_PASS_WITH_NOTES
- Required Action: COMPLETE_CHILD
- Fix round: 0
- Fix commits: —
- RECORD_ONLY: O-04-E2-01 existing Node baseline; O-04-E2-02 optional shared-DB IT skip, not pass.
- No whole-system verification was performed.

## Epoch 2 — Evidence closing correction 1
- Child04 artifact checkpoint required a bare COMPLETE_CHILD token; appended the actual verifier action canonically without changing the verdict or product.
- A4 master Before canonicalized to preceding master amendment After, whose master bytes equal the pre-amendment pause HEAD. Implementation remains a0103c2ea9284c030ad0b0b10c4177474c4f4d47; Fix round remains0.
