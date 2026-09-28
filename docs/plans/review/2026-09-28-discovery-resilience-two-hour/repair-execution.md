# Repair Execution — discovery-resilience-two-hour (R-1 / V-1)

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour/docs/plans/fix/discovery-resilience-two-hour/repair.md`
- Plan SHA-256: `830b2ab77e854dd31c9e18259f0545277afd5e160ac60cc8ac910da574779ade` (6,851 bytes; recomputed before execution and again before this commit — unchanged)
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour/docs/plans/fix/discovery-resilience-two-hour/repair.md@830b2ab77e854dd31c9e18259f0545277afd5e160ac60cc8ac910da574779ade`
- Execution epoch: NEW (no prior execution evidence names this EXECUTION_ID)
- Approval basis: human-originated invocation `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour/docs/plans/fix/discovery-resilience-two-hour/repair.md` (2026-09-28, this session) — the exact path and command named by the repair plan's "Review-Fast-P Execution Handoff" clause. The plan carries `Status: DRAFT — HUMAN APPROVAL REQUIRED`; that invocation is the approval that released it.
- Executor: `RepairExec01` (execute-p worker sub-agent; delegated writer)
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour`
- Target branch: `fast/2026-09-28-discovery-resilience-two-hour`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour@fast/2026-09-28-discovery-resilience-two-hour@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour`
- Pre-execution code SHA: `008f4c474f0f1bd071bfd13eefb257cc29a3304e`
- Post-execution code SHA: `8e622680c9fafeb68c6b220da8ef47c947ff2fcd` (`fix(discovery): retain recovery retry audit`, 2 files, +18/−5)
- Evidence HEAD: `pending` — the docs-only evidence commit created by this execution is self-referential (its SHA depends on this file's content), so it cannot be written inside the file it commits. The exact SHA is returned to the controller and belongs in `ledger.md`. Parent of that commit: `8e622680c9fafeb68c6b220da8ef47c947ff2fcd`.
- Implementation boundary: `008f4c474f0f1bd071bfd13eefb257cc29a3304e..8e622680c9fafeb68c6b220da8ef47c947ff2fcd`

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| R-1 (resolves V-1, P1): on recovery completion, terminal retry exhaustion, and recovery-time budget deferral, retain the already-known retry `round`/`maxRounds`/sanitized `reason` in `SourceStats.retry` and clear `nextRetryAt`; no recovery history ⇒ no retry object; `nextRetryAt` alone still means pending; no new retry created from the audit object | IMPLEMENTED | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`; `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | Fix: `ExpertDiscoveryService.kt:1025-1034` keeps the existing audit object when no new retry is scheduled (`?: sourceStats.retry?.copy(nextRetryAt = null)`). TDD red→green: pre-fix `ExpertDiscoveryServiceTest` was exit 1 with 3 failures (`round` expected 1/3/1, was `null`, at `:4121/:4177/:4453`); post-fix 160/0/0. No-history cases still assert a null object (`:4054` empty recovery list, `:4889` EUROPE_PMC consumption error). |

## Commands

All four plan commands ran freshly in this invocation, in the target worktree, JDK 11 (`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`), after the final implementation state was established.

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `mvn test -Dtest=ExpertDiscoveryServiceTest` | PASS | exit 0; `Tests run: 160, Failures: 0, Errors: 0, Skipped: 0`; BUILD SUCCESS. Pre-fix red run of the same command: exit 1; `Tests run: 160, Failures: 3, Errors: 0, Skipped: 0` — the three I-5 assertions failed with `expected: <1>/<3>/<1> but was: <null>` at `ExpertDiscoveryServiceTest.kt:4121/4177/4453`. |
| 2 | `mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest,ExpertDiscoveryPropertiesTest,DiscoveryCheckpointCodecTest,OpenAlexRequestPolicyTest,DiscoveryPromotionProgressServiceTest,TaskProgressStoreTest,TaskProgressStoreRebindTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest,DiscoveryPipelineServiceTest` | PASS | exit 0; `Tests run: 354, Failures: 0, Errors: 0, Skipped: 0` (all 11 classes ran: 160 + 15 + 5 + 11 + 44 + 5 + 26 + 3 + 13 + 26 + 46); BUILD SUCCESS. Same count as the pre-repair aggregate baseline (354). |
| 3 | `mvn clean package` | PASS | exit 0; `Tests run: 4217, Failures: 0, Errors: 0, Skipped: 13`; exec-plugin Node suite `pass 1199 / fail 0`; BUILD SUCCESS. Matches the pre-repair aggregate baseline (4217 / 13 skipped / Node 1199). |
| 4 | `git diff --check` | PASS | exit 0; no output (no whitespace errors). |

## Changed Files

Exactly the two Authorized Files; nothing else was modified, added, or deleted.

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` (+3/−2) | R-1/V-1: the end-of-`discoverFromSource` assignment of `SourceStats.retry` no longer clobbers the audit object. When a new recovery was scheduled the new pending object still wins; otherwise the already-known object is kept with `nextRetryAt` cleared: `} ?: sourceStats.retry?.copy(nextRetryAt = null)`. Comment updated to state the I-5 rule (start/cancel/timeout/end clear only `nextRetryAt`). |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` (+15/−3) | R-1/V-1: the three assertions that pinned the prohibited `retry == null` result now assert the retained audit state with a cleared plan time — recovery success (`round=1`, `maxRounds=3`, `reason=REMOTE_TLS_HANDSHAKE`), all recovery groups exhausted (`round=3`, `maxRounds=3`, `reason=TIMEOUT`), and recovery-time budget deferral (`round=1`, `maxRounds=3`, `reason=REMOTE_TLS_HANDSHAKE`); each also asserts `nextRetryAt == null`. |

