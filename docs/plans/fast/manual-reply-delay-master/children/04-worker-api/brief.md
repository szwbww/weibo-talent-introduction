# Child Brief — 04-worker-api

## Identity and authority
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master
- Branch: fast/manual-reply-delay-master
- Product base: the terminal 03-lifecycle Code head recorded by the controller before dispatch. No implementation before the 03 child checkpoint passes.
- Implementation subject: `feat(fast-p): implement 04-worker-api`
- Read exact master docs/plans/2026-11-04/manual-reply-delay-master.md and child docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md. The child plan is the approved authority; this brief does not expand its scope.
- Prior outputs: 01 storage/snapshot codec, 02 PreparedManualReply/deliverPrepared, 03 transaction state machine and public error mapping. Read their execution reports under docs/plans/fast/manual-reply-delay-master/children/ as needed, not later child plans.

## Authorized files
1. src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyWorker.kt (new)
2. src/main/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyController.kt (new)
3. src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt
4. src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuard.kt
5. src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyWorkerTest.kt (new)
6. src/test/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyControllerTest.kt (new)
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertMailPermissionGuardTest.kt

No product/test changes outside these paths. Never edit applied migrations, plans, config, shared models or lifecycle files. If an exact requirement cannot be implemented inside this set, report PLAN_CONFLICT with smallest required amendment; do not silently widen scope. Controller owns fast-p evidence except your execution report.

## Required implementation
Implement T1–T4 and all child acceptance criteria, preserving master I-1/I-4/I-5/I-7/I-10/I-11.
- Rehydrate only current runtime entities/attachment bytes; never re-render final subject/text/html, reselect facts, regenerate ICS or call a public send entry point. Verify reconstructed fingerprint against persisted attempt and revalidate current actor, contact/account/recipient/thread, suppression, source version/review context and attachment size/SHA. No null actor bypass.
- Worker uses fixed one-thread ThreadPoolExecutor, SynchronousQueue, no local backlog, named manual-reply-delay. Schedule callback submits only; do not sleep 60 seconds or run SMTP on shared scheduler. Busy tick is not failure. Entry enabled=false does not disable old job processing. Separate short stale-recovery callback must not wait for SMTP worker.
- Claim only when capacity and DB deadline allow, use lifecycle transaction before SMTP, never hold transaction during delivery. Multi-instance exclusion is DB CAS. Pre-SMTP known failures use FAILED; uncertainty after claim/SMTP or completion failure uses UNKNOWN; recovery never re-delivers.
- APIs under /api/mail/mailbox/conversations/{contactId}/delayed-replies: exact source list(max20), get, restore, cancel, retry. Current resource permission plus service job/actor/origin check. READ permits state GET, no restoration/download/actions; REVIEW actions require admin. Never return snapshot/evidence/credentials. Server computes capabilities with current source validity.
- Enforce expectedRevision, exact 403/404/409/422/413 contracts, DELAYED_REPLY_TOO_LATE text, and all ten fixed public error mappings from T4. No raw SMTP errors in DTOs.
- Exercise real HandlerMethod permission mapping and existing read-only/resource policy; do not broaden path whitelist.

## Required commands and proof
Java 11: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home.
1. `mvn -q -DskipTests test-compile`
2. `mvn test -Dtest='DelayedManualReplyWorkerTest,DelayedManualReplyControllerTest,ExpertMailPermissionGuardTest'`
3. `mvn test -Dtest=DelayedManualReplyLifecycleIT -DmysqlIt=true` using the isolated Testcontainers environment supplied by 03, never default dev/prod datasource.
Record actual counts/skips, environment and exit codes. Exercise the real changed worker/API path with a throwaway smoke harness if it is not already exercised live by the required runtime scenario; remove scaffolding afterwards. No production SMTP or production database. Preserve the plan's 59999/60000ms boundary, replayed frozen content, unknown-result convergence, current-permission and thread-capacity proof.

## Reporting and commit
Read skill://execute-p first. One writer only; no push/merge/rebase/amend/reset or later-child implementation. Commit only authorized product/test files locally with the specified subject. Append execution report at docs/plans/fast/manual-reply-delay-master/children/04-worker-api/execution.md, including agent identity, base/head, commands/counts, runtime proof, contract decisions and any missing evidence. Do not include fast-p files in implementation commit. Return READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary and report path.

## Prior child terminal output

- Child03 terminal code head / this child's product base: 9bbb3b050f533a0010d48d545b4756abc8aa7e82.
- Shared dispatch reference is the inherited internal DelayedDispatchRef; child03 delayed result methods consume it. `PendingMailOperationService.deliverPrepared` already accepts it but forwarding into the delayed result finalizers belongs to this child, as recorded by the child03 verifier. Implement that forwarding on every result path; otherwise SMTP would not settle the job atomically. No second token type.
- Review request `delayExpectedRevision` and CANCELLED prepareSend guard are already implemented by approved A3; final public-send propagation remains07.
- Required historical note: MailReviewQueryIT:239 is a classified baseline failure, not a repair item. Child03 lifecycle40/40 and RepositoryIT8/8 passed; no SMTP integration proof is inherited from them.
- Human approved necessary existing-test compatibility amendments with concrete failure evidence only; report them to controller for exact plan/ledger recording before editing an unlisted test. No production scope expansion or weakened invariants is authorized.

