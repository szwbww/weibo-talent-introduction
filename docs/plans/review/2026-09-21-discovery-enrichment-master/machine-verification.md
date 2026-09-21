# Aggregate Machine Verification — 00-discovery-enrichment-master

## Epoch 1 — 2026-09-21T09:47:44Z

- Master plan: `docs/plans/2026-09-21/00-discovery-enrichment-master.md` (sha256 `b71e3a5c2af7f3b7e2a5a659f7fcab49f81816978e767a7c4d64aec1474ad837`)
- Governing master identity: worktree sha256 `b71e3a5c2af7f3b7e2a5a659f7fcab49f81816978e767a7c4d64aec1474ad837`; recorded commit `831e6604cf97e7acba005d8f00827659b49ce010`
- Master identity state: CONSISTENT; invoked identity is the same; amendments: N/A.
- Boundary: `f0c41271fc56d7455e14d28a71d563a5341dfdeb..e12c3471f89a9bc333ef8e7777703214abe29d5e`
- Reviewer: `/root/aggregate_reviewer` (fresh independent reviewer)
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: `docs/plans/fix/00-discovery-enrichment-master/repair.md` — DRAFT_READY

The reviewer inspected the complete 51-file product/test boundary. No product code, tests, plans, index, branch, or review evidence was modified by the reviewer; only the authorized repair artifact was created through `repair-p`.

### Fresh Command Evidence

