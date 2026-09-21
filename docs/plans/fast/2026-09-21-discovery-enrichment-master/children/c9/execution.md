## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/09-discovery-throughput.md
Plan SHA-256: eee00b54b5ca7a82de730dfb52b4889c6d7770987e10cb42bf609f6a66871e84
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/2026-09-21/09-discovery-throughput.md@eee00b54b5ca7a82de730dfb52b4889c6d7770987e10cb42bf609f6a66871e84
Execution epoch: NEW
Approval basis: explicit fast-p child instruction (c9) in this invocation, bound to child brief
`docs/plans/fast/2026-09-21-discovery-enrichment-master/children/c9/brief.md`
(SHA-256 `c3371135e1ccc29ac6c8ce0ad617a55ad007f2f51b542edf70d767ee245f9262`), which names the plan identity `commit:831e6604cf97e7acba005d8f00827659b49ce010` and the authorized file list. Plan file read in full from disk in this invocation.
Executor: C9Implementer (task subagent)
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
Target branch: fast/2026-09-21-discovery-enrichment-master
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master@fast/2026-09-21-discovery-enrichment-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master
Pre-execution code SHA: e946feecb31f6dff040cd2b1a7f446ebd6305804 (c8 code head 8873dc96cd2799a49ee1bd055d5366c9e3a78ae0 + c8 verification-record commit)
Post-execution code SHA: f81f71f30ee6a9749aeba3556a0527b8ccf05901
Evidence HEAD: N/A (single implementation commit; the report and ledger live under `docs/plans/fast/**`, which is never committed)
Implementation boundary: e946fee..f81f71f (7 files, working tree otherwise clean apart from the uncommitted `docs/plans/fast/**` ledger)

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| Task 1 配额分配（I-1 批量≠总量）| IMPLEMENTED | `ExpertDiscoveryService.kt`, `ExpertDiscoveryProperties.kt`, `OpenAlexProperties.kt`, `application.yml` | Run target decoupled from page size: `max-papers-per-run=15000` (env `EXPERT_DISCOVERY_MAX_PAPERS`), per-source caps OpenAlex 10000 / Crossref 1000 / CORE 1000 / arXiv 2000. Tests `公平额度：全局上限恰好覆盖各来源一页时每源各发一次请求（I-1 I-2）` (4 sources × 1 request × 100 papers, total 400 ≠ page 100), `discover respects maxPapersPerRun limit` (target 200 = two sources' pages, 1 request each), `本源上限为 0 的来源不发请求也不计论文（I-1）` |
| Task 1 公平且不越界（I-2）| IMPLEMENTED | `ExpertDiscoveryService.kt` (`allocateSourceQuota`) | `本源额度 = min(本源上限, 全局剩余 - Σ后来源 min(pageSize, cap))`; test `穷尽来源释放的剩余额度由后来源在各自上限内用尽（I-2）` (EPMC 40 → OpenAlex 160 → CROSSREF/ARXIV 100 each, total exactly 400, ARXIV `stopReason=GLOBAL_PAPER_LIMIT`), `手动只选 arXiv 时全额使用自身额度且不碰 OpenAlex（I-2 V-2）` (only ARXIV in `bySource`, OpenAlex zero requests) |
| Task 1 启动校验（I-1）| IMPLEMENTED | `ExpertDiscoveryService.kt` (`validateRunQuota`) | `全局上限 ≥ Σ min(pageSize, cap)` else `IllegalArgumentException` → 400 via `GlobalExceptionHandler`; test `全局上限不足各来源基础份额时启动校验失败且不发任何请求（I-1）` asserts 0 search requests and 0 checkpoint writes; `0 的配置不会被当成无限量（I-1）` covers `max-papers-per-run=0` and `time-budget=0` |
| Task 1 ORCID 独立限额（I-2）| IMPLEMENTED | `ExpertDiscoveryService.kt` (`discoverFromOrcid`), `SourceStats.kt` | ORCID `runBudget=1000` (its own `maxRecordsPerRun`), `unit=RECORD`, still bounded by `maxAuthorsPerRun=20000`; test `ORCID 记录与论文数在汇总里分列且限额独立（I-2）` |
| Task 2 时间预算（I-3）| IMPLEMENTED | `ExpertDiscoveryService.kt` (`timeBudgetReached`, deadline checks in both loops), `ExpertDiscoveryProperties.kt` (`timeBudget`, default 4h), `application.yml` (`EXPERT_DISCOVERY_TIME_BUDGET`) | Run-level deadline checked before every HTTP request and per consumed item; stop names `TIME_BUDGET` (new constant) and keeps the entering-page checkpoint; test `时间预算到点停在进入页并记录 TIME_BUDGET（I-3）` (2 requests only, second page not advanced, `pendingSources=1`) |
| Task 2 上线记录/回滚（I-4）| IMPLEMENTED | `docs/plans/2026-09-21/discovery-enrichment-rollout.md` (new) | Runbook: post-c9 default table + env knobs, staged 2500→5000→10000 procedure with the exact metrics to record (unique new experts / enrichment success / elapsed / failure distribution), rollback switches, cursor restore + rescan-window rules, UTC reset / 02:00 Asia/Shanghai note; no key or credential |
| 停止原因词汇稳定（brief downstream）| IMPLEMENTED | `ExpertDiscoveryService.kt` | Existing values unchanged (`BUDGET_DEFERRED`, `SOURCE_LIMIT`, `GLOBAL_PAPER_LIMIT`, `CANCELLED`, `WINDOW_LIMIT`, `PAGE_PARTIAL`, `RAW_WRITE_INCOMPLETE`, `ENQUEUE_INCOMPLETE`); added exactly `TIME_BUDGET`; a stop caused by the run quota now self-names `GLOBAL_PAPER_LIMIT` instead of `PAGE_PARTIAL` |
| `SourceStats` 报告保持可加 | IMPLEMENTED | `SourceStats.kt` (`SourceUnit`, `runBudget`), `buildBySourceDetails` | `bySource.<source>.runBudget` / `.unit` added to the existing details JSON (no new column, no frontend change); `papersSearched` semantics (records for ORCID) unchanged |
| I-1 三层写入/去重/资格门禁保持 | IMPLEMENTED | unchanged code paths | No change to `toIndexMap`, dedup, eligibility, promotion or mail paths; `fetchConcurrency=4` untouched |

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=…zulu-11… mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest` (pre-implementation, after adding the acceptance tests) | FAIL (expected red) | 9 failures, exactly the new contract gaps: validation not thrown (×2), quota not allocated (×4), `TIME_BUDGET` unreachable, ORCID unit=PAPER, P1-1 checkpoint missing |
| `JAVA_HOME=…zulu-11… mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest` (final state, run #1) | PASS | exit 0, `BUILD SUCCESS`, `Tests run: 122, Failures: 0, Errors: 0, Skipped: 0` (Service 114 / Scheduler 8) |
| `JAVA_HOME=…zulu-11… mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertDiscoverySchedulerTest,ExpertDiscoveryControllerTest,ExpertDiscoveryControllerMvcTest,ExpertAcademicEnrichmentWorkerTest,DiscoveryResultTest,OpenAlexDataSourceTest,TaskExecutionSummaryExtractorTest,TaskExecutionServiceTest` (final state, run #2 = required command + every directly affected neighbour: SourceStats/config/summary consumers) | PASS | exit 0, `BUILD SUCCESS`, `Tests run: 243, Failures: 0, Errors: 0, Skipped: 0`; includes the required classes (Service 114 / Scheduler 8) and the JS suite bound to `test` phase |
| `JAVA_HOME=…zulu-11… mvn test -Dtest=ExpertDiscoveryControllerMvcTest -Dtalent-introduction.expert-discovery.time-budget=4h` (Spring binding smoke for the new `Duration` knob) | PASS | exit 0, `Tests run: 3, Failures: 0, Errors: 0` — context binds `4h` without conversion failure |
| `python3 -c "yaml.safe_load(open('src/main/resources/application.yml'))"` + value dump | PASS | YAML parses; expert-discovery = 15000/20000/`4h`/4, openalex 10000, crossref 1000, core 1000, arxiv 2000, europe-pmc 1500, pmc-oa 1000, orcid 1000 |
| Full `mvn test` suite | NOT RUN | Integration-stage gate per the brief; only the child's directed command (plus the scoped neighbour classes above) was executed |

### Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/config/ExpertDiscoveryProperties.kt` — run-level global/author caps documented, defaults aligned to 15000/20000, new `timeBudget: Duration = 4h`
- `src/main/kotlin/com/weibo/talentintroduction/config/OpenAlexProperties.kt` — `maxPapersPerSource` default 2500-era 500 → 10000 (same value as the yml env default)
- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/SourceStats.kt` — additive `SourceUnit` enum, `runBudget`, `unit`
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` — run-level validation, per-source quota allocation, run deadline + `TIME_BUDGET`, stop-reason naming, `runBudget`/`unit` reporting, paper/ORCID count separation
- `src/main/resources/application.yml` — `EXPERT_DISCOVERY_MAX_PAPERS:15000`, `EXPERT_DISCOVERY_TIME_BUDGET:4h`, `OPENALEX_MAX_PAPERS:10000`, `CROSSREF_MAX_PAPERS:1000`, `CORE_MAX_PAPERS:1000`, `ARXIV_MAX_PAPERS:2000`
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` — 8 new c9 tests + 7 existing tests adapted to the new start-validation contract
- `docs/plans/2026-09-21/discovery-enrichment-rollout.md` — new rollout runbook + verification record (no key)

### Deviations

- **No injected `Clock`**: the plan snippet shows `clock.instant()`; implemented as a run-scoped `deadline = Instant.now().plus(timeBudget)` read by `timeBudgetReached` (no new DI seam, no Spring bean-optionality risk). The `TIME_BUDGET` path is proved deterministically by a stub whose second page request outlives the budget (1 s budget / 1.5 s page), asserting 0 further requests, entering-page checkpoint kept, `pendingSources=1`.
- **Checks are both per-request and per-item** (plan required "HTTP 请求前/每页检查"): the per-item check bounds the overrun inside a long page and is what keeps a half page from being counted as consumed.
- **Stop-reason strings**: kept the existing values (`BUDGET_DEFERRED`, `GLOBAL_PAPER_LIMIT`, `SOURCE_LIMIT`, `CANCELLED`, `WINDOW_LIMIT`) because the brief requires the cross-child vocabulary to stay stable and c2/c4/c10 consume these exact values; the brief's `API_BUDGET`/`GLOBAL_CAP` read as constraint names for them. Only `TIME_BUDGET` was added (the plan's literal). A page/loop stop caused by the *run* quota (quota < source cap) now reports `GLOBAL_PAPER_LIMIT` instead of `PAGE_PARTIAL`/`SOURCE_LIMIT`, and an in-page stop names its own constraint (`GLOBAL_PAPER_LIMIT`/`GLOBAL_AUTHOR_LIMIT`/`SOURCE_LIMIT`); `PAGE_PARTIAL` still covers the cancel-in-page path.
- **Crossref/CORE/arXiv per-source caps were changed only in `application.yml`** (their `*Properties.kt` classes are outside the authorized list, so their conservative Kotlin defaults 300/300/100 remain and the yml env defaults now hold the rollout values) — same pattern c3 documented for arXiv. OpenAlex's cap was changed in its authorized Properties class and in yml (both 10000).
- **`papersUsedForGlobalCap` sums only `SourceUnit.PAPER` sources** rather than `stats.totalPapers` (equivalent because ORCID runs after the paper sources); it keeps the paper budget from ever being consumed by record counts and is self-documenting.
- **Sources skipped by the outer global-cap break still create no `bySource` entry** (unchanged behaviour: `attemptedSources`/`pendingSources` semantics preserved). The allocation makes that outer break unreachable for any source with cap > 0, which is exactly the I-2 guarantee tested above.
- **Existing tests adapted (authorized test file), no assertion weakened**: `discover respects maxPapersPerRun limit` (global target 2 → 200 across two sources, now also asserts the `GLOBAL_PAPER_LIMIT` naming), `partial batch does not advance cursor to nextCursor` (P1-1: the old "global cap 1 < one page" config is now rejected by design, so the partial page is produced by a non-page-aligned run target 150 and the same invariant — never save `nextCursor` — is asserted), `batch numbers are globally unique and monotonic across sources` (run target 100 → 300 to cover both sources' base shares), `resolveEnabledSources keeps all six sources when enabled and scope null` (explicit properties with a covering run target), `parallel fetch produces same stats as serial run` / `managed executor accepts batch larger than fetchConcurrency` / `parallel fetch respects maxPapersPerRun and only counts consumed papers` (source cap pinned to the run target so the start validation holds), `budget deferred stop keeps the entering cursor and is not a search failure` (run target 300 for two enabled sources). Unauthorized test files (e.g. `DiscoveryMockHelper.java`, `DiscoveryResultTest`) were not touched and still compile/pass.
- No plan amendment, no new file beyond the authorized runbook, no migration, no frontend change, no dependency change, `docs/plans/fast/**` not staged.

### Freshness

- Plan identity rechecked: YES (`eee00b54…`, recomputed after the final edit via `scripts/plan_identity.py`)
- Worktree identity rechecked: YES (root/branch/git-dir unchanged before staging and after commit; `worktree_identity.py` HEAD = f81f71f)
- Reported commits reachable from target branch: YES (`git merge-base --is-ancestor HEAD fast/2026-09-21-discovery-enrichment-master` → 0)
- Required commands run this invocation: YES (final state, run #2 above; run #1 is the same required command on the same frozen code)
- Historical evidence used only as baseline: YES (the pre-implementation red run was this invocation's own evidence; no prior-run output was reused as proof)

### Remaining Blocker

- None. `git status` shows only the uncommitted `docs/plans/fast/2026-09-21-discovery-enrichment-master/ledger.md` owned by the controller.

### Next Action

- READY_FOR_VERIFICATION → run `verify-p`
