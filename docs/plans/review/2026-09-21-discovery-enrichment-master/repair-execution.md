# Repair Execution — 00-discovery-enrichment-master

## Identity

- Approval source: HUMAN invocation `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/fix/00-discovery-enrichment-master/repair.md` (2026-09-21) — the exact path and command named by the repair plan's "Review-Fast-P Execution Handoff" clause. The plan itself is marked `DRAFT — HUMAN APPROVAL REQUIRED`; this invocation is the approval that released it.
- Repair plan identity: `docs/plans/fix/00-discovery-enrichment-master/repair.md` @ `sha256:9bfd7110023a853488d9c4fe6fc245ffa427789bf35bd49142285aa8687f3f2b` (11,621 bytes). Recomputed before execution and again before this commit: unchanged.
- Baseline plan (context, not modified): `docs/plans/2026-09-21/00-discovery-enrichment-master.md`.
- Aggregate verification that produced the findings: `docs/plans/review/2026-09-21-discovery-enrichment-master/machine-verification.md`, epoch 1, result FAIL, findings V-1..V-4.
- Executor: `Main` (omp controller session, single execution context; no delegated sub-agent writer).
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master`
- Target branch: `fast/2026-09-21-discovery-enrichment-master`
- Worktree Git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master@fast/2026-09-21-discovery-enrichment-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master`
- Pre-execution code SHA: `cd39503b7d6dc0d3fa12a02a226e6995bd2910e7`
- Post-execution code SHA: `7312143c484fe00162c8f602b9295f3029ce5588` (`fix(discovery): restore aggregate discovery contracts`, 16 files, +496/−28)
- Implementation boundary: `cd39503b7d6dc0d3fa12a02a226e6995bd2910e7..7312143c484fe00162c8f602b9295f3029ce5588`

## Findings → repairs

| Finding | Severity | Repair task | What changed | Evidence |
|---|---|---|---|---|
| V-1 | P1 | R-1 | `PdfEmailExtractor.extract` is a five-parameter JVM method since c10; the two retained adapter tests still stubbed three matchers, which raised `InvalidUseOfMatchersException` and leaked unfinished Mockito state into later tests. Both stubs now match the full JVM signature (5 matchers) and their delegation assertions are unchanged. | c3 group exit 0 (37 tests, 0 F/0 E) where the pre-repair run was exit 1 with 4 errors; full Maven exit 0 with 0 errors (was 4). |
| V-2 | P1 | R-2 | `TaskExecutionSummaryExtractor`'s `EXPERT_ENRICHMENT` rule only read the manual `enriched/failed` shape, so every automatic batch rendered "0 passed". It now maps both shapes through one helper (`claimed`/`succeeded`/`pending`+`unmatched`+`failed` for automatic; `enriched/failed` unchanged for manual), in both the `resultSummary` level and the progress-log fallback level. `app.js` renders automatic `bySource` counts as enrichment stages (入队/成功/待补/未匹配/失败) in the task modal and in the task detail row, keeping the raw result block below. | `TaskExecutionSummaryExtractorTest` 20/0/0 with new automatic-shape cases (claimed=3, succeeded=1, pending=1, unmatched=1, failed=0 → 3 processed / 1 passed / 2 not passed; log-fallback variant 3/2/1); `node --test src/test/js/*.test.js` 1037 pass / 0 fail including two new stage-rendering cases. |
| V-3 | P1 | R-3 | The worker acquired the shared task lock (which persists a progress-log row) before it knew whether any job was due, so idle 30-second ticks accumulated orphan `RUNNING` records that read as interrupted/failed executions. `ExpertDiscoveryService.hasDueEnrichmentJobs()` is a new read-only probe over the same due predicate; the worker short-circuits on it before touching the lock. Claiming, mutual exclusion, lease recovery, cadence and tail batches are unchanged. | `ExpertAcademicEnrichmentWorkerTest` 10/0/0 with a new idle-tick case (no lock, no `task_execution`, no progress update, no executor use); `ExpertDiscoveryServiceTest` 121/0/0 with two new probe cases (asserts no `claimById`, no `claimDue`, no `insertIfAbsent`; `false` without OpenAlex or without due work). |
| V-4 | P1 | R-4 | The 90-second per-paper budget was started before the XML stage but never reached the PMC XML fetch or the Unpaywall lookup. `EuropePmcDataSource.extractAuthorEmails(paper, deadline)` now issues no XML request once the budget is gone and reports the existing `TIMEOUT` category; `UnpaywallClient.findPdfUrls(doi, deadline)` skips both the politeness delay and the lookup on expiry; `OpenAlexDataSource` threads one deadline through all three stages and refuses to start the Unpaywall stage after expiry. | `OpenAlexDataSourceTest` 61/0/0 (same-deadline assertions for the XML and Unpaywall stages; no later stage after expiry); `EuropePmcDataSourceTest` expired ⇒ 0 HTTP requests + `TIMEOUT` via `MockRestServiceServer.verify()`; `UnpaywallClientTest` 8/0/0 (expired ⇒ no sleep, no request). |

