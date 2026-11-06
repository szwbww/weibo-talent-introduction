# Child Brief — 07-activation

## Identity
Worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master; branch fast/manual-reply-delay-master. Product base is terminal 07-source-contract Code head supplied at dispatch, not evidence HEAD. Start only after its clean checkpoint. Read master docs/plans/2026-11-04/manual-reply-delay-master.md and exact child docs/plans/2026-11-04/manual-reply-delay-07-activation.md. Implementation subject `feat(fast-p): implement 07-activation`.
Current A31/A32 authority: master and07 plan commit8a51021ca463da1e3ea279bf5a94e998267751bf. Explicit user decision2026-11-06「按总 A-5 补齐」moves only frontend/native A5 closure to new sequential08; keep all other07 requirements and its actual negative evidence. Resume07 epoch2/fix0 after this new human intervention; product base and eleven-file scope unchanged, epoch1 evidence not rewritten.

## Authorized files
1. src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt
2. src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt
3. src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt
4. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewSendService.kt
5. src/main/resources/application.yml
6. src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualReplyDelayEndToEndIT.kt (new)
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt
8. src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailReviewDraftController.kt (A28 narrow revision/status contract)
9. src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt (A28 pure identity reader and one completed-read preparation call)
10. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt (A28 default-strict server-only read preparation)
11. src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyWorkerTest.kt (A30 single existing reflection-fixture compatibility)
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
Run actual isolated60-second black-box master A1–A4/A6–A12 plus all backend/server deadline gates. A31/A32 explicitly assigns frontend/nativeA5 to pending08; preserve07's negative native and independentAPI409 evidence as unclosed here, never relabel it PASS. Export the master checklist with actual operator/date/result/evidence and honest pending08 marker; no human sign-off invented. No production enablement before07+08 and all master acceptance. Artifact-only proof belongs under authorized fast-p evidence.

