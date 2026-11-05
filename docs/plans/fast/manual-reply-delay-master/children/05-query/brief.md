# Child Brief — 05-query

## Identity and authority
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master
- Branch: fast/manual-reply-delay-master
- Product base: terminal 04-worker-api Code head provided at dispatch; do not start before its clean child checkpoint.
- Read exact master docs/plans/2026-11-04/manual-reply-delay-master.md and child docs/plans/2026-11-04/manual-reply-delay-05-query.md. Child T1–T3 and all acceptance clauses are binding. Prior child reports describe lifecycle and public projection APIs; do not infer later interfaces.
- Commit subject: `feat(fast-p): implement 05-query`.

## Authorized files
1. src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt
2. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt
3. src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt
4. src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt
5. src/test/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyQueryIT.kt (new)
6. src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt
7. src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt
8. src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentService.kt (A12: readVerifiedFile private→internal only; unchanged body/public APIs)
9. src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt (A14: internal bounded canonical source page read only)
10. src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt (A14: internal bounded canonical source page read only)

No changes outside these product/test paths; no applied migration edits. Tests not listed are run-only. Report PLAN_CONFLICT rather than weakening an invariant or changing unauthorized files.

## Fixed gates and downstream contracts
- Master/child I-8: DELAYED_MANUAL_REPLY source/id=job.id/rank3/time=first createdAt remains stable through all states; exclude linked mail_record from UI union but preserve it as transactional/statistical fact. SENT success1; FAILED/UNKNOWN failure1; WAITING/SENDING/CANCELLED neither. No fake inbound/pending. Preserve existing all-history summary semantics and membership filters on the same matching message.
- I-8b: split rawMailUnionSql (reviewContext) from uiMessageUnionSql, no boolean default choice. Preserve original context hash bytes on original data; job queue/cancel/retry cannot invalidate itself. Real inbound/successful outbound still changes context. Real anchors remain processing/mail_record ids, never job ids.
- I-7/I-10: batch current-page sanitized projections with contact/account restriction. READ-permission users never query private attachment snapshots, read disk or receive actions/restoration. FULL/admin capability projection may verify file integrity to preserve03/04 eligibility, but SQL remains fixed-page/batched with no per-job queries. Use real sentMailRecordId for sent attachments; job.id must not reach legacy attachment APIs. Pending attachment metadata respects original actor/admin. Historical failed mail has no synthetic job or retry.
- DTO additions nullable/defaulted at end, delayedPendingCount default0, serverNowMs and delayedReply per approved protocol. Keep original rank1/2 cursors valid, add rank3, prove same-time pagination and UTC/Asia-Shanghai SQL sessions.
- Update only moved exact line numbers in OperatorStatusWriteSeamGuardTest; do not broaden exclusions or change snippets.

## Required commands and runtime proof
Java 11: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home.
1. `mvn -q -DskipTests test-compile`
2. `mvn test -Dtest=DelayedManualReplyQueryIT -DmysqlIt=true` (isolated Testcontainers MySQL).
3. `mvn test -Dtest=MailboxConversationRepositoryIT -DmysqlIt=true` with explicit datasource override to disposable isolated MySQL. Never use default development or production DB.
4. Run SqlCompatTest (the declared class in MailboxConversationRepositoryIT.kt; determine its exact existing class name), OperatorStatusWriteSeamGuardTest, OutboundAttachmentFlowTest, OutboundAttachmentServiceTest, MailboxReadAccessProjectionTest, MailboxAttachmentServiceTest.
Record exact commands/counts/skips and isolated connection environment. Exercise the runtime query/API scenario from A-1/A-2 without pretending unit assertions are browser proof; throwaway fixtures/harness must be removed. No production sends/database writes.

## Reporting
Use execute-p; one writer, no push/merge/rebase/amend/reset. Commit authorized implementation files only, exclude docs/plans/fast/**. Append full execution result with base/head, agent, commands, runtime proof and blockers to docs/plans/fast/manual-reply-delay-master/children/05-query/execution.md. Return READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary and report path.

## Dispatch binding and literal role clarification

- Product base9cebea6d41cc3b749382deb7691ba704471b131e; clean04 checkpoint3c681a44f6911e28c637451e3ee7610c9b9fea9d. Approved05 identity is `commit:05342b4be63bbc7d9b30416ae73ada2d44b562df` plus exact plan path, not a blob hash. Implementer confirmed current plan bytes match that commit.
- Child05T2:61 says `READ两者均隐藏，磁盘不读取`; I-7 acceptance says `READ不查询附件快照`. READ is the permission level, not a ban on every GET reading files. FULL/admin may perform required file-integrity verification; their canRetry must retain03/04 semantics. This literal clarification does not authorize metadata-only weakening, per-job SQL, or extra production paths. Implementer reported authorized repository/service can implement batched current-source metadata with full-access integrity checks.


## Epoch 2 — Approved bounded internal interface resume

- Master/05 identity `commit:2485d075409fe94098f788e5baf442c7151bedb6`; exact path remains above. User selected 「授权本次及同类内部接口修订」. A11/A12 authorizes the eighth file only for the existing no-SQL verifier's visibility; implement T2a/A3 and associated QueryIT acceptance. Original base9cebea6d41cc3b749382deb7691ba704471b131e unchanged, epoch1 had no product edits.
- Preserve full04-equivalent capability rules, source/actor/attachment ownership and per-job limits. Existing file-security implementation is reused unchanged, never copied. READ does not fetch private attachment data or read disk.
- Future necessary existing internal visibility or side-effect-free batch-read scope amendments are now preapproved within A11's strict boundary, but MUST be reported with exact evidence/files/interface first so controller records plan+ledger amendments before edits. No new business, compatibility, schema or safety changes are covered.
- Same required commands and runtime smoke. Append epoch2 execution report; preserve epoch1. Fresh independent verifier follows; no completed child replay or review-fast-p.


## Epoch 2 — A13/A14 canonical source page interfaces

- Latest master/05 identity `commit:baa9ba43a9ddf8c39ff2feae1c91c3c76fea2acb`. Applies prior A11 human approval, not a new business decision or repair epoch.
- Authorize exact two repository paths for internal `findForDelayedPage(contactId: Long, accountCodes: List<String>, jobIds: Collection<Long>)` returning existing canonical rows. Server-page job join/contact/account scope, max100ids, emptyreturnsEmpty, existingmapper, no locks/writes or altered lifecycle methods. Do not fabricate trusted refs from partial projections.
- Authorized set10files. QueryIT covers scope/current-source parity/fixedqueries; required commands unchanged. Append concrete evidence and newest identity to execution report.

