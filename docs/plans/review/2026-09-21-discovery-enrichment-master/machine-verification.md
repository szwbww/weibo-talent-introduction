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
