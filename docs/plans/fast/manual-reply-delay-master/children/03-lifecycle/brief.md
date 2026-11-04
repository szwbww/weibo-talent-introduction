# Child Brief — 03-lifecycle

## Identity
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master
- Branch: fast/manual-reply-delay-master
- Child base SHA: 72906c0c5e36393429320817ea0de4f7d6f3ff05
- Planned implementation commit subject: `feat(fast-p): implement 03-lifecycle`

## Contract sources (read before editing)
- Master plan: docs/plans/2026-11-04/manual-reply-delay-master.md
- Child plan: docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md
- Prior child evidence: docs/plans/fast/manual-reply-delay-master/children/01-storage/execution.md, children/02-preparation/execution.md
- Source evidence: docs/plans/2026-11-04/manual-reply-delay-evidence/schemas.md, source-excerpts.md, 07-secondary-store-references.txt

## Authorized files (exactly these 10; no other product/test files)
1. src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyService.kt (new)
2. src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt
3. src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt
4. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt
5. src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt
6. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt
7. src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt
8. src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyLifecycleIT.kt (new)
9. src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftSendIntegrationTest.kt
10. src/test/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftIT.kt

Do not modify pom.xml, application.yml, migrations, other tests, or docs/plans/fast/**.

## Fixed requirements
- Implement child plan T1 (reserveDelayed + enqueue/requeue), T2 (CAS state table + public error codes), T3 (all draft write paths + review events/context), T4 (finalize/recovery) exactly.
- Lock order: review authorization row (when applicable) → source draft/review → attempt → job. A lock-free job read used to identify the source must be re-checked after locking. All transitions and bindings in one transaction.
- `prepareAndClaim` must never claim an attempt that already has a job; only a claim-success dispatchToken may complete that round. SMTP is never called while a DB transaction is open.
- WAITING/SENDING/UNKNOWN block ordinary draft save/discard/reopen/rebind at the service+repository level (not just UI). Cancel releases only the exact id/version/attempt binding and must not clear body/context/attachments. REVIEW remains APPROVED and unchanged; record SEND_QUEUED/SEND_CANCELLED/SEND_REQUEUED at most once per job+revision.
- UNKNOWN never re-claims or auto-retries. Only a definite success carrying the same dispatchToken may converge UNKNOWN→SENT. Late failures must not downgrade SENT or another revision.
- `MailReplyDraftSendIntegrationTest` and `MailReviewDraftIT` are authorized only for genuine regressions caused by this child's mandated guards; keep unrelated assertions intact.
- Public error codes are the fixed set in the child plan (TEMPORARY_DELIVERY_FAILURE, PERMANENT_DELIVERY_FAILURE, DELIVERY_UNKNOWN, ACCESS_CHANGED, SOURCE_CHANGED, REVIEW_CONTEXT_CHANGED, ATTACHMENT_UNAVAILABLE, ACCOUNT_UNAVAILABLE, RECIPIENT_SUPPRESSED, SNAPSHOT_INVALID); no raw SMTP detail in new APIs.
- Java 11 only. Never edit applied migrations.

## Required commands (run all, record exit codes and executed/skipped counts)
`export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

1. `mvn -q -DskipTests test-compile`
2. `mvn test -Dtest='ManualReplySendAttemptServiceTest,MailReplyDraftServiceTest'`
3. `mvn test -Dtest='DelayedManualReplyLifecycleIT,MailReplyDraftRepositoryIT,MailReplyDraftSendIntegrationTest,MailReviewDraftIT,MailReviewQueryIT' -DmysqlIt=true` (Testcontainers MySQL where the class supplies it; Docker available)
4. `mvn test -Dtest='PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest'` (existing sync chain regression)

Run the concurrency tests against real MySQL; do not simulate due-time with sleeps.

## Reporting
Write the full execution report to docs/plans/fast/manual-reply-delay-master/children/03-lifecycle/execution.md (append-only; commands with exit codes, files changed, key decisions, skipped items). Do not commit fast-p evidence.

Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path.

## Epoch 2 — Approved repair contract (A2/A3)

- Supersedes only the earlier ten-file authorization limit: the approved child plan at commit ea61d579425e894ee37c600f599beef1fdb46395 authorizes the above ten files plus `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftModels.kt` (11 total).
- Human approval: 「在 03 补齐审核请求代次」. Add `MailReviewSendRequest.delayExpectedRevision` at the end with nullable default, enforce current CANCELLED revision in prepareSend as the amended child T3 requires, and retain first-send compatibility. Do not defer this requirement to 04/06/07.
- Original implementation remains 798206d5b2c8dafd892f5c5f275e952a99c18fa8; product base remains 72906c0c5e36393429320817ea0de4f7d6f3ff05. No replay of children 01/02. Preserve epoch 1 evidence.
- Correct exactly F-1–F-11 in this child's verify-log plus P-1 now resolved by A2/A3 authority. Do not address RECORD_ONLY baseline failures or unrelated hygiene. No new product classes/states/interfaces for repairs; use existing authorized seams and the approved DTO field.
- Run the four required commands above after edits are complete; compare MailReviewQueryIT:239 with recorded baseline. Do not rerun the optional full suite or baseline reproduction, and do not create/delete any extra worktree.
- Runtime proof is required for the fixed transaction paths: use isolated MySQL and a throwaway invocation of the real services/transactions if the test suite alone does not supply an actual smoke scenario. Report the result, remove scaffolding, and do not claim SMTP/browser proof that was not exercised.
- Epoch 2 repair commit format: `fix(fast-p): repair 03-lifecycle round <N>`. Append canonical `## Epoch 2 — Round <N>/3` entry to fix-log.md with findings, Before SHA, actual fix commit SHA, authorized files, commands/results and notes. Preserve all prior log entries; exclude all fast-p evidence from the product commit.
- Independent fresh lightweight re-verification follows each repair. Do not invoke verify-p/review-p/repair-p or review-fast-p in this loop.

