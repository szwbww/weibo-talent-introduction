# Child 03 Verification Log


## Epoch 1 — Attempt 1 — 2026-11-05T11:27:32+08:00 — 00d48c74955626519fcfcc6de859f1fcc63b8c82..e73f902a06e45f42722e39682e20a8742ba9d388

## Light Verification: LIGHT_PASS
Child: 03 — docs/plans/2026-11-05/expert-material-filter-03-projections.md
Boundary: 00d48c74955626519fcfcc6de859f1fcc63b8c82..e73f902a06e45f42722e39682e20a8742ba9d388
Verifier: MaterialProjectionVerifier — independent of writer MaterialProjectionImplementer; epoch 1, attempt 1.

Mode: AUDIT_ONLY, restricted to the four fast-p light gates, with this verify-log append as the sole authorized repository artifact write. Read the exact brief and complete child plan (identity ef85056d991448e846a729ca2fdff8d8ab83869cfc645ee36181d5d72abca2ed), referenced master I-1..I-6/N-1..N-4, docs/design.md, predecessor contracts/checkpoints in the brief, ledger Baseline, child execution evidence, and the empty prior child fix/verify logs. No product/test/plan/index/branch/commit changes, repairs, fix plans, knowledge writes, whole-suite run, aggregate master review, or review-fast-p.

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact child-boundary product/test diff names equal the Authorized Files: MailRecordRepository.kt; MailboxService.kt; AutoReplyPreviewService.kt; OperatorStatusReconcileService.kt; MailboxServiceTest.kt; AutoReplyPreviewServiceTest.kt; OperatorStatusReconcileServiceTest.kt; new MailboxSignatureProjectionIT.kt; OperatorStatusWriteSeamGuardTest.kt. `git diff e73f902a06e45f42722e39682e20a8742ba9d388 -- src/main src/test pom.xml` returned no output before fresh commands. Prior child02/controller control-plane artifacts in this boundary do not authorize or introduce other product edits. Guard diff changes only the existing MailboxService NoiseSite 168→169 and MailRecordRepository NoiseSites 628→630/685→687 plus their movement comments; paths, context strings, other noise sites, ALLOWED_WRITE_SITES, and guard assertions remain unchanged. |
| Plan and invariants | PASS | I-1/T1: MailRecordRepository:515/549/784/826 applies the shared fixed-alias MATERIAL_SQL inside existing EXISTS projections, retaining original scope, Message-ID/direction matching, count/filter/order/page clauses; MailboxService:207-234 uses shared isMaterial and preserves null-id false. I-2/T2: AutoReplyPreviewService:50-152 changes only the attachmentIntentIgnored predicate (:59), not classification/dispatch/action/rendering. I-3/T3: OperatorStatusReconcileService:57-160 filters before mapNotNull(mailRecordId) (:71-73), retains human override/COMPLETED behavior and unchanged milestone/address priority (:193-233), and performs no state writes. New regressions exercise signatures, mixed PDF, image/zero bytes, p7m, empty/null-id task rows, same-body false/true/false previews, historical REPLIED drift, manual override, invited/completed/material contacts and read-only repository/ES interaction closure. I-4/T4 passes freshly in C1. |
| Required commands | PASS | Both exact required commands ran freshly once, sequentially, after bounded source audit, in the assigned worktree with approved JDK11. C1: exit 0 / BUILD SUCCESS; 83 tests, 0 failures, 0 errors, 0 skipped. C2: exit 0 / BUILD SUCCESS; 2 tests, 0 failures, 0 errors, 0 skipped; actual isolated MySQL 5.7.44 and 8.0.36 servers and production Spring Data JDBC proxy queries. Exact commands, runtime outputs, timing, and baseline comparison below. |
| Downstream interfaces | PASS | Shared predecessor ExpertMaterialEligibility.kt:9-21 supplies the existing compile-time alias-a constant and stateless Kotlin rule; no predecessor edits. MailboxRow.hasAttachment remains Long (:932), mapped by MailboxService:239-266 to the existing Boolean DTO; MailboxController:103-121 routes task/normal paths, :125-145 routes expert grouping, and app.js:22313-22315 consumes the boolean attachment badge without new dispatch variants. UnmatchedInboundMailController:312-314/1179-1190 forwards preview including attachmentIntentIgnored unchanged. ReconcileReport retains existing categories/summary contract (:322-350); ExpertIndexController:248-260 and MailAutomationScheduler:88-97 consume the read-only report through existing task recording, not state repair. MailboxService:289-334 still delegates message details to predecessor resolveMessageAttachments; ExpertMaterialService:722-767 selects raw direct/bridge/unique legacy before filtering. No downstream product child. Joint references were checked as interfaces only, not as authorization to repeat predecessor or whole-system verification. |

