# Repair Execution — OpenAlex Daily Budget Design

## Approval

- Approval source: explicit human invocation `$execute-p docs/plans/fix/openalex-daily-budget-design/repair.md` (2026-09-23), which is the approval gate written at the end of the repair artifact itself.
- Repair artifact: `docs/plans/fix/openalex-daily-budget-design/repair.md`
- Repair identity: `sha256:545b8402eb52fce9f0e1947504f2553f784821fd960f3fd828dc21293f9c190d`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design/docs/plans/fix/openalex-daily-budget-design/repair.md@545b8402eb52fce9f0e1947504f2553f784821fd960f3fd828dc21293f9c190d`
- Upstream review: `docs/plans/review/2026-09-22-openalex-daily-budget-design/machine-verification.md` (Epoch 1, result FAIL, convergence INITIAL)
- Executor: `Main` (omp session controller), executing `execute-p` directly at the human's request

## Identity and boundary

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-22-openalex-daily-budget-design`
- Branch: `fast/2026-09-22-openalex-daily-budget-design`
- Pre-execution code SHA: `c5fa9431c6be3a0c2a3ec50c93da425c3241da69`
- Post-execution code SHA (product commit): `f51512591f27ab1f21a81302f7f5f767d7f06695`
- Product commit subject: `fix(openalex): repair daily budget pipeline lifecycle`
- Reviewed product boundary before this repair: `e2247680592603b091af791ef3629d70739a015b..13b82fde5d74836977d12e97b7f794a98803429d`
- Authorized files changed: 6 of 6 (exactly the plan's list; no migration, route, configuration key, CSS file or class was added)
  1. `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicy.kt`
  2. `src/test/kotlin/com/weibo/talentintroduction/config/OpenAlexRequestPolicyTest.kt`
  3. `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineService.kt`
  4. `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPipelineServiceTest.kt`
  5. `src/main/resources/static/app.js`
  6. `src/test/js/discoveryContinuousRun.test.js`

## Findings repaired

| Finding | Repair | Where |
|---|---|---|
| V-4 | A 429 cooldown (`Retry-After` / exponential backoff, minute scale) now returns `Deferred(RATE_LIMIT, retryAt)` immediately — the HTTP worker and scheduler threads never sleep a cooldown. Only the shared rate-limit **pacing slot** (≤ one rate interval and ≤ `MAX_INLINE_WAIT_MS` = 1 s) is still served inline; non-positive waits always defer. See Deviation D-1. | `OpenAlexRequestPolicy.kt:868-882`, `:1084-1092` |
| V-5 | `snapshot()` derives an explicit sync-deferred status: while the official cycle is unconfirmed (or the provider ceiling is unknown) it reports `deferredReason = BUDGET_SYNC` with a retry window instead of presenting locally derived credits as available; the continuous-run budget renderer shows 已用/上限/可用/reset as 待同步 for that status. | `OpenAlexRequestPolicy.kt:638-665`, `app.js:858-872` |
| V-6 | Pipeline scheduling now separates "runnable now" from "merely deferred" before creating a task execution: `nextProgressAt()` returns `now` / the earliest retry time / `null`, the tick opens a window only when work is runnable now, and the window's next wake time is the earliest progressable time instead of a fixed tick. No window and no `task_execution` is created while every source is deferred or no source is enabled. | `DiscoveryPipelineService.kt:471-488`, `:748-756`, `:1163-1192` |
| V-7 | The window result carries the final drained state and the terminal task status is derived from it, so a window that drains at the deadline reports `SUCCESS` instead of `PARTIAL_SUCCESS`. | `DiscoveryPipelineService.kt:202-237`, `:1349-1376` |
| V-1 | A failed/timed-out pipeline-info GET now also writes to the always-visible page status bar (the configuration area lives inside the not-yet-revealed modal). | `app.js:6692-6701` |
| V-2 | Every rejected continuous launch path (409 and other failures) restores the page-level trigger through the existing `stopTaskWatcher(taskType, true)`. | `app.js:7176-7204` |
| V-3 | Per-source detail is rendered with the existing `.data-table` class; the newly introduced inline styles are gone. | `app.js:905-941` |

## Required commands (all executed freshly in this invocation)

| # | Command | Exit | Result |
|---|---|---:|---|
| 1 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=<zulu-11> mvn -B -Dtest=OpenAlexRequestPolicyTest,OpenAlexDataSourceTest,OpenAlexBudgetRepositoryIT -DmysqlIt=true -Dapi.version=1.40 test` | 0 | `Tests run: 121, Failures: 0, Errors: 0, Skipped: 0` — Policy 42, DataSource 67, OpenAlexBudgetRepositoryIT 12 on Testcontainers MySQL 25.4 s |
| 2 | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=<zulu-11> mvn -B -Dtest=DiscoveryPipelineServiceTest,DiscoveryPaperQueueRepositoryIT,ExpertDiscoveryServiceTest,RestTemplateConfigTest -DmysqlIt=true -Dapi.version=1.40 test` | 0 | `Tests run: 215, Failures: 0, Errors: 0, Skipped: 0` — Pipeline 45, DiscoveryPaperQueueRepositoryIT 31 on Testcontainers MySQL 23.4 s, ExpertDiscoveryServiceTest 121, RestTemplateConfigTest 18 |
| 3 | `JAVA_HOME=<zulu-11> mvn -B -Dtest=ExpertDiscoveryControllerMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,TaskProgressControllerTest,TaskProgressControllerExecutionsTest test` | 0 | `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0` |
| 4a | `node --check src/main/resources/static/app.js` | 0 | no output |
| 4b | `node --test src/test/js/*.test.js` | 0 | `tests 1067 / pass 1067 / fail 0` (baseline 1037 + 30 new) |
| 5 | `JAVA_HOME=<zulu-11> mvn -B -DskipTests clean package` | 0 | BUILD SUCCESS; `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war` |

Falsification evidence gathered while developing (not part of the required commands): the three new front-end repair tests fail against the pre-repair `app.js`; the two new pipeline tests fail against the pre-repair `DiscoveryPipelineService` (a fully deferred pipeline previously opened a window and produced an empty `task_execution`; a deadline-drained window previously reported `PARTIAL_SUCCESS`).

## Deviations

- **D-1 (V-4 reading).** The plan's task text says "replace bounded inline rate-limit sleep with immediate `Deferred(retryAt)` behavior", while its acceptance criterion says "No request-policy path sleeps or blocks a caller for **rate-limit cooldown**". Implemented per the criterion: cooldowns always defer immediately, and the only remaining inline wait is the shared pacing slot (≤ `min(rate interval, 1 s)`), which is what keeps the 5/s account rate limit and the pre-existing `OpenAlexDataSourceTest` fixtures valid. Evidence that the stricter reading is not executable inside the authorized file set: `OpenAlexDataSourceTest` (not authorized) issues three metered calls at one fake-clock instant in `list search and singleton calls are charged 1 10 and 0 credits per path`; with the pacing wait removed those calls become `Deferred(RATE_LIMIT)` and the suite fails, i.e. the strict reading would require an unauthorized file. With this reading the non-authorized suite is green (67/67) and every cooldown path returns `Deferred`.
- **D-2 (fixture updates in an authorized test file).** Two pre-existing cases in `DiscoveryPipelineServiceTest` were adjusted because the tick now (correctly) refuses to open a window without runnable work: the seeded queue entry is now due (`KEEP-DISPATCH`/`KEEP-DISPATCH-2`) and the host-busy case advances the clock past the persisted wake time before the second window. All assertions in those cases are unchanged.
- **D-3 (additive DTO field).** `PipelineWindowResult` gained `drained: Boolean` so the terminal status can be derived from the final pipeline state. No store method, migration, ES field, route or configuration key was added; the existing `toDetails()` key set is unchanged.
- **D-4 (test fidelity).** The deadline-drained case drives the extraction executor with a single-thread pool so the worker completes after the loop's `activeCount` read, matching production asynchrony; the synchronous harness cannot otherwise reach a deadline close with a drained queue.

## Clean-state evidence

- `git status --porcelain` in the worktree is empty after the product commit and again after the docs-only evidence commit.
- The product commit changes exactly the 6 authorized files; the docs commit changes exactly this file.
- The repair artifact itself was not modified; its sha256 still equals `545b8402eb52fce9f0e1947504f2553f784821fd960f3fd828dc21293f9c190d`.
- No push, merge, rebase, amend, squash, reset or history rewrite was performed.

## Next action

- Return to the already authorized aggregate re-review: `$review-fast-p docs/plans/fast/2026-09-22-openalex-daily-budget-design/human-review-handoff.md` (not executed in this invocation, which requested only the repair).