## Changed files (exactly the 16 authorized files)

| File | Purpose |
|---|---|
| `src/test/kotlin/.../discovery/service/ArxivDataSourceTest.kt` | R-1: 5-argument extractor stub, delegation assertion kept. |
| `src/test/kotlin/.../discovery/service/CrossrefDataSourceTest.kt` | R-1: 5-argument extractor stub, delegation assertion kept. |
| `src/main/kotlin/.../task/service/TaskExecutionSummaryExtractor.kt` | R-2: shared `enrichmentTotals` helper for automatic + manual shapes (summary and log-fallback levels). |
| `src/test/kotlin/.../task/service/TaskExecutionSummaryExtractorTest.kt` | R-2: automatic-shape totals at both levels. |
| `src/main/resources/static/app.js` | R-2: `isEnrichmentBySource` / `renderEnrichmentSourceTable` / `normalizeEnrichmentResultSummary` + `EXPERT_ENRICHMENT` detail branch. |
| `src/test/js/taskRecordsSemantics.test.js` | R-2: automatic stage rendering + discovery-table regression case. |
| `src/main/kotlin/.../discovery/service/ExpertAcademicEnrichmentWorker.kt` | R-3: read-only due probe before the task lock. |
| `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt` | R-3: `hasDueEnrichmentJobs()` seam + read-only repository dependency. |
| `src/test/kotlin/.../discovery/service/ExpertAcademicEnrichmentWorkerTest.kt` | R-3: idle-tick case; existing cases stub the probe. |
| `src/test/kotlin/.../discovery/service/ExpertDiscoveryServiceTest.kt` | R-3: probe cases (no claim/advance/insert). |
| `src/main/kotlin/.../discovery/service/OpenAlexDataSource.kt` | R-4: one deadline through XML → URL → Unpaywall. |
| `src/main/kotlin/.../discovery/service/EuropePmcDataSource.kt` | R-4: deadline-aware XML stage entry point. |
| `src/main/kotlin/.../discovery/service/UnpaywallClient.kt` | R-4: deadline-aware lookup. |
| `src/test/kotlin/.../discovery/service/OpenAlexDataSourceTest.kt` | R-4: deadline propagation and post-expiry suppression cases. |
| `src/test/kotlin/.../discovery/service/EuropePmcDataSourceTest.kt` | R-4: expired → no request, `TIMEOUT`; live/null deadline unchanged. |
| `src/test/kotlin/.../discovery/service/UnpaywallClientTest.kt` | R-4: expired → no delay/request; null deadline unchanged. |

## Commands (all run freshly in the target worktree, JDK 11)

| Command | Result |
|---|---|
| 1. `mvn test -Dtest=CrossrefDataSourceTest,ArxivDataSourceTest,SubjectScopeCatalogTest` | exit 0; `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0`; BUILD SUCCESS |
| 2. `mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentWorkerTest,ExpertDiscoveryControllerTest` | exit 0; `Tests run: 148, Failures: 0, Errors: 0, Skipped: 0`; BUILD SUCCESS |
| 3. `mvn test -Dtest=OpenAlexDataSourceTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,UnpaywallClientTest` | exit 0; `Tests run: 217, Failures: 0, Errors: 0, Skipped: 0`; BUILD SUCCESS |
| 4. `node --test src/test/js/*.test.js` | exit 0; `tests 1037, pass 1037, fail 0` |
| 5. `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dapi.version=1.40` | exit 0; `Tests run: 3694, Failures: 0, Errors: 0, Skipped: 13`; BUILD SUCCESS; zero `[ERROR]` lines (pre-repair aggregate run: `3682 run, 0 failures, 4 errors`). The plan's command 5 omits `-DmigrationIt=true`, so `FlywayMigrationIntegrationTest` reports `Tests run: 1, Skipped: 1` there. |
| Extra (not a plan command, recorded for continuity): `DOCKER_HOST=… mvn test -Dtest=FlywayMigrationIntegrationTest,ExpertAcademicEnrichmentJobRepositoryIT,ExpertAcademicEnrichmentJobServiceTest -DmigrationIt=true -Dapi.version=1.40` | exit 1; `Tests run: 44, Failures: 0, Errors: 1` — the sole error is the documented pre-existing `FlywayMigrationIntegrationTest.V124 allows material attached promotion audit trigger` FK fixture error (`:613→:1596 SQLIntegrityConstraintViolation`). V131 job coverage is green: `ExpertAcademicEnrichmentJobRepositoryIT` 14/0/0, `ExpertAcademicEnrichmentJobServiceTest` 4/0/0. Unchanged by this repair. |

