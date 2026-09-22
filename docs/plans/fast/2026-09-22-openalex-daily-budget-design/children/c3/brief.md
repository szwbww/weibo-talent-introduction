# Child Brief c3 — Unified entry, continuous running and visible state (fast-p)

## Identity

- Child id: `c3`
- Approved plan: `docs/plans/2026-09-22/03-discovery-continuous-run.md` (identity `commit:ee1dfcd5439de54475c12ff51c9c713e984a82ec`) — read it first; it is the complete approved contract.
- Parent master plan: `docs/plans/2026-09-22/openalex-daily-budget-design.md` (same identity).
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design`
- Branch: `fast/2026-09-22-openalex-daily-budget-design`
- `child_base_sha`: `ef1eb2b8c1e17aabaf04178f5f7931852872a42b` (child c2's terminal code head). Current HEAD is `ee7a45fa23c166c4e235cb3121c4fc10b402f265` (c2 evidence commit, docs-only); commit the implementation on top of HEAD.
- Execution report to write: `docs/plans/fast/2026-09-22-openalex-daily-budget-design/children/c3/execution.md`
- Method: use the `execute-p` skill against this brief + the approved plan.

## Authorized files (exactly these 10; nothing else)

1. `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryController.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryScheduler.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressController.kt`
4. `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerMvcTest.kt`
5. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoverySchedulerTest.kt`
6. `src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerTest.kt`
7. `src/main/resources/static/app.js`
8. `src/main/resources/static/index.html`
9. `src/test/js/discoveryContinuousRun.test.js` (new)
10. `src/test/js/taskActivityCenter.test.js` (does not exist at this base — create it, see "Base facts" below)

**Not authorized, must stay green unmodified:** `src/main/resources/static/styles.css` (the plan requires zero CSS diff and no new classes), `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/ExpertDiscoveryControllerTest.kt`, `src/test/kotlin/com/weibo/talentintroduction/task/controller/TaskProgressControllerExecutionsTest.kt`, `src/main/kotlin/com/weibo/talentintroduction/task/service/TaskProgressStore.kt`, `src/main/kotlin/com/weibo/talentintroduction/task/repository/TaskExecutionRepository.kt`. If a required change falls into any of these, return `PLAN_CONFLICT`.

## Base facts you must re-verify and honour (the plan's audit snapshot is stale here)

The plan was written against a workspace that carried uncommitted `task-activity-center` work; this worktree is the clean committed base. Re-check each before relying on it:

- `index.html`: all **11** versioned resources currently use `?v=20260920-manual-material-upload` (5 stylesheets in `<head>`, 6 scripts at the end of `<body>`); `task-modal-runtime.js` is referenced without a version key. **No test file at this base pins the cache key** (`grep -rn "20260920-manual-material-upload" src` matches only `index.html`).
- `src/test/js/taskActivityCenter.test.js` does not exist here. Authorized file #10 is therefore a **new** file: implement the plan's intent for it — a cache-key contract test that derives the live key from the shipped files (no hardcoded key literal) and asserts resource consistency, so a future key bump does not require editing the test. Bump all 11 keys to `20260922-discovery-continuous` in `index.html` (plan T-3) and make the assertions match that contract.
- `ExpertDiscoveryController` does **not** yet force `RND_TARGET` scope at this base. The plan's audit claims the fix already exists; it does not. Plan I-1 requires `scope强制RND_TARGET` and the same disabled-source rules for both POSTs, so you must implement the scope forcing here.
- DOM/CSS contract S-1/S-2: the referenced ids and functions all exist at this base (`#taskModalProgressSection`, `#taskModalStatus`, `#taskModalPercent`, `#taskModalMessage`, `#taskModalCancelBtn`, `#taskLaunchDesc`, `#taskLaunchRunBtn`, `#taskModalBySource`; `openTaskLaunchModal`, `executeDiscover`, `executeCheckReplies`, `handleCancelTask`, `bindTaskModalExecution`, `progressStoreHasRunningTask`; `.modal-content.task-modal`, `.task-modal-progress`, `.task-modal-status`, `.task-progress-detail`). The plan's `styles.css` line numbers differ from this base — bind to the selectors, never to line numbers, and do not edit `styles.css`.

## Global constraints

