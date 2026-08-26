# Aggregate Machine Verification — rnd-gate

## Epoch 1 — 2026-08-26T04:51:44Z

- Master plan: docs/plans/2026-08-25/00-rnd-gate-master.md (sha256: 0fb670be5d91dad6f4172d515fc00cee95208a0a12df7c11b4125a1c75e10ac5)
- Governing master identity: worktree sha256 0fb670be5d91dad6f4172d515fc00cee95208a0a12df7c11b4125a1c75e10ac5; recorded commit 5718abb
- Master identity state: CONSISTENT; governing amendment A3 (主计划「已识别但本轮不做：SBIR 接入」节; SBIR 地域/IP 封禁定性，01-04 子计划契约不变; HUMAN direct user plan-file update 2026-08-26)
- Boundary: f2935072c819a9167e75220a6a959b0769462fde..ee152d2b21030f6b86da16769f638b29d4be094b
- Reviewer: 01a03c5f-b084-7db3-b270-4d39b913c865 (Hilbert; fresh isolated reviewer)
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A; repair-p was not run because verification passed.

## review-p aggregate/master output

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | PASS | exit 0 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest` | PASS | exit 0; 65 tests, 0 failures, 0 errors |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 743 passed |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `git diff --check` | PASS | exit 0; no output |

### Contract Matrix

| ID | Requirement | Verdict | Evidence |
|---|---|---|---|
| M-1 | `expertTypes` optional and ANDed with sendability hard gate | PASS | `ExpertSearchService.kt`; required tests passed |
| M-2 | Classification semantics remain single-source | PASS | `classificationNode` remains the only construction seam |
| M-3 | Empty multi-value selection means unrestricted | PASS | empty list returns null and does not append filter |
| M-4 | Promotion excludes only `SERVICE_ONLY`/`OUT_OF_SCOPE`; `UNKNOWN` passes | PASS | `ExpertRevalidationService.kt` |
| M-5 | Promotion freshly classifies RAW profile and reuses writer node | PASS | `classify(profile)` and `classificationNode()` |
| M-6 | Default behavior remains isolated from release | PASS | empty batch filter; promotion default false; explicit discovery scope |
| O-1 | Expert-list type filtering and display | PASS | controller, service, `app.js` |
| O-2 | Batch-send type filter retains hard gate | PASS | manual/batch seams; V108 migration |
| O-3 | Promotion classification gate and write | PASS | properties, revalidation, writer |
| O-4 | Discovery subject scope and EuropePMC switch | PASS | catalog, datasource, scheduler, `application.yml` |
| N-1 | MATERIAL_REMINDER unaffected | PASS | INTRODUCTION-only branch |
| N-2 | Classification algorithm and discipline behavior unchanged | PASS | complete boundary diff inspection |
| N-3 | SBIR remains out of scope | PASS | A3 marks it PARKED, not an implementation contract |
| Scope | Cumulative product/test delta follows authorized child paths | PASS | complete boundary diff inspection |
| Manual | A-1, A-2, A-3 | PENDING | human-only; does not block machine PASS |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| N/A | N/A | No stable `V-*` findings. |

### Findings

#### P1

- N/A

#### P2

- N/A

#### Observations

- N/A

### Evidence Boundaries

- Manual acceptance A-1, A-2, and A-3 remains human-owned and pending.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-1: batch-page `app.js` diff wiring | Authorized batch configuration UI scope | RECORD_ONLY | Authorized file; makes existing modified badge functional and adds no filter logic. |
| O-2: Flyway empty-database Docker integration did not run | Master requires full Maven suite, not this Docker-only check | RECORD_ONLY | Full Maven suite passed; V108 parsed during startup. |
| D-1: appended default constructor dependencies | Promotion gate behavior and production injection | RECORD_ONLY | Production uses actual Spring dependencies; defaults preserve legacy positional test construction. |

Repair planning: N/A. The reviewer modified no product code, tests, plans, index, HEAD, or branch; it did not write this report, stage, or commit.
