# Aggregate Machine Verification — discovery-review-master

## Epoch 1 — 2026-10-05T21:16:34+0800

- Master plan: docs/plans/2026-10-04/discovery-review-master.md (sha256 a25228028b6d2a6190ebeb65e8dc15ad914e3c9a5208bef2cf55c5173f488888)
- Governing master identity: sha256 a25228028b6d2a6190ebeb65e8dc15ad914e3c9a5208bef2cf55c5173f488888; recorded commit 60d97bbe1425c429b4e6e66409a5585fcd08b3f7
- Master identity state: AMENDMENT_RECORDED. A8: `主计划实现方案 06 文件数上限`; `同步 A7 文件上限 6→10`; `HUMAN:2026-10-05 用户“批准 继续”`. Retroactively authorized A7 files: `DiscoveryReview.kt`, `DiscoveryReviewScanService.kt`, `DiscoveryReviewAllPagesTest.kt`, and `DiscoveryReviewServiceTest.kt`; audit found their changes follow the approved initialized/UNINITIALIZED rule.
- Boundary: e28e53fd898edd62905a0d45a6bf90396b18b1bf..ead644fbff77036a09302e91acb86ef092941c19
- Reviewer: /root/aggregate_reviewer
- Result: FAIL
- Convergence: INITIAL
- Repair artifact/result: docs/plans/fix/discovery-review-master/repair.md — DRAFT_READY

### Fresh Command Evidence

| Command | Result | Evidence |
|---|---|---|
| JDK11 `mvn test` | BASELINE_FAIL | Exit 1; 4655 tests, 0 failures, 19 errors, 13 skipped. All 19 are the pre-existing `ExpertContactLocationServiceTest` Chile timezone-catalog error. |
| `node --test src/test/js/*.test.js` | PASS | Exit 0; 1461 pass, 0 fail; 278 suites. |
| Isolated MySQL CAS `-DmysqlIt=true -Dtest=DiscoveryReviewRepositoryIT test` | PASS | Exit 0; 13 tests, 0 failure/error; database `talent_introduction_fastp`. |
| `git diff --check` over boundary | PASS_WITH_NOTES | Product/test diff clean; warnings only in committed documentation evidence whitespace. |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| I-1 — 准入分离 | PASS | `DiscoveryAdmissionPolicy.kt`; `DiscoveryReviewService.kt` persistent admission paths. |
| I-2 — 显式过滤 | PASS | `BatchRecipientSelectionService.kt:91-148` uses declared scope fields and admission result only. |
| I-3 — 事实原因/持久明细 | PASS | `DiscoveryReviewRepositoryIT` 13/13; item CAS/state aggregates. |
| I-4 — 全页快照、恢复、显式重试 | FAIL | V-2. |
| I-5 — MySQL 权威 | PASS | Isolated MySQL CAS suite green. |
| I-6 — preview/execute/retry parity | FAIL | V-1; V-2 user-visible phase parity. |
| I-7 — D1 发布边界 | N/A | D1 remains human policy/release gate; no cutover claimed. |
| Authorized cumulative scope | PASS | Complete boundary inspected; product/test changes are within approved child/amendment scope. |
| Manual A-1…A-5 | N/A | Deferred human acceptance. |

### Findings

| ID | Severity | State | Evidence |
|---|---:|---|---|
| V-1 | P1 | NEW | `ManualInitialOutreachService.kt:670-705,1870-1901` previews/totals each ES page/level and retries independently; `OutreachTargetIterator.kt:48-53` globally deduplicates execution. Existing `ManualInitialOutreachServiceTest.kt:979-1044` explicitly asserts preview total 2 versus sent 1 for duplicates. Violates I-6/child-05 I-3. |
| V-2 | P1 | NEW (promoted child-03 O-2) | `DiscoveryReviewService.kt:1239-1243` maps an interrupted apply task with pending items and no summary to `APPLIED`; `app.js:2486-2492,3742-3779` displays completion and refreshes success. Violates I-4/I-6, child-03 I-3, child-06 I-1. |

### Evidence Boundaries

- Manual acceptance A-1…A-5 remains pending.
- No local live ES endpoint; isolated MySQL CAS plus deterministic/mock ES scan evidence was used.
- No live SMTP end-to-end execution.
- Same-request-key asynchronous-persistence timing is adjacent risk only; no approved concurrent-prepare acceptance requirement proves a repairable violation.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 O-1 reason-code drift | I-3 事实原因词汇 | PASS; risk retained | `DiscoveryAdmissionModels.kt:100-114` matches `CandidateEligibilityService.kt:27-60` for 13 reject codes; future codes map to `UNKNOWN`. |
| 02 O-1 migration target 147 | Authorized scope/migration compatibility | Observation retained | `FlywayMigrationIntegrationTest.kt` still has 27 assertions for 147; V148 exists; the opt-in test is outside this review's required commands/scope. |
| 03 O-1 shared DB drift | I-5 MySQL authority/isolated CAS | External environment; not blocking | Shared DB lacks 148 and carries 149; isolated `talent_introduction_fastp` `DiscoveryReviewRepositoryIT` is 13/13 green. |
| 03 O-2 interrupted phase | I-4/I-6; 03 I-3; 06 I-1 | FAIL; promoted to V-2/P1 | `DiscoveryReviewService.kt:1239-1243`; `app.js:2486-2492,3742-3779`. |
| 04 O-1 manual allowedMap projection | I-1 admission/manual approval | PASS; manual acceptance focus | Approved hard-guard removal; `ExpertDiscoveryService.kt:3123,3138` retains real author binding through `DiscoveryIdentity.allowed`. |
| 05 epoch1 O-1 preview template seam | I-6 parity | RESOLVED | `batchTemplateGate` calls `evaluateForBatch` for preview/run/retry in `ManualInitialOutreachService.kt:601-608,1891`. |
| 05 epoch1 O-2 preview DTO gaps | I-6; 06 downstream interface | RESOLVED | `PendingOutreachSummary` includes template, admission, reasonHits, excludedRecipients. |
| 05 epoch1 O-3 material placeholder classification | Template error visibility | Observation retained | `ManualInitialOutreachService.kt:397-400` retains pre-existing generic `SEND_EXCEPTION`; no top-level master-contract violation proven. |
| 05 epoch2 O-4 AllPages regression | I-4 persistent state/CAS | RESOLVED | A5 records `CANDIDATE_SYNC_FAILED` while preserving `APPLIED`; fresh full Maven has no such failure. |
| 05 O-5/O-6 timezone | Required Maven evidence | Baseline observation | Fresh Maven: 4655 tests, 0F, 19E; all ancestor `ExpertContactLocationServiceTest` `America/Coyhaique` errors. |
| 06 O-1 D1 undecided | I-7 release boundary | PENDING/N/A | Fast ledger keeps D1 undecided; no historical resend, unsubscribe, rebind, or production-cutover authority. |
| 06 O-2 bounded smoke | Manual A-1…A-5 | PENDING | Chromium mock smoke is not real-DB whole-system acceptance. |
| 06 O-3 governance evidence | Authorized scope | PASS | Plans/reports are controller evidence; product/test boundary audit found no extra product scope. |

### Review-P Routing

`FAIL` with `INITIAL` convergence produced one bounded repair plan. It authorizes no implementation absent explicit human `$execute-p` approval. No product code was modified during aggregate review.
