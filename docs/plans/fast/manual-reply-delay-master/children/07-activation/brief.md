# Child Brief — 07-activation

## Identity
Worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master; branch fast/manual-reply-delay-master. Product base is terminal 07-source-contract Code head supplied at dispatch, not evidence HEAD. Start only after its clean checkpoint. Read master docs/plans/2026-11-04/manual-reply-delay-master.md and exact child docs/plans/2026-11-04/manual-reply-delay-07-activation.md. Implementation subject `feat(fast-p): implement 07-activation`.

## Authorized files
1. src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt
2. src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt
3. src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt
4. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendService.kt
5. src/main/resources/application.yml
6. src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplyDelayEndToEndIT.kt (new)
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt
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


## Prior outputs and bounded standing authority

- Plans/ledger remain authoritative. Latest master identity is commit:525aeeb9e97241d5ad044f5662c75194db1a5944; approved07-activation identity remains commit:45f517a3ee337dfa74eb33cb56cdb14f0ba09d69. A23/A24 inserts the source-contract prerequisite (initial ten-file contract plus A25/A26 one existing-test compatibility); activation retains the seven-file list and A4 ownership. No DTO duplication is authorized.
- Historical terminal06 product head is `4e64c1f4da4eb5acdac282731f58f2798ab9ff5d`, now the prerequisite's base rather than activation's new base. Reverify06Round1 returned LIGHT_PASS_WITH_NOTES/COMPLETE_CHILD and closed F06-01. Fresh focused153/affected613/controller13/fullJS2065/syntax16 passed. Runtime archive `local://reverify06-epoch2-attempt2-round1-evidence.tar.gz` and prior archives referenced there contain isolated app/MySQL/SMTP/browser startup sources and genuine UI/MIME receipts; all owned services stopped. Reuse infrastructure, not prior results as new07 acceptance.
- 04 actual SMTP proof used controlled lifecycle collaborators plus separate MySQL CAS tests;05 real API/query/MySQL proof stubbed SMTP;06 public send remained synchronous before07. None waive07's integrated controller/session→queue→worker→finalization, true60-second or restart acceptance.
- Known03 baseline: `MailReviewQueryIT` existing size100 constant-batch query assertion failed at old line239 and was independently reproduced at02 base72906c0c5e36393429320817ea0de4f7d6f3ff05. Preserve classification; do not rerun to establish that known failure or repair unrelated code. Old installed11.0.15 tzdb also lacks America/Coyhaique; previous isolated runtime used installed Zulu11.0.32.1 (same Java major), while mandatory Maven checks used specified zulu-11. Report actual runtime and required-command environments.
- User's standing approvals A5, A11 and A15 allow evidence-backed necessary existing-test compatibility, existing internal visibility/pure bounded batch readers, and missing source/status/response contract fields plus direct validation/tests, respectively. Before any outside-seven-file edit, send exact mismatch/source evidence, proposed bounded files and contract/commands to the controller for an explicit plan+ledger amendment commit. No silent scope change, security weakening, schema expansion or new business behavior. Broader decisions still require human authority. This does not authorize changing completed-child logs.
- Impl07Activation's pre-edit research proved02's no-draft conversation limitation; source/transport receipts are `local://07-source-contract-amendment-evidence.md`. A23/A24 resolves it through the ordered prerequisite, not draftRef enforcement or synchronous fallback. Read its terminal evidence at dispatch; consume original nullable input separately from actual frozen anchor, preserving its C-1–C-7. Run the added Snapshot/Controller/Lifecycle/mysqlIt and mailboxDelayedReply regressions plus every original07 gate.

## Pre-edit research disposition

Impl07Activation completed source research only; no product edit, implementation commit, build/test or runtime proof. Its PLAN_CONFLICT remains in execution.md. Controller applied existing standing authority and the max10 rule to seed the new prerequisite; this is not a failed repair round or human-intervention epoch reset. The activation implementation will be acquired fresh after that prerequisite's checkpoint. All original60-second/masterA1–A12 acceptance remains mandatory.

## Terminal prerequisite output

07-source-contract terminal product base for this activation is `a9c09c9abf8cf08707d86514b168318341716126`. Verify07SourceContract returned LIGHT_PASS_WITH_NOTES/COMPLETE_CHILD, epoch1/fix0; fresh compile/JVM82/MySQL44/focusedJS86/fullJS2076/syntax all passed with zero skips. The source contract now preserves supported draftless OUTBOUND, original nullable input versus frozen actual anchor, strict source locks/restore, local editor source without fake ordinary draft, and current body/raw boundaries. Read its execution/verify logs and current plan C-1–C-7; its11th file is only the A26 existing cache-test compatibility.

Independent replay infrastructure/evidence: `local://verify07-source-contract-epoch1attempt1fix0-evidence.tar.gz` (SHA256 `7ade55b53a4d13311efed5dfb6aea08f285327b5c033b81557e5649d668beabd`), plus writer `local://resume07-source-contract-epoch1-evidence.tar.gz`. Actual source/permission/race/attachment/inbound/outbound MIME proofs are retained; all owned runtimes stopped. Reuse fixtures, not results as activation acceptance.

O07SC-01 is an explicit activation handoff: before activation, an unchanged cancelled source POST keeps UUID/raw/ordered attachments/blank scope and verified old anchor, but the old synchronous guard produces HTTP500 wrapping422 and SMTP0. Implement original07 authoritative existing-job/flag routing rather than adding an out-of-scope blanket exception suppression. Current-revision requeue and disabled-existing-job cases must receive the approved authority/response behavior through real endpoints. This prior negative is not a public requeue PASS and does not waive the full60-second/restart/masterA1–A12 matrix.
