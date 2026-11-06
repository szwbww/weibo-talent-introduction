# Child Brief — 02-preparation

## Identity
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master
- Branch: fast/manual-reply-delay-master
- Child base SHA: d57026edc072b5469f08e8642c8c771acbd894e9
- Planned implementation commit subject: `feat(fast-p): implement 02-preparation`

## Contract sources (read before editing)
- Master plan: docs/plans/2026-11-04/manual-reply-delay-master.md
- Child plan: docs/plans/2026-11-04/manual-reply-delay-02-preparation.md
- Prior child evidence: docs/plans/fast/manual-reply-delay-master/children/01-storage/execution.md
- Source evidence: docs/plans/2026-11-04/manual-reply-delay-evidence/source-excerpts.md, 07-secondary-store-references.txt

## Authorized files (exactly these 8; no other product/test files)
1. src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt
2. src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt
3. src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt
4. src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt
5. src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt
6. src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptServiceTest.kt
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplySnapshotTest.kt (new)
8. src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt (amendment A1: only the 3 RAG success cases that asserted Pending's saveAll; rewrite them to assert SendPayload.ragEvidence ordering and the finalize transaction seam)

`MailReplyDraftSendIntegrationTest.kt` is RUN ONLY, never modified in this child. If it cannot pass because of the mandated change, stop and report BLOCKED with the exact failure; do not edit it.
Do not modify pom.xml, application.yml, CLAUDE.md, migrations, or docs/plans/fast/**.

## Fixed requirements
- Implement child plan T1 (extract prepare), T2 (extract deliverPrepared), T3 (RAG into finalizeSuccess), T4 (regression) exactly.
- Preserve existing public signatures/defaults of both Pending entry points and their synchronous behavior when no queue row exists.
- Do not change `computeFingerprint` fields, order, domain segments, `MANUAL_RICH:` / `MANUAL_RICH_REQUEST:` rules, completedBinding / findCompletedByRequestId / findCompletedByPayload early returns, SMTP thread headers, safety/meeting/attachment validation order, or account quota behavior.
- `deliverPrepared(prepared, claimedAttempt, delayedRef = null)` only accepts an already persisted ClaimedAttempt; no second enqueue, no public send recursion. `delayedRef` is server-only and must not leak into HTTP-bound models.
- Move RAG `saveAll` from PendingMailOperationService (around :915-932) into ManualReplySendAttemptService.finalizeSuccess inside the SENT transaction; empty evidence means no RAG rows and must not fabricate QA. QA and RAG remain mutually exclusive.
- Post-commit audit / ES unsupported-answer archiving stays after the committed SENT: its failure may adjust archive status only and must never turn a committed SENT into UNKNOWN or resend SMTP.
- Snapshot factory must use the explicit whitelist from 01 DelayedManualReplyModels; import no entities/credentials/file bytes/paths. Keep the strict codec and 4 MiB limit unchanged.
- Java 11 only.

## Required commands (run all, record exit codes and executed/skipped counts)
`export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`

1. `mvn -q -DskipTests test-compile`
2. `mvn test -Dtest='PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,ManualReplySendAttemptServiceTest,DelayedManualReplySnapshotTest'`
3. `mvn test -Dtest=MailReplyDraftSendIntegrationTest -DmysqlIt=true` (Testcontainers MySQL; Docker available)

## Reporting
Write the full execution report to docs/plans/fast/manual-reply-delay-master/children/02-preparation/execution.md (append-only; commands with exit codes, files changed, key decisions, skipped items). Do not commit fast-p evidence.

Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path.
