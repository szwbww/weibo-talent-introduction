# 原文容量预检与清理恢复 Implementation Plan

> User approved in chat: modify local main directly; disk below 5 GiB triggers cleanup, recover to 15 GiB before starting/resuming. Do not deploy or change production data.

**Goal:** Prevent repeated ARCHIVE_STORAGE_BLOCKED at the reserve threshold by completing safe oldest-first cleanup before download.
**Architecture:** Extend existing DiscoveryPaperArchiveService preflight/cleanup, reuse mutation lock and deletion/reference protections. Wire legacy discover startup and actual pipeline collection path; per-file checks remain authoritative. Existing TaskProgress displays cleaning and reclaimed bytes. No new task subsystem or tables.
**Tech Stack:** Kotlin/JVM 11, Spring/JdbcTemplate, MySQL/Flyway, existing static JS UI.

## Approved contract
- C1 Default trigger 5 GiB; once cleanup starts, continue until usable disk >=15 GiB AND archive quota has headroom (40 GiB quota remains independent; target <=38 GiB at current settings). Account for incoming body bytes. Merely crossing 5 GiB cannot finish cleanup.
- C2 Startup checks complete before source searches/downloads. Runtime downloads also share gate; insufficient reclaimable space prevents further downloads with explicit reason; current page/cursor is not consumed. No unbounded wait or automatic duplicate task.
- C3 Preserve existing current-execution, active scope, analysis lease, protected and outside-selected shared-blob protections. Keep metadata, expert data, parse history and admission intact. Atomic DELETING/MISSING cleanup semantics remain.
- C4 Cancellation is checked before and during cleanup; cleanup progress identifies execution, phase/reclaimed bytes. Do not overwrite progress for a different execution. No new frontend layout/CSS; use existing progress message/status and policy text.
- C5 Update existing persisted 10 GiB gate to approved 5 GiB at deployment via new narrowly scoped migration (never edit applied migration), and fresh default. Recovery policy visible to operator. Validate configurable thresholds; account for settings with quotas smaller than default 40 GiB. No production SQL now.
- C6 Avoid repeated entire archive traversal per deleted blob; preserve final disk/quota verification and concurrent safety. Do not broaden into stats-page performance rewrite.
- C7 User authorizes local main implementation only; do not commit, deploy, SSH mutate or start tasks.

## Tasks
- [x] Add failing focused regression tests: below 5 triggers; exactly 5 no unnecessary cleanup; once triggered continue to15; quota-only cleanup; cannot reach target; protection/sharing/readers; cancellation and concurrent entry; startup before any download.
- [x] Implement shared capacity gate/cleanup target in archive service/models/capture and relevant discovery startup/pipeline entry. Existing fields/config preferred; minimal migration if needed for persisted default.
- [x] Show cleanup/recovery policy and progress using existing UI; update stale statement that automatic cleanup never occurs; add focused JS/source contract proof.
- [x] JDK11 focused Maven archive/flow/discovery tests, migration checks if changed, affected JS tests and syntax, git diff --check. Record exact failures/skips without hiding known baseline issues.
- [x] Independent spec audit then quality audit; final fix-v audit against C1-C7, no deployment acceptance claim.

## Evidence anchors before implementation
- archive/DiscoveryPaperArchiveService.kt:208-248 currently stops at first hasCapacity; 299-325 body preflight and post-write guard.
- discovery/service/ExpertDiscoveryService.kt:423 discover establishes deadline and 435 startup; captureExtractedContacts at1828 delegates capture.
- discovery/service/DiscoveryPipelineService.kt uses capture and PaperArchiveBlockedException in process job; must inspect actual enabled paths before wiring.
- archive/DiscoveryPaperArchiveModels.kt:17-25 persisted minFreeBytes and response.
- static/discovery-papers.js:56 policy and193-202 settings; index.html existing archive settings fields.
- Production last7 completed runs all ARCHIVE_STORAGE_BLOCKED; runtime class matched local baseline. No capacity-buffer fix deployed.

## Implementation boundaries
- Fresh archive quota remains 20 GiB; existing production quota 40 GiB is unchanged. Only the disk default changes to 5 GiB.
- V176 updates persisted 10 GiB thresholds only; custom settings, enabled flag and quotas remain intact.
- Recovery pending is process-local. Failed/cancelled entries in the same process retain the 15 GiB recovery requirement; after a process restart, admission checks actual disk/quota thresholds again. No durable recovery-task subsystem was added.
- Both independent source reviews found C1–C7 compliant and no confirmed production defect. Final independent fix-v machine audit: PASS, AUDIT_ONLY. Manual acceptance PENDING; no production deployment claim.

## Verification work log
- Red proof: 26 archive-service tests, 3 expected behavioral failures, 0 errors; existing 23 passed.
- First implementation run: 339 JVM tests and 2,172 JS tests passed. Later progress-binding additions required a final rerun; these earlier results are not the sole final evidence.
- Final compile succeeded. Final focused run: 343 JVM tests, 4 failures caused by an obsolete mocked progress-store fixture, 1 error caused by the standalone MySQL fixture lacking a local database. Archive, pipeline and Flyway suites passed.
- Mechanical repair attempt 1: `ExpertDiscoveryServiceTest` isolated pipeline fixture now uses real `TaskProgressStore`; production code and business assertions unchanged.
- Standalone MySQL fresh-schema assertion passed after connecting explicitly to a disposable loopback-only MySQL database. No production credentials or database were used.
- Bare `kotlin:test-compile` reported `No sources to compile`; that invocation did not verify the corrected fixture. The configured `kotlin:test-compile@test-compile` execution is required for the replacement result.

## Final verification evidence
- JDK 11 compilation PASS. Corrected fixture was actually compiled by `kotlin:test-compile@test-compile` before `surefire:test`.
- 343 distinct focused JVM tests PASS, zero failures/errors/skips in final combined evidence: 128 unchanged passing cases from `/tmp/archive-capacity-final.log`; 214 ExpertDiscoveryServiceTest cases from `/tmp/archive-capacity-expert-final.log` (BUILD SUCCESS); 1 standalone fresh-schema case from `/tmp/archive-capacity-fixture-recheck.log`. The latter log also contains obsolete-class failures from an ineffective compile command; those 214 results are superseded by `archive-capacity-expert-final.log`.
- Full JS suite: `node --test src/test/js/*.test.js`, 2,172 passed, zero failures/cancellations/skips; `/tmp/archive-capacity-js-final.log`.
- `git diff --check`: PASS. Main branch retained; changes uncommitted and undeployed. Disposable MySQL fixture stopped and removed.
- Independent specification audit and subsequent quality audit: no confirmed production defects. Final fix-v C1–C7 audit PASS after the single mechanical fixture repair.
