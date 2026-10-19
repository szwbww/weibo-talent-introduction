# Aggregate Machine Verification — Apollo Assist Repair

## Epoch 1 — 2026-10-19T14:41:12Z

- Master plan: docs/plans/2026-10-19/apollo-assist-repair-master.md (sha256 ecf15cc88cecdf2c3e019285d079935fce8c8ec2492cc88acd5fa60bfd06145d)
- Governing master identity: worktree/recorded/invoked sha256 ecf15cc88cecdf2c3e019285d079935fce8c8ec2492cc88acd5fa60bfd06145d; CONSISTENT; amendments N/A.
- Boundary: e33b3cdcd228a6692c7aaeab047e5bafd0e7bd3f..fe5e8172cfd6d0c7fddb84266f9ceefafb5652b0
- Evidence head: 3f51b54dcff3f165cd85196f30c29ed7f165275e
- Reviewer: /root/aggregate_reviewer_retry
- Result: BLOCKED
- Convergence: BLOCKED
- Repair artifact/result: N/A; `repair-p` did not run.

## Verification Result: BLOCKED

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master/docs/plans/2026-10-19/apollo-assist-repair-master.md`

Implementation boundary: `e33b3cdcd228a6692c7aaeab047e5bafd0e7bd3f..fe5e8172cfd6d0c7fddb84266f9ceefafb5652b0`

Manual acceptance: PENDING

### Commands

| Command | Result | Evidence |
|---|---|---|
| `env JAVA_HOME=/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-baseline-x8k1v_ua/jdk-11.0.32.1+1/Contents/Home mvn -Dapi.version=1.40 -Dtest=QualificationEvidenceParserTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationAdmissionGateTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` | PASS | exit 0; 49 JVM; 0 failures/errors/skips; JS 1,724 pass/0 fail/0 skipped; BUILD SUCCESS; 2:45. |
| `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-baseline-x8k1v_ua/jdk-11.0.32.1+1/Contents/Home mvn -Dapi.version=1.40 -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test` | PASS | exit 0; 36 JVM; 0 failures/errors/skips; JS 1,724 pass/0 fail/0 skipped; BUILD SUCCESS; 4:39; Docker/MySQL exercised. |
| `bash -n scripts/expert_discovery/replay_verification.sh` | PASS | exit 0. |
| `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-baseline-x8k1v_ua/jdk-11.0.32.1+1/Contents/Home mvn -Dapi.version=1.40 -Dtest=DiscoveryVerificationReplayToolTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationFlowIT,DiscoveryVerificationReportingIT,DiscoveryVerificationAdmissionGateTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` | BLOCKED | Not freshly run in aggregate epoch 1; child report is unverified under `verify-p`; unique `DiscoveryVerificationReplayToolTest` coverage lacks fresh evidence. |
| `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-baseline-x8k1v_ua/jdk-11.0.32.1+1/Contents/Home mvn -Dapi.version=1.40 test` | BLOCKED | Not freshly run in aggregate epoch 1. |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 | PASS | APOLLO_UNCLEAR/UNKNOWN preserved: `DiscoveryVerificationRules.kt:113-122`; fresh focused and FlowIT groups pass. |
| M-2 | PASS | Strict public parsing and SELF identity without qualification: `QualificationEvidenceParser.kt:12-105`; `DiscoveryVerificationRules.kt:113-122`; fresh 49-test group passes. |
| M-3 | PASS | Cached recovery, frozen query cursor, budget/limit reasons: `DiscoveryVerificationWorker.kt:149-285`; fresh focused and FlowIT groups pass. |
| M-4 | BLOCKED | Queue-only historical replay, CAS/protection, Commit chain: `DiscoveryVerificationRepository.kt:42-101`; `DiscoveryVerificationReplayTool.kt:179-211`; required fresh replay-tool group unavailable. |
| M-5 | PASS | Retention/config/schema/ES/frontend unchanged: exact product diff is approved 14 `src`/`scripts` files; no config, DDL, ES, Gate, Commit, frontend, Sample Store/Capture change; fresh sample tests pass. |
| M-6 | BLOCKED | Three-layer replay reporting/fixed input/no efficacy claim: `DiscoveryVerificationReplayTool.kt:215-235`; fresh `DiscoveryVerificationReplayToolTest` and full-suite evidence unavailable. |
| M-7 | PASS | Approved scope: `git diff --check e33b3cdcd228a6692c7aaeab047e5bafd0e7bd3f..fe5e8172cfd6d0c7fddb84266f9ceefafb5652b0 -- src scripts` exits 0; exact approved 14-file union. |
| M-8 | BLOCKED | Full mandatory acceptance requires the exact child-02 focused and full Maven commands freshly in the aggregate epoch. |
| A-1..A-3 / child A-1..A-6 | PENDING | Production freeze/apply/new-run/UI acceptance intentionally deferred; not machine-release blockers. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW — Observation | Parser guard is fail-closed only when retained body contains `orcid-identifier.path` or `path`; client may omit both. `QualificationEvidenceParser.kt:86-101`; callers use item-owned cache; no demonstrated production defect. |
| V-2 | NEW — Observation | Missing-item WRONG_RUN receipt branch unreachable because `lockedItem(...).single()` precedes it. `DiscoveryVerificationRepository.kt:47,68`; no item-delete production path. |
| V-3 | NEW — Observation | Atomic file writes fsync hash directory but not new parent-directory entries. `DiscoveryVerificationReplayTool.kt:120-155`; no observed durability failure. |
| V-4 | NEW — Observation | `inspect` silently emits empty rule-pass list without evaluate report. `DiscoveryVerificationReplayTool.kt:198-210`; durable-row recovery works. |
| V-5 | NEW — Observation | Whole-boundary `git diff --check` flags only blank EOF lines in child evidence Markdown; `src`/`scripts` check passes. |

### Findings

#### P1

- N/A.

#### P2

- N/A.

#### Observations

- V-1 through V-5. No RECORD_ONLY observation proves a mandatory violation or creates repair authority.

### Evidence Boundaries

- Required fresh evidence is unavailable for the exact child-02 focused command and full Maven command.
- Historical raw 89-item input, live replay/apply, external new run, and production retention/UI checks remain unavailable or unauthorized.

### Next Action

- Obtain the two named fresh command results, then dispatch a fresh aggregate `review-p` re-review.

No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-1 (child 01): client may omit parser guard fields | I-1 / X-1 | RECORD_ONLY | Current callers use the item-owned cache; no production violation demonstrated. |
| O-1 (child 02): missing-item receipt branch | X-4 | RECORD_ONLY | No production item-deletion path found; `lockedItem(...).single()` is before receipt handling. |
| O-2 (child 02): parent-directory fsync | I-2 | RECORD_ONLY | No observed crash-durability failure. |
| O-3 (child 02): inspect without evaluate report | I-3 | RECORD_ONLY | Durable rows remain recoverable; missing list marker is not a plan violation. |
| O-4 (child 02): 101 repository batch | X-4 | RECORD_ONLY | Direct range guard and tool-level 101 rejection were evidenced by child reports; fresh group for this path is unavailable, so it is not promoted without contrary proof. |

## Epoch 2 — 2026-10-19T15:06:07Z

- Master plan: docs/plans/2026-10-19/apollo-assist-repair-master.md (sha256 ecf15cc88cecdf2c3e019285d079935fce8c8ec2492cc88acd5fa60bfd06145d)
- Governing master identity: worktree/recorded/invoked sha256 ecf15cc88cecdf2c3e019285d079935fce8c8ec2492cc88acd5fa60bfd06145d; CONSISTENT; amendments N/A.
- Boundary: e33b3cdcd228a6692c7aaeab047e5bafd0e7bd3f..fe5e8172cfd6d0c7fddb84266f9ceefafb5652b0
- Evidence head: 5aaf93e515b018d52ecad72e5c6a57e1a5380416
- Reviewer: /root/aggregate_reviewer_epoch2
- Result: PASS
- Convergence: PROGRESSING — epoch-1 missing fresh evidence resolved.
- Repair artifact/result: N/A; `repair-p` entry condition false.

## Verification Result: PASS

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master/docs/plans/2026-10-19/apollo-assist-repair-master.md`

