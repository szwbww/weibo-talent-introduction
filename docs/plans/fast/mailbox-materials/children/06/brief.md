# Fast-P Child Brief — 06 材料统一查询、所有权和文件就绪 API

- Master: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb5)
- Plan: docs/plans/2026-09-07/06-shared-material-api.md — the complete approved contract. Read it first.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials (branch fast/mailbox-materials)
- Base SHA: 21dad8bf0573c21eec52cd9783967ae97222ce3d (= child 05 Code head)
- Execution report: docs/plans/fast/mailbox-materials/children/06/execution.md

## Role
Use execute-p (read skill://execute-p first). Implement ONLY child 06. Serialized run: children 07+ untouched. You are the sole writer.

## Authorized files (exactly the plan's 10-file list)
1. src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt (new)
2. src/main/kotlin/com/weibo/talentintroduction/document/controller/ExpertMaterialController.kt (new)
3. src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt (modify)
4. src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt (modify)
5. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentService.kt (modify)
6. src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialServiceTest.kt (new)
7. src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt (modify)
8. src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt (modify)
9. src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt (modify)
10. src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentServiceTest.kt (modify)

No other files. Compile/test proof requiring an unlisted file → return PLAN_CONFLICT. Note: the plan mentions later wiring of MailboxService consumers in child 07 — do NOT touch MailboxService here.

## Constraints
- Invariants I-1..I-4 of the plan; full API/item-field contract in master plan API契约 (GET /api/expert-contacts/{id}/materials page=0.. size default 10 max 100, q/source/sourceId/state filters; POST …/transfers {attachmentIds} 1..500 dedup; POST …/reconcile {dryRun}; items fixed field set incl. canFetch/canDownload/canPreview/analysisSupported/canAnalyze + summary incl. defaultAnalysisAttachmentIds; NEVER output storagePath/IMAP credentials/workerToken; sourceInboundId NEVER used as a processing id).
- Ownership chain: document→attachment→unique owner→contact; old mailRecord attachments matched ONLY when same account/expert/INBOUND/non-empty messageId uniquely — ambiguity → explicit refusal (SOURCE_AMBIGUOUS-style diagnosis on the READ side, no new transfer state); GET never registers transfers or writes review state; unknown actual size = null (0B stays 0 when real); transfer failure never drops the list row.
- readiness: resolveReadyFile centralizes ownership validation + realpath/readiness for ExpertDocumentBrowseService/DocumentTextExtractor/MailboxAttachmentService delegation; old URLs unchanged; file responses keep Long size; only docs with valid contact ownership can be AI-analyzed.
- reconcile: only rows where processing.expert_contact_id=id AND attachment processing-owner matches; transaction-locked idempotent补缺 ExpertDocument inheriting original file+attachmentId, default PENDING_REVIEW, type from filename classification; dryRun returns candidate IDs WITHOUT DB writes; no IMAP, no review overwrite; ambiguous mailRecord rows never force-linked here.
- POST transfers: Session-verified login + per-ID ownership, all-or-nothing validation before enqueue (a single foreign expert ID rejects the whole batch); queue via the child-02 AttachmentTransferService.
- 409 mapping: MaterialNotReadyException → 409 MATERIAL_NOT_READY, transfer capacity exception → 429, via a high-priority RestControllerAdvice declared ONLY for the new controller + the three existing controllers (MailboxAttachmentController/ExpertDocumentBrowseController/ExpertDocumentAnalysisController); NEVER ResponseStatusException alone (GlobalExceptionHandler catch(Exception) preempts to 500); no global exception-rule changes; MockMvc must exercise the real global advice chain to prove 409 sticks.
- JdbcTemplate parameterized queries only (no user-SQL concatenation); page-window queries + aggregate counts, no N+1 full loads; LIKE escaping; source filter takes source/id pair; state enum validated; sort receivedAt DESC, attachmentId DESC.
- Environment: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. MySQL test DB provisioned at 127.0.0.1:3306 (root/root, talent_introduction) if a test needs it; this child adds no migration and its plan lists no mysqlIt-gated IT — docker-free tests must stay green in plain mvn test.
- Fast-p evidence excluded from your commit.

## Required commands (run all; exact output + exit codes in execution.md)
1. Targeted tests: JAVA_HOME=... mvn test -Dtest=ExpertMaterialServiceTest,ExpertDocumentBrowseServiceTest,ExpertDocumentAnalysisServiceTest,DocumentTextExtractorTest,MailboxAttachmentServiceTest
2. Full suite: JAVA_HOME=... mvn test

## Commit
Commit the implementation locally as: `feat(fast-p): implement 06` — 10 authorized files only. No push/merge/amend/rebase.

## Acceptance for return
Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Write full result to execution.md (files changed, per-command evidence, I-1..I-4 checks, deviations).
