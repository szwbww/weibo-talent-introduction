# Child Brief — 07-activation

## Identity
Worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master; branch fast/manual-reply-delay-master. Product base is terminal 06-frontend Code head supplied at dispatch, not evidence HEAD. Start only after the clean 06 checkpoint. Read master docs/plans/2026-11-04/manual-reply-delay-master.md and exact child docs/plans/2026-11-04/manual-reply-delay-07-activation.md. Implementation subject `feat(fast-p): implement 07-activation`.

## Authorized files
1. src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt
2. src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt
3. src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt
4. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftModels.kt
5. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendService.kt
6. src/main/resources/application.yml
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplyDelayEndToEndIT.kt (new)
8. src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt
No other product/test files or plan modifications. Preserve any pre-existing application.yml edits and add only the target option. Unauthorized dependency changes require PLAN_CONFLICT.

## Contract
Execute T1–T4 with all child/master invariant and acceptance criteria.
- Both ordinary Pending entrypoints and approved-review adapter use existing canonical preparation, dedup and permission checks, then enqueue when enabled. Real authenticated actor required; nullable legacy actor is only old synchronous compatibility, never enqueue admin bypass.
- Existing job authority takes precedence independent of enabled flag: never synchronous claim of queued attempt. Old SENT stays dedup; repeated WAITING same due; CANCELLED needs current delayExpectedRevision (positive), FAILED only explicit retry. No HTTP-bound dispatch token/snapshot/trusted refs.
- Request/result fields additive at end: delayExpectedRevision nullable, delayedReply nullable; pass real session identity through controllers. WAITING returns reserved messageId but no fake sentAt/record/archive success (NOT_APPLICABLE/count0). Complete dependency injection required, enabled without dependencies fails closed rather than synchronous fallback.
- REVIEW uses only prepareSend APPROVED server snapshot; keep version/body/context and explicit current confirmation semantics. No client content override.
- YAML option talent-introduction.manual-reply-delay.enabled uses ${MANUAL_REPLY_DELAY_ENABLED:false}. Never enable production or deploy. Disabled only controls new enqueues; worker/read/cancel/recovery of old tasks stay active. Do not drop/erase queue or roll back to code unable to understand unresolved tasks.
- True controller/session→prepare→queue→worker→final transaction→query E2E on isolated Testcontainers MySQL with real Spring transaction proxies/repositories. SMTP only controlled test service. Required ordinary inbound DRAFT, LEGACY QA/RAG/assembly, OUTBOUND anchor, REVIEW confirmation cases; 451/550/DATA disconnect, races, finalization rollback, lost HTTP response, restart, permissions/suppression/attachment/context changes, switch-off semantics.
- Prior 02 RECORD_ONLY O-3 documents no-draft conversation snapshot limitation. Do not silently require a draft or narrow O-1/O-4; check reachability against actual routes. If an approved model seam cannot satisfy the master, report exact plan conflict rather than adding unauthorized file or rejecting supported input.

## Required commands and scenario evidence
Java 11 required. Run test-compile and PendingMailOperationServiceTest, new ManualReplyDelayEndToEndIT, FlywayMigrationIntegrationTest with -DmigrationIt=true, MailboxConversationRepositoryIT with -DmysqlIt=true and explicit isolated datasource, new Testcontainers ITs and original draft/review tests with opt-in properties, and 06 frontend contract tests. Record exact commands/environment/executed/skipped/failure counts; green default mvn test does not substitute for enabled IT evidence. These are this child's explicit integration gates, not a controller-owned aggregate review.
Run actual isolated 60-second black-box scenarios required by child/master A-1–A-12. Export the master acceptance checklist only when beginning acceptance, with operator/date/result/evidence and unexecuted items explicitly marked; no fictional evidence. If a required runtime prerequisite is missing, finish reachable work and report exact blocker. Do not enable production before acceptance. All artifact-only proof goes in the authorized fast-p execution evidence; ask scope authority before adding any permanent product/test path outside the list.

## Delivery
Use execute-p. One writer, no push/merge/rebase/amend/reset. Local implementation commit excludes docs/plans/fast/**. Append detailed report at docs/plans/fast/manual-reply-delay-master/children/07-activation/execution.md including agent, base/head, commands, runtime proof, acceptance evidence and blockers. Return READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary and report path. Do not claim whole-system PASS or production rollout.

## Approved ownership amendment A4

The approved child 07 plan at commit ea61d579425e894ee37c600f599beef1fdb46395 supersedes the earlier eight-file list: remove `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftModels.kt` from modification authority (seven files remain). Child 03 owns and implements `MailReviewSendRequest.delayExpectedRevision` plus CANCELLED preparation validation; 07 only consumes/forwards it through MailReviewSendService and proves the integrated behavior. Do not re-add or change a parallel DTO field. User authorizes continuous execution through READY_FOR_HUMAN_REVIEW, explicitly not review-fast-p.

