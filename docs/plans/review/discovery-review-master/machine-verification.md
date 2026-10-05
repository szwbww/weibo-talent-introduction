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

## Epoch 2 — 2026-10-05T23:26:53+0800

- Master plan: docs/plans/2026-10-04/discovery-review-master.md (sha256 a25228028b6d2a6190ebeb65e8dc15ad914e3c9a5208bef2cf55c5173f488888)
- Governing master identity: sha256 a25228028b6d2a6190ebeb65e8dc15ad914e3c9a5208bef2cf55c5173f488888; recorded commit 60d97bbe1425c429b4e6e66409a5585fcd08b3f7
- Master identity state: AMENDMENT_RECORDED. A8: `主计划实现方案 06 文件数上限`; `同步 A7 文件上限 6→10`; `HUMAN:2026-10-05 用户“批准 继续”`. Retroactively authorized A7 files: `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryReview.kt`, `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewScanService.kt`, `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt`, and `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewServiceTest.kt`. (The previously supplied `discovery/api/DiscoveryReview.kt` path does not exist; the A7 file is in `discovery/domain/`.)
- Boundary: e28e53fd898edd62905a0d45a6bf90396b18b1bf..6b5c201c7a8667376cb214f74a7069594cc9a6cc
- Reviewer: /root/aggregate_reviewer_epoch2 (created after the reviewed repair commit; distinct from recorded writers, lightweight verifiers, and repair executor)
- Result: FAIL
- Convergence: PROGRESSING
- Repair artifact/result: docs/plans/fix/discovery-review-master/repair.md — DRAFT_READY

### Fresh Command Evidence

| Command | Result | Evidence |
|---|---|---|
| JDK11 `mvn -Dtest=ManualInitialOutreachServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewServiceTest test` | PASS | Exit 0; 222 tests (ManualInitialOutreach 180, AllPages 15, ReviewService 27); 0 failure/error. |
| `node --test src/test/js/*.test.js` | PASS | Exit 0; 1462 pass, 0 fail, 278 suites. |
| Isolated MySQL `-DmysqlIt=true -Dtest=DiscoveryReviewRepositoryIT test` | PASS | Exit 0; 13 tests, 0 failure/error; database `talent_introduction_fastp`. |
| JDK11 `mvn test` | BASELINE_FAIL | Exit 1; 284 XML reports, 4674 tests, 0 failures, 19 errors. All errors are ancestor `ExpertContactLocationServiceTest` `America/Coyhaique` timezone-catalog failures. |
| `git diff --check e28e53f..6b5c201` | PASS_WITH_NOTES | Product/test diff clean; only already-committed documentation evidence has trailing whitespace. |
| `curl -I http://127.0.0.1:9200` | BLOCKED | Exit 7; no local ES endpoint. Test configuration's ES URL is remote. |

### Master Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| I-1 — 准入双通道 | PASS | `DiscoveryAdmissionPolicy.kt:49-81`; `DiscoveryReviewService.kt:780-808` retains same-identity manual results. |
| I-2 — 显式条件分离 | PASS | `BatchRecipientSelectionService.kt:91-148` and send paths consume admission result plus declared scope fields. |
| I-3 — 事实原因 | PASS | Selector reason keys and isolated MySQL repository IT 13/13. |
| I-4 — 固定快照/CAS/显式重试 | BLOCKED | V-2 is resolved at `DiscoveryReviewService.kt:1231-1244` and `app.js:3644-3646,3775-3780`; no isolated real ES/index CAS evidence exists, only mock `RestTemplate` 10005-item fixture. |
| I-5 — MySQL 权威 | PASS | Repository CAS/version locking; isolated MySQL IT 13/13. |
| I-6 — preview/执行/重试同源 | FAIL | V-3. |
| I-7 — D1 | N/A/PENDING | D1 remains a human policy/release gate; no historical resend, unsubscribe, rebind, or cutover claim. |
| Authorized cumulative scope | PASS | `e28e53f..6b5c201` has 44 `src/**` files mapped to 01–06/A1/A3/A5/A7/A8; `ead644f..6b5c201` has exactly the six prior-repair-authorized product/test files. |
| Manual A-1…A-5 | PENDING | Not performed. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| V-1 | RESOLVED | `ManualInitialOutreachService.kt:559-572,674-703,1872-1907` shares the retry/ORCID set; focused command passed. |
| V-2 | RESOLVED | `DiscoveryReviewService.kt:1242` returns `INTERRUPTED`; UI does not complete or auto-continue. |
| V-3 | NEW P1 | `BatchRecipientSelectionService.kt:94-99` deduplicates by docId, but `ManualInitialOutreachService.kt:1897,1902` deduplicates by ORCID then maps one included docId back to all original profiles. `OutreachTargetIterator.kt:48-54` likewise only deduplicates ORCIDs. Different ORCIDs with one real `esDocId` become two targets. |

V-3 can duplicate preview, execution, cross-level/page, and NEW-retry targets. Smallest implicated scope: `ManualInitialOutreachService.kt` and `ManualInitialOutreachServiceTest.kt`.

### Evidence Boundaries

- No local live ES endpoint or isolated real index/CAS evidence; mock `RestTemplate` 10005-scroll evidence is not a substitute.
- No live SMTP; master prohibits production expert test sends.
- No review ledger from a non-selected worktree was used.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| 01 O-1 reason-code snapshot | I-3 | Observation retained | Current 13 code mappings agree. |
| 02 O-1 / 03 pause Flyway 147 pin | Migration compatibility | Observation retained | Unauthorized opt-in test; not part of this repair. |
| 03 O-1 shared DB drift | I-5 | External environment | Isolated `fastp` IT passes. |
| 03 O-2 interrupted phase | I-4/I-6 | RESOLVED | Prior V-2 now resolved. |
| 04 O-1 manual allowedMap | I-1 | PASS | Authorized review path; real author binding remains. |
| 05 O-1 template seam | I-6 | RESOLVED | Shared template gate remains. |
| 05 O-2 preview DTO | I-6 | RESOLVED | DTO fields remain present. |
| 05 O-3 placeholder `SEND_EXCEPTION` | Template errors | Observation retained | No master violation proven. |
| 05 O-4 all-pages regression | I-4 | RESOLVED | Prior regression remains green. |
| 05 O-5/O-6 timezone 19E | Required Maven evidence | Baseline observation | Ancestor JDK11 timezone-catalog errors only. |
| 06 O-1 D1 | I-7 | PENDING | Policy/release gate remains. |
| 06 O-2 Chromium mock smoke | Manual A-1…A-5 | PENDING | Not real DB acceptance. |
| 06 O-3 governance evidence | Authorized scope | PASS | No extra product scope. |

### Review-P Routing

`FAIL` with `PROGRESSING` convergence produced one bounded repair plan for V-3. Repair evidence mode is `DURABLE_HANDOFF`; the prior executor/approval are available in `repair-execution.md`. No product code, tests, review evidence, index, HEAD, or branch was modified by the reviewer.
