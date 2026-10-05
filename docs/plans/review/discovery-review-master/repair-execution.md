Current handoff status: **READY_FOR_VERIFICATION** for the latest V-3 repair identity, recorded in [the V-3 execution handoff](#v-3-execution-result-ready_for_verification). Reports below with older plan hashes are historical; their approval/completion state is not transferred to the current plan. No independent review was invoked in this execution.

## Execution Result: BLOCKED

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/fix/discovery-review-master/repair.md`

Plan SHA-256: `5e687250f9b420d87483eec38d35fe1708969c0098a5e89129596b0b0e8765f4`

Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/fix/discovery-review-master/repair.md@5e687250f9b420d87483eec38d35fe1708969c0098a5e89129596b0b0e8765f4`

Execution epoch: NEW

Approval basis: exact human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/fix/discovery-review-master/repair.md` invocation; repair.md lines 89–107 authorize execution and this evidence handoff. No amendment to the all-commands-pass product-commit prerequisite was supplied.

Executor: Main coding assistant (openai-codex/gpt-6.1-sol); implementation delegates `RepairOutreachDedup` and `RepairInterruptedReview`. Main integrated the test-contract correction, ran commands/smokes, and owns this report. This is execution evidence, not independent verification or PASS.

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`

Target branch: `fast/2026-10-04-discovery-review-master`

Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master@fast/2026-10-04-discovery-review-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`

Pre-execution code SHA: `078bd8b5f8c33fc9c82282e75d38900b17756051`

Post-execution code SHA: N/A — no repair product commit is authorized while a required command fails. Last committed code remains `078bd8b5f8c33fc9c82282e75d38900b17756051`; six repaired product/test files remain modified and unstaged.

Evidence HEAD: the single docs-only commit containing this report, subject `docs(review-fast-p): record repair execution`. Its resolved SHA is returned in the execution response; it is not a product-code SHA.

Implementation boundary: six authorized working-tree changes against the pre-execution SHA. No push, merge, deployment, amendment, migration edit, or aggregate re-review.

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| R-1 / V-1 | IMPLEMENTED | ManualInitialOutreachService.kt; ManualInitialOutreachServiceTest.kt | Counts seed a copied normalized-identity set from retry selection, consume identity before selector/filter, and deduplicate across ES pages/levels without consuming the execution iterator's set. Regression covers retry precedence, cross-page/level duplicates, excluded first duplicates, and historical retry exclusion. Focused tests pass. Actual service smoke: preview=1, total=1, sent=1, remaining=0, one mocked SMTP attempt. |
| R-2 / V-2 | IMPLEMENTED | DiscoveryReviewService.kt; DiscoveryReviewAllPagesTest.kt; app.js; discoveryReview.test.js | Persisted INTERRUPTED apply task with pending items derives INTERRUPTED, not APPLIED. UI stops review polling, disables confirmation, preserves submitted selection, shows interruption, and does not run completion refresh or automatic retry. Explicit record retry remains available. Tests cover unchanged counts/hash, partial progress, explicit retry/CAS, cancellation, and zero-pending completion. |
| Required commands and runtime smoke | IMPLEMENTED | Verification only | All four required commands ran freshly against final source state; full Maven failed. Two production service smokes outside JUnit and real-browser interrupted UI observation were performed. |
| Product commit prerequisite | BLOCKED | Six authorized files remain unstaged | repair.md line 102 requires all repair tasks and required commands to pass. Full Maven exited 1. No product commit or READY_FOR_VERIFICATION declaration. |

### Commands

All repository commands used the exact target worktree. JDK 11 was used for every Maven command.

| Command | Result | Evidence |
|---|---|---|
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=ManualInitialOutreachServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewServiceTest test` | PASS, exit 0 | Final rerun: 222 tests, 0 failures/errors/skipped; BUILD SUCCESS. `artifact://1312`. |
| `node --test src/test/js/*.test.js` | PASS, exit 0 | Final rerun: 1462 tests, 278 suites, 1462 pass, 0 failures/cancelled/skipped; `artifact://1315`. |
| `env DB_URL='jdbc:mysql://localhost:3306/talent_introduction_fastp?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true' DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -DmysqlIt=true -Dtest=DiscoveryReviewRepositoryIT test` | PASS, exit 0 | Isolated MySQL database: 13 tests, 0 failures/errors/skipped; BUILD SUCCESS. `artifact://1316`. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test` | FAIL, exit 1 | 4661 tests, 1 failure, 19 errors, 13 skipped; BUILD FAILURE. `artifact://1320`. |
| `git diff --check && git diff --stat && git diff --name-only` | PASS, exit 0 | Exactly six authorized product/test files; 324 insertions, 14 deletions. No diff whitespace errors. |

Initial focused run after delegated implementation failed three existing assertions expecting duplicate-inflated totals of 2. The approved R-1 contract requires 1. Updated only those total assertions and removed contradictory comments; retained send-count/retry behavior assertions. Final focused command above passed. Initial run: 222 tests, 3 failures, 0 errors; `artifact://1307`. The final node command also reran after this last source edit. No required command is credited solely from historical output.

Full Maven failures:

- 19 errors in `ExpertContactLocationServiceTest`: `America/Coyhaique` unknown to the required JDK 11 timezone database. This is the plan's explicitly excluded baseline; it was not repaired or suppressed.
- One newly observed failure in `RestTemplateConfigTest.a deadline wider than both socket caps still wraps the body and cuts a trickle {R-1, V-4}`, line 295: expected absolute-deadline truncation; actual `ResourceAccessException` wrapping `SocketException: Connection reset`, local GET `/wide`. Test-set output: 18 tests, 1 failure, 0 errors. Full detail: `target/surefire-reports/com.weibo.talentintroduction.config.RestTemplateConfigTest.txt`. This is outside authorized scope. Baseline/flakiness/causality is not established; do not label it pre-existing or transient, and do not silently waive it. No repeated confirmation run or unauthorized fix was made.

### Runtime Smoke

- JDK 11 JShell invoked actual `ManualInitialOutreachService.countBySnapshot()` and `run()` outside the JUnit runner, using compiled fixture setup and mocked external seams. Input identities differed in whitespace/case. Observed `ACTUAL_OUTREACH_SMOKE preview=1 total=1 sent=1 SMTP attempts=1 remaining=0`. No real SMTP delivery was attempted.
- JDK 11 JShell invoked actual `DiscoveryReviewService.batchStatus()` outside the JUnit runner against fixture-backed persisted prepare/interrupted-apply records. Observed `ACTUAL_SERVICE_SMOKE phase=INTERRUPTED pending=3 total=3 hashPreserved=true`.
- Real Chromium loaded actual index.html/app.js via disposable static server on port 18767 with mock API responses. Opened discovery task modal/review tab; applied an interrupted batch with total=3, applied=1, pending=2, fixed hash. Observed phase INTERRUPTED, message `审核已中断，仍有待处理项；不会自动继续，请在审核记录中明确重试`, disabled confirmation, review pollTimer null, and no immediate review continuation requests. General task-modal progress GETs may continue; they are not review auto-retry. Visual evidence: `/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/omp-sshots-159a5e78d57bceee.webp`.
- Runtime limitation: fixture/mock persistence and mock HTTP were used; this is not a deployed end-to-end SMTP/ES interruption exercise. JShell inputs were disposable stdin, no scaffold files retained. Browser tab closed and static server stopped.

### Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` — deduplicated retry/ES preview and execution totals, copied prescan identity set.
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` — identity/precedence/filter regressions and approved deduplicated total assertions.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` — persisted pending interruption phase derivation.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt` — interrupted count/hash/retry/cancellation/completion regressions.
- `src/main/resources/static/app.js` — interrupted display, terminal handling, polling stop, retained submitted state, explicit retry label.
- `src/test/js/discoveryReview.test.js` — interrupted-not-complete UI transition and explicit retry regression.
- `docs/plans/review/discovery-review-master/repair-execution.md` — only authorized evidence-commit file, this report.

### Deviations

- Product commit not created: full required Maven command did not pass. This is an unmet completion prerequisite, not an approved acceptance downgrade.
- Failing-before proof for newly added regression cases was not run against reverted baseline production code. Prior aggregate V-1/V-2 findings are baseline evidence only. Fresh post-change tests and service/UI smokes provide execution evidence, not independent plan compliance.
- D1, excluded observations, historical resend/unsubscribe/account binding policy, mappings/migrations, SMTP API, scheduler/task-platform behavior and adjacent asynchronous timing were not changed. No new repair plan was generated.

### Freshness and Clean State

- Plan identity rechecked: YES, same SHA-256; helper exit 0.
- Worktree identity rechecked: YES; exact root/branch/git-dir matches; helper exit 0.
- Required commands run this invocation after final source edit: YES.
- Historical evidence used only as baseline: YES.
- Initial target working tree/index: clean.
- Before evidence staging: six authorized files modified, all unstaged; HEAD unchanged from pre-execution code SHA.
- Evidence commit staging: only this report; verify its HEAD and target-branch ancestry immediately after commit.
- Expected final state: clean index, six authorized product/test modifications still unstaged. Working tree is intentionally NOT clean; no claim of committed repair completion.

### Remaining Blocker

Required full Maven command fails. The plan authorizes a product commit only after every required command passes. Resolving the new HTTP/deadline test failure or changing acceptance requires authority outside the current six-file repair; the excluded timezone errors cannot be silently promoted to a PASS either.

### Next Action

Human decision/amendment is needed: retain strict all-commands-pass and authorize a separately scoped resolution, or explicitly authorize proceeding with named out-of-scope failures after their disposition. Do not automatically retry, expand repair scope, commit product files, or run aggregate re-review under this blocked execution. On an approved amendment, re-read the exact plan bytes and re-establish execution identity before resuming.

## Amended Execution Result: READY_FOR_VERIFICATION

### Identity and Approval

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/fix/discovery-review-master/repair.md`
- Plan SHA-256: `5a4a0786b50405000dd81e090d70a9dbf7191e98be6dc922be1e79335efae091`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/fix/discovery-review-master/repair.md@5a4a0786b50405000dd81e090d70a9dbf7191e98be6dc922be1e79335efae091`
- Execution epoch: NEW — amended plan bytes, not a completion carryover from the previous hash.
- Approval basis: after recommending a minimal commit-prerequisite amendment, the human said “你不要调用 review-fast-p 你只要完成达到条件的就行了”, then “好的 继续” after the assistant explicitly restated amendment, six-file product commit, updated evidence, and no review invocation.
- Additional explicit human amendment during this execution: the new `UnmatchedInboundAiReplyTurnKnowledgeTest.kt:1090` 1 Hz failure was surfaced with two choices. The human selected **“允许具名交接”**: commit the six repair files and complete evidence, retaining this named failure unresolved for independent judgment. This overrides the plan's “Other failures still block handoff” only for this specific newly observed failure; it does not waive the failure, authorize unrelated fixes, or label it baseline/transient. Record this approval here without changing the execution's immutable plan bytes.
- Executor: Main coding assistant, openai-codex/gpt-6.1-sol. No new implementation delegate or independent verifier in this epoch.
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`
- Target branch: `fast/2026-10-04-discovery-review-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master@fast/2026-10-04-discovery-review-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`
- Pre-execution code SHA: `078bd8b5f8c33fc9c82282e75d38900b17756051` (last code-bearing commit; previous blocked report is docs-only).
- Invocation starting HEAD: `79bbc1638622c8cf19f89ae7c13188a53abc25d3`.
- Plan amendment commit: `4de97115fdb20aa6a66009a631be7871031d993c`, subject `docs(discovery-review): record approved handoff prerequisite`, containing only repair.md.
- Post-execution code SHA: `6b5c201c7a8667376cb214f74a7069594cc9a6cc`, subject `fix(discovery-review): align target totals and interrupted review state`.
- Evidence HEAD: the later report-only commit containing this appended handoff, subject `docs(review-fast-p): record repair execution`; its resolved SHA is emitted in the final response, not conflated with the product SHA.
- Implementation boundary: `078bd8b5f8c33fc9c82282e75d38900b17756051..6b5c201c7a8667376cb214f74a7069594cc9a6cc`. Product commit itself contains exactly the six authorized files; intervening report/plan commits contain no product changes.

### Task Status

| Requirement | Status | Files / Evidence |
|---|---|---|
| R-1 / V-1 | IMPLEMENTED | Existing current six-file diff inspected freshly; copied retry-seeded global normalized identity set is used for preview/run prescan. Regression proves duplicate totals, target/SMTP parity, retry precedence and first-filter exclusion. Fresh JVM checks and actual-service smoke pass. |
| R-2 / V-2 | IMPLEMENTED | Current diff derives pending interruption before completion fallback; frontend stops review polling and completion refresh, retains interrupted snapshot, and exposes only explicit retry. Fresh JVM/JS checks and actual-service interruption smoke pass. |
| Amended commit prerequisite | IMPLEMENTED | Focused JVM, Node and isolated MySQL all pass; full Maven ran and failures are reported. Human explicitly authorized named 1 Hz failure to remain unresolved at handoff. |
| Product and evidence handoff | IMPLEMENTED | One six-file product commit, separate plan amendment, and one report-only evidence commit for this epoch. No review invocation, push, merge, deployment or history rewrite. |

### Fresh Required Commands

All commands used the exact target worktree and ran after final product-source state was established. Executed sequentially with `&&`; the first three exited 0, enabling the next command, and the full Maven command exited 1. No skipped/excluded test flags were added. Full sequence output: `artifact://1326`; final command detail: `artifact://1329`.

| Exact command | Result |
|---|---|
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=ManualInitialOutreachServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewServiceTest test` | PASS, exit 0: 222 tests, 0 failures/errors/skipped; BUILD SUCCESS. |
| `node --test src/test/js/*.test.js` | PASS, exit 0: 1462 tests, 278 suites, 1462 pass, 0 failures/cancelled/skipped. |
| `env DB_URL='jdbc:mysql://localhost:3306/talent_introduction_fastp?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true' DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -DmysqlIt=true -Dtest=DiscoveryReviewRepositoryIT test` | PASS, exit 0: 13 tests, 0 failures/errors/skipped; BUILD SUCCESS. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test` | FAIL, exit 1: 4661 tests, 1 failure, 19 errors, 13 skipped; BUILD FAILURE. |
| `git diff --check` (as the prerequisite of the current authorized diff inspection) | PASS, exit 0, no whitespace errors. |

Fresh full-suite disposition:

- 19 `ExpertContactLocationServiceTest` errors: `America/Coyhaique` unknown to JDK 11, still the specifically excluded baseline.
- One failure: `UnmatchedInboundAiReplyTurnKnowledgeTest.real endpoint keeps same phase progress at one hertz`, line 1090: `endpoint same phase progress must wait for the 1 Hz window`, expected 1, actual 2. Explicitly human-authorized as a named unresolved handoff item; no causal/baseline/flakiness conclusion, no out-of-scope fix, and no rerun-to-green.
- `RestTemplateConfigTest` passed all 18 tests in this run. Its earlier `Connection reset` failure remains historical unresolved evidence; a passing fresh run does not establish the earlier failure's cause or harmlessness.
- Scope receipt: `git diff --` against the six authorized paths reported six files, 324 insertions, 14 deletions (`artifact://1328`); product `git diff-tree --no-commit-id --name-only -r 6b5c201c7a8667376cb214f74a7069594cc9a6cc` returned exactly those six paths. No claim that the full suite passed.

### Fresh Runtime Smoke

Disposable JDK 11 JShell stdin invoked actual production service methods outside JUnit against compiled fixtures and mocked external seams. Observed exit 0 and both markers (`artifact://1331`):

- `ACTUAL_OUTREACH_SMOKE preview=1 total=1 sent=1 SMTP attempts=1 remaining=0`.
- `ACTUAL_REVIEW_SMOKE phase=INTERRUPTED pending=3 total=3 hashPreserved=true`.

No real SMTP delivery, live ES or deployed interruption was exercised. The prior real-browser interruption observation is historical supporting evidence only; it was not rerun in this docs/commit-only continuation. No permanent smoke scripts retained.

### Changed Files and Boundaries

Product commit paths:

- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` — deduplicated accounting.
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` — target/precedence/exclusion regressions and corrected duplicate totals.
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` — pending interruption phase.
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt` — interruption/retry/cancellation regressions.
- `src/main/resources/static/app.js` — interrupted display and terminal behavior.
- `src/test/js/discoveryReview.test.js` — interrupted UI transition and explicit retry.

Documentation-only paths: `docs/plans/fix/discovery-review-master/repair.md` (approved commit prerequisite), and this execution report (append-only epoch evidence plus current-status pointer). No extra product file, migration, mapping, SMTP API, scheduler/task-platform behavior or D1 policy change.

### Deviations, Freshness and Clean State

- Acceptance change is human-approved and limited to allowing the named outside-scope failures into independent verification; it is not a PASS declaration or unilateral test waiver.
- No product source/test edits were needed in this new epoch: inspected existing repair diff, ran fresh checks/smokes, and committed it. The earlier failing-before regression proof limitation is retained; historical tests do not satisfy current required commands.
- Plan hash rechecked unchanged before each staging/commit and handoff; worktree root/branch/git-dir checked using the execute-p helpers.
- Product and docs commits verified as target-worktree HEAD when created and as ancestors of the target branch, with exact commit file scopes.
- Working tree and index were clean immediately after the product commit. After the report-only evidence commit, confirm both are clean before emitting final handoff.
- Required commands ran freshly in this invocation: YES. Historical evidence used only as baseline/support: YES.

### Remaining Blocker and Next Action

No remaining execution/commit blocker under the explicit approvals. The named HTTP and 1 Hz failures remain unresolved matters for independent verification; D1 remains a policy/release gate. `READY_FOR_VERIFICATION` means ready to hand off, not PASS or release approval.

Human instruction: **do not invoke review-fast-p or any independent verification now**. Stop after committing this handoff and reporting exact SHAs/clean state. A later human invocation owns the review decision.

## V-3 Execution Result: READY_FOR_VERIFICATION

### Execution Identity

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/fix/discovery-review-master/repair.md`

Plan SHA-256: `b1ab4f0f97c530717ecfd221a6c05a3c95de50c0b5d35f141c928511ff45739d`

Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/fix/discovery-review-master/repair.md@b1ab4f0f97c530717ecfd221a6c05a3c95de50c0b5d35f141c928511ff45739d`

Execution epoch: NEW. The same path now contains V-3 and authorizes only the service and its test, not the prior V-1/V-2 repair. Read the current complete plan from disk and reset task/file/command/commit evidence.

Approval basis: exact current human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/fix/discovery-review-master/repair.md` invocation, interpreted under its execution-handoff section. No plan modification or additional failure waiver in this epoch.

Executor: Main coding assistant, openai-codex/gpt-6.1-sol; inline implementation, no delegated or independent verification agent.

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`

Target branch: `fast/2026-10-04-discovery-review-master`

Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master@fast/2026-10-04-discovery-review-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`

Pre-execution code SHA: `6b5c201c7a8667376cb214f74a7069594cc9a6cc`.

Invocation starting HEAD: `0a26e56ab0266e0fcb962805a02a3acab2dc4975`; initial worktree/index clean.

Post-execution code SHA: `0ce61cc2d8bc4dd7c2de868a9d3a78eb180b1e41`.

Product subject: `fix(discovery-review): preserve real document target uniqueness`.

Evidence HEAD: the later report-only commit containing this section, subject `docs(review-fast-p): record repair execution`; resolved SHA is emitted in the final execution response. It is not the product SHA.

Implementation boundary: current product commit `0a26e56ab0266e0fcb962805a02a3acab2dc4975..0ce61cc2d8bc4dd7c2de868a9d3a78eb180b1e41`; code-bearing boundary `6b5c201c7a8667376cb214f74a7069594cc9a6cc..0ce61cc2d8bc4dd7c2de868a9d3a78eb180b1e41`.

### Task Status and Changes

| Requirement | Status | Files / Evidence |
|---|---|---|
| R-1 / V-3 real-docId target uniqueness | IMPLEMENTED | `ManualInitialOutreachService.kt`: retry construction retains one selector representative per real docId before template gating; target conversion has both normalized-ORCID and real-docId guards. Eligible retries seed the docId set used by preview and execution. ES count copies both identity sets, removes already retained real docIds and page-local duplicate representatives, then records surviving identities. Iterator `filterPage` uses the same seeded docId semantics without changing OutreachTargetIterator. |
| Regression and ORCID preservation | IMPLEMENTED | `ManualInitialOutreachServiceTest.kt`: parameterized retryCount=0/1/2; different ORCIDs share one esDocId across pages/levels and retry/ES, plus a normalized-ORCID alias with another esDocId. Preview/total/target/SMTP attempt are one; first profile or first retry wins. The existing V-1 regressions remain unchanged and pass. |
| V-2 unchanged behavior | IMPLEMENTED | Full Maven freshly ran `DiscoveryReviewAllPagesTest`: 15 tests, zero failures/errors, and `DiscoveryReviewServiceTest`: 27 tests, zero failures/errors. Full Node suite passes. No V-2 product/test files changed. |
| Required commands and runtime proof | IMPLEMENTED | Four current required commands ran against final source state; focused JVM, Node and isolated MySQL pass. Full Maven fails only on the explicitly excluded timezone errors. Actual production service smoke outside JUnit observes one target/SMTP attempt. |
| Commit/evidence contract | IMPLEMENTED | One current-epoch two-file product commit with the exact subject, then one report-only evidence commit. Scope/reachability/identity/clean-state gates recorded below. No review invocation. |

Product changed files:

- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` — real-docId uniqueness through retry, preview prescan and execution page filtering; private prescan copies prevent consuming execution state.
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` — same-docId/different-ORCID target parity, retry precedence and retained ORCID guard regression.

Control-plane changed file: only this authorized execution report. No approved-plan edit, new product file, iterator change, schema/migration/mapping, selector admission behavior, D1 policy, account assignment, send-attempt uniqueness policy or SMTP API change.

### Failing-Before / Passing-After

Added the regression before changing production code and ran the focused JVM command. Observed 183 tests, 3 failures, 0 errors/skipped, exit 1 (`artifact://1337` / `artifact://1338`): all new retryCount scenarios failed on preview pending, actual 4 versus expected 1 without retry or expected 0 with retry. Existing tests had no failures.

After the production repair, reran all required commands freshly. All three new scenarios and the full focused class passed without changing the new assertions. No failing test was removed or weakened.

### Exact Commands and Results

All repository commands used the exact target worktree and Maven used the required JDK 11. The four final commands ran sequentially with `&&`; first three exit 0 enabled the next, and full Maven exited 1. Sequence evidence: `artifact://1340`; final Maven summary: `artifact://1341`. No suppression/exclusion flags were added.

| Exact command | Result |
|---|---|
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=ManualInitialOutreachServiceTest test` | PASS, exit 0: 183 tests, 0 failures/errors/skipped; BUILD SUCCESS. |
| `node --test src/test/js/*.test.js` | PASS, exit 0: 1462 tests, 278 suites, 1462 pass, 0 failures/cancelled/skipped. |
| `env DB_URL='jdbc:mysql://localhost:3306/talent_introduction_fastp?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true' DB_USERNAME=root DB_PASSWORD=root JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -DmysqlIt=true -Dtest=DiscoveryReviewRepositoryIT test` | PASS, exit 0: 13 tests, 0 failures/errors/skipped; BUILD SUCCESS. |
| `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test` | FAIL, exit 1: 4664 tests, 0 failures, 19 errors, 13 skipped; BUILD FAILURE. All errors are `ExpertContactLocationServiceTest` construction errors: `America/Coyhaique` unavailable in JDK 11, explicitly excluded by this plan. |
| `git diff --check && git diff --stat && git diff --name-only && git diff -- src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | PASS, exit 0: exactly the two authorized files, 117 insertions and 14 deletions; `artifact://1345`. |

Count/scope receipts: `grep` over `artifact://1340` for `Tests run: 183`, `Tests run: 4664`, `DiscoveryReviewAllPagesTest`, `DiscoveryReviewServiceTest`, `RestTemplateConfigTest`, Node totals and `BUILD SUCCESS` showed focused 183 green, full 4664/0F/19E/13S, V-2 15+27 green, and HTTP 18 green. The `UnmatchedInboundAiReplyTurnKnowledgeTest` class also passed 37 tests in this fresh full run. These are observed run results, not retrospective causality/flakiness claims.

### Actual-Service Smoke

Disposable JDK 11 JShell stdin loaded the newly compiled service and existing fixture setup, not a test-method invocation. Input: FIRST and SECOND have different normalized ORCIDs but share esDocId `shared-real-id`; a ` first ` alias has esDocId `other-real-id`. Called actual `countBySnapshot()` and `run()` and verified one mocked SMTP attempt. Observed exit 0 and:

`V3_ACTUAL_SERVICE_SMOKE distinctORCIDs=2 sharedDocId=1 aliasOtherDocId=1 preview=1 total=1 sent=1 remaining=0 SMTP attempts=1`

Limit: mocked external seams/fixture setup, not live ES or real SMTP. No smoke file was retained, no deployed UI change was made, and the missing live local ES index remains an excluded environment evidence gap rather than a product repair.

### Deviations and Freshness

- Deviations: none to current authorized repair/commit scope. Full Maven FAIL is reported as FAIL; only this plan's named 19 timezone errors remain. No old HTTP/1 Hz waiver was applied to the new plan hash.
- Plan identity rechecked unchanged: YES, execute-p plan helper before staging/commits and handoff.
- Worktree root/branch/git-dir rechecked: YES, execute-p worktree helper with exact expected values.
- Required commands ran freshly after final implementation: YES.
- Historical evidence used only as baseline: YES; earlier product/evidence commits did not satisfy current V-3 tasks.
- Product commit verified as target HEAD at creation and ancestor of target branch, with `git diff-tree --no-commit-id --name-only -r` returning exactly the two authorized paths. Evidence commit receives the same HEAD/ancestry/file-scope gate.
- Initial tree/index clean; post-product tree/index clean. Confirm final tree/index clean after report-only commit before final delivery.

### Remaining Blocker and Next Action

No remaining execution blocker under the current plan's explicitly excluded timezone baseline. This is `READY_FOR_VERIFICATION`, not independent PASS, live-ES proof or D1 release approval.

No `review-fast-p`, `verify-p`, aggregate re-review, push, merge, deployment or history rewrite was invoked. Stop at execution handoff; independent verification requires a separate human request.
