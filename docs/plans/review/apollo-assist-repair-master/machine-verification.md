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
