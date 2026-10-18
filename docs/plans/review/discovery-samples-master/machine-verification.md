# Aggregate Machine Verification — discovery-samples-master

## Epoch 1 — 2026-10-19T01:31:15+08:00

- Master plan: `docs/plans/2026-10-18/discovery-samples-master.md` (governing sha256:`b0eb3402f6144f13f4e99de5082ca43eaa27136b2bc96e8f7cab3d676725ed7f`; recorded commit `cd6189360196700cf65c5e21ab3512ddedb98abc`)
- Master identity state: `AMENDMENT_RECORDED`; A2, rule `I-5 / 变更文件清单`, records the frozen Apollo05 import exception and its human approval. A1/A2 are recorded in `docs/plans/fast/discovery-samples-master/ledger.md`.
- Boundary: `3efc192faf043badc1ef27bef124a71d3120c4c5..eb6080147141bc3d7dd00054705ba8973ea2b270`
- Evidence-only tail: `eb6080147141bc3d7dd00054705ba8973ea2b270..9e2ec5c043bb790ffab04324c007f045c7ff8937`
- Reviewer: `/root/aggregate_reviewer` (fresh; no inherited implementation or lightweight-verification context)
- Result: `PASS`
- Convergence: `INITIAL`
- Repair artifact/result: N/A

### Commands

| Command | Result | Evidence |
|---|---|---|
| `mvn -Dtest=DiscoverySampleStoreTest,DiscoverySampleCaptureTest -DskipNodeTests=true test` | PASS | exit 0; 11 tests; 0 failures, 0 errors, 0 skips |
| `mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,PdfEmailExtractorTest,EuropePmcDataSourceTest,OpenAlexDataSourceTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest -DskipNodeTests=true test` | RECORD_ONLY | exit 1; 418 tests; 0 failures, 53 errors, 1 skip; all errors are the identical pre-existing frozen-A2 fixture NPE described below |
| `mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,ExpertDiscoveryControllerMvcTest,ExpertDiscoveryContinuousMvcTest,ExpertDiscoveryPipelineUnavailableMvcTest,ExpertDiscoveryControllerTest,ExpertDiscoverySchedulerTest,DiscoveryVerificationIntegrationTest -DskipNodeTests=true -Dapi.version=1.44 test` | RECORD_ONLY | exit 1; 318 tests; 0 failures, 53 errors; isolated MySQL `DiscoveryVerificationIntegrationTest` 10/10 passed; Controller 4+6+1+20 and Scheduler 32 passed |
| `mvn -Dtest=DiscoverySampleReplayTest,PdfEmailExtractorTest,EuropePmcDataSourceTest -DskipNodeTests=true test` | PASS | exit 0; 77 tests; 0 failures, 0 errors, 1 existing optional skip |
| `bash -n scripts/expert_discovery/replay_samples.sh` | PASS | exit 0 |
| Full shell build + Java CLI `list` | PASS | exit 0; 9 samples, 7 bodies, 0 invalid packages |
| A2 manifest/blob audit | PASS | exit 0; 42 checked, 0 mismatches; frozen import head `3ccdcdddcdc68b2aef8a586582b5baa598c976ab` matches source `1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848` |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| C-01 | PASS | I-1/I-2: synchronous worker and queue paths pass explicit capture contexts; fresh child01 tests pass. |
| C-02 | PASS | Existing download bytes only; cache/admission/consume behavior remains outside capture. Sampling-only delta `3ccdcddd..e40863f6` is exactly the eight authorized files. |
| C-03 | PASS | I-3: capture `finally` and `HostBusy` paths preserve permit and business behavior; covered by fresh tests. |
| C-04 | PASS | C-1..C-5 bounded raw storage: disabled default, lock, symlink, atomic commit, quota and retention paths covered by 11 fresh tests. |
| C-05 | PASS | I-4 offline replay: local executor plus rejecting HTTP request factory; replay tests passed. |
| C-06 | PASS | C-6 comparison/provenance: strict SHA/config comparability; no-label reports preserve `accuracy=null`. |
| C-07 | PASS | A2 frozen import/order: 42/42 manifest blobs exact; seven ordered source commits retained. |
| C-08 | PENDING | Master manual A-1/A-2/A-3 require human-controlled integrated-test capture and human review; synthetic evidence is not represented as production evidence. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW — Observation | Both child02 commands reproduce 53 identical `ExpertDiscoveryServiceTest` fixture errors from `recordAutomaticConclusion(... automatic=null)`. It is in the frozen A2 import, outside the eight sampling files; no assertion failures; isolated Apollo integration passes. |
| V-2 | NEW — Observation | child01 historical pre-change failing-red command is unavailable; immutable chronology retained and fresh final 11-test command passes. |
| V-3 | NEW — Observation | child01 compiler warnings remain unchanged. |
| V-4 | NEW — Observation | child03 CLI evidence uses retained synthetic/frozen packages, never task-26127 or production evidence. |
| V-5 | NEW — Observation | `git diff --check` exits 2 with 152 whitespace diagnostics limited to plan/evidence snapshots, not product paths. |

### Findings

#### P1

N/A

#### P2

N/A

#### Observations

- V-1 through V-5 above are RECORD_ONLY leads re-evaluated against the master contract. None proves an in-scope mandatory-contract failure.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| child01 O-01 | Fresh required verification, no fabricated red/green chronology | Observation | V-2; fresh final command passes. |
| child01 O-02 | No unrelated compiler-warning cleanup | Observation | V-3; unchanged warnings. |
| child02 O-1 | I-5 / A2 frozen import; do not falsely claim global green | Observation | V-1; exact repeated frozen-fixture NPEs, zero assertion failures, isolated integration passes. |
| child03 O-1 | I-4 / no claim of real task or optimization evidence | Observation | V-4; CLI packages are synthetic/frozen. |

### Evidence Boundaries

- Human A-1/A-2/A-3 are pending; no controlled new integrated-test capture or human label result was performed by this review.
- No product code was modified.