Implementation boundary: `e33b3cdcd228a6692c7aaeab047e5bafd0e7bd3f..fe5e8172cfd6d0c7fddb84266f9ceefafb5652b0`

Manual acceptance: PENDING

### Commands

All Maven commands ran from `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master` with `JAVA_HOME=/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-baseline-x8k1v_ua/jdk-11.0.32.1+1/Contents/Home`, `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` where Docker/MySQL was required, `-Dapi.version=1.40`, and Docker server 29.8.2.

| Command | Result | Evidence |
|---|---|---|
| `mvn -Dapi.version=1.40 -Dtest=QualificationEvidenceParserTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationAdmissionGateTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` | PASS | exit 0; 49 JVM, 0 failures/errors/skips; JS 1,724 pass/0 fail/0 skipped; 2:53. |
| `mvn -Dapi.version=1.40 -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test` | PASS | exit 0; 43 JVM, 0 failures/errors/skips; Docker/MySQL exercised; 4:43. |
| `mvn -Dapi.version=1.40 -Dtest=DiscoveryVerificationReplayToolTest,DiscoveryVerificationRepositoryIT,DiscoveryVerificationFlowIT,DiscoveryVerificationReportingIT,DiscoveryVerificationAdmissionGateTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test` | PASS | exit 0; 69 JVM, 0 failures/errors/skips; JS 1,724 pass/0 fail/0 skipped; 5:12. |
| `bash -n scripts/expert_discovery/replay_verification.sh` | PASS | exit 0. |
| `env DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/apollo-replay-baseline-x8k1v_ua/jdk-11.0.32.1+1/Contents/Home mvn -Dapi.version=1.40 test` | PASS | exit 0; 5,225 JVM, 0 failures, 0 errors, 21 skipped; JS 1,724 pass/0 fail/0 skipped; BUILD SUCCESS; 5:57. |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| M-1 | PASS | APOLLO_UNCLEAR/UNKNOWN admission preserved; SELF employment supports identity only. `DiscoveryVerificationRules.kt:75-120`; fresh FlowIT. |
| M-2 | PASS | Strict textual PUBLIC parsing; cached ORCID rebuild removes rejected stale facts. `QualificationEvidenceParser.kt:85-105`; `DiscoveryVerificationWorker.kt:149-183`. |
| M-3 | PASS | Persisted Apollo queries preserve in-flight cursor; aliases/narrow-query bounds; limit reasons emitted. `DiscoveryVerificationWorker.kt:185-227`. |
| M-4 | PASS | Queue-only replay, settled-run/CAS/protection checks, atomic bounded batch. `DiscoveryVerificationRepository.kt:40-95`; `DiscoveryVerificationReplayTool.kt:104-214`; fresh replay group. |
| M-5 | PASS | Retention/config/schema/ES/frontend unchanged. Exact approved 14-file `src`/`scripts` union; scoped `git diff --check` clean. |
| M-6 | PASS | Replay reporting separates pure rules, actual commit/projection, and new-run observation. `DiscoveryVerificationReplayTool.kt:188-235`. |
| M-7 | PASS | Approved scope only; wrapper has JDK/classpath guard and no build/Spring startup. |
| M-8 | PASS | Every master-required aggregate command freshly passed. |
| A-1..A-3 / child A-1..A-6 | PENDING | Deferred production freeze/apply/new-run/UI acceptance; not a machine blocker. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RECORD_ONLY | Parser optional body-path guards remain fail-closed; no demonstrated defect. |
| V-2 | RECORD_ONLY | `WRONG_RUN` missing-item receipt branch remains unreachable after `lockedItem`; no deletion path. |
| V-3 | RECORD_ONLY | Parent-directory-entry durability remains an unobserved crash-durability lead. |
| V-4 | RECORD_ONLY | `inspect` without evaluate report emits an empty suggested list; durable rows remain recoverable. |
| V-5 | RECORD_ONLY | Whole-boundary whitespace flags only child evidence Markdown EOF blanks; `src`/`scripts` clean. |

### Findings

#### P1

- N/A.

#### P2

- N/A.

#### Observations

- V-1 through V-5 only. Child RECORD_ONLY O-4 (101-item replay bound) had fresh replay-group PASS; no observation was promoted.

### Evidence Boundaries

- Historical raw 89-item input, live replay/apply, external new run, and production retention/UI checks remain manual/unauthorized.

### Next Action

- Complete human manual acceptance A-1 through A-3 and sign off the reviewed boundary.

No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-1 (child 01): optional parser guard fields | I-1 / X-1 | RECORD_ONLY | Item-owned cached docs are current callers; no production violation demonstrated. |
| O-1 (child 02): missing-item receipt branch | X-4 | RECORD_ONLY | No production item-delete path; `lockedItem(...).single()` is before receipt handling. |
| O-2 (child 02): parent-directory fsync | I-2 | RECORD_ONLY | No observed crash-durability failure. |
| O-3 (child 02): inspect without evaluate report | I-3 | RECORD_ONLY | Durable rows remain recoverable. |
| O-4 (child 02): 101 repository batch | X-4 | RECORD_ONLY | Fresh replay group passed; no mandatory violation proved. |