## Delivery
Use execute-p. One writer, no push/merge/rebase/amend/reset. Local implementation commit excludes docs/plans/fast/**. Append detailed report at docs/plans/fast/manual-reply-delay-master/children/07-activation/execution.md including agent, base/head, commands, runtime proof, acceptance evidence and blockers. Return READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary and report path. Do not claim whole-system PASS or production rollout.

## Approved ownership amendment A4

The approved child 07 plan at commit ea61d579425e894ee37c600f599beef1fdb46395 supersedes the earlier eight-file list: remove `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftModels.kt` from modification authority (seven files remain). Child 03 owns and implements `MailReviewSendRequest.delayExpectedRevision` plus CANCELLED preparation validation; 07 only consumes/forwards it through MailReviewSendService and proves the integrated behavior. Do not re-add or change a parallel DTO field. User authorizes continuous execution through READY_FOR_HUMAN_REVIEW, explicitly not review-fast-p.


## Prior outputs and bounded standing authority

- Plans/ledger remain authoritative. Latest master and approved07-activation identities are commit:8a51021ca463da1e3ea279bf5a94e998267751bf. A23/A24 prerequisite is completed; A27/A28 ten-file contract plus A29/A30 one existing Worker fixture makes eleven. A31/A32 moves only frontend/native A5 to08 after07, preserves its original requirement and forbids07 frontend edits. A4 DTO ownership remains03.
- Historical terminal06 product head is `4e64c1f4da4eb5acdac282731f58f2798ab9ff5d`, now the prerequisite's base rather than activation's new base. Reverify06Round1 returned LIGHT_PASS_WITH_NOTES/COMPLETE_CHILD and closed F06-01. Fresh focused153/affected613/controller13/fullJS2065/syntax16 passed. Runtime archive `local://reverify06-epoch2-attempt2-round1-evidence.tar.gz` and prior archives referenced there contain isolated app/MySQL/SMTP/browser startup sources and genuine UI/MIME receipts; all owned services stopped. Reuse infrastructure, not prior results as new07 acceptance.
- 04 actual SMTP proof used controlled lifecycle collaborators plus separate MySQL CAS tests;05 real API/query/MySQL proof stubbed SMTP;06 public send remained synchronous before07. None waive07's integrated controller/session→queue→worker→finalization, true60-second or restart acceptance.
- Known03 baseline: `MailReviewQueryIT` existing size100 constant-batch query assertion failed at old line239 and was independently reproduced at02 base72906c0c5e36393429320817ea0de4f7d6f3ff05. Preserve classification; do not rerun to establish that known failure or repair unrelated code. Old installed11.0.15 tzdb also lacks America/Coyhaique; previous isolated runtime used installed Zulu11.0.32.1 (same Java major), while mandatory Maven checks used specified zulu-11. Report actual runtime and required-command environments.
- User's standing approvals A5, A11 and A15 allow evidence-backed necessary existing-test compatibility, existing internal visibility/pure bounded batch readers, and missing source/status/response contract fields plus direct validation/tests, respectively. Before any edit outside the current eleven-file authority, send exact mismatch/source evidence, proposed bounded files and contract/commands to the controller for an explicit plan+ledger amendment commit. No silent scope change, security weakening, schema expansion or new business behavior. Broader decisions still require human authority. This does not authorize changing completed-child logs.
- Impl07Activation's pre-edit research proved02's no-draft conversation limitation; source/transport receipts are `local://07-source-contract-amendment-evidence.md`. A23/A24 resolves it through the ordered prerequisite, not draftRef enforcement or synchronous fallback. Read its terminal evidence at dispatch; consume original nullable input separately from actual frozen anchor, preserving its C-1–C-7. Run the added Snapshot/Controller/Lifecycle/mysqlIt and mailboxDelayedReply regressions plus every original07 gate.

## Pre-edit research disposition

Impl07Activation completed source research only; no product edit, implementation commit, build/test or runtime proof. Its PLAN_CONFLICT remains in execution.md. Controller applied existing standing authority and the max10 rule to seed the new prerequisite; this is not a failed repair round or human-intervention epoch reset. The activation implementation will be acquired fresh after that prerequisite's checkpoint. All original60-second/masterA1–A12 acceptance remains mandatory.

## Terminal prerequisite output

07-source-contract terminal product base for this activation is `a9c09c9abf8cf08707d86514b168318341716126`. Verify07SourceContract returned LIGHT_PASS_WITH_NOTES/COMPLETE_CHILD, epoch1/fix0; fresh compile/JVM82/MySQL44/focusedJS86/fullJS2076/syntax all passed with zero skips. The source contract now preserves supported draftless OUTBOUND, original nullable input versus frozen actual anchor, strict source locks/restore, local editor source without fake ordinary draft, and current body/raw boundaries. Read its execution/verify logs and current plan C-1–C-7; its11th file is only the A26 existing cache-test compatibility.

Independent replay infrastructure/evidence: `local://verify07-source-contract-epoch1attempt1fix0-evidence.tar.gz` (SHA256 `7ade55b53a4d13311efed5dfb6aea08f285327b5c033b81557e5649d668beabd`), plus writer `local://resume07-source-contract-epoch1-evidence.tar.gz`. Actual source/permission/race/attachment/inbound/outbound MIME proofs are retained; all owned runtimes stopped. Reuse fixtures, not results as activation acceptance.

O07SC-01 is an explicit activation handoff: before activation, an unchanged cancelled source POST keeps UUID/raw/ordered attachments/blank scope and verified old anchor, but the old synchronous guard produces HTTP500 wrapping422 and SMTP0. Implement original07 authoritative existing-job/flag routing rather than adding an out-of-scope blanket exception suppression. Current-revision requeue and disabled-existing-job cases must receive the approved authority/response behavior through real endpoints. This prior negative is not a public requeue PASS and does not waive the full60-second/restart/masterA1–A12 matrix.

## Pre-edit amendment A27/A28

Master/child commit7ac03c6e3a0ab9ebf4cf406fbc35af082a472888 applies existing A15 approval to the precise pre-edit receipts in `local://07-activation-contract-amendment-evidence.md`, its contract grep, review-preparation grep and all-references JSON. Read the full current child T1a/T2/A28. Exactly ten files; no new execution epoch or repair round.

- Review HTTP accepts nullable positive integral in-range Long delayExpectedRevision, forwards existing DTO, and preserves exact existing delayed exception status/code. Unknown content/authority fields remain forbidden. The same precise local exception handler is allowed at the two already-authorized ordinary controllers; never blanket suppression or global handler edits.
- New internal findExistingByPayload is one existing fingerprint query plus existing identity validation, immutable row only, no locks/writes/claim/new DTO/SQL. It supplies existing-job identity and real persisted Message-ID, not authorization. Existing or enabled routes always use original enqueue authority, including flagfalse; avoid redundant lookup when a validated row is already available.
- Server-only allowQueuedResult defaultsfalse on existing prepareSend/lockForSend; only ReviewSendService.prepareSend and Attempt.findCompletedByPayload's lockForSend explicitlytrue. Only WAITING/SENDING queue-read rejection may be bypassed; UNKNOWN and every other original check remain. No flag in HTTP/ref/snapshot; prepareAndClaim stays defaultstrict and result-finalization locks unchanged.
- New authorized E2E proves same-job/due duplicate REVIEW under both flags, cancellation revision HTTP boundaries, current-author revocation, and true-read-prepared ref still blocked by direct synchronous claim409/SMTP0/unchanged job+due. Keep existing LifecycleIT defaultqueued409 assertion. All original commands and real60-second/restart/A1–A12 gates remain.
- Actual controller xd://lsp references returned `No language server found for this action`; worker's complete src/main+src/test literal grep/read matrix is the fallback, not an LSP PASS. Include those pre-edit receipts in retained evidence, distinctly from runtime verification.

## Current existing-test compatibility amendment A29/A30

Master/child commit895b441ac1f01caaf69ff3a711c98fb44914e5f0 applies standing A5 only to the evidenced final-class mock incompatibility in existing Worker Runtime. Scope is the prior ten files plus DelayedManualReplyWorkerTest.kt. Use a real defaultfalse ManualReplyDelayProperties in its existing constructor-parameter map, retaining override precedence, other mocks and every behavior assertion. No production type/config/mock plugin change.

Exact command117/0fail/36error/0skip, source53–61 and src/test reflection-consumer receipts are `local://07-activation-worker-compatibility-evidence.md` / `local://07-activation-worker-compatibility-grep.json`, original artifact2844/raw2848. Worker7 errors and newE2E29 context errors are initial failed checks, not passes or independent repair rounds. The newIT fixes its own test-output scan collision inside its existing authority; real production services/repositories/transactions remain. Re-run the changed gate freshly after final edits and complete every original command/runtime/60-second/restart/A1–A12 criterion. Same epoch1/fix0, no human-intervention reset.

## Explicit A31/A32 arbitration and epoch2 continuation

User chose「按总 A-5 补齐」rather than retaining06 expiry-close and reducing native acceptance. Read current07 T4/A3 and new08 plan. Historical06 plan/evidence stays immutable; only successor08 implements the narrow already-open cancellation exception in five frontend/test files. Every remaining07 runtime/backend gate stays required; no overall PASS before08 and final artifacts.

Resume07FinalActivation epoch1 preserved actual evidence at local://resume07-activation-epoch1-final-evidence.tar.gz (SHA25646581d5c44bbcd39710a1c973637d97775a05386f173d7f3a23cc3b4a3ab9d2a), execution.md and master acceptance Markdown. NewE2E final29/29 and boundJS2076/2076 plus test-compile/syntax passed; original355-test command remainsFAIL with then-newIT failures and unchanged-base subject assertion candidate. A1–A4/A6–A12 have attributed real evidence including actual ES/IMAP/SMTP; A5 remainsnegative and now assigned08. All owned services were archived and cleaned. Do not replay unchanged product/tests merely for this authority update, suppress old failures or invent independent classification.

Epoch2 implementer rebinds exact authority, commits only existing11 product/test paths, and appends truthful current outcome/source identity/archive receipts. Independent fresh verifier still runs current required commands and changed-path smoke under four gates. MailReviewSendIT subject assertion remains an outside-scope RECORD_ONLY candidate to classify from retained actual failure and unchanged-base provenance, not a granted edit/exclusion/waiver. Full current class command and explicit known ReviewQuery size100 exclusion are recorded in epoch1 execution.md:109.