- JDK 11 only: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.
- Kotlin + Spring Boot 2.7. No new Maven dependencies, no new task type, no new CSS file or class, no ES/mail changes.
- The feature is behind `pipeline-enabled` and must ship **default off** (c2 already added the key with `false`): with the flag off, the legacy synchronous POST/cron behaviour must be byte-for-byte preserved. When the flag is on and a dependency is missing, fail loudly — never silently fall back to the legacy path.
- First deployment state stays `PAUSED`; only an explicit user start writes `RUNNING` (c2 guarantees the persistence; you must not import a running legacy process).
- One local implementation commit only: `feat(fast-p): implement c3`. No push, merge, rebase, squash, amend, reset, or history rewrite.
- Exclude `docs/plans/fast/**` from the implementation commit.
- Do not run formatters, linters, or the whole-project suite; run the required commands below.

## Inputs from c1 and c2 (already implemented and verified)

- c1 `OpenAlexRequestPolicy.snapshot(): OpenAlexBudgetSnapshot` (`accountScope`, `resetAt`, `officialLimitCredits`, `officialRemainingCredits`, `confirmedSpentCredits`, `reservedCredits`, `effectiveRemainingCredits`, `enrichmentReserveCredits`, `lastSyncedAt`, `deferredReason`, `retryAt`) — read it through a service, never recompute credits/dollars in the controller, and never expose the API key.
- c2 `DiscoveryPipelineService`: `launch(criteria, triggeredBy, includeRawScan)` (idempotent per query, `PipelineRejection(QUERY_CONFLICT)` for a different active query, `NOT_CONFIGURED` when nothing is saved), `pause()` (idempotent, persisted, no running task record required, returns phase + in-flight counts), `resume()` (saved query only), `tick()` (short transaction, dispatch only), `status()` (state/phase/desiredState/currentExecutionId/queryHash/per-source rows/counters/queueDepth/bytes/oldest/waitReasons/nextWakeAt + budget snapshot).
- The pipeline may surface raw source-error strings inside `waitReasons` beyond the seven documented constants; the frontend must tolerate unknown values instead of failing.

## Invariants that gate this child (full text in the plan)

- **I-1 Single saved configuration for every entry**: with `pipeline-enabled=true`, both `/run` and `/run/by-keyword` normalize and call c2's `launch`; scope is forced to `RND_TARGET` and sources resolve by the same rule; the scheduled tick only advances the already-enabled pipeline and never invents a default query that overwrites the operator's; there is no "already ran today" gate that blocks continuation; after a manual pause, cron, day rollover and restart must all leave it paused.
- **I-2 HTTP acceptance is strictly separated from completion**: the new-mode POST returns **202** only after the configuration is persisted, with `mode=CONTINUOUS`, `pipelineId`, `phase` and a nullable `executionId`; 202 is not completion and must not render as "专家发现完成"; a different query is 409; the same query is idempotent; a persist failure is 503 with the config screen still retryable; a null `executionId` must never bind the latest historical task — only a `currentExecutionId` from `status()` may be bound; when the executor is temporarily unavailable the work stays persisted as `QUEUED` for a later tick.
- **I-3 Pause control is persisted and recoverable**: the new-mode `EXPERT_DISCOVERY` cancel endpoint calls c2's `pause()` first and only then requests cancellation of the current window; a window gap must still answer 200 with `PAUSED` instead of 409; pause is idempotent; resume is an explicit user action; only discovery production/consumption stops — already-enqueued academic enrichment continues; closing the dialog is not a pause; in-flight results may still be saved after the response.
- **I-4 Background windows never occupy the scheduler thread**: the new-mode cron and the 30 s recovery tick only call c2's `tick()` (no synchronous `discover`, no sleeping for quota/PDFs); global ownership and per-query mutual exclusion come from c2's database state; with the flag off the legacy once-per-day cron semantics stay exactly as they are; an enabled flag with no persisted configuration stays paused and must not import a running legacy process.
- **I-5 Honest budget/progress/waiting/fault surface**: no fixed daily paper total and no fake 100% progress; show the CONTINUOUS state with collected papers/ORCID records, active queue/processed, new/duplicate experts; show budget free limit, official used, local reservations, protected remaining, and next reset; unsynchronised data shows "待同步" rather than 0; `WAITING` distinguishes `DAILY_BUDGET/QUEUE_FULL/RATE_LIMIT/ENRICHMENT_RESERVE/BUDGET_SYNC/OWNER_RECOVERY/SOURCE_ERROR`, shows several simultaneously, and never renders sources that can still progress as fully stopped; a window terminal state is not the end of the pipeline.
- **I-6 Only the confirmed mutex exception is opened up**: the front-end run lock is evaluated per requested task type and only allows `EXPERT_DISCOVERY` and `CHECK_REPLIES` to coexist; the same type still cannot run twice; all other combinations keep their current exclusion; backend check-reply logic and the task lock are untouched; a failed state query must be surfaced as a failure and retryable, never silently treated as "everything is idle".
- **I-7 Dialog lifecycle and legacy isolation**: source loading, launch and polling all have finite timeouts and visible errors; a failed source load must not silently start with all default sources; launch configuration is read before switching views; `includeRawScan` is explicit; every async callback validates modal generation, task type and pipeline/execution identity so a closed dialog or a switched task cannot be overwritten by a stale response; legacy modes and other tasks keep their existing display/notification semantics.