| Command/group | Result | Evidence |
|---|---|---|
| c1 targeted Java 11 gate | PASS | exit 0; 81 run, 0 failures, 0 errors |
| c2 targeted Java 11 gate | PASS | exit 0; 136 run, 0 failures, 0 errors |
| c3 targeted Java 11 gate | FAIL | exit 1; 37 run, 4 errors |
| c4 targeted Java 11 gate | FAIL | exit 1; 163 run, 2 errors |
| c5 targeted Java 11 gate | PASS | exit 0; 220 run, 0 failures, 0 errors |
| c6 targeted Java 11 gate | PASS | exit 0; 274 run, 0 failures, 0 errors |
| c7 MySQL/Flyway with OrbStack workaround | baseline error | exit 1; 44 run, 1 pre-existing V124 FK error; new V131 job repository/service cases: 18 run, 0 failures, 0 errors |
| c8 targeted Java 11 gate | PASS | exit 0; 146 run, 0 failures, 0 errors |
| c9 targeted Java 11 gate | PASS | exit 0; 127 run, 0 failures, 0 errors |
| c10 targeted Java 11 gate | PASS | exit 0; 210 run, 0 failures, 0 errors |
| `node --test src/test/js/*.test.js` | PASS | 1035 pass, 0 fail |
| full Java 11 Maven gate | FAIL | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dapi.version=1.40`; exit 1; 3682 run, 0 failures, 4 errors, 13 skipped |

### Master Contract Matrix

| Contract | Verdict | Evidence |
|---|---|---|
| R-1 OpenAlex authentication/free budget | PASS | c1 target passes; policy/auth source and tests pass. |
| R-2 recoverable multi-source discovery | FAIL | V-1 prevents Crossref/arXiv regression suites from executing correctly. |
| R-3 automatic enrichment, max-100 and tail batch | FAIL | V-2 has false summary/detail stats; V-3 persists false idle lifecycle evidence. |
| R-4 trusted author identity | PASS | c5/c6 targets pass; real IDs retained and ambiguous identity guarded. |
| R-5 configurable fair throughput | PASS | c9 target: 127 run, 0 failures, 0 errors; cap/time-budget tests pass. |
| R-6 bounded fulltext/statistics | FAIL | V-4: XML and Unpaywall bypass the shared 90-second deadline. |
| M-1 email/dedup/gates/no mail | PASS | No mail workflow changes; targeted discovery tests pass. |
| M-2 manual enrichment/scopes/retry | PASS | c6/c8 targets pass. |
| M-3 preserve profile/operations | PASS | c5/c6 targets pass. |
| M-4 default R&D scope/medical exclusions | PASS | c3/c4 logic tests pass where executed. |
| M-5 free/no Key exposure/no applied migration edits | PASS | No paid flow or applied migration mutation; literal config defaults remain a pre-existing observation. |
| I-1 separate paper/author/request/batch budgets | PASS | c1/c9 targeted tests pass. |
| I-2 persist before advancing/replay | PASS | c2 target passes. |
| I-3 true identity/document IDs | PASS | c5 target passes. |
| I-4 three-layer academic write contract | PASS | c6/c8 targets pass. |
| I-5 honest lifecycle/statistics | FAIL | V-2 and V-3. |
| I-6 scope/cost containment | PASS | No scope/cost expansion found. |
| MySQL/Flyway V131 integration | PASS with baseline observation | Real MySQL V131 repository/service coverage passes; V124 fixture is pre-existing. |
| Required full Maven gate | FAIL | V-1 causes four errors. |
| A-1 through A-6 manual acceptance | PENDING | Human-only; not simulated. |

### Findings

#### P1

- **V-1 NEW** — `PdfEmailExtractor.extract` is now a five-argument JVM method, but adapter tests still mock three arguments: `CrossrefDataSourceTest.kt:225` and `ArxivDataSourceTest.kt:262`. Fresh c3/full Maven fail with `InvalidUseOfMatchersException`; unfinished Mockito state causes downstream errors.
- **V-2 NEW** — automatic result JSON uses `claimed/succeeded/pending/unmatched/failed`, while `TaskExecutionSummaryExtractor` reads only `enriched/failed` at `TaskExecutionSummaryExtractor.kt:102`; successful automatic runs render zero passed and the source-detail renderer reads discovery fields at `app.js:1429`.
- **V-3 NEW** — the worker writes progress before determining that its claim is empty: `ExpertAcademicEnrichmentWorker.kt:50` precedes empty handling at `:82`. Clearing in-memory state leaves a false interrupted progress record.
- **V-4 NEW** — fulltext deadline starts before XML, but the XML call at `OpenAlexDataSource.kt:100` and Unpaywall call at `:129` do not receive/enforce it, violating the per-paper total 90-second bound.

#### P2

- N/A.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| c1 O-1 quota writer | I-1 | Resolved | c10 wires the OpenAlex quota response writer. |
| c2 O-1 | I-5 | Non-blocking | No confirmed mandatory aggregate violation. |
| c3 O-1..O-3 | R-2/M-4 | Non-blocking | Pre-existing, ambiguous, or outside a proven master violation. |
| c4 O-1..O-4 | R-2/I-5 | Non-blocking | Same; no new mandatory violation proven. |
| c6 O-1 | I-4 | Non-blocking | No confirmed mandatory aggregate violation. |
| c7 O-1..O-2 | I-5 | Non-blocking | V124 Flyway fixture reproduces at c7 base. |
| c8 O-1/O-2 | I-5 | Promoted | V-2 and V-3. |
| c9 O-1 | M-5 | Non-blocking | Literal config defaults predate this boundary. |
| c10 O-1..O-3 | R-6/I-1 | Non-blocking | No mandatory defect beyond V-4. |
| c10 O-4 | R-6 | Promoted | V-4. |

### Boundaries and Next State

- Live API quota and production behavior were not exercised.
- Manual A-1 through A-6 remain pending human-only evidence.
- Verified failure is INITIAL. `repair-p` produced one bounded repair plan covering only V-1 through V-4. No repair execution is authorized until explicit human invocation of the exact `execute-p` path.

## Epoch 2 — 2026-09-21T13:29:37Z

- Master plan: `docs/plans/2026-09-21/00-discovery-enrichment-master.md` (sha256 `b71e3a5c2af7f3b7e2a5a659f7fcab49f81816978e767a7c4d64aec1474ad837`)
- Governing master identity: sha256 `b71e3a5c2af7f3b7e2a5a659f7fcab49f81816978e767a7c4d64aec1474ad837`; commit `831e6604cf97e7acba005d8f00827659b49ce010`; identity state `CONSISTENT`; amendments N/A.
- Boundary: `f0c41271fc56d7455e14d28a71d563a5341dfdeb..7312143c484fe00162c8f602b9295f3029ce5588`; post-repair `cd39503b7d6dc0d3fa12a02a226e6995bd2910e7..7312143c484fe00162c8f602b9295f3029ce5588`.
- Reviewer: `/root/aggregate_rereviewer` (fresh; created after repair commit; no inherited execution context).
- Result: FAIL
- Convergence: PROGRESSING
- Repair artifact/result: `docs/plans/fix/00-discovery-enrichment-master/repair.md` — DRAFT_READY, V-4 only.
- Repair evidence: DURABLE_HANDOFF at `docs/plans/review/2026-09-21-discovery-enrichment-master/repair-execution.md`; approved through the recorded human `$execute-p` invocation; executor `Main`.

### Fresh Command Evidence

| Command | Result | Evidence |
|---|---|---|
| c1 OpenAlex policy/config | PASS | exit 0; 23/0/0 |
| c2 checkpoint/result/service | PASS | exit 0; 138/0/0 |
| c3 Crossref/arXiv/scope | PASS | exit 0; 37/0/0 |
| c4 CORE/ORCID/scope | PASS | exit 0; 32/0/0 |
| c5 revalidation/search/ORCID | PASS | exit 0; 105/0/0 |
| c6 discovery/worker/controller | PASS | exit 0; 148/0/0 |
| c7 Flyway/job integration | FAIL — baseline | exit 1; 44 run; V131 job tests 18/0/0; only unchanged V124 FK fixture error |
| c8 summary/worker/controller | PASS | exit 0; 47/0/0 |
| c9 discovery/controller/scope | PASS | exit 0; 145/0/0 |
| c10 fulltext | PASS | exit 0; 217/0/0 |
| `node --test src/test/js/*.test.js` | PASS | 1037 pass, 0 fail |
| Full Maven | FAIL — unrelated observation | exit 1; 3694 run, 1 failure, 0 errors, 13 skipped; unchanged 1Hz `UnmatchedInboundAiReplyTurnKnowledgeTest` assertion expected 1, got 2 |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| R-1 OpenAlex auth/budget | PASS | c1 green; trusted-origin auth/policy remains. |
| R-2 recoverable discovery | PASS | c2–c4 green; checkpoint/status/source paging evidence. |
| R-3 auto enrichment | PASS | c6/c8 green; V-2/V-3 resolved. |
| R-4 trusted identity | PASS | c5 green. |
| R-5 fair throughput | PASS | c9 green. |
| R-6 bounded fulltext | FAIL | V-4 persistent: active XML/Unpaywall calls can exceed the shared 90-second deadline. |
| M-1 email/dedup/gates/no mail | PASS | No repair-boundary changes to those paths. |
| M-2 manual enrichment/retry | PASS | c6/c8 green. |
| M-3 preserve profile/operations | PASS | c5/c6 green. |
| M-4 default R&D scope/exclusions | PASS | c3/c4/c9 green. |
| M-5 free/no key exposure/migrations | PASS | No introduced paid/key/migration behavior; literal config-key note remains excluded. |
| I-1 separate budgets | PASS | c1/c9 green. |
| I-2 persist before advance/replay | PASS | c2 green. |
| I-3 true identity/document IDs | PASS | c5 green. |
| I-4 three-layer write contract | PASS | c6/c8 green. |
| I-5 honest lifecycle/statistics | PASS | V-2/V-3 repaired; c6/c8/JS green. |
| I-6 scope/cost containment | PASS | No scope/cost expansion. |
| 10/I-1 total fulltext deadline | FAIL | V-4. |
| Full Maven mandatory gate | FAIL | One unrelated flaky timing assertion. |
| A-1–A-6 | PENDING | Human-only; not simulated. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | c3 37/0/0; full JVM mock signature fixed. |
| V-2 | RESOLVED | c8 47/0/0; JS 1037/0; automatic totals/stages render correctly. |
| V-3 | RESOLVED | c6/c8 green; empty tick due-work probe precedes progress lock. |
| V-4 | PERSISTENT | `EuropePmcDataSource.kt:145` and `UnpaywallClient.kt:47` issue blocking calls without remaining-time enforcement; unqualified `RestTemplateConfig.kt:47` has no timeout. |

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| c1 O-1 | I-1 | Resolved | c10 wires fulltext quota response recording. |
| c2 O-1 | I-5 | Non-blocking | No proven source-level mandatory violation. |
| c3 O-1..O-3 | R-2/M-4 | Non-blocking | Pre-existing/compatibility/deviation notes. |
| c4 O-1..O-4 | R-2/I-5 | Non-blocking | No new master violation proven. |
| c6 O-1 | I-4 | Non-blocking | No demonstrated gate regression. |
| c7 O-1 | I-5 | Baseline | V124 fixture reproduced. |
| c7 O-2 | I-5 | Non-blocking | Same local-time convention on read/write. |
| c8 O-1 | I-5 | Resolved | V-2. |
| c8 O-2 | I-5 | Resolved | V-3. |
| c9 O-1 | M-5 | Non-blocking | Pre-existing, outside repair boundary. |
| c10 O-1..O-3 | R-6/I-1 | Non-blocking | URL-cap interpretation/procedural or telemetry notes. |
| c10 O-4 | R-6 | Persistent | V-4. |

### Boundaries and Next State

- Live quota/production behavior was not exercised.
- Manual A-1 through A-6 remain pending human-only evidence.
- Full Maven is red only on the unrelated timing flake; c7 is red only on the base-reproduced V124 fixture.
- No product code was modified by this review. `repair-p` produced the one bounded V-4 repair plan; execution requires explicit human invocation of its exact `execute-p` path.

## Epoch 3 — 2026-09-21

- Master plan: `docs/plans/2026-09-21/00-discovery-enrichment-master.md` (sha256 `b71e3a5c2af7f3b7e2a5a659f7fcab49f81816978e767a7c4d64aec1474ad837`)
- Governing master identity: worktree sha256 `b71e3a5c2af7f3b7e2a5a659f7fcab49f81816978e767a7c4d64aec1474ad837`; recorded commit `831e6604cf97e7acba005d8f00827659b49ce010`; invoked identity same; state `CONSISTENT`; amendments N/A.
- Boundary: `f0c41271fc56d7455e14d28a71d563a5341dfdeb..ebd16aca1788056236fe9b6bf686f9972bacc173`; post-repair delta `1c4d7c12c02ae93532840a17c71b4d2b7903a3e2..ebd16aca1788056236fe9b6bf686f9972bacc173` stays inside the ten Authorized Files in repair `sha256:2105d2c6d899b4a4dbb23730cb49073962b61e9826e54698a81067845ca3d961`.
- Reviewer: `/root/final_aggregate_reviewer` (fresh, created after the repair code commit, with no inherited implementation/light-verification context).
- Result: `FAIL`; convergence: `PROGRESSING`.
- Repair artifact/result: `docs/plans/fix/00-discovery-enrichment-master/repair.md` — `DRAFT_READY`, V-4 only.
- Post-repair evidence: `DURABLE_HANDOFF`; approved repair execution recorded in `docs/plans/review/2026-09-21-discovery-enrichment-master/repair-execution.md`; executor `Main`.

### Fresh Command Evidence

| Command/group | Result | Evidence |
|---|---|---|
| c1 Java 11 policy/config/fulltext | PASS | 89 run, 0 failures, 0 errors |
| c2 Java 11 checkpoint/result | PASS | 138 run, 0 failures, 0 errors |
| c3 Java 11 Crossref/arXiv/scope | PASS | 37 run, 0 failures, 0 errors |
| c4 Java 11 CORE/ORCID/scope | PASS | 165 run, 0 failures, 0 errors |
| c5 Java 11 revalidation/search | PASS | 228 run, 0 failures, 0 errors |
| c6 Java 11 discovery/revalidation | PASS | 280 run, 0 failures, 0 errors |
| c7 MySQL/Flyway | baseline error | 44 run; V131 repository/service 18 run, 0 failures, 0 errors; base-equivalent V124 FK fixture error |
| c8 Java 11 discovery/worker/controller | PASS | 148 run, 0 failures, 0 errors |
| c9 Java 11 discovery/scheduler | PASS | 129 run, 0 failures, 0 errors |
| c10 Java 11 fulltext | PASS | 221 run, 0 failures, 0 errors |
| `node --test src/test/js/*.test.js` | PASS | 1,037 pass, 0 fail |
| Full Java 11 Maven with OrbStack | unrelated error | 3,718 run, 0 failures, 1 error, 13 skipped; pre-existing `MailComposeTemplateBlockRepositoryIT` missing `${senderEmail}` Flyway placeholder |

### Master Contract Matrix

| Contract | Verdict | Evidence |
|---|---|---|
| R-1 / I-1 OpenAlex auth, budget, separate accounting | PASS | c1 green; policy/config tests |
| R-2 / I-2 recoverable discovery | PASS | c2–c4 green; checkpoint/paging tests |
| R-3 / I-4 / I-5 automatic enrichment and honest status | PASS | c6/c8 green; worker probe and summaries |
| R-4 / I-3 trusted identity | PASS | c5/c6 green |
| R-5 throughput/fairness | PASS | c9 green |
| R-6 / 10-I-1 fulltext absolute deadline | FAIL | V-4 persistent |
| M-1 through M-5 | PASS | No mail, identity, scope, paid API, or applied-migration regression |
| I-6 scope/cost containment | PASS | Boundary stays inside child-plan scopes |
| V131 MySQL persistence | PASS | Repository/service integration 18 green |
| Full Maven required gate | unrelated error | Missing `senderEmail` placeholder outside boundary |
| A-1 through A-6 | PENDING | Human-only; not simulated |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | c3 remains 37/0/0 |
| V-2 | RESOLVED | c8 and JS green |
| V-3 | RESOLVED | Idle due-work probe remains green |
| V-4 | PERSISTENT | `RestTemplateConfig.kt:178-209`, `EuropePmcDataSource.kt:96-108`, `UnpaywallClient.kt:51-61` |

### P1 Finding

- **V-4 PERSISTENT** — `SimpleClientHttpRequestFactory` read timeout limits one blocking read, not total response lifetime. XML and Unpaywall JSON can trickle bytes before each read timeout and exceed the shared absolute deadline. `UnpaywallClient` also permits a post-preflight expiry race: zero remaining time becomes a 1-ms client and may still dispatch. This violates R-6/10-I-1.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result |
|---|---|---|
| c1 O-1 | I-1 | RESOLVED |
| c2 O-1 | I-5 | Non-blocking |
| c3 O-1..O-3 | R-2/M-4 | Non-blocking |
| c4 O-1..O-4 | R-2/I-5 | Non-blocking |
| c6 O-1 | I-4 | Non-blocking |
| c7 O-1 | I-5 | Baseline V124 fixture |
| c7 O-2 | I-5 | Non-blocking |
| c8 O-1/O-2 | I-5 | RESOLVED |
| c9 O-1 | M-5 | Non-blocking |
| c10 O-1..O-3 | R-6/I-1 | Non-blocking |
| c10 O-4 | R-6 | V-4 persistent |

### Boundaries and Next State

- Live quota and manual A-1 through A-6 were not run.
- Full Maven's sole error and the c7 V124 error are base/unrelated observations, not repair scope.
- `repair-p` updated only `docs/plans/fix/00-discovery-enrichment-master/repair.md`; no product code, test, review evidence, index, branch, or commit was modified by the reviewer.
- Verification result `FAIL/PROGRESSING`; repair planning `DRAFT_READY`. Human approval of that exact repair artifact is required before execution.