### Fresh required command evidence

C1 — `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=MailboxServiceTest,AutoReplyPreviewServiceTest,OperatorStatusReconcileServiceTest,OperatorStatusWriteSeamGuardTest -DskipNodeTests=true`

- Completed 2026-11-05T11:24:00+08:00; duration 02:59; exit 0; BUILD SUCCESS. Fresh output: artifact://131 (job bg_25).
- MailboxServiceTest: 21; AutoReplyPreviewServiceTest: 22; OperatorStatusReconcileServiceTest: 39; OperatorStatusWriteSeamGuardTest: 1. Total 83 tests, 0 failures, 0 errors, 0 skipped.
- Historical-signature fixture emitted `total=5 consistent=3 dbVsExpected=1 esVsDb=0 humanOverride=1` and passed its expected REPLIED / DB MATERIALS_RECEIVED assertions and read-only closure.

C2 — `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=MailboxSignatureProjectionIT -DmysqlIt=true -DskipNodeTests=true`

- Completed 2026-11-05T11:27:32+08:00; duration 03:21; exit 0; BUILD SUCCESS. Fresh output: artifact://133 (job bg_26); 2 tests, 0 failures, 0 errors, 0 skipped.
- Actual Testcontainers MySQL5.7.44 container 6538ab6e0ede5b8f73101bccac98efc7e0172bbbeb7ce9249ddbd1907b1b817c, isolated `jdbc:mysql://localhost:33516/mailbox_projection`; output `MAILBOX_SQL_SERVER=5.7.44`.
- Actual Testcontainers MySQL8.0.36 container 9bfb2b26f3587116e3f124566107c4fbe6f880631479a74fa26b637f8a228bb2, isolated `jdbc:mysql://localhost:33517/mailbox_projection`; output `MAILBOX_SQL_SERVER=8.0.36`.
- For each server the production repository-proxy fixture passed both query methods and record/processing branches and emitted `proxy=true branches=4 signature=false pdf=true image=true empty=false account/contact isolation=true pagination=4 total=10/12 readOnly=true`. Source asserts SELECT VERSION(), obtains MailRecordRepository from its isolated Spring JDBC context, and snapshots historical tables before/after; this is actual query execution, not a string-only SQL assertion or default application datasource run (MailboxSignatureProjectionIT.kt:22-98).
- Node exec steps were intentionally disabled by the exact required commands; JUnit skipped counts above are zero. No frontend gate is claimed.

Baseline comparison: ledger Baseline records the original broader targeted315 command at master base with exit 0, 315 tests, 0 failures, 0 errors, 0 skipped. That larger pre-change selection is baseline evidence only, not a like-for-like count for this narrower fresh C1 selection. The required child service/preview/reconcile/guard surface is freshly green; the new projection IT did not exist at master base, so no pre-change pass is asserted. Writer failing-before and corrected fixture-sort history remain in execution.md; these historical runs were not substituted for fresh verification or rerun to confirm reported failures.

### Source receipts

Scope receipt: `git diff --name-only 00d48c74955626519fcfcc6de859f1fcc63b8c82..e73f902a06e45f42722e39682e20a8742ba9d388 -- src/main src/test pom.xml` returned exactly the authorized paths listed in Gate 1. Child-only full product/test patch: artifact://127. No master-base-to-head aggregate patch was reviewed.

Specialized grep receipt: pattern `ExpertMaterialEligibility\.(MATERIAL_SQL|isMaterial)` on the authorized MailRecordRepository, MailboxService, AutoReplyPreviewService, and OperatorStatusReconcileService production paths returned:

- MailRecordRepository.kt:515 — MATERIAL_SQL, record branch.
- MailRecordRepository.kt:549 — MATERIAL_SQL, processing branch.
- MailRecordRepository.kt:784 — MATERIAL_SQL, expert-list record branch.
- MailRecordRepository.kt:826 — MATERIAL_SQL, expert-list processing branch.
- MailboxService.kt:211 — any(isMaterial).
- AutoReplyPreviewService.kt:59 — any(isMaterial).
- OperatorStatusReconcileService.kt:72 — filter(isMaterial).

Specialized grep of the unchanged NoiseSite contexts returned MailboxService.kt:169 `operatorStatus = summary.operatorStatus`, MailRecordRepository.kt:630 `ec.operator_status AS operator_status`, and :687 `ec.operator_status, ec.current_index_level`; these match the mechanical-only guard diff and fresh guard success.

### AUTO_FIX

N/A — no proven four-gate violation; no fix was authorized or applied.

### RECORD_ONLY

N/A — no additional observation requiring disposition within this bounded child verification. Manual A-1..A-4 remain PENDING human acceptance as prescribed; this child verdict is not final whole-system or release acceptance.

### Required Action

- COMPLETE_CHILD