## Deviations

- **R-3 seam shape.** The plan authorized only `ExpertAcademicEnrichmentWorker.kt` and `ExpertDiscoveryService.kt` for the due-work seam, while `ExpertAcademicEnrichmentJobService` exposes no read-only due query and the job repository/service files are outside the authorized list. The probe therefore reuses the existing read-only `ExpertAcademicEnrichmentJobRepository.findDueCandidates(1, …)` and injects that repository into `ExpertDiscoveryService` (constructor addition; no repository method was added or changed, and no column/status is written). The claim predicate is unchanged, so the probe only decides whether to start; lease recovery, mutual exclusion and the 30-second cadence are untouched.
- **R-4 in-flight enforcement.** "Observe remaining time" is implemented as: no request is issued once the budget is exhausted, the same `Instant` is threaded through XML/URL/Unpaywall, and no later stage starts after expiry. A request already in flight is still bounded by the HTTP client's configured timeouts, not by the per-paper deadline — shortening a live socket read would require `RestTemplate`/client configuration outside the authorized files. No per-stage 90-second reset, no new concurrency, size or page limits.
- **R-2 frontend cache key (needs a human decision at release time).** `app.js` changed but `src/main/resources/static/index.html` is outside the authorized files, so the shared cache-key triad (`styles.css` / `trust-reply-workbench.js` / `app.js`, currently `?v=20260920-manual-material-upload`) was **not** bumped. No test pins break (the full JS suite is green), but a browser holding the old `app.js?v=…` will keep rendering the previous task-detail markup until the next release bumps all three keys together.
- **Scope.** No file outside the Authorized Files list was modified; no plan was edited; nothing was pushed, merged, rebased, amended or squashed.

## Clean-state evidence

- `git status --porcelain` in the target worktree: empty immediately before and immediately after the product commit.
- Only the 16 authorized files are in `7312143` (`git show --stat`).
- Plan identity re-checked after the commit: `sha256:9bfd7110023a853488d9c4fe6fc245ffa427789bf35bd49142285aa8687f3f2b` (unchanged, 11,621 bytes).
- Worktree identity re-checked after the commit: root, branch `fast/2026-09-21-discovery-enrichment-master`, same Git dir; the product commit is the branch HEAD and reachable from it.
- Baseline preserved: no mail was sent, no ES/DB state was written outside tests, no applied migration or mapping was touched (V131 untouched), no API key was read, logged or printed.

## Next step for the reviewer

The plan's clause 5 returns to the already authorized `review-fast-p` aggregate re-review; this invocation did not request it, so execution stops here at `READY_FOR_VERIFICATION` for an independent light/full verification of `cd39503..7312143`.

---

# Epoch 2 — `fix(discovery): enforce fulltext deadline`

## Identity (epoch 2)

- Approval source: HUMAN invocation `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-21-discovery-enrichment-master/docs/plans/fix/00-discovery-enrichment-master/repair.md` (2026-09-21, second invocation) — the same declared path, now carrying **new content**, matching the plan's "Review-Fast-P Execution Handoff" clause.
- Repair plan identity: `docs/plans/fix/00-discovery-enrichment-master/repair.md` @ `sha256:c337c37f5f511d69e892e3a3e45c10b7e6a003f5e39ca1258861c1760e42deeb` (8,173 bytes) — replaced the epoch-1 identity `sha256:9bfd7110…` (11,621 bytes); recomputed unchanged before and after execution. Same-path new content = new execution epoch; all epoch-1 evidence below stays historical.
- Aggregate verification that produced this plan: `machine-verification.md` aggregate epoch 2 re-review — `V-1`, `V-2`, `V-3` resolved in `7312143`; `V-4` persistent.
- Executor: `Main` (omp controller session, single execution context; no delegated writer).
- Target worktree / branch / Git dir / worktree ID: unchanged from epoch 1 (`fast/2026-09-21-discovery-enrichment-master`).
- Pre-execution code SHA: `1c4d7c12c02ae93532840a17c71b4d2b7903a3e2`
- Post-execution code SHA: `ebd16aca1788056236fe9b6bf686f9972bacc173` (`fix(discovery): enforce fulltext deadline`, exactly the 10 authorized files, +505/−17)
- Implementation boundary: `1c4d7c12c02ae93532840a17c71b4d2b7903a3e2..ebd16aca1788056236fe9b6bf686f9972bacc173`