## Style contract (S-1/S-2, from the plan)

- Update only the existing status nodes: reuse `.modal-content.task-modal`, `.task-modal-progress`, `.task-modal-status` (with the existing four state classes), `.task-progress-detail`, `.text-muted`; keep the existing DOM hierarchy and every pre-existing inline style verbatim; write via `textContent`/`hidden` and existing state classes only.
- No new badge cards, no dialog width change, no `innerHTML` with untrusted error/URL/query text, no new colours, no percentage in continuous mode.
- `#taskModalCancelBtn` alone toggles its text between "暂停发现" and "恢复发现" via `handleCancelTask`, disabled while a request is pending (native `disabled`, no new CSS); `#taskLaunchDesc` carries the continuous-mode explanation or the source-load error; no new retry widget.
- Colour mapping for the status text/class: `QUEUED/RUNNING/WAITING` → running class, `PAUSED` → cancelled class, `FAULTED` → failed class, `DRAINED` → completed class (failed class + "已排空，存在失败" when failures exist). State name and reason text must always be shown, not colour alone.
- `styles.css` must have zero diff from this child.

## Required commands (paste exact commands, exit codes and counts into the execution report)

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest test`
   - `ExpertDiscoveryControllerTest` and `TaskProgressControllerExecutionsTest` are **not authorized for editing** and must stay green unmodified; keep existing constructor/`ObjectProvider` call compatibility (the plan explicitly allows appending a trailing optional `ObjectProvider` parameter).
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -Dtest=DiscoveryPipelineServiceTest,OpenAlexRequestPolicyTest,ExpertDiscoveryServiceTest test` — regression proof that c1/c2 behaviour is untouched by the entry-point wiring.
3. `node --check src/main/resources/static/app.js`
4. `node --test src/test/js/*.test.js` — baseline is `tests 1037 / pass 1037 / fail 0`; it must stay green including your two test files.
5. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -B -DskipTests clean package` once before handoff.

Baselines: `docs/plans/fast/2026-09-22-openalex-daily-budget-design/baseline-java.txt`, `children/c1/verify-log.md`, `children/c2/verify-log.md`.

## Acceptance (from the plan, verbatim requirements)

- I-1: both POSTs normalize to the same output; cron/tick read the saved condition with identical scope/sources; multiple windows may run in the same day; the initial `PAUSED` and a manual pause are never lifted by cron.
- I-2: with a slow external API the POST still returns quickly (≤2 s in an isolated environment); a null `executionId` binds no old record; 202 triggers no completion notification; a repeated identical query is idempotent, a different one is 409, a persist failure is 503.
- I-3: pause works while running, during a window gap and while waiting on budget; restart/day rollover does not resume it; an explicit resume continues the same queue; independently enqueued academic enrichment is not cancelled.
- I-4: with a 100 s blocked PDF the scheduler dispatch call returns within 1 s and other scheduled ticks still run; the legacy once-per-day behaviour is unchanged.
- I-5: all wait reasons, unknown budget data and mixed per-source states have copy; no daily percentage; window termination is not confused with overall state; faults never render as success.
- I-6: discovery↔check-replies allowed in both directions; same type still rejected; discovery↔RAW_PROMOTION_SCAN and all other combinations still rejected; a failed state query never starts anything on its own.
- I-7: 10 s source timeout and 15 s new-mode POST timeout have visible exits; a 409 restores the configuration; selections are read before starting; a closed/switched dialog is never updated by a stale response; legacy-mode tests still pass.
- S-1/S-2: no `styles.css` diff; no new class or inline style; existing DOM hierarchy preserved; colours, font sizes, state classes and the pause/resume text follow the contract; all 11 resources share one key and no fixed-key test is left behind.

## Report contract

Write `docs/plans/fast/2026-09-22-openalex-daily-budget-design/children/c3/execution.md` with: status, commit SHA, changed files, per-invariant evidence (`file:line` + test name), the exact commands with exit codes/counts, any deviation from the plan's stale audit snapshot, and blockers.

Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.
Do not review later children, repair unrelated behavior, push, merge, or rewrite history.
