# Child 05 execution log

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-05-cache.md`

Plan SHA-256: `d26d5f80c547da19d8e6fc9d8bb90722d7261b2831257940d893eac75ba7c34d`

Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master/docs/plans/2026-10-22/batch-demand-05-cache.md@d26d5f80c547da19d8e6fc9d8bb90722d7261b2831257940d893eac75ba7c34d`

Execution epoch: NEW

Approval basis: current child05 dispatch and recorded `/fast-p` approval. Approved plan commit `f684da0a316b8cfb560fe7ebb0a8817d8a2594f2`; `git show f684da0a316b8cfb560fe7ebb0a8817d8a2594f2:docs/plans/2026-10-22/batch-demand-05-cache.md | shasum -a 256` returned the same SHA-256.

Executor: `Demand05Implementer`, with isolated template/fingerprint, endpoint, and cache-test implementation slices; executor integrated changes and ran checks sequentially after integration.

Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master`

Target branch: `fast/batch-demand-master`

Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master@fast/batch-demand-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-batch-demand-master`

Pre-execution code SHA / child base: `5b4bc0a134a7af7c08ce0b7ac5376ee567649c47`

Pre-execution evidence HEAD: `248e33230c30e9d53751c252caae1fe5b0425d8d`

Post-execution code SHA: `0d5f7dbf6255cae21f263d6136571b2912454a19`

Evidence HEAD at executor handoff: `0d5f7dbf6255cae21f263d6136571b2912454a19`; this appended report is intentionally not committed by the executor. Controller owns subsequent evidence/ledger commits.

Implementation boundary: `248e33230c30e9d53751c252caae1fe5b0425d8d..0d5f7dbf6255cae21f263d6136571b2912454a19`

Product commit: `feat(fast-p): implement 05`. Commit contains exactly the authorized product/test paths listed below, not this report or the controller-owned ledger.

### Task Status

| Requirement | Status | Implementation and execution evidence |
|---|---|---|
| I-1 display-only / zero business writes | IMPLEMENTED | Controller calls cache, which uses only template fingerprint and Manual read-only context/strict-statistics seams. Manual has no cache dependency. Real Manual/cache/API smoke asserts no delivery, transaction helper, task, attempt, contact/campaign/mail-record save, binding or ES-writer calls. Verification interactions are limited to historical reads. Legacy preview remains on `countBySnapshot`; diagnostics endpoint remains unchanged. |
| I-2 effective canonical key / full template identity | IMPLEMENTED | Key hashes structured effective scope, resolved template identity/fingerprint, rule version `v1`, gate fields/switch, verification policy and exclusion/type/direction/region filters. Collection order/duplicates canonicalize; pacing is excluded. Template fingerprint includes live subject/default/reference contents, block boundaries/reference existence/enabled state and available variants. Existing launch `versionToken` implementation is unchanged. Fingerprint tests include a golden legacy-token expectation and default-template switch. |
| I-3 bounded LRU / single-flight / failure atomicity | IMPLEMENTED | Access-order completed store capped at 64; distinct in-flight map capped at 2; existing futures are joined outside the monitor. Active refresh entries retain old successes during LRU churn. Completion-only Shanghai timestamps; 24-hour stale marking without refresh. Controlled failures preserve old summary/time, or ERROR with no fabricated summary. Concurrency tests exercise 20 same-key requests, third-key busy, nonblocking lookup and slot reuse. |
| I-4 explicit endpoint status contract | IMPLEMENTED | Added `POST /api/mail/batch-send/recipients/statistics`; snapshot body, `cacheOnly`/`refresh` query flags. States MISS/READY/REFRESHING/ERROR, opaque key, nullable value/time/error code, stale flag; `diagnosticLimit=20000` explicitly identifies the bounded diagnostic scope. ERROR maps to 503; conflicting flags rejected as 400. Old controller constructors remain source compatible through the appended optional cache argument. Standalone MockMvc tests exercise request binding and status behavior. |
| I-5 complete strict targets / bounded independent diagnostics | IMPLEMENTED | Strict target count uses PIT cursor traversal instead of legacy swallowing scroll; NEW uses keyset pages and strict PIT/mget lookup, sharing normalized ORCID/doc/person identity reservations with ES counting. Diagnostics independently use strict PIT/mget and NEW keyset with shared 20000 collected-profile cap and `partial=true` at cap. Material count reuses child04 read-only streaming source. Cursor resources close through `use`. Tests cover 30001 complete targets with diagnostic baseline 20000, retry/ES page-boundary dedupe, same-person retry/ES dedupe, null/failed-shard/timed-out PIT responses and cache rejection, and template drift. |
| Scope / commit | IMPLEMENTED | Exact authorized paths staged explicitly; `git diff --cached --check` exited 0; product commit and branch reachability confirmed. No migrations, framework/cache dependencies, background refresh, plan amendments or knowledge edits. |

### Commands

All repository commands ran with target-worktree cwd. All Maven commands used the authorized installed JDK 11 home `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`.

