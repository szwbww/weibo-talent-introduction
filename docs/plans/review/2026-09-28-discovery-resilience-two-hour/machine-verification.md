# Aggregate Machine Verification — discovery-resilience-two-hour

## Epoch 1 — 2026-09-28T03:17:33Z

- Master plan: `docs/plans/2026-09-28/discovery-resilience-two-hour.md` (commit `3f167a2820c3f9bdb344f19da2123d6a9ff2f12a`)
- Governing master identity: sha256 `c94fd0e4a8aaaa54ecf1727d8622812666af1df65a64fbde4635cdacf41db88b`; recorded commit `3f167a2820c3f9bdb344f19da2123d6a9ff2f12a`
- Master identity state: CONSISTENT
- Boundary: `f98e27c7538d091bfcdecfcb6ffc10360a35ba04..8700a605427aaedb4c31be63a72e22a657b94208`
- Reviewer: `/root/aggregate_reviewer`
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: `docs/plans/fix/discovery-resilience-two-hour/repair.md` — DRAFT_READY

## Verification Result: FAIL

Plan: `docs/plans/2026-09-28/discovery-resilience-two-hour.md`

Implementation boundary: `f98e27c7538d091bfcdecfcb6ffc10360a35ba04..8700a605427aaedb4c31be63a72e22a657b94208`; product implementation subset: `3f167a2820c3f9bdb344f19da2123d6a9ff2f12a..8700a605427aaedb4c31be63a72e22a657b94208`.

Evidence HEAD: `6f39736c55fb489782fe29246802ca052952c4a4`.

Convergence: INITIAL

Manual acceptance: PENDING (A-1..A-8)

### Commands

| Command | Result | Evidence |
|---|---|---|
| Exact 11-class `mvn test -Dtest=...` | PASS | exit 0; BUILD SUCCESS; 354 tests, 0 failures/errors/skips |
| Exact `mvn clean package` | PASS | exit 0; BUILD SUCCESS; 4217 tests, 0 failures, 0 errors, 13 skipped; Node 1199 pass, 0 fail |
| `git diff --check` | PASS | exit 0 |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| I-1 | PASS | `ExpertDiscoveryService.kt:779-806,829-859,1216-1232`; bounded 3+3×3 recovery; properties validation. |
| I-2 | PASS | `ExpertDiscoveryService.kt:668-720,946-955`; exact resume cursor and checkpoint delta. |
| I-3 | PASS | `ExpertDiscoveryService.kt:699-703,714-720,1018-1023`; accumulated counters, quota, elapsed time. |
| I-4 | PASS | `ExpertDiscoveryService.kt:801,852,821,859,1023-1024`; retry wait is nonterminal and terminal failure recorded once. |
| I-5 | FAIL | `ExpertDiscoveryService.kt:1026-1033` erases `SourceStats.retry` on completed/exhausted/deferred recovery, losing required audit round/reason. Tests explicitly assert the prohibited null at `ExpertDiscoveryServiceTest.kt:4119,4171,4443`. |
| I-6 | PASS | `ExpertDiscoveryScheduler.kt:72-90`; Asia/Shanghai cron, removed daily gate, retained shared run slot. |
| I-7 | PASS | `ExpertDiscoveryService.kt:1074-1141,1160-1173`; ≤100ms cancel/deadline checks and no subsequent recovery request. |
| I-8 | PASS | `ExpertDiscoveryService.kt:766-778,2410-2435`; budget/429 bypass recovery; certificate/protocol errors remain excluded. |
| X-1 | PASS | I-2 cursor/recovery path and checkpoint delta evidence above. |
| X-2 | PASS | `ExpertDiscoveryService.kt:934-1003`; consumer failures retain entry and do not create recovery. |
| X-3 | PASS | `ExpertDiscoveryService.kt:294-300,332-366,1180-1195`; retry info traverses progress details and summary. |
| X-4 | PASS | `ExpertDiscoveryScheduler.kt:76-104`; slot, execution binding, finally cleanup retained. |
| X-5 | PASS | I-8 path retains `OpenAlexDataSource`/request-policy route; no budget code changed. |
| X-6 | PASS | Scheduler pipeline branch remains separate; focused pipeline/scheduler tests pass. |
| Scope/non-goals | PASS | `3f167a2..8700a60` changes exactly eight authorized product/test files; no prohibited product path changed. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | NEW | I-5 mandatory audit-retention violation at `ExpertDiscoveryService.kt:1026-1033`. |

### Findings

#### P1

- V-1: I-5 requires clearing only `nextRetryAt` on recovery start, cancellation, timeout, and end, retaining round/reason for audit. The implementation replaces the entire retry object with null on recovery success, terminal exhaustion, and budget deferral. Smallest scope: `ExpertDiscoveryService.kt` plus its regression tests.

#### P2

- N/A

#### Observations

- O-1 → promoted to V-1/I-5.
- O-2 → I-6-related, nonblocking: test-profile cron remains daily but discovery is disabled and this file is outside approved scope.
- O-3 → I-5-related, nonblocking: reason-label/running-summary wording is not uniquely mandated.
- O-4 → T-4-related, nonblocking: historical red-test form does not prove a runtime defect.
- O-5 → I-3-related, nonblocking: elapsed-time uses system time while deadline uses injected time; accumulated elapsed behavior is proven, but test seam is imperfect.

### Evidence Boundaries

- Manual A-1..A-8 remain PENDING.
- No amendments. One child only; no cross-child handoff gap.
- Post-repair metadata: N/A.

### Next Action

- FAIL + INITIAL: repair plan is ready for human approval.

## Repair Planning Result: DRAFT_READY

Baseline plan: `docs/plans/2026-09-28/discovery-resilience-two-hour.md`

Verification result: FAIL / INITIAL

Repair artifact: `docs/plans/fix/discovery-resilience-two-hour/repair.md`

### Included Findings

- V-1

### Excluded Findings

- O-2..O-5: nonblocking observations; no confirmed mandatory violation.

### Required Human Decision

- Approve the repair plan.

No implementation was performed. No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-1 | I-5 | Promoted to V-1 | `ExpertDiscoveryService.kt:1026-1033`; `ExpertDiscoveryServiceTest.kt:4119,4171,4443` |
| O-2 | I-6 | Nonblocking observation | `src/test/resources/application.yml:86`; profile disabled; outside authorized scope |
| O-3 | I-5 | Nonblocking observation | reason-label/running-summary wording not uniquely required |
| O-4 | T-4 | Nonblocking observation | historical red-test form; no delivered runtime defect |
| O-5 | I-3 | Nonblocking observation | accumulated elapsed behavior proven; clock seam imperfect |