## V-4 → R-1 (only finding in scope)

| Requirement | What changed | Evidence |
|---|---|---|
| One 90-second budget covers connection, response-header and body-read work of PMC XML, OA URL and Unpaywall | `RestTemplateConfig.kt` gains `BoundedHttpExecutor` + `BoundedFulltextHttp`: a one-shot `SimpleClientHttpRequestFactory` whose connect **and** read timeout is `min(client configuration, remaining budget)` — it can only shorten a deadline, and when the budget is not tighter it returns the *same* client instance (ordinary callers unchanged). Under a compressed budget the retry interceptor is not carried over, so one attempt cannot be multiplied past the budget; converters, error handler and other interceptors (e.g. OpenAlex auth) are inherited. `PDF_DOWNLOAD_CONNECT_TIMEOUT_MS` became single-source. | `RestTemplateConfigTest` 14/0/0: timeout narrowing + never-widening, same-instance for an unbounded budget, converter/error-handler/interceptor inheritance, retry-interceptor omission, and a real stalled-header call aborted inside the budget with exactly one accepted connection |
| In-flight XML request obeys the budget | `EuropePmcDataSource.fetchFullTextXml(pmcId, deadline)` re-evaluates the remaining budget before every attempt (no request once exhausted), runs each attempt through the bounded client, and returns a typed `XmlFetchResult` so the outcome can distinguish "no content" (`FULLTEXT_FETCH_FAILED`, unchanged) from "budget exhausted" (`FULLTEXT_FETCH_FAILED` + existing `TIMEOUT` category, with the truthful request count). | `EuropePmcDataSourceTest` 29/0/0 (1 pre-existing `@Disabled`): slow-XML case ends `TIMEOUT` with `httpRequests = 1` and exactly one accepted connection; live/null-deadline behaviour unchanged |
| Delay **and** in-flight lookup obey the budget | `UnpaywallClient.findPdfUrls(doi, deadline)` refuses the politeness delay when the remaining budget is shorter, re-checks after the delay, and runs the lookup through the bounded client (the generic client has no configured timeout today, so this only ever tightens; a `null` deadline still uses the original client). | `UnpaywallClientTest` 9/0/0: slow lookup returns no candidates within the budget with one accepted connection; expired-deadline and no-deadline cases unchanged |
| In-flight PDF/HTML download obeys the budget | `PdfEmailExtractor` performs its streaming `execute` through the injected `BoundedHttpExecutor` (new constructor seam, production bean `boundedHttpExecutor`), so the connect and response-header waits are bounded by the remaining budget while the existing per-chunk deadline check still guards the body read. | `PdfEmailExtractorTest` 29/0/0: stalled-header and stalled-body cases both end `TIMEOUT` inside the budget; the existing mock-based cases keep their pass-through executor so their assertions stay meaningful |
| No later stage after expiry; one elapsed budget reports `TIMEOUT` | `OpenAlexDataSource` records `TIMEOUT` when the Unpaywall stage is cut short by the shared budget and starts no download afterwards. | `OpenAlexDataSourceTest` 62/0/0, including an end-to-end case with a real `UnpaywallClient` against a stalled server: `TIMEOUT`, 1 request, `verifyNoInteractions(pdfExtractor)`, one accepted connection |

## Changed files (exactly the 10 authorized files)

