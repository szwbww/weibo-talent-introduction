# Fast-P Human Review Handoff

- Outcome: READY_FOR_HUMAN_REVIEW
- Master base: e2247680592603b091af791ef3629d70739a015b
- Current/final code head: 13b82fde5d74836977d12e97b7f794a98803429d
- Branch/worktree: fast/2026-09-22-openalex-daily-budget-design / /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design

## Child Status

| Child | Status | Code boundary | Fix rounds | Evidence commit |
|---|---|---|---:|---|
| c1 | LIGHT_PASS_WITH_NOTES | e2247680592603b091af791ef3629d70739a015b..b96470e4be86408b185c2fbdd2fb0037e8f4fc2c | 1 | 6aa09e3de91b705dfb3e423bff71d24d37e4b080 |
| c2 | LIGHT_PASS_WITH_NOTES | b96470e4be86408b185c2fbdd2fb0037e8f4fc2c..ef1eb2b8c1e17aabaf04178f5f7931852872a42b | 1 | ee7a45fa23c166c4e235cb3121c4fc10b402f265 |
| c3 | LIGHT_PASS_WITH_NOTES | ef1eb2b8c1e17aabaf04178f5f7931852872a42b..13b82fde5d74836977d12e97b7f794a98803429d | 0 | e2aaa61003224abe7ca22c886ba5298950ccc244 |

## Repair Rounds

| Child | Round | Finding | Fix commit | Result |
|---|---|---|---|---|
| c1 | 1 | F-1 — a non-positive RATE_LIMIT wait reached `Thread.sleep` and threw `IllegalArgumentException` out of `OpenAlexRequestPolicy.reserve` instead of returning `Permit.Deferred` (plan I-4). The controller routed the first verifier's note O-1 as a fix round because it was a proven I-4 violation with a uniquely determined repair inside an authorized file. Re-verification confirmed FIXED, with falsification evidence. | b96470e4be86408b185c2fbdd2fb0037e8f4fc2c | FIXED |
| c2 | 1 | F-1 — streams were created with the pipeline-level query hash instead of the per-source hash, so an equivalent query written with a different `sources` field abandoned every stored cursor including `EXHAUSTED` (plan I-1, verdict LIGHT_FAIL / AUTO_FIX). Re-verification confirmed FIXED, with falsification evidence. | ef1eb2b8c1e17aabaf04178f5f7931852872a42b | FIXED |

## RECORD_ONLY Index

| Observation | Child | Evidence | Source report |
|---|---|---|---|
| The JDBC IT always passes `reservedFloorCredits = 0L`, so the MySQL store's floor-rejection branch is exercised only through the in-memory store, not on real MySQL. | c1 | `OpenAlexBudgetRepositoryIT.kt:459`; in-memory coverage `OpenAlexRequestPolicyTest.kt:290`, `OpenAlexDataSourceTest.kt:1358` | `children/c1/verify-log.md` O-2 |
| `snapshot().deferredReason/retryAt` come from a process-local `lastDeferral`: a freshly started process with no trusted official cycle reports `deferredReason = null` with `effectiveRemainingCredits = 10000` and `resetAt = null`, even though metered requests are deferred as `BUDGET_SYNC`. | c1 | `OpenAlexRequestPolicy.kt:616`, `:650`, `:882-899`; `OpenAlexBudgetRepository.kt:110-112` | `children/c1/verify-log.md` O-3 |
| Rate-limit waits are served inline in the calling thread with up to 3 attempts (≈180 s) after a 429, whereas I-4 prefers expressing the wait as `Deferred(reason, retryAt)`. Bounded and outside any DB transaction. | c1 | `OpenAlexRequestPolicy.kt:859-864`, `:1006`, `:1069`; tests `OpenAlexRequestPolicyTest.kt:494`, `:600` | `children/c1/verify-log.md` O-4 |
| `releaseWindow` always sets `next_wake_at = now + pipelineTick` and `hasProgressableWork` is true when `streams.isEmpty()`, so a RUNNING pipeline with all sources deferred (or a criteria resolving to no enabled source) produces one `EXPERT_DISCOVERY` task record per tick with 0 processed. I-7's "no empty task records" is implemented/tested only for PAUSED/DRAINED. | c2 | `DiscoveryPipelineService.kt:1228-1236`, `:1140-1145`; test `DiscoveryPipelineServiceTest.kt:1659-1672` | `children/c2/verify-log.md` O-1 |
| `status()` merges raw `stream.source_error` strings into `waitReasons`, so the list can carry values outside the seven documented reasons (e.g. `SEARCH_FAILED`, `NO_PROGRESS`); consumers must tolerate unknown values. | c2 | `DiscoveryPipelineService.kt:538-540` | `children/c2/verify-log.md` O-2 |
| `DiscoveryPaperQueueStore.forceFailForLeaseLoss` has no production caller and, unlike `completeJob`, leaves `active_count` / `processed_*` / `failed_items` and the result reservation untouched — a dead seam that would desync the capacity ledger if invoked. | c2 | `DiscoveryPaperQueueRepository.kt:331`, `:1235` | `children/c2/verify-log.md` O-3 |
| A window whose deadline expires exactly while the queue is already drained reports `WINDOW_END → PARTIAL_SUCCESS` although the phase becomes `DRAINED`; the "drained ⇒ SUCCESS" behaviour holds only for `SOURCE_EXHAUSTED`. | c2 | `DiscoveryPipelineService.kt:204-206`, `:712-718` | `children/c2/verify-log.md` O-4 |
| A failed or timed-out (10 s) `GET /api/expert-discovery/pipeline` writes `加载失败：…` into `#taskLaunchDesc` and returns before the modal is revealed, so clicking 深度发现 leaves no dialog, no message and no visible exit (I-7 / T-3 require a visible error). Two corrections are equally supported by the plan. | c3 | `app.js:6688-6694`; `index.html:1035`; reveal at `app.js:6770` | `children/c3/verify-log.md` O-1 |
| After a refused continuous launch (409/503/timeout) `handleDiscoveryLaunchFailure` returns without restoring the page-level `#discoverBtn`, which `openTaskModal` had set to the 执行中 state; it stays that way until a page reload. Cosmetic — no state corruption. | c3 | `app.js:7170-7186`, `:1326`, `:1248`, `:1182`, `:756-777` | `children/c3/verify-log.md` O-2 |
| The plan's required `-Dtest=` command does not execute the two extra new-mode MVC classes declared in the authorized `ExpertDiscoveryControllerMvcTest.kt` (surefire matches class names exactly). The implementer ran them separately (6 + 1 cases, green) and the verifier inspected their assertions. | c3 | `ExpertDiscoveryControllerMvcTest.kt` (`ExpertDiscoveryContinuousMvcTest`, `ExpertDiscoveryPipelineUnavailableMvcTest`) | `children/c3/verify-log.md` O-3 |

## Pause/Resume

- Reason: N/A
- Resume from: N/A

No whole-system verification was performed.
