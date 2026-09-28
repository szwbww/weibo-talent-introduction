# Aggregate Machine Verification — discovered-institution-repair-00-master

## Epoch 1 — 2026-09-28T21:00:00+08:00

- Master plan: docs/plans/2026-09-28/discovered-institution-repair-00-master.md (sha256 2f7fdb23cdfa016795827416d9d43f73d19798457a9c32c20605ea3a42d404c6)
- Governing master identity: worktree sha256 2f7fdb23cdfa016795827416d9d43f73d19798457a9c32c20605ea3a42d404c6; recorded commit 2e9639df7947bc5f3057ca08e1445b155aba7cd7
- Master identity state: CONSISTENT; A1 and A2 are recorded child-plan amendments, not a governing-master amendment.
- Boundary: d90084841d400e75eb0f2b6c4c6726e54307260a..70e6144065335beee72dbd22a84e4bb975a68928
- Reviewer: /root/aggregate_reviewer
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: docs/plans/fix/discovered-institution-repair-00-master/repair.md — DRAFT_READY

## Verification Result: FAIL

Plan: `docs/plans/2026-09-28/discovered-institution-repair-00-master.md`

Implementation boundary: `d90084841d400e75eb0f2b6c4c6726e54307260a..70e6144065335beee72dbd22a84e4bb975a68928`; evidence HEAD `f455045ef153b047baa5c60b0a4cbd2a691c8019`

Convergence: INITIAL

Manual acceptance: PENDING

### Commands

| Command | Result | Evidence |
|---|---|---|
| 01 targeted Maven | PASS | exit 0; 309 tests / 0 failures / 0 errors / 0 skipped; Node 1202 / 0 |
| 01b targeted Maven | PASS | exit 0; 187 tests / 0 failures / 0 errors / 0 skipped; Node 1202 / 0 |
| 02 targeted Maven | PASS | exit 0; 246 tests / 0 failures / 0 errors / 0 skipped; Node 1202 / 0 |
| 03 targeted Maven | PASS | exit 0; 262 tests / 0 failures / 0 errors / 0 skipped; Node 1202 / 0 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0; 4262 tests / 0 failures / 0 errors / 13 skipped; Node 1202 / 0 |
| `git diff --check` | PASS | exit 0 |
| `git diff --check d90084841d400e75eb0f2b6c4c6726e54307260a..70e6144065335beee72dbd22a84e4bb975a68928` | P2 | exit 2; evidence-only trailing whitespace at `children/01/execution.md:39` |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| Master outcome | PASS | Source-bound institution facts; evidence-gated discovery outreach. |
| Master I-1 | PASS | `JatsXmlEmailParser.kt:59`; `OpenAlexDataSource.kt:284`; `ExpertDiscoveryService.kt:2256`. |
| Master I-2 | PASS | `BatchExecutionModels.kt:209`; `ManualInitialOutreachService.kt:697`; `InitialOutreachService.kt:61`. |
| Master I-3 | FAIL | `ExpertDiscoveryService.kt:3008` writes root `updatedAt` during academic enrichment. |
| 01 I-1/I-2/I-3 | PASS | Conservative JATS/OpenAlex extraction and no enrichment type overwrite. |
| 01b I-1/I-2 | PASS | `OrcidDataSource.kt:168`; `ExpertDiscoveryService.kt:1515`. |
| 02 I-1/I-2/I-3 | PASS | Three mappings; signed token validation; search projection. |
| 03 I-1/I-2/I-3 | PASS | Shared final predicate, country gate, pagination, pre-send guard. |
| Authorized scope | PASS | Product/test changes match child plans plus approved A1/A2 files. |
| Non-goals | PASS | No history mutation, mapping deployment, or outreach-template change. |
| Manual A-1/A-2/A-3 | PENDING | Requires deployed test environment. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | `ExpertDiscoveryService.kt:3008` violates master I-3. |

### Findings

#### P1

- V-1: Academic enrichment sends `updatedAt` in its partial-update document, violating mandatory I-3. Smallest scope: `ExpertDiscoveryService.kt` and a regression test.

#### P2

- RECORD-1 persistent: stale `I1-4` comment at `ExpertDiscoveryService.kt:3024`.
- RECORD-3 persistent: dead `OrcidRecord.country`.
- RECORD-4 persistent: committed evidence whitespace only.
- RECORD-5 persistent: no positive production-path OPENALEX token issuance test.
- RECORD-10 corrected: full suite reports `ManualInitialOutreachServiceTest` 187, not handoff's 182.

#### Observations

- RECORD-2 backlog counter remains; authorized behavior unchanged.
- RECORD-6 resolved: stale 02 fix-log placeholder corrected.
- RECORD-7 additive reason code remains non-breaking.
- RECORD-8 cancelled prescan total deviation remains disclosed.
- RECORD-9 A2 test seam stubs remain within approved scope.
- RECORD-11 cross-package predicate reuse remains non-breaking.

### Evidence Boundaries

- No live ES mapping deployment or manual acceptance performed.
- No product code was modified.

## Repair Planning Result: DRAFT_READY

Baseline plan: `docs/plans/2026-09-28/discovered-institution-repair-00-master.md`

Verification result: FAIL / INITIAL

Repair artifact: `docs/plans/fix/discovered-institution-repair-00-master/repair.md`

### Included Findings

- V-1.

### Excluded Findings

- RECORD_ONLY items: no repair authority.

### Required Human Decision

- Approve the repair artifact.

No implementation was performed.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Stale `I1-4` comment | I-3 | P2 | `ExpertDiscoveryService.kt:3024`; no behavior claim. |
| `institutionTypePending` backlog | I-3 | Observation | Counter behavior is outside authorized scope. |
| Dead `OrcidRecord.country` | I-1 | P2 | Producer writes null; no source reader. |
| Evidence trailing whitespace | N/A | P2 | `children/01/execution.md:39`; not product behavior. |
| No positive OPENALEX issuance test | I-1 | P2 | Production path audited; test depth is not a mandatory acceptance item. |
| Stale 02 fix-log placeholder | N/A | Resolved | Corrected before evidence commit. |
| `DISCOVERY_EVIDENCE_MISSING` reason | I-2 | Observation | Additive and non-breaking. |
| Cancelled prescan total deviation | I-2 | Observation | Disclosed; final status/cancellation behavior unchanged. |
| A2 seam stubs | A2 authorized scope | Observation | Test-only, inside approved A2 scope. |
| Manual service count note | N/A | Corrected | Fresh full suite establishes 187. |
| Cross-package predicate reuse | I-2 | Observation | Single module; no duplicate predicate behavior. |