| File | Purpose |
|---|---|
| `src/main/kotlin/.../config/RestTemplateConfig.kt` | `BoundedHttpExecutor` interface, `BoundedFulltextHttp` implementation, `boundedHttpExecutor` bean, `PDF_DOWNLOAD_CONNECT_TIMEOUT_MS` single source |
| `src/main/kotlin/.../discovery/service/EuropePmcDataSource.kt` | Budget-bounded XML stage + typed fetch result |
| `src/main/kotlin/.../discovery/service/UnpaywallClient.kt` | Budget-bounded delay and lookup |
| `src/main/kotlin/.../discovery/service/PdfEmailExtractor.kt` | Bounded streaming download via the injected executor |
| `src/main/kotlin/.../discovery/service/OpenAlexDataSource.kt` | `TIMEOUT` when the lookup is cut by the shared budget |
| `src/test/kotlin/.../config/RestTemplateConfigTest.kt` | Bounded-client contract + `SlowHttpServer` helper (shared by the client tests) + stalled-header abort |
| `src/test/kotlin/.../discovery/service/EuropePmcDataSourceTest.kt` | Slow XML → `TIMEOUT` within budget |
| `src/test/kotlin/.../discovery/service/UnpaywallClientTest.kt` | Slow lookup → bounded |
| `src/test/kotlin/.../discovery/service/PdfEmailExtractorTest.kt` | Stalled header / stalled body → bounded; existing cases use a pass-through executor |
| `src/test/kotlin/.../discovery/service/OpenAlexDataSourceTest.kt` | End-to-end elapsed-budget case |

## Commands (all fresh in this invocation, JDK 11)

| # | Command | Result |
|---|---|---|
| 1 | `mvn test -Dtest=OpenAlexRequestPolicyTest,RestTemplateConfigTest` | exit 0 · 27 / 0 F / 0 E |
| 2 | `mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentWorkerTest,ExpertDiscoveryControllerTest` | exit 0 · 148 / 0 F / 0 E |
| 3 | `mvn test -Dtest=TaskExecutionSummaryExtractorTest,ExpertAcademicEnrichmentWorkerTest,ExpertDiscoveryControllerTest` | exit 0 · 47 / 0 F / 0 E |
| 4 | `mvn test -Dtest=OpenAlexDataSourceTest,PdfEmailExtractorTest,ExpertDiscoveryServiceTest,UnpaywallClientTest` | exit 0 · 221 / 0 F / 0 E |
| 5 | `node --test src/test/js/*.test.js` | exit 0 · 1037 pass / 0 fail |
| 6 | `DOCKER_HOST=… mvn test -Dapi.version=1.40` | exit 0 · **3703 / 0 F / 0 E / 13 skipped**, BUILD SUCCESS, zero `[ERROR]` lines |
| extra | `mvn test -Dtest=EuropePmcDataSourceTest,UnpaywallClientTest` | exit 0 · 38 / 0 F / 0 E / 1 pre-existing skip |
| extra | `… -DmigrationIt=true` (epoch 1, unchanged) | exit 1 · sole error = pre-existing `FlywayMigrationIntegrationTest.V124` FK fixture; V131 coverage 18/0/0 |

## Deviations (epoch 2)

- **Executor seam.** `PdfEmailExtractor` gained a required fourth constructor parameter (`BoundedHttpExecutor`), supplied in production by the new `boundedHttpExecutor` bean. The three mock-based `PdfEmailExtractorTest` cases and the `OpenAlexDataSourceTest` real-extractor case now pass a pass-through implementation so their existing assertions keep testing what they tested; the in-flight bound is covered by the new real-client cases instead. Without the seam the bounded client would bypass their `RestTemplate` stubs.
- **Per-read vs total bound.** The remaining budget caps connect, header and each body read. A peer that trickles bytes always just under the read timeout could still keep a body read alive past the deadline for the URL stage; the existing per-512KB deadline check inside `PdfEmailExtractor` bounds that path. XML (`ByteArray`) and Unpaywall (`JsonNode`) responses are fully read under the single bounded timeout.
- **Retry suppression under a compressed budget.** `BoundedFulltextHttp.bounded` drops `RetryingClientHttpRequestInterceptor` only when the budget is tighter than the client configuration, so one attempt cannot be multiplied past the deadline. Normal (unbounded / not-tighter) callers keep the interceptor untouched.
- **Frontend cache key** (carried over from epoch 1): `index.html` remains outside the authorized files, so the `?v=…` triad was not bumped.
- No file outside the authorized list was changed; no plan edited; nothing pushed, merged, rebased, amended or squashed.

## Clean-state evidence (epoch 2)

- `git status --porcelain`: empty immediately before and after `ebd16aca`.
- Plan identity re-checked after the commit: `sha256:c337c37f5f511d69e892e3a3e45c10b7e6a003f5e39ca1258861c1760e42deeb` (unchanged).
- Worktree identity re-checked: same root/branch/Git dir; `ebd16aca` is the branch HEAD.
- No mail sent, no ES/DB state written outside tests, no migration/mapping touched, no API key read, logged or printed.