Unchanged by design (verified by the minimal diff): retry eligibility/whitelist, 3×3+3 attempt bounds, cursors/checkpoints, budgets, cancellation, scheduler, pipeline, TLS validation, progress batch semantics, task-progress storage, persistence. No new retry can be created from the audit object: `SourceRunOutcome.retry` is still `retryPending` (null when no group remains), so `consumeDeferredRecoveries` still treats only a non-null `outcome.retry` as pending.

## Deviations

- **`Evidence HEAD` is written as `pending`.** The plan requires `Evidence HEAD` to name the docs-only evidence commit, but that commit's SHA depends on this file's bytes, so writing it here is impossible without amending or a second docs commit — both prohibited. The exact SHA is returned to the controller; no other field is left unresolved.
- **None otherwise.** No file outside the Authorized Files list was changed; no plan was edited; no unrelated cleanup, refactor, or new behavior was added; nothing was pushed, merged, rebased, amended, or reset; the main worktree and the fast-p artifacts/ledger/handoff were not touched.

## Observations (recorded, not acted on — outside this repair's scope)

- On the terminal-exhaustion path the retained `reason` is the last *scheduled* recovery group's sanitized code (the plan's "already-known … sanitized reason"). In the covered test the final failure and the last group share the code `TIMEOUT`, so the retained value is also the terminal failure's code. If a future path let the final short-attempt failure carry a different code than the last scheduled group, the audit would still show the group's code. This matches the plan wording; flagged only so the reviewer can confirm the reading.
- Pre-existing, untouched: the `retry` audit object is backend-only — no `app.js`/`index.html` reader consumes it (`retryWaitSegment` in `buildSummaryText` keys off a non-null `nextRetryAt`, so a completed recovery produces no "待恢复" segment).

## Clean-state evidence

- `git status --porcelain` before the product commit: only the two authorized files modified, no untracked files.
- `git status --porcelain` immediately after the product commit: empty.
- `git show --name-only 8e622680`: exactly the two authorized files.
- The product commit is the branch HEAD and an ancestor of `fast/2026-09-28-discovery-resilience-two-hour` (`git merge-base --is-ancestor` exit 0).
- Plan identity re-checked after the commit: `830b2ab77e854dd31c9e18259f0545277afd5e160ac60cc8ac910da574779ade` (unchanged).
- Worktree identity re-checked with `--expect-root/--expect-branch/--expect-git-dir` immediately before staging: same root, branch and Git dir; HEAD `008f4c474f0f1bd071bfd13eefb257cc29a3304e` at that moment.
- No mail was sent, no ES/DB state was written outside tests, no migration or mapping was touched, no API key was read, logged, or printed.

## Freshness

- Plan identity rechecked: YES (before execution and before this commit; unchanged)
- Worktree identity rechecked: YES (`--expect-root/--expect-branch/--expect-git-dir`, exit 0)
- Reported commits reachable from target branch: YES
- Required commands run this invocation: YES (all four, freshly, after the final implementation state)
- Historical evidence used only as baseline: YES (the pre-repair 354/4217 counts are baselines; the pre-fix red run was produced in this invocation)

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p` (or the already authorized `review-fast-p` aggregate re-review) over `008f4c474f0f1bd071bfd13eefb257cc29a3304e..8e622680c9fafeb68c6b220da8ef47c947ff2fcd`.