| Command | Result | Evidence |
|---|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-10-22/batch-demand-05-cache.md` | PASS, exit 0 | Initial and final canonical path/hash matched the SHA above, 12631 bytes. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-10-22/batch-demand-05-cache.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-batch-demand-master` | PASS, exit 0 | Initial HEAD `248e332...`; root/branch/git-dir bound to retained worktree. Expected-identity options were also used immediately before staging, commit, and final handoff. |
| `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=BatchRecipientStatisticsCacheTest,ManualInitialOutreachServiceTest,MailComposeTemplateServiceTest,BatchSendConfigControllerTest` — initial integrated run | FAIL, exit 1 | 313 tests, 0 assertion failures, 4 fixture errors: replacement `when` stubs invoked pre-existing answers (nested retry stub and cursor page-size 0 during stubbing). Changed those new test stubs to `doReturn`/`doAnswer`; no production-error suppression. Output `artifact://671`. |
| Same required Maven command — subsequent run | PASS, exit 0 | 313 tests, 0 failures/errors/skips; Node 1799 passed, 0 failures/skips. Output `artifact://674`. |
| `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dexec.skip=true -Dtest=ManualInitialOutreachServiceTest#isolatedStatisticsApiCacheStrictSmoke` | PASS, exit 0 | One isolated API/cache/strict-statistics test passed. Despite `-Dexec.skip=true`, this pom still ran Node: 1799 passed, 0 failures/skips. Output `artifact://676`. A subsequent allocation-only cleanup reused strict NEW identity sets instead of copying a growing population per page; the final required suite below reran against that final state. |
| Same exact required Maven command — FINAL integrated state | PASS, exit 0 | 313 tests, 0 failures/errors/skips: Controller 9, Template 73, Manual 213, Cache 18. Node 1799 passed / 334 suites / 0 failures, cancellations, skips or todos. Bound app/task-modal JS syntax checks completed; BUILD SUCCESS, 3:05. Output `artifact://679`. This final suite also freshly exercised the isolated API smoke method. |
| `env JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn surefire:test -Dtest=ManualInitialOutreachServiceTest#isolatedStatisticsApiCacheStrictSmoke` — FINAL-state focused runtime smoke | PASS, exit 0 | One test, 0 failures/errors/skips; BUILD SUCCESS, 3.410 seconds. Uses the final suite's freshly compiled classes. Output `artifact://684`. |
| `git diff --check` and `git diff --cached --check` | PASS, exit 0 | No whitespace errors at final implementation/staging boundary. |
| `git commit -m 'feat(fast-p): implement 05'` | PASS, exit 0 | `0d5f7dbf6255cae21f263d6136571b2912454a19`; 8 authorized files. |
| `git merge-base --is-ancestor 0d5f7dbf6255cae21f263d6136571b2912454a19 fast/batch-demand-master` | PASS, exit 0 | Commit is target-worktree HEAD and reachable on target branch. |

### Runtime Smoke Detail

`ManualInitialOutreachServiceTest#isolatedStatisticsApiCacheStrictSmoke` constructs the actual controller, cache, Manual strict statistics service, selector/preflight, and real strict `ExpertSearchCursor` protocol parser. It drives the POST route through standalone Spring MockMvc with a fixed 100-profile isolated fixture; repository and ES transport seams are controlled test doubles.

Observed assertions: cache-only MISS opens no cursor; first load returns READY/100 and baseline 100; envelope identifies diagnostic limit 20000; same-request hit preserves cachedAt and does not reopen cursors; explicit refresh performs another traversal. Business-write/delivery/verification-provider noninteraction assertions pass. Companion strict tests exercise 30001 profiles, NEW keyset boundary 501, shared person identity, and malformed/partial/timeout responses without cache publication.

This is meaningful in-process API/cache/strict-statistics smoke, not a claim of live MySQL/Elasticsearch integration or browser verification. No production network, real SMTP delivery, provider verification, migration, release or background scan was used. Child02/04 historical real-container evidence remains baseline only.

### Changed Files

1. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientStatisticsCache.kt` — process-local bounded cache, effective key, envelope/status and failure/timing semantics.
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` — normalized context, strict complete targets, NEW keyset/PIT/mget reads and independent capped diagnostics; old preview/diagnostics retained.
3. `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt` — statistics-only structural fingerprint and shared read-only reference metadata, without changing launch tokens.
4. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` — new statistics POST route and status mapping.
5. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientStatisticsCacheTest.kt` — cache concurrency, capacity, LRU, clock, failure, restart, effective-key and template-drift coverage.
6. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` — full/capped strict counting, NEW/person dedupe, strict response errors, zero-business-write assertions and integrated API smoke.
7. `src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt` — reference/body/variant/default/enabled/structure invalidation and legacy-token coverage.
8. `src/test/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigControllerTest.kt` — real-cache MockMvc envelope/status/failure/reuse tests and legacy preview compatibility.

### Deviations

- No product-scope or approved-plan amendment.
- Used the explicitly authorized installed Java 11.0.32 location, not the unavailable/known-broken historical tzdb location. Did not reconfirm the reported older-JDK failures.
- Controller-owned `docs/plans/fast/batch-demand-master/ledger.md` was already modified at entry and remains untouched/uncommitted by this executor.
- No `verify-p`, `review-p` or `fix-v` invocation. These are execution results only, not independent approval.

### Freshness

- Plan identity rechecked: YES; unchanged and equal to approved commit bytes.
- Worktree identity rechecked: YES; expected root/branch/git-dir before staging, commit and handoff.
- Product commit reachable from target branch: YES.
- Required commands run freshly after final implementation state: YES.
- Focused runtime smoke rerun on final compiled state: YES.
- Historical evidence used only as baseline: YES.
- Product paths committed and clean at final identity check: YES. Only controller-owned ledger was then dirty; this authorized evidence report is appended afterward and intentionally remains outside product commit.

### Remaining Blocker

None.

### Next Action

Controller-owned independent lightweight child verification against approved child05 and product commit `0d5f7dbf6255cae21f263d6136571b2912454a19`; controller then records ledger/evidence and determines whether child06 may proceed.
