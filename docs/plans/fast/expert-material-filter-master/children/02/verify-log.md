# Child 02 Verification Log



## Epoch 1 — Attempt 1 — 2026-11-05T10:48:39+08:00 — 51268418bfeb0dd666c1fcc079cbb6f4cb239c4a..00d48c74955626519fcfcc6de859f1fcc63b8c82

## Light Verification: LIGHT_PASS
Child: 02 — docs/plans/2026-11-05/expert-material-filter-02-ingress.md
Boundary: 51268418bfeb0dd666c1fcc079cbb6f4cb239c4a..00d48c74955626519fcfcc6de859f1fcc63b8c82
Verifier: MaterialIngressVerifier — distinct from MaterialIngressImplementer. Applied fix-v evidence and authority discipline in AUDIT_ONLY, restricted to the four light-verification gates; only this report append is authorized.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Read the complete exact child brief and approved child plan, the referenced master I-1..I-6/N-1..N-4, ledger Baseline/predecessor checkpoint, and child execution evidence. `git diff --name-status 51268418bfeb0dd666c1fcc079cbb6f4cb239c4a 00d48c74955626519fcfcc6de859f1fcc63b8c82` exited successfully and listed the authorized four production files (ImapMailReceiveService, AutoMailReplyService, MailAttachmentService, ManualExpertMaterialUploadService) and five test files (ImapMailReceiveServiceTest, ImapMetadataFetchIT, AutoMailReplyServiceTest, MailAttachmentServiceTest, ManualExpertMaterialUploadFlowTest). The other boundary paths are predecessor/controller fast-p evidence, not additional product scope; full patch receipt is artifact://90. `git diff 00d48c74955626519fcfcc6de859f1fcc63b8c82 -- src pom.xml` returned no output, so audited working sources match the requested code head. No schema, configuration, dependencies, frontend, DTO, worker, outgoing attachments, historical deletion/state rewrite, or queue cancellation was introduced. |
| Plan and invariants | PASS | T1/I-1/I-5: `ImapMailReceiveService.kt:511-560` retains multipart traversal and original child indexes, decodes the leaf name with the existing fallback, then calls unchanged `ExpertMaterialEligibility.isMaterial` before attachment registration or text/message stream reads. `ImapMailReceiveServiceTest.kt:94-129` exercises both modes, filename/MIME/unnamed/disguised-text signatures, throwing stream/content access, signed-container body/CV retention and CV partPath=3; fresh socket coverage is `ImapMetadataFetchIT.kt:98-119`.<br>T2/I-2/I-5: `AutoMailReplyService.kt:114-211` constructs one ordered MIME-plus-linked filtered businessMail before transaction/classification, clears linkedMaterials and preserves the recovery/source calculation. All core branches receive that value. Global-disabled/contact-disabled/normal material counts at `:369-381,427-439,539-551`, intent at `:1365-1379`, and confirmation bridges at `:1491-1494,1548-1551` consume it. Tests `AutoMailReplyServiceTest.kt:209-316` capture empty save/bridge inputs, all three zero-count promotion paths, unchanged manual/disabled policy, unmatched/no-introduction handling and retained MIME/linked order/source. Raw-signature duplicate-body, repeated-UID and unverifiable-source fixtures at `:1808-1884,2135-2171,2236-2278` preserve the original outcomes, single-send behavior and no repeated attachment/mail/SMTP work.<br>T3/I-3: `MailAttachmentService.kt:49-120,125-146,160-200,362-374` filters both save entrances before source/time/filesystem/repository work, skips signature backfill/bridge and returns null for signature-only intent. Existing eligible metadata still requires transfer registration. Tests `MailAttachmentServiceTest.kt:90-128` cover metadata/legacy, both owners, known/unknown contacts, zero repository/file effects, and mixed CV count=1; `:264-324,379-500,532-571` retain source-retry/binding idempotency, valid missing-transfer failure, and nondeletion of signature rows.<br>T4/I-4: `ManualExpertMaterialUploadService.kt:89-100` rejects immediately after normalization, before manualRoot/createDirectories/writeStreamedFile/transaction. `ManualExpertMaterialUploadFlowTest.kt:277-314` proves the requested HTTP 400/BAD_REQUEST/message for both signature variants, unread throwing streams, zero upload/attachment/document rows and no manual directory/transfer interaction; PNG/JPG/zero-byte text still return 201 with exact bytes/size and PENDING_REVIEW. Existing rollback, size, ownership and policy regressions execute in the same fresh required run. |
| Required commands | PASS | Both exact child commands below ran freshly once, sequentially, in the assigned worktree after the bounded source audit, with the approved JDK 11. Focused run: exit 0 / BUILD SUCCESS / 197 tests, 0 failures, 0 errors, 0 skipped (artifact://94:524-535,1238-1308). Actual local-socket run: exit 0 / BUILD SUCCESS / 16 tests, 0 failures, 0 errors, 0 skipped (artifact://96:519-539). Fresh Surefire XML confirms each selected suite count, approved java.home and target basedir; ImapMetadataFetchIT XML additionally has mysqlIt=true and an executed signature-plus-PDF testcase at line 85. The fixture binds a real loopback ServerSocket (`ImapMetadataFetchIT.kt:515-539`); its metadata server rejects attachment byte requests (`:1004-1010`), and the new test asserts BODYSTRUCTURE, readable body, retained PDF locator and no signature/PDF byte FETCH. No MySQL was required or used for this socket gate. Baseline comparisons are recorded below; the broader 315-test baseline was not rerun or represented as the child-only command. |
| Downstream interfaces | PASS | Shared predecessor API remains unchanged (`ExpertMaterialEligibility.kt:6-21`); all new admission checks call it, with no local extension blacklist or new DTO/state/queue variant. `convertToReceivedMail` forwards the walk result using existing attachment/source fields (`ImapMailReceiveService.kt:346-389`; `MailReceiveService.kt:37-102`), and both bulk and UID entrypoints still route ordinary receipts into processSingle (`AutoMailReplyService.kt:1059,1129-1141`). Empty effective sets are handled by existing save/inference/bridge loops; the receiving material-promotion gate explicitly returns unchanged for count<=0 (`AutomaticApplicationPromotionService.kt:40-46`), while ordinary reply-count/status behavior remains independent (`AutoMailReplyService.kt:1226-1243`). Valid metadata still registers/reuses the same source identity and transfer purpose, and missing valid transfer still throws before acknowledgment (`MailAttachmentService.kt:163-283`; transaction/ack `AutoMailReplyService.kt:159-220`). Processing-owner registration forwards the filtered collection (`:1618-1681`); the existing bind adapter calls the filtered backfill without moving attachment ownership (`UnmatchedInboundMailService.kt:202-210`). Manual upload still uses the existing controller/service response and OutboundAttachmentException HTTP mapping (`ExpertMaterialController.kt:116-132`; `GlobalExceptionHandler.kt:80-82`), exercised through real MockMvc. Bulk bounce/DMARC/self-check routing remains before business merge (`AutoMailReplyService.kt:1010-1059`), and ordinary Drive material sources retain their existing order and gdrive locator. Later consumers require no ingress-specific field or state; child 03 was not inspected. |

Fresh required commands and completion receipts:

| Exact command | Fresh result |
|---|---|
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ImapMailReceiveServiceTest,AutoMailReplyServiceTest,MailAttachmentServiceTest,ManualExpertMaterialUploadFlowTest,BatchAutoMailReplyServiceTest -DskipNodeTests=true` | Exit 0; BUILD SUCCESS; 197 run / 0 failures / 0 errors / 0 skipped; finished 2026-11-05T10:44:44+08:00; artifact://94. |
| `JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home mvn test -Dtest=ImapMetadataFetchIT -DmysqlIt=true -DskipNodeTests=true` | Exit 0; BUILD SUCCESS; 16 run / 0 failures / 0 errors / 0 skipped; finished 2026-11-05T10:48:39+08:00; artifact://96. |

Baseline comparison: ledger records targeted315 and local IMAP15, both exit 0 with zero failures/errors/skips. Baseline class receipts were independently read using `grep` pattern `Tests run:|BUILD SUCCESS` against artifact://14 and artifact://17. The five suites required by this child comprise 185 of the broader 315 baseline tests; the fresh required subset is 197, not a reduction of the broader baseline. Every class remains unskipped:

| Suite | Recorded baseline | Fresh verification |
|---|---:|---:|
| ImapMailReceiveServiceTest | 37 | 38 |
| AutoMailReplyServiceTest | 102 | 109 |
| MailAttachmentServiceTest | 12 | 14 |
| ManualExpertMaterialUploadFlowTest | 13 | 15 |
| BatchAutoMailReplyServiceTest | 21 | 21 |
| Exact five-suite subset total | 185 | 197 |
| ImapMetadataFetchIT, actual local socket | 15 | 16 |

### AUTO_FIX
- N/A — no proven four-gate violation. No product/test repair, staging, commit, branch mutation, plan amendment, fix-plan generation or knowledge write was performed.

### RECORD_ONLY
- N/A — no out-of-scope concern was raised or investigated further. Human A-1..A-7 remain PENDING as specified; the machine fixtures do not claim live-mailbox/UI human acceptance.

### Required Action
- COMPLETE_CHILD

This is LIGHT_PASS for child 02 only, not an overall product PASS or final/full-system review. Both required commands were freshly exercised once with zero skips. No extra smoke/build/full-suite/lint/formatter, child-03 review, review-p/verify-p/repair-p/review-fast-p, deployment or default application database access was performed. Only this named verify-log.md was manually appended; its existing text was preserved.

