# Child Brief c2 — Persisted discovery paper queue and full-text processing (fast-p)

## Identity

- Child id: `c2`
- Approved plan: `docs/plans/2026-09-22/02-discovery-paper-queue.md` (identity `commit:ee1dfcd5439de54475c12ff51c9c713e984a82ec`) — read it first; it is the complete approved contract.
- Parent master plan: `docs/plans/2026-09-22/openalex-daily-budget-design.md` (same identity).
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design`
- Branch: `fast/2026-09-22-openalex-daily-budget-design`
- `child_base_sha`: `b96470e4be86408b185c2fbdd2fb0037e8f4fc2c` (child c1's terminal code head). Current HEAD is `6aa09e3de91b705dfb3e423bff71d24d37e4b080` (c1 evidence commit, docs-only); commit the implementation on top of HEAD.
- Execution report to write: `docs/plans/fast/2026-09-22/openalex-daily-budget-design/children/c2/execution.md`
- Method: use the `execute-p` skill against this brief + the approved plan.

## Authorized files (exactly these 10; nothing else)

1. `src/main/resources/db/migration/V133__create_discovery_paper_queue.sql` (new)
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepository.kt` (new)
3. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt` (new)
4. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/config/DiscoveryExecutorConfig.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/config/ExpertDiscoveryProperties.kt`
7. `src/main/resources/application.yml`
8. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt` (new)
9. `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPaperQueueRepositoryIT.kt` (new)
10. `src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt`

`V133` is free at this base (highest applied migration is `V132__create_openalex_budget.sql`, added by c1). Never edit an applied migration; if `V133` turns out to be taken, return `PLAN_CONFLICT` instead of renumbering.

`src/test/kotlin/com/weibo/talentintroduction/config/RestTemplateConfigTest.kt` is **not authorized** for modification even though the plan's required command runs it: your `RestTemplateConfig.kt` changes must keep the pre-existing no-queue-scope behavior intact so that test stays green unmodified. If it cannot, return `PLAN_CONFLICT`.

The discovery controller and scheduler are **c3's** files: this phase must not switch the production entry points, must not add endpoints, and must keep `pipeline-enabled=false` as the shipped default. Acceptance for this child is by direct service calls and explicit `tick()`.

## Global constraints

- JDK 11 only: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
- Kotlin + Spring Boot 2.7, Spring Data JDBC (no JPA), kotlin `spring` all-open plugin on. No new Maven dependencies.
- No ES mapping change, no `updateExpertAcademicFields`/`LayerUpdateResult` change, no mail/conversation change, no frontend change, no new task type.
- One local implementation commit only: `feat(fast-p): implement c2`. No push, merge, rebase, squash, amend, reset, or history rewrite.
- Exclude `docs/plans/fast/**` from the implementation commit.
- Do not run formatters, linters, or the whole-project suite; run the required commands below.
- The retained worktree is clean at base; the primary `main` worktree's uncommitted `task-activity-center` work is deliberately absent and out of scope.

## Inputs from child c1 (already implemented and verified at `b96470e`)

Use these seams; do not re-implement or bypass them:

- `com.weibo.talentintroduction.config.OpenAlexRequestPolicy`:
  - `reserve(kind: RequestKind, operation: Operation, targetUrl: String?): Permit` — `Permit.Allowed(permitId)` or `Permit.Deferred(reason, retryAt)`; `beforeRequest(kind)`/`recordResponse(permitId, headers)`/`recordResponse(headers)`/`recordTimeout` remain the compatibility entries.
  - `Operation` costs: `LIST=1`, `SEARCH=10`, `SINGLETON=0`, `CONTENT=100` (refused this phase), `RATE_LIMIT=0`; `RequestKind` = `DISCOVERY`/`HISTORY_ENRICHMENT`/`NEW_ENRICHMENT`.
  - `DeferredReason` = `DAILY_BUDGET`, `RATE_LIMIT`, `BUDGET_SYNC`, `BUDGET_STORE_UNAVAILABLE`, `ENRICHMENT_RESERVE`.
  - `snapshot(): OpenAlexBudgetSnapshot` with exactly: `accountScope`, `resetAt`, `officialLimitCredits`, `officialRemainingCredits`, `confirmedSpentCredits`, `reservedCredits`, `effectiveRemainingCredits`, `enrichmentReserveCredits`, `lastSyncedAt`, `deferredReason`, `retryAt`.
  - `isMetered(url)` identifies metered OpenAlex API/Content hosts.
- `OpenAlexDataSource` maps `Permit.Deferred` to `OpenAlexBudgetDeferredException` before any HTTP call; a budget deferral must reach the pipeline as a wait reason, never as a job failure or a consumed retry attempt.

## Invariants that gate this child (full text in the plan)

- **I-1 Query vs processing position**: per-source normalized criteria → `query_hash` (keywords, institution, excluded countries, years, OA, scope, page size, that source's own name; independent of other sources' ordering/omission); stream unique on `(query_hash, source, epoch)`; cursor advances only in the same transaction that enqueues a whole page; cursor state `ACTIVE`/`EXHAUSTED`; `EXHAUSTED` is not reset by restart, next day, or a new 4-hour window; the legacy v2 cursor may only seed a stream when the full condition is provably identical, otherwise replay conservatively from the start (never pick the largest cursor).
- **I-2 Work identity and version**: queue unique on `(stream_id, item_key)`; PAPER keys prefer normalized DOI/PMCID/PMID with a type prefix SHA-256, else the SHA-256 of the canonical serialized metadata marked `identity_quality=PAYLOAD_HASH` (never title fuzzy-merging); ORCID keys use the full ORCID; `payload_version=1` with explicit `unit=PAPER/RECORD`; unknown versions must not be consumed; unidentifiable/oversized records become observable `FAILED` items, never silently dropped.
- **I-3 Lease and state machine**: `PENDING→RUNNING→SUCCEEDED/RETRY_WAIT/FAILED`; expired `RETRY_WAIT`/`RUNNING` leases can be re-claimed; claim/heartbeat/finish/saveExtraction all validate `lease_token` + `generation`; the one exception is a pipeline `generation` bump from a manual pause with an un-reclaimed still-valid lease — such a job may save its extraction and return to `PENDING` but must not consume experts or complete; `attempts` only increase on real processing failures (budget waits, manual pause, capacity waits do not consume attempts); retryable failures max 5 with 30s/2m/10m/30m backoff; auth/payload errors go straight to `FAILED` with a reason; `FAILED` is never auto re-enqueued daily.
- **I-4 Persist extraction first, consume idempotently**: a non-empty `extraction_json` means the extraction is reliable and later attempts consume it without re-downloading; expert writes still go through the original `consumeOutcome` gates and enrichment enqueue, and only then may the job be `SUCCEEDED`; cross ES/MySQL is at-least-once replay, never claimed as exactly-once; existing experts are not re-counted as new and their missing academic task is re-created; a RAW write failure and "no eligible email" are different outcomes (the latter may `SUCCEEDED` with 0 new experts).
- **I-5 Bounded capacity**: active jobs (`PENDING/RUNNING/RETRY_WAIT`) high-water 20000 / low-water 10000, plus a 1 GiB total-byte guard over actual UTF-8 payload bytes and reserved result space; metadata ≤ 64 KiB, extraction ≤ 32 KiB; each not-yet-extracted job reserves 32 KiB at enqueue and converts to actual bytes when saved; PDF binaries are never stored; a page that does not fit is not committed and does not advance the cursor; a single oversized item becomes a light `FAILED` diagnostic instead of truncating identity fields.
- **I-6 Fairness and external-request bounds**: exactly one effective window owner; at most 8 global extraction tasks and 2 per target host (including redirect destinations); 90 s per paper; each producer takes one page of 100 per source per round, ORCID one page of 100 records; consumers rotate across sources, preferring publicly downloadable work within a source while taking at least one oldest ordinary task per 10 high-priority tasks; a slow source must not block others; OpenAlex deferral only delays OpenAlex-dependent steps.
- **I-7 Persisted control and window handoff**: singleton `pipeline(id=1)` holds `desired_state=PAUSED/RUNNING`, `phase=QUEUED/RUNNING/WAITING/DRAINED/FAULTED`, `criteria_json/version`, `generation`, `owner_token/until`, `execution_id`, `window_until`, `next_wake_at`, `wait_reason`, `raw_scan_done`; initial `PAUSED`; `pause()` commits `PAUSED` and bumps `generation` to stop new claims (in-flight work may still save reliable extractions but must not consume experts until resume); restart/reset must never clear `PAUSED`; the 4-hour window only ends claiming (≤ 90 s wrap-up, release owner) and a later tick may open a new window while `RUNNING`; all sources `EXHAUSTED` with no active jobs → `DRAINED`; no empty `task_execution` loops.
- **I-8 Counters and cleanup never change facts**: `queuedPapers`, `queuedRecords`, `processedPapers`, `processedRecords`, `indexedExperts`, `duplicateExperts`, `failedItems` are counted separately and a state CAS adds each at most once; replay against an existing expert may count duplicate, never retroactively new; crash boundaries may under-count (explicitly not a global total); `queueDepth` counts active jobs only; terminal payloads may be cleared after 7 days, dedup keys/status after 90 days; non-terminal jobs and unconsumed extraction results are never cleaned; cleanup must not reset stream cursors or recompute counters.

## Downstream interfaces (c3 consumes these; keep the shapes exactly)

| Interface | Contract |
|---|---|
| `launch(criteria, triggeredBy, includeRawScan)` | atomically persists `RUNNING` + normalized query; same query idempotent → `pipelineId=1`, `phase`, nullable `executionId`; a different query with active work/backlog → 409; `PAUSED` + same query means explicit resume |
| `pause()` | idempotently persists `PAUSED`, needs no running task record, returns `phase` and in-flight count |
| `resume()` | resumes the saved query only; 409 when nothing is configured; never creates a new epoch |
| `tick()` | short transaction that checks `PAUSED`/`nextWake`/owner, dispatches to the dedicated executor and returns immediately; executor rejection keeps `QUEUED` for the next tick |
| `status()` | `state` (derived `PAUSED` when `desired_state=PAUSED`, else `phase`), `phase`, `desiredState`, `currentExecutionId`, `queryHash`, per-source status, I-8 counters, `queueDepth`/bytes/oldest age, `waitReasons`, `nextWakeAt`, plus c1's budget snapshot |
| wait reasons | `DAILY_BUDGET`, `QUEUE_FULL`, `RATE_LIMIT`, `ENRICHMENT_RESERVE`, `BUDGET_SYNC`, `OWNER_RECOVERY`, `SOURCE_ERROR` |
| window task record | one `EXPERT_DISCOVERY` `task_execution` per window via `TaskExecutionService.runAndRecord`, result implements the existing `TaskExecutionSummaryProvider` and keeps the existing stats/`indexed` semantics, with termination reasons `WINDOW_END/DAILY_BUDGET/QUEUE_FULL/MANUAL_PAUSE/SOURCE_EXHAUSTED/SOURCE_ERROR`; pending work at handoff → `PARTIAL_SUCCESS`, manual pause → `CANCELLED`, unrecoverable failure → `FAILED`, `SUCCESS` only when drained without failures |

Configuration keys to add in `application.yml` (env-overridable, startup-validated `0 < low < high`, capacity fits one page, positive concurrency): `pipeline-enabled=false`, queue high/low `20000/10000`, `queue-max-bytes=1073741824`, `metadata-max-bytes=65536`, `extraction-max-bytes=32768`, `per-host-concurrency=2`, `pipeline-tick=30s`, window `4h`, plus the dedicated `pipelineFetchExecutor` with `pipeline-fetch-concurrency=8` (the legacy `discoveryFetchExecutor` settings stay unchanged).

## Required commands (paste exact commands, exit codes and counts into the execution report)

1. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=DiscoveryPipelineServiceTest,DiscoveryPaperQueueRepositoryIT,ExpertDiscoveryServiceTest,RestTemplateConfigTest -DmysqlIt=true -Dapi.version=1.40 test`
   - `DOCKER_HOST=…` and `-Dapi.version=1.40` are required environment wiring (controller-verified; see `baseline-java.txt`); without them testcontainers fails for pre-existing environment reasons.
   - `DiscoveryPaperQueueRepositoryIT` must really execute on Testcontainers MySQL through Flyway to V133 (the real migration, not hand-written DDL). A skipped IT is not a pass; if Docker cannot run, report the blocker explicitly.
   - The plan requires the MySQL transaction/concurrency/recovery ITs to actually pass — "只 mock 通过" is not acceptable for the lease/capacity/CAS semantics.
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,ExpertAcademicEnrichmentWorkerTest,ExpertAcademicEnrichmentJobRepositoryIT test`
   - Regression proof that the untouched controller/scheduler/enrichment paths and the pre-existing enrichment job IT still pass on top of your `ExpertDiscoveryService.kt` and `RestTemplateConfig.kt` edits.
3. `node --test src/test/js/*.test.js` — baseline is `tests 1037 / pass 1037 / fail 0`; it must stay green (no frontend files are authorized).
4. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -DskipTests clean package` once before handoff, to prove the WAR still builds. If it fails for reasons unrelated to your change, report the failure verbatim instead of fixing unrelated code.

Baseline for comparison: `docs/plans/fast/2026-09-22-openalex-daily-budget-design/baseline-java.txt` (and child c1's `children/c1/verify-log.md`).

## Acceptance (from the plan, verbatim requirements)

- I-1: sources omitted/reordered but equivalent yield the same stream; a rolled-back page transaction leaves the cursor unchanged; `EXHAUSTED` survives the day boundary; conflicting legacy cursors replay conservatively.
- I-2: normalized duplicate DOI yields one job; different metadata with no ID is not merged by title; ORCID counts as a record; unknown version/oversized items fail observably.
- I-3: two connections claim uniquely; an old token's terminal update affects 0 rows; the 5th failure terminates; budget waits do not change `attempts`; an expired lease is recoverable.
- I-4: an already-saved extraction plus an ES outage does not increase download counts; a RAW write failure does not complete the job; replay re-creates the missing academic task without overwriting `APPLICATION` fields.
- I-5: either count or bytes being full stops a whole page; multiple enqueuers cannot exceed the bound; in-flight result reservation works; recovery only below the low-water mark; terminal cleanup releases bytes.
- I-6: suspending the first source's request still lets other sources collect/consume; global ≤ 8 and per target host ≤ 2; slow sites/redirects verified; the oldest ordinary task is still claimed while priority work keeps arriving.
- I-7: pause is persisted, in-flight work only saves (no new consumption), restart/day-rollover does not clear it; a 1-minute test window can continue the same queue; no duplicate active window after executor rejection or owner crash.
- I-8: repeated complete does not double-count; paper/ORCID/expert counters stay separate; cleanup changes neither counters nor cursors; the historical task result, progress and status agree on the termination reason.

## Report contract

Write `docs/plans/fast/2026-09-22/openalex-daily-budget-design/children/c2/execution.md` with: status, commit SHA, changed files, per-invariant evidence (`file:line` + test name), exact commands with exit codes/counts, deviations, blockers.

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
Do not review later children, repair unrelated behavior, push, merge, or rewrite history.
