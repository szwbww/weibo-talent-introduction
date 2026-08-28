# Child 02 Execution Report — 旧首发链路改为显式配置研发类型

- Status: READY_FOR_VERIFICATION
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-single-gate (branch `fast/single-gate`)
- Child base SHA (pre-execution): cec6ce15ba3b41a6bf76e70eae503cdc5a925560
- Pre-execution HEAD: 427222f5058c30bb53d640efd8b4b9a0bc70b46e
- Executor: Impl02LegacyTypes (task)
- Date: 2026-08-28

## Plan Identity

- Plan: `docs/plans/2026-08-28/02-legacy-outreach-explicit-types.md`
- Plan SHA-256: `fc4a622d874924bcc1f1d118dfcea237923aa55eda5fed3cf0cbedd747c8f858`
- Execution ID: `docs/plans/2026-08-28/02-legacy-outreach-explicit-types.md@fc4a622d874924bcc1f1d118dfcea237923aa55eda5fed3cf0cbedd747c8f858`
- Identity rechecked after execution: YES (unchanged)

## Files Changed (exactly the 7 authorized)

| # | File | Change |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/config/MailSchedulingProperties.kt` | Task 1: tail field `initialOutreachExpertTypes: List<String> = emptyList()` + I2-1 KDoc |
| 2 | `src/main/resources/application.yml` | Task 1: `initial-outreach-expert-types: ${MAIL_SCHEDULING_INITIAL_OUTREACH_EXPERT_TYPES:}` after `initial-outreach-send-jitter-ms` |
| 3 | `src/test/resources/application.yml` | Task 1: same line in `scheduling` block after `initial-outreach-batch-size` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | Task 2: new `searchExpertsByTypesWithEmail(size, level=CANDIDATE, expertTypes)` — filter exactly `[exists email, expertTypesFilter(types)]`, `_source`/sort/result mapping verbatim from `searchSendableExpertsWithEmail` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | Task 3: `sendInitialBatch` — types trim/filter/distinct + `require(types.isNotEmpty())` with message naming `initial-outreach-expert-types` (I2-2) + whitelist validation via `ExpertSearchService.ALLOWED_EXPERT_TYPES` (I2-3); query switched to `searchExpertsByTypesWithEmail(size, CANDIDATE, types)`; last-chance gate replaced with type-set match, `UNCLASSIFIED` = null classification/type (I2-5); unused `ExpertClassificationService` import removed |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | Task 4 items 1–3: filter-exactly-two-items test (no sendable/version), empty-list `IllegalArgumentException`, explicit APPLICATION level index+sort regression |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | Task 4 items 5–9: fast-fail on empty config (message contains `initial-outreach-expert-types`), verbatim third-arg passing, OUT_OF_SCOPE skip/send, null-classification UNCLASSIFIED-only send; all 14 existing `searchSendableExpertsWithEmail` stubs replaced with `searchExpertsByTypesWithEmail(.., listOf("PRODUCTION_RND"))`; class-level + interval-service `MailSchedulingProperties` get `initialOutreachExpertTypes`; gate tests adapted to I2-5 semantics (see Deviations) |

Note: plan said 15 stubs; actual count at execution was 14 (plan line numbers drift; symbols authoritative).

## Commands + Exit Codes

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…zulu-11… mvn test -Dtest='ExpertSearchServiceTest,InitialOutreachServiceTest'` | PASS (exit 0) | ExpertSearchServiceTest: 71 run / 0 fail / 0 err; InitialOutreachServiceTest: 18 run / 0 fail / 0 err; JS suite: 755 pass / 0 fail; BUILD SUCCESS |
| `JAVA_HOME=…zulu-11… mvn test` (full regression) | PASS (exit 0) | Kotlin: **2967 run / 0 failures / 0 errors / 5 skipped**; BUILD SUCCESS (see note below on first-run failure) |
| `git diff --check` | PASS (exit 0, no output) | — |

First full-run failed exactly one test: `OperatorStatusWriteSeamGuardTest` (source-scanning guard pinning `ExpertSearchService.kt:545` as excluded noise). Root cause: the new method was initially inserted before that line, shifting it to 587 and breaking the pinned exclusion. Fixed within authorized scope by relocating `searchExpertsByTypesWithEmail` to the end of the class (after `aggregateEmailDomains`) so no existing line moves; the exclusion re-matches at 545. Guard test + full suite then green. No other test touched.

Baseline comparison: brief cites de228e1 baseline 2952 Kotlin green; this run 2967 (2952 + 7 new tests + 8 net from prior child work on this branch — count includes all tests present at HEAD 427222f; 0 failures).

## Invariant Verification (grep)

- I2-1: `MailSchedulingProperties.kt:22` → `val initialOutreachExpertTypes: List<String> = emptyList()`; both `application.yml` → `${MAIL_SCHEDULING_INITIAL_OUTREACH_EXPERT_TYPES:}` (nothing after `:`).
- I2-2: `require(types.isNotEmpty())` with message `未配置 talent-introduction.scheduling.initial-outreach-expert-types…`; test asserts exception message contains `initial-outreach-expert-types`.
- I2-3: validation uses `ExpertSearchService.ALLOWED_EXPERT_TYPES`; no hand-written six-value list in diff.
- I2-4: new query filter exactly `[exists email, expertTypesFilter(types)]`; test asserts exactly 2 filter items and absence of `sendable`/`version`.
- I2-5: gate `types.any { if (it == "UNCLASSIFIED") typeName == null else typeName == it }`; tests cover OUT_OF_SCOPE skip/send and null-classification UNCLASSIFIED-only send.
- Downstream (child 04): `grep -rn "searchSendableExpertsWithEmail" src/main` → only the definition + its KDoc in ExpertSearchService.kt (zero production call sites; method deliberately kept for child 04 to delete); `grep -rn "expertSendableFilter" …/InitialOutreachService.kt` → zero hits.

## Deviations

1. **`searchExpertsByTypesWithEmail` placement**: plan sketch showed it adjacent to `searchSendableExpertsWithEmail`; placed at end of class instead (after `aggregateEmailDomains`) to keep `OperatorStatusWriteSeamGuardTest`'s line-pinned exclusion (`ExpertSearchService.kt:545`) stable. Same method content; no other file affected.
2. **Gate test (former I5a2-9) renamed/refixtured**: `sendInitialBatch last-chance gate rejects legacy version and accepts current VERSION (I5a2-9)` → `sendInitialBatch last-chance gate skips expert outside configured types and accepts matching type (I2-5)`, fixture changed from stale-version to `OUT_OF_SCOPE` expert, because I2-5 removes the version criterion (old fixture would now be matched and sent, contradicting the removed contract). Two other gate tests kept names but comments updated to I2-5 semantics (fixtures still valid: null classification skipped when UNCLASSIFIED not configured).
3. **14 stubs, not 15**: plan's "15 处 stub" vs actual 14 at execution (grep-verified); all replaced.
4. **worktree_identity.py helper failure**: script errors on a stale locked worktree entry (`/sessions/...` no longer exists) in `git worktree list`; identity computed manually with identical logic (root/branch/git-dir/HEAD verified via `git rev-parse`).

## Commit

- Message: `feat(fast-p): implement child 02`
- Files: exactly the 7 authorized files (fast-p docs `docs/plans/fast/**`, `docs/runbooks/**` excluded)
- Post-execution code SHA / commit: see `git log -1` at handoff; commit is HEAD of `fast/single-gate`.

## Remaining Blocker

None.
