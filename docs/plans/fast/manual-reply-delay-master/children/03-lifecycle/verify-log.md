## Light Verification: PAUSE
Child: 03-lifecycle — docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md; dispatch authority: docs/plans/fast/manual-reply-delay-master/children/03-lifecycle/brief.md
Boundary: 72906c0c5e36393429320817ea0de4f7d6f3ff05..798206d5b2c8dafd892f5c5f275e952a99c18fa8
Verifier: Verify03Lifecycle (isolated subagent; openai-codex/gpt-6-astra; distinct from writer Impl03Lifecycle)
Epoch: 1
Attempt: 1
Timestamp: 2026-11-04T15:51:50Z (UTC; all required commands completed)
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master
Branch: fast/manual-reply-delay-master

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Exact boundary inventory below: 8 product/test files, all in the 10-file authorized set. No changes to the two restricted regression tests, migrations, pom.xml, application.yml, or frontend. HEAD is exactly 798206d5b2c8dafd892f5c5f275e952a99c18fa8; source/pom diff against that head was empty. |
| Plan and invariants | PAUSE | Proven current-child failures F-1 through F-7 and F-9 through F-11 below. P-1 additionally requires authority resolution: 03T3 explicitly requires a current queue revision when preparing a CANCELLED review again, but the implementation deliberately allows preparation without it and defers the requirement without an approved amendment. Empty existing verify-log/fix-log provide no overriding decision. |
| Required commands | PASS | All four prescribed commands freshly executed once, sequentially, with zulu JDK 11. Exit codes 0/0/1/0; JVM totals 260 run, 259 pass, 1 known baseline failure, 0 errors, 0 skipped. MySQL was isolated Testcontainers 8.0.36. The single failure matches the supplied base-and-head evidence exactly; no new command regression. |
| Downstream interfaces | FAIL | F-8: claim/finalize use a new incompatible DelayedDispatch instead of the inherited DelayedDispatchRef accepted by deliverPrepared. F-2/F-6: the required worker preflight-failure/recovery/claim boundaries do not support changed review context or current authorization correctly. F-10: the public capability projection enables retry before the required repair/revalidation. Fixed error-code/message strings and public projection field names otherwise match 04T3/T4. |

Scope receipt: `git diff --name-status 72906c0c5e36393429320817ea0de4f7d6f3ff05..798206d5b2c8dafd892f5c5f275e952a99c18fa8` returned these product/test paths (repository root relative):
- M src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt
- M src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt
- M src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt
- A src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyService.kt
- M src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt
- M src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt
- M src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt
- A src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyLifecycleIT.kt
The boundary additionally contains controller evidence changes under docs/plans/fast/manual-reply-delay-master (02 brief/execution/fix/verify, 03 brief, ledger); these are not product/test scope expansion. This verifier changed only this verify-log; Maven generated its normal target artifacts. No checkout, reset, index change, commit, additional worktree, product edit, or plan edit was performed.

Fresh command evidence (each command used `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`; this is the brief export applied per invocation):

| # | Exact command after environment prefix | Exit | Executed / failed / errors / skipped | Baseline comparison and output |
|---|---|---|---|---|
| 1 | `mvn -q -DskipTests test-compile` | 0 | Compilation only; 0 tests executed, test execution intentionally omitted by the prescribed command | Matches recorded success; bg_89, 175.86 seconds, no output. |
| 2 | `mvn test -Dtest='ManualReplySendAttemptServiceTest,MailReplyDraftServiceTest'` | 0 | JVM 76 / 0 / 0 / 0; classes 62 + 14 | Matches baseline. artifact://2096; BUILD SUCCESS, finished 2026-11-04T23:41:29+08:00. |
| 3 | `mvn test -Dtest='DelayedManualReplyLifecycleIT,MailReplyDraftRepositoryIT,MailReplyDraftSendIntegrationTest,MailReviewDraftIT,MailReviewQueryIT' -DmysqlIt=true` | 1 | JVM 81 / 1 / 0 / 0; 80 passed | Matches supplied baseline exactly. artifact://2098; BUILD FAILURE, finished 2026-11-04T23:47:48+08:00. Failure: MailReviewQueryIT.size100 uses constant batched queries and readers never query review summaries, line 239, expected true but false. |
| 4 | `mvn test -Dtest='PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest'` | 0 | JVM 103 / 0 / 0 / 0; classes 43 + 60 | Matches baseline. artifact://2101; BUILD SUCCESS, finished 2026-11-04T23:51:11+08:00. |

Command 3 class counts: DelayedManualReplyLifecycleIT 22/22 pass; MailReplyDraftRepositoryIT 8/8 pass; MailReplyDraftSendIntegrationTest 27/27 pass; MailReviewDraftIT 19/19 pass; MailReviewQueryIT 4/5 pass. All have skipped=0. Fresh output confirms Java 11.0.15 and separate MySQL container URLs at localhost:33438/review_query, :33439/talent_introduction, :33440/review_draft, :33441/talent_introduction, and :33442/delayed_reply. No application/production MySQL endpoint was used.

Maven-bound frontend stages were not independently invoked or suppressed: commands 2 and 4 each executed the configured JS suite, 2038 tests / 354 suites / 2038 pass / 0 fail / 0 cancelled / 0 skipped / 0 todo, followed by successful node --check for app.js and task-modal-runtime.js. Thus 4076 JS test executions and four syntax-check executions occurred as required-command side effects. Command 3 stopped at the known Surefire failure, so its JS and two syntax-check stages were not reached (not successful or test-level skips). Command 1 does not reach the test phase. No optional full suite, base reproduction, extra diagnostic test, browser/manual acceptance, standalone lint, formatter, package, or migration-only suite was run.

Direct positive evidence, without treating green commands as proof of unexercised cases:
- T1/I-1: DelayedManualReplyService.kt:167-185 uses database time plus 60000; WAITING repeat branch at 148-152 retains the original deadline. LifecycleIT:308-329 and 371-393 exercise deadline stability and explicit cancellation revision requeue, including same-content newer draft version.
- I-2: lifecycle source/attempt/job mutations are transactional; repository cancelWaiting/claimDue use state/revision/deadline predicates. LifecycleIT:466-534 exercises cancel/claim, save/claim, and discard/enqueue on MySQL. These cases do not establish the missing save/enqueue snapshot interleaving in F-1 or the explicitly required retry/finalize race in F-11.
- I-3: reserveDelayed is MANDATORY and leaves PREPARED unclaimed; ManualReplySendAttemptService.kt:464-478 and 510-522 route existing delayed jobs to existing ClaimResult variants. The receiving when in PendingMailOperationService.kt:961-1174 explicitly handles every reused variant; queued attempts do not fall through into SMTP. LifecycleIT:443-453 exercises the legacy queued-attempt guard.
- I-5: finalization uses REQUIRES_NEW and writes record/facts/calendar/attempt/job/draft/review within that transaction; same-token UNKNOWN success and rejection of late failure are exercised at LifecycleIT:564-598. No SMTP call is present in the new lifecycle service. These observations do not replace the mandatory injected rollback evidence in F-11, and duplicate success currently contradicts T4 (F-7).
- I-6: service write guards and exact binding release are visible at MailReplyDraftService.kt:49-88, 200-216, 260-269; cancellation preservation and review APPROVED/version/event preservation are exercised at LifecycleIT:333-366, 690-715. Queue-event consumers were traced: MailReviewDraftRepository.kt:149-185 inserts and deduplicates job/revision, and eventMapper/validatePayload at 235-276 recognizes all three new event types rather than dropping them.
- I-7/T3: review retry selects the latest matching actor/draft/version/current-context confirmation (MailReviewDraftService.kt:265-277; repository:143-151), without constructing an event. Existing permissions and unchanged-context tests pass, but the changed-context and original-author/claim boundaries below are not satisfied.
- I-10: DelayedReplyPublicError at DelayedManualReplyService.kt:26-45 matches the ten 04T4 codes and literal messages, with unknown codes mapped to SNAPSHOT_INVALID. No raw SMTP text enters this projection. State-specific and capability defects remain below.

### AUTO_FIX
These are static, patch-anchored violations of the supplied child requirements, not newly executed reproductions. Corrections below stay within the authorized product files plus DelayedManualReplyLifecycleIT.kt; no changes were made by this verifier.

- F-1 (P1), I-2/I-6, preserve a fresh post-lock draft read: MailReplyDraftService.kt:49-56 adds REQUIRES_NEW but not READ_COMMITTED. For user1, authorizeTarget first performs a nonlocking permission SELECT (ExpertMailPermissionGuard.kt:100-103; ExpertMailReadAccessService.kt:43-56), establishing the MySQL default REPEATABLE READ snapshot. If enqueue commits the send binding before save acquires lockTargetRow, findTarget and delayedJobStatus still read that earlier snapshot. Enqueue does not increment draft version, so the subsequent version CAS can overwrite a now-WAITING draft. Smallest correction: use the same explicit READ_COMMITTED/current-read contract as the queue transactions and preserve the repository write guard; add a two-connection permission-read → enqueue-commit → save-lock regression. The current save/claim test begins with an already queued row and cannot exercise this ordering.

- F-2 (P1), T2/T3/T4 and I-5/I-7, separate transition locking from permission/content admission: DelayedManualReplyService.kt:373 and 404 call lockTransitionSource, whose review branch at 503-505 calls lockDelayedReviewForRetry and whose ordinary branch rechecks the original owner FULL/content. After a new inbound message changes review context, preflightFailure(REVIEW_CONTEXT_CHANGED) throws before writing FAILED; cancel also throws, while confirmContext refuses WAITING, leaving no way to confirm or resolve the job. After an ordinary owner loses FULL, preflightFailure(ACCESS_CHANGED), admin cancellation, and stale recovery similarly fail; recoverStale logs and leaves SENDING indefinitely. Smallest correction: cancellation, preflight failure and recovery must take source locks without re-admitting the frozen send; keep current authorization for the requested cancel actor, and retain full source/context checks only on enqueue/retry/claim. Recheck attempt/job under the prescribed lock order and retain CAS/token protection. Cover changed review context, owner downgrade, and missing source in the existing lifecycle IT.

- F-3 (P1), T1/I-3 fixed actor/origin identity: DelayedManualReplyService.kt:148-159 returns or requeues an existing fingerprint without checking actor, origin kind/id, or owner; requeueCancelled at 200 checks only the broader cancel/admin permission. Ordinary draft identity is intentionally absent from computeFingerprint. Consequently a cancelled DRAFT can be resubmitted as LEGACY with the same content/current revision: requeue replaces snapshot_json with a LEGACY restore while leaving the row origin_kind/origin_id as DRAFT and does not rebind the draft. Another actor can also receive the old task as an idempotent enqueue instead of the required 409. Smallest correction: lock/re-read the existing job after the attempt, verify exact fixed actor and origin kind/id/owner before any idempotent return or requeue, allow only the explicitly permitted ordinary-draft version refresh, and keep the original frozen identity consistent. Add cross-origin/cross-actor collision cases.

- F-4 (P1), T1/T3/I-6, release completed failed bindings before editing/rebinding: MailReplyDraftService.kt:200-203 unconditionally calls bindAttempt, but FAILED leaves send_attempt_id/send_version populated. Saving an ACTIVE failed draft increments version without clearing that binding (repository save at 64-68). An identical-content save then retry reaches bindAttempt with the new version and fails because the old send_version differs; editing the body then enqueueing a new attempt also fails because the old attempt still owns the binding. This contradicts both permitted FAILED_SAFE_TO_RETRY higher-version reuse and new-content/new-job submission. Smallest correction: under the source lock, release only the exact terminal failed binding before replacing it, keeping WAITING/SENDING/UNKNOWN immutable and preserving content/context. Test both same-content retry and changed-content new enqueue after a failure.

- F-5 (P2), T1/T2/I-1/I-10, route the existing job state before rejecting its attempt state: DelayedManualReplyService.kt:137-145 rejects DELIVERY_IN_PROGRESS and FAILED_SAFE_TO_RETRY before reading the associated job. A temporary failure → retry → cancel leaves CANCELLED plus FAILED_SAFE_TO_RETRY, which the state table explicitly permits, but current-revision enqueue can never requeue it: enqueue demands retry and retry rejects CANCELLED. A repeated enqueue during SENDING similarly throws instead of returning the original projection. Smallest correction: after locking/validating existing job identity, apply the planned job/attempt matrix; allow explicit CANCELLED requeue for PREPARED or FAILED_SAFE_TO_RETRY, return unchanged WAITING/SENDING projections, and still reject FAILED without explicit retry. Add the retry/cancel/requeue sequence and repeat-while-SENDING cases.

- F-6 (P1), I-2/I-7 and downstream 04T1, revalidate authorization/source at the queue admission and claim seams: MailReviewDraftService.kt:295-307 calls access with admin, whose branch at 313-323 never locks/checks the original author grant; unlike validateSendRow, this new delayed path does not invoke grant. A valid prepared review can therefore be enqueued/retried after author access is revoked. DelayedManualReplyService.kt:338-352 then calls lockClaimSource, which at 511-517 merely locks a draft/review (ignoring a null result), performs no current actor permission/content/context validation, and claims the attempt. A later worker preflight cannot close a permission/context change between preflight and this claim transaction; 04 expressly relies on the final claim recheck. Smallest correction: acquire/check the original review authorization before source locking for admission, and at claim use the persisted actor and validated current source/context before attempt/job CAS. Keep result finalization and recovery free from post-delivery authorization rejection. Cover revoke/downgrade/new-inbound between preflight and claim, as well as review enqueue after revoke.

- F-7 (P2), T4/I-5, return the existing record for an idempotent SENT completion: ManualReplySendAttemptService.kt:1050-1055 excludes SENT unconditionally, so repeating finalizeDelayedSuccess with the same valid token after a committed success throws rather than returning the durable record required by T4. LifecycleIT:588-591 explicitly asserts this contrary behavior. Smallest correction: after exact job/attempt/token/source validation, treat already-SENT success as a read-only return of its matching existing record; do not write facts/calendar/events or accept a failure downgrade. Update the authorized lifecycle assertion to the required idempotent return.

- F-8 (P2), downstream interface/T4, reuse the existing dispatch reference: DelayedManualReplyService.kt:64-67 introduces DelayedDispatch and DelayedClaimRow returns it; ManualReplySendAttemptService.kt:621-626 and the delayed-failure overload require that new type. The inherited 01 model is internal DelayedDispatchRef (DelayedManualReplyModels.kt:80-84), and the actual receiving deliverPrepared signature remains DelayedDispatchRef? (PendingMailOperationService.kt:943-946). A 04 worker cannot pass the claim token through the prescribed interface without a conversion or another contract change. Smallest correction: remove the second token type and use DelayedDispatchRef in the authorized lifecycle/attempt service methods, with internal visibility where needed. Do not modify the already-authoritative model merely to make it public. Wiring the previously deferred Pending delivery branch is 04 work; the type mismatch introduced here is not.

- F-9 (P1), T1/I-2/I-3, serialize LEGACY reservations on their real source: DelayedManualReplyService.kt:488-491 returns Legacy without any source lock, then unresolvedJobFor at 542-553 performs an ordinary SELECT after reserving an attempt. Two transactions for the same actor/inbound target with different content reserve different attempt rows, can both see no unresolved job, and can both insert WAITING. V174:26 has only a unique send_attempt_id for this purpose; the origin index is not unique and LEGACY has null origin_id. This is a concrete unprotected read-check-insert interleaving, not prevented by the sequential test at LifecycleIT:456-462. Smallest correction: lock the actual legacy inbound source in the authorized repository before attempt reservation, then re-read unresolved jobs using the planned true target plus actor identity inside the same transaction. Preserve source → attempt → job order and add a real-MySQL competing-fingerprint enqueue case. No migration is required or authorized.

- F-10 (P2), T2 preflightFailure capability and 04T4, do not equate a potentially recoverable error with current retry eligibility: DelayedManualReplyService.kt:435-436 sets canRetry solely from error.retryableAfterRevalidation. Thus freshly failing ACCOUNT_UNAVAILABLE/ATTACHMENT_UNAVAILABLE/ACCESS_CHANGED/REVIEW_CONTEXT_CHANGED immediately reports true while the account/file/grant/context has not been repaired or confirmed; an edited source with TEMPORARY_DELIVERY_FAILURE also remains advertised as retryable. The child state table explicitly requires canRetry=false before repair. Smallest correction: keep these reasons disabled until current source/authorization/context and required runtime validation have actually succeeded, and make retry admission consistent with the capability. Do not silently downgrade that current-child requirement to a later UI concern; later 04 preflight supplies runtime validation, not authority to expose an unvalidated true flag now.

- F-11 (P2), explicit I-2/I-5/I-6/I-7/I-10 acceptance evidence, complete the mandated lifecycle MySQL cases: the newly added LifecycleIT has 22 tests but no injected QA/RAG/calendar/job-final-write rollback test and no retry/finalize barrier test. The existing race at 536-559 is finalizeFailure/recover, which is a different requirement. Its payloadOf helper at 163-177 also omits trusted draft/review refs and RAG evidence, so its completion cases do not prove ordinary closeSent/review result/QA/RAG/calendar/job atomicity together. Cancellation uses an empty attachment fixture; no later-round freshly claimed token is tested (the old-token test stops at WAITING). These are named child acceptance criteria, not preferences for test rigor. Smallest correction: add the prescribed production-transaction fault-injection and barrier cases, real trusted-ref finalization, attachment/context preservation, and illegal matrix/permission cases to the already authorized LifecycleIT; retain unrelated original regression assertions. Search receipt: grep `@Test|rollback|TRIGGER|QA|RAG|meeting|reopen|retry|SENT|INVALID|invalid|NONE|grant|token|claim|actor` over this file lists the 22 test declarations; no rollback/TRIGGER case is returned, meeting fields in exercised fixtures are null, and the concurrency declarations are at 467/494/514/537. The fresh 22/22 pass therefore cannot be represented as completion of the missing acceptance cases.

### RECORD_ONLY
- O-1: MailReviewQueryIT:239 is the supplied, independently reproduced base-and-head failure. The required current-head rerun matched it exactly (81 run, one failure). It is not patch-introduced, so no repair is authorized here. No base reproduction was repeated.
- O-2: The optional full-suite America/Coyhaique errors are documented in execution.md as pre-existing on base and implementation. Per assignment, the optional full suite was not rerun; no fresh result is claimed for it and no timezone repair is proposed.
- P-1 (PAUSE; not incidental and not closed): 03T3 requires CANCELLED review preparation to carry the current queue revision. MailReviewDraftService.kt:215-220 instead permits it without a revision, and LifecycleIT:728-730 asserts successful preparation with MailReviewSendRequest(row.version, null). The real request definition at MailReviewDraftModels.kt:74-79 has expectedVersion/confirmationEventId/safetyWarningConfirmed/strongConfirmationText but no queue revision. Execution.md deviation 4 moves the requirement to 04/06, yet that DTO is outside the current authorized files and the supplied child plan/brief contains no approval of the deferral. This is a plan/authority conflict, not style or optional future work. Controller must resolve the authorized service/DTO handoff or obtain an explicit plan amendment; the verifier cannot approve the unamended behavior or authorize edits to the excluded DTO. P-1 takes precedence over LIGHT_FAIL under the rubric.

### Required Action
- PAUSE
- Do not COMPLETE_CHILD or advance to 04. Resolve P-1 authority explicitly, then correct the bounded AUTO_FIX violations in authorized files and dispatch a fresh isolated verifier against the resulting exact boundary. Required commands themselves are fully classified: no optional suite or baseline rerun is needed. This report is not permission for this verifier to edit product code, tests, plans, or scope.



## Epoch 3 — Attempt 1

## Light Verification: LIGHT_FAIL
Child: 03-lifecycle — docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md; dispatch contract: docs/plans/fast/manual-reply-delay-master/children/03-lifecycle/brief.md
Boundary: 72906c0c5e36393429320817ea0de4f7d6f3ff05..63bf74170d126428dc8a9cab279506cca157bc90
Verifier: Reverify03Epoch3 (fresh isolated subagent, distinct from Impl03Lifecycle, Fix03Epoch2Round1, Fix03Epoch3Round1, and Verify03Lifecycle)
Epoch: 3
Attempt: 1
Timestamp: 2026-11-04T17:32:59Z (UTC; all four required commands completed)
Plan identity: commit:0bfe5b07c85e76f85c43a31d30fde8b37f30b9b9
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master
Branch: fast/manual-reply-delay-master
Reviewed product HEAD: 63bf74170d126428dc8a9cab279506cca157bc90
Repairs included: ed041935a500aa4517ca9f880644a2e7110063d5 (epoch 2), 63bf74170d126428dc8a9cab279506cca157bc90 (epoch 3)

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Boundary inventory contains 10 changed product/test files, all in the amended 12-file list. MailReviewDraftModels.kt and MailReplyDraftRepositoryIT.kt are authorized by A2–A6; that authority is closed, not a blocker. The RepositoryIT repair only installs the existing real Spring transaction-proxy pattern and retains its behavior assertions. No migration, pom.xml, application.yml, or frontend product change. |
| Plan and invariants | FAIL | F-9 remains incomplete at the requeue/retry admissions: a second unresolved LEGACY job can be admitted. New F-12: a same-content autosave before first enqueue can commit inconsistent job/restore/binding versions and make the accepted task unclaimable. Both are direct T1/I-2/I-3/I-6 violations in authorized code, not baseline issues or optional quality preferences. Other repaired seams and acceptance cases are evidenced below. |
| Required commands | PASS | Fresh final-state Java-11 commands ran once each, sequentially after review: exits 0/0/1/0. JVM totals: 275 executed, 274 passed, 1 supplied baseline failure, 0 errors, 0 skipped. Command 3 exactly matches the supplied current baseline: 96 executed, only MailReviewQueryIT:239 fails; lifecycle 37/37 and RepositoryIT 8/8 pass. No base reproduction or optional suite. |
| Downstream interfaces | PASS | Existing internal DelayedDispatchRef is shared by DelayedClaimRow (DelayedManualReplyService.kt:61), delayed finalizers (ManualReplySendAttemptService.kt:621-626,794-801), and deliverPrepared (PendingMailOperationService.kt:943-946). Reused ClaimResult variants are explicitly routed by Pending:961-1174. Queue events are accepted by repository event validation/mapping. A3 request field/default and CANCELLED revision admission match the amended contract. This is interface-shape/dispatch compatibility, not a claim of worker/HTTP/SMTP integration; Pending result routing remains deliberate 04 work, as recorded in original F-8. |

#### Scope and identity receipts

- `git show -s --format='%H%n%D%n%s' HEAD` returned `63bf74170d126428dc8a9cab279506cca157bc90`, `HEAD -> fast/manual-reply-delay-master`, and `fix(fast-p): repair 03-lifecycle round 1`.
- `git diff 63bf74170d126428dc8a9cab279506cca157bc90 -- src/main src/test pom.xml docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md` returned no output before verification.
- `git diff 0bfe5b07c85e76f85c43a31d30fde8b37f30b9b9 63bf74170d126428dc8a9cab279506cca157bc90 -- docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md` returned no output: the reviewed plan matches the exact approved identity. A2–A6 amendments were accepted as authority, not re-litigated.
- `git diff --name-status 72906c0c5e36393429320817ea0de4f7d6f3ff05 63bf74170d126428dc8a9cab279506cca157bc90` returned the following product/test paths (full receipt: artifact://2150). The other paths in that boundary are the recorded approved plan amendments and controller-owned fast-p evidence, not extra product/test scope.

| Change | Product/test path |
|---|---|
| M | src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt |
| A | src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyService.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftModels.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt |
| M | src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt |
| A | src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyLifecycleIT.kt |

The two other authorized regression tests, MailReplyDraftSendIntegrationTest.kt and MailReviewDraftIT.kt, remain unchanged. Their assertions were not weakened. This verifier made no product/test/index/branch/commit/plan changes; the only authored repository change is this append. Maven generated its normal target output.

#### Fresh required-command evidence

Every command ran in the retained worktree with the exact prefix `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`. Test startup output confirms Java 11.0.15. All commands ran once after code review, not during repairs or concurrently.

| # | Exact command after environment prefix | Exit | Executed / failures / errors / skipped | Observed result and evidence |
|---|---|---:|---|---|
| 1 | `mvn -q -DskipTests test-compile` | 0 | Compilation only; 0 tests | Completed without output, bg_103, 180.32 seconds. Matches supplied compilation baseline. |
| 2 | `mvn test -Dtest='ManualReplySendAttemptServiceTest,MailReplyDraftServiceTest'` | 0 | JVM 76 / 0 / 0 / 0 | Classes 62 + 14; BUILD SUCCESS. bg_104, artifact://2154, 185.71 seconds; finished 2026-11-05T01:20:36+08:00. Matches baseline. |
| 3 | `mvn test -Dtest='DelayedManualReplyLifecycleIT,MailReplyDraftRepositoryIT,MailReplyDraftSendIntegrationTest,MailReviewDraftIT,MailReviewQueryIT' -DmysqlIt=true` | 1 | JVM 96 / 1 / 0 / 0; 95 passed | BUILD FAILURE only for supplied MailReviewQueryIT line-239 assertion. bg_105, artifact://2156, 356.53 seconds; finished 2026-11-05T01:26:38+08:00. Exactly matches the current recorded baseline, with no new error. |
| 4 | `mvn test -Dtest='PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest'` | 0 | JVM 103 / 0 / 0 / 0 | Classes 43 + 60; BUILD SUCCESS. bg_106, artifact://2159, 184.62 seconds; finished 2026-11-05T01:29:48+08:00. Matches baseline. |

Command 3 class results (all skipped=0):

| Class | Executed | Passed | Failures | Errors |
|---|---:|---:|---:|---:|
| DelayedManualReplyLifecycleIT | 37 | 37 | 0 | 0 |
| MailReplyDraftRepositoryIT | 8 | 8 | 0 | 0 |
| MailReplyDraftSendIntegrationTest | 27 | 27 | 0 | 0 |
| MailReviewDraftIT | 19 | 19 | 0 | 0 |
| MailReviewQueryIT | 5 | 4 | 1 | 0 |

The single failure is `MailReviewQueryIT.size100 uses constant batched queries and readers never query review summaries`, expected true but false, `MailReviewQueryIT.kt:239`. It is the proven supplied baseline, not a new finding. No base worktree or baseline rerun was used.

Fresh MySQL evidence: five isolated Testcontainers MySQL 8.0.36 JDBC URLs, `localhost:33460/review_query`, `:33461/talent_introduction`, `:33462/review_draft`, `:33463/talent_introduction`, and `:33464/delayed_reply` (artifact://2156). The real RepositoryIT HTTP fixture and lifecycle Spring service transactions executed against those containers, not an application/production datasource.

Commands 2 and 4 each also executed the Maven-bound frontend stages: 2038 tests / 354 suites / 2038 passed / 0 failed / 0 cancelled / 0 skipped / 0 todo, then successful node-check-app and node-check-task-modal-runtime stages (artifact://2154:3359-3375 and artifact://2159:3288-3304). Thus 4076 JS test executions and four syntax-stage executions occurred as command side effects. Command 3 stopped at Surefire, before those frontend stages; command 1 does not reach test execution. No standalone frontend suite, optional full suite, build/package, formatter, lint, timezone diagnosis, additional smoke invocation, or browser/SMTP acceptance was run.

#### Direct invariant and previous-finding disposition

Passing command counts establish only their exercised cases. They do not discharge F-9/F-12, which the present test sequences omit.

| ID / seam | Current evidence and disposition |
|---|---|
| F-1 — stale permission snapshot during save | Corrected: MailReplyDraftService.kt:50,82 explicitly use READ_COMMITTED; repository save/discard have unresolved-job SQL predicates at :64-73. LifecycleIT:776-799 exercises permission read -> queue commit -> save continuation. F-12 below is a separate reverse ordering at first-enqueue admission, not a renumbering of F-1. |
| F-2 — transition versus admission locks | Corrected: DelayedManualReplyService.kt:393-410,428-445,570-585 use state-source locking without re-admitting old permission/context; cancellation still checks the requesting actor. Lifecycle downgrade, review-context, and missing-source recovery cases at :858-911 execute. |
| F-3 — fixed actor/origin identity | Corrected: locked existing-job path :127-140 calls requireSameIdentity at :627-639 before returning/requeueing; the actor/origin collision test at LifecycleIT:809-823 rejects cross-kind/cross-actor replacement. |
| F-4 — terminal binding release | Corrected: MailReplyDraftService.kt:218-239 releases only the matching failed/cancelled binding before save/rebind; LifecycleIT:843-856 exercises unchanged-body newer-version retry and changed-body new job. |
| F-5 — job-state-first admission | Corrected: DelayedManualReplyService.kt:127-159 routes existing job status before standalone attempt-state rejection. LifecycleIT:826-840 exercises SENDING repeat and failed retry -> cancellation -> current-revision requeue. |
| F-6 — source/current authorization at claim | Corrected at the reviewed seams: review original-author grant locks precede source locks (MailReviewDraftService.kt:306-320); claim uses the persisted actor and current source/context at DelayedManualReplyService.kt:359-374. LifecycleIT:858-900 covers downgrade/revocation/new inbound. |
| F-7 — idempotent definite success | Corrected: ManualReplySendAttemptService.kt:650-657 returns the existing durable record for matching-token SENT before any repeat writes. LifecycleIT:581-613,913-937,1081-1105 exercises duplicate success and rejection of late failure. |
| F-8 — shared dispatch-reference type | Corrected: existing internal DelayedDispatchRef is used end-to-end at the 03-facing interfaces; no parallel token class remains. Pending delivery has the matching nullable parameter. Per the explicit original F-8 decision, forwarding it from Pending into delayed finalizers is 04 wiring, not reopened 03 authority/scope. No SMTP result-routing completion is claimed here. |
| F-9 — source exclusion | Original competing-first-enqueue race is corrected by DelayedManualReplyRepository.kt:103-113 source locking and the LifecycleIT:801-807 competing-fingerprint test. However the same T1 exclusion is absent on existing-job requeue/retry; F-9 stays open for that bounded admission defect below. |
| F-10 — repair-gated capability | Corrected within the 03 contract: view/retry use source/permission/context validation plus account/attachment checks at DelayedManualReplyService.kt:461-503; transaction-held transition responses remain conservative. LifecycleIT:876-900,944-959,990-1028 covers current confirmation, account repair, and real attachment integrity restoration. 04 still owns complete runtime rehydration/preflight as planned. |
| F-11 — mandated transaction/concurrency evidence | Corrected for the formerly absent named cases: trusted-ref payload helper :163-193, later-round old-token rejection :618-643, retry/finalize barrier :962-979, real attached-draft transaction smoke :990-1028, QA/RAG/calendar/final-job SQL fault injection :1038-1078, review-result rollback/dedup :1081-1105. These all executed in the fresh 37/37 lifecycle result. |
| P-1 / A3 — review queue revision | Authority and implementation are resolved, not deferred: nullable field/default at MailReviewDraftModels.kt:74-79; prepareSend locks original grant -> review -> attempt -> job, validates nonpositive/current CANCELLED revision at MailReviewDraftService.kt:182-194 and repository :145-151. LifecycleIT:741-763 exercises missing/stale/zero/current revisions; first prepare remains exercised in :876-880. |
| A6 — RepositoryIT compatibility | Real Spring ProxyFactory/TransactionInterceptor at MailReplyDraftRepositoryIT.kt:87-92 applies the production save annotations using the existing isolated datasource transaction manager. All eight cases pass, retaining the original attachment ownership/restoration and business-isolation assertions. No production no-transaction bypass was restored. |

Further direct boundary evidence: database-time +60000 insertion and unchanged WAITING deadline are at DelayedManualReplyService.kt:127-139,168-186. WAITING/CANCELLED allow PREPARED or FAILED_SAFE_TO_RETRY, SENDING requires DELIVERY_IN_PROGRESS, SENT requires SENT, UNKNOWN requires DELIVERY_UNKNOWN, FAILED permits FAILED/FAILED_SAFE_TO_RETRY in the exhaustive matrix at :641-654. Claim never admits UNKNOWN. Ordinary save/discard guards block WAITING/SENDING/UNKNOWN; cancellation releases only the exact source binding and preserves content/context/attachment references. Result finalizers lock source -> attempt -> job, validate token/source/fingerprint, and atomically persist record/facts/calendar/draft or review result/job; same-token UNKNOWN permits only definite success. Finalization and recovery do not require post-delivery grants. No SMTP dependency is introduced into this transaction service.

Cross-boundary consumers inspected, not inferred from successful compilation:
- DelayedReservationState is explicitly exhausted by enqueue (DelayedManualReplyService.kt:143-159), and existing job states route at :129-140.
- Existing ClaimResult variants remain explicitly handled in PendingMailOperationService.deliverPrepared:961-1174; queue WAITING/SENDING map to IN_PROGRESS, UNKNOWN to UNKNOWN, FAILED/CANCELLED to PERMANENT_FAILED, SENT to DEDUP_SENT in ManualReplySendAttemptService.kt:1122-1136. The receiving branches reject/no-send or return completion rather than falling through into SMTP.
- SEND_QUEUED/SEND_CANCELLED/SEND_REQUEUED are inserted with job+revision dedup and explicitly accepted by MailReviewDraftRepository.kt:160-193,235-285 event parsing/schema dispatch. Generic event DTO forwarding therefore does not discard them.
- MailReviewSendRequest.delayExpectedRevision is consumed in prepareSend now; planned 07 propagation into Pending/enqueue remains as explicitly approved A3/A4, not a missing 03 caller.
- The ten public errors/messages at DelayedManualReplyService.kt:26-44 match 04T4 exactly; unknown historical codes become SNAPSHOT_INVALID. Public projections do not include raw attempt/SMTP diagnostics.

Search receipts supporting the admission findings: grep of `unresolvedJobFor|requeueCancelled|repository.retryFailed|repository.requeue` in DelayedManualReplyService.kt returned call sites at :139,164,213,329 and the definition at :609. The exclusion check is reached only after the existing-job early return. Grep of source-version/binding expressions returned first insert originVersion at :177, bind at :184, current binding checks at :362-364, and body/context-only ACTIVE source comparison at MailReplyDraftService.kt:194-201. The static sequences below are not claimed as additional executed reproductions.

### AUTO_FIX
- **F-9 (P1, remains open; T1 and I-2/I-3): reject a second unresolved source job on existing-job requeue/retry.** Evidence: `DelayedManualReplyService.kt:139,190-224,280-340,609-622`. Sequence: LEGACY A is CANCELLED (or safely FAILED); the same actor enqueues different content as B for the same real inbound source, which is legal while A is terminal. Enqueue of A with its current cancellation revision returns through requeueCancelled before the sole unresolvedJobFor guard; retry(A) has no guard at all. Both A and B commit WAITING, and each can be independently claimed. The source row lock added for the original F-9 serializes operations but cannot enforce a check those two branches omit. Smallest authorized correction: under that existing source lock, apply the same other-unresolved-job exclusion before either requeue/retry CAS; preserve the current attempt exclusion, actor/true-target match, revision and token checks. Add both sequential A-terminal -> B-WAITING -> A-requeue/retry rejection cases in DelayedManualReplyLifecycleIT.kt. Authorized correction files: DelayedManualReplyService.kt and DelayedManualReplyLifecycleIT.kt; no migration or new class/interface/state.
- **F-12 (P1, new; T1 and I-2/I-6): reject a stale first-enqueue draft version before committing a mismatched binding.** Evidence: `DelayedManualReplyService.kt:173-184,359-364`; `MailReplyDraftService.kt:173-203,210-223`. Sequence: prepare a DRAFT snapshot at v1; an unchanged-body/context autosave commits v2 before enqueue acquires the source lock. lockDelayedSource accepts the current v2 based on matching content and returns a v2 validated ref. First insert still stores origin_version=1 and the original restore.draftVersion=1, but bindDraft writes send_version=2; all writes commit. claim then deterministically rejects row.version/sendVersion != originVersion, leaving an accepted WAITING job frozen and unclaimable. Smallest authorized correction: for a new DRAFT job, reject a snapshot whose draftVersion differs from the locked validated current version before insertion/binding; keep the separately authorized current-version refresh for explicit CANCELLED requeue and FAILED retry intact. Add the same-content save-between-preparation-and-first-enqueue case to DelayedManualReplyLifecycleIT.kt and assert no inconsistent job/binding is committed. Authorized correction files: DelayedManualReplyService.kt and DelayedManualReplyLifecycleIT.kt; no new class/interface/state.

### RECORD_ONLY
- O-1: MailReviewQueryIT:239 remains the proven supplied pre-existing failure. Fresh command 3 matches the recorded current baseline exactly: 96 run / 1 failure / 0 errors / 0 skipped; lifecycle 37/37 and RepositoryIT 8/8. No baseline repair or base reproduction is authorized or needed.
- O-2: The supplied optional full-suite America/Coyhaique failures remain excluded. No optional suite or timezone reproduction/repair was run or proposed; no fresh result for those cases is claimed.

### Required Action
- AUTO_FIX
- Do not COMPLETE_CHILD or advance to 04 yet. Apply the two bounded admission corrections above within the existing approved scope, preserve F-9 lineage and new F-12, record the repair, then acquire a different fresh lightweight verifier against the exact resulting range. There is no remaining DTO/RepositoryIT authority conflict, no need for a deferral, and no missing command classification. The baseline-only failure is not a repair task. This report authorizes no product edit by the verifier.


## Epoch 3 — Attempt 2

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 03-lifecycle — docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md; dispatch contract: docs/plans/fast/manual-reply-delay-master/children/03-lifecycle/brief.md
Boundary: 72906c0c5e36393429320817ea0de4f7d6f3ff05..9bbb3b050f533a0010d48d545b4756abc8aa7e82
Verifier: Reverify03Epoch3Attempt2 (fresh isolated verifier session; distinct from the recorded writers and earlier verifiers)
Epoch: 3
Attempt: 2
Timestamp: 2026-11-05T02:12:34+08:00 (all four fresh required commands completed)
Plan identity: commit:0bfe5b07c85e76f85c43a31d30fde8b37f30b9b9
Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master
Branch: fast/manual-reply-delay-master
Reviewed product HEAD: 9bbb3b050f533a0010d48d545b4756abc8aa7e82
Latest repair boundary: 63bf74170d126428dc8a9cab279506cca157bc90..9bbb3b050f533a0010d48d545b4756abc8aa7e82
Authority: A2–A6 remain closed and accepted. Prior epoch-3 attempt-1 and fix-log round-2 evidence were read before analysis. No approved authority or closed finding was reopened without contradictory evidence.

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | Full child boundary changes 10 product/test files, all within the approved 12-file list. Latest repair changes only DelayedManualReplyService.kt and DelayedManualReplyLifecycleIT.kt (10 and 77 insertions). Exact scope and identity receipts are below. No unauthorized product/test, migration, configuration, or frontend change. |
| Plan and invariants | PASS | F-9/F-12 are closed by direct admission-path inspection and fresh MySQL regressions, not merely suite totals. Actor/origin identity stays fixed; source locks cover first enqueue/requeue/retry; stale first-enqueue DRAFT versions roll back before job insertion/binding. Retained invariant evidence is below. |
| Required commands | PASS | All four prescribed commands ran freshly once, sequentially after analysis, with zulu JDK 11. Exits 0/0/1/0; JVM totals 278 executed, 277 passed, 1 supplied baseline failure, 0 errors, 0 skipped. Command 3 exactly matches the current baseline: 99 executed, only MailReviewQueryIT:239 fails; lifecycle 40/40. MySQL fixtures used isolated Testcontainers. |
| Downstream interfaces | PASS | Claim and delayed finalizers share inherited DelayedDispatchRef, matching deliverPrepared. Existing ClaimResult variants have explicit receiving branches; queue events survive repository validation/mapping; A3 delayExpectedRevision is consumed in prepareSend. Public fields and ten fixed messages match 04T3/T4. Deliberate 04/07 wiring remains later-child work under closed decisions. |

#### Scope and identity receipts

- `git show -s --format='%H%n%D%n%s%n%aI' HEAD` returned `9bbb3b050f533a0010d48d545b4756abc8aa7e82`, `HEAD -> fast/manual-reply-delay-master`, and `fix(fast-p): repair 03-lifecycle round 2` (artifact://2180).
- `git diff 9bbb3b050f533a0010d48d545b4756abc8aa7e82 -- src/main src/test pom.xml docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md` returned no output before verification: working product/plan state matches the dispatched head.
- `git diff 0bfe5b07c85e76f85c43a31d30fde8b37f30b9b9 9bbb3b050f533a0010d48d545b4756abc8aa7e82 -- docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md` returned no output: plan matches its exact approved identity.
- `git diff --name-status 72906c0c5e36393429320817ea0de4f7d6f3ff05..9bbb3b050f533a0010d48d545b4756abc8aa7e82` yielded this product/test inventory (complete receipt: artifact://2180). Other boundary paths are approved plan amendments and controller-owned fast-p evidence, not additional product/test scope.

| Change | Product/test path |
|---|---|
| M | src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt |
| A | src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyService.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftModels.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt |
| M | src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt |
| M | src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt |
| A | src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyLifecycleIT.kt |

`git diff --name-only 63bf74170d126428dc8a9cab279506cca157bc90..9bbb3b050f533a0010d48d545b4756abc8aa7e82` returned only the lifecycle service and lifecycle IT (repair diff: artifact://2179). MailReplyDraftSendIntegrationTest.kt and MailReviewDraftIT.kt remain unchanged and were freshly exercised. This verifier authored only this append to the assigned verify-log; product/test/plan/index/commit/branch were not modified. Maven generated normal target artifacts. No worktree was created or removed.

#### Fresh required-command evidence

Every command ran in the retained worktree with prefix `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`. Startup confirms Java 11.0.15 (artifact://2187:1118,2197,2590). Commands ran once each, sequentially after analysis; no tests ran during analysis.

| # | Exact command after environment prefix | Exit | Executed / failures / errors / skipped | Fresh result and evidence |
|---|---|---:|---|---|
| 1 | `mvn -q -DskipTests test-compile` | 0 | Compilation only; 0 tests executed | bg_112, completed without output in 179.21 seconds. Matches compilation baseline. |
| 2 | `mvn test -Dtest='ManualReplySendAttemptServiceTest,MailReplyDraftServiceTest'` | 0 | JVM 76 / 0 / 0 / 0 | Classes 62 + 14; BUILD SUCCESS. bg_113, artifact://2185, 182.27 seconds; finished 2026-11-05T02:03:08+08:00. |
| 3 | `mvn test -Dtest='DelayedManualReplyLifecycleIT,MailReplyDraftRepositoryIT,MailReplyDraftSendIntegrationTest,MailReviewDraftIT,MailReviewQueryIT' -DmysqlIt=true` | 1 | JVM 99 / 1 / 0 / 0; 98 passed | BUILD FAILURE solely for supplied MailReviewQueryIT:239. bg_114, artifact://2187, 363.88 seconds; finished 2026-11-05T02:09:20+08:00. Exactly matches recorded current baseline. |
| 4 | `mvn test -Dtest='PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest'` | 0 | JVM 103 / 0 / 0 / 0 | Classes 43 + 60; BUILD SUCCESS. bg_115, artifact://2190, 187.15 seconds; finished 2026-11-05T02:12:34+08:00. |

Command 3 class results (all skipped=0):

| Class | Executed | Passed | Failures | Errors |
|---|---:|---:|---:|---:|
| DelayedManualReplyLifecycleIT | 40 | 40 | 0 | 0 |
| MailReplyDraftRepositoryIT | 8 | 8 | 0 | 0 |
| MailReplyDraftSendIntegrationTest | 27 | 27 | 0 | 0 |
| MailReviewDraftIT | 19 | 19 | 0 | 0 |
| MailReviewQueryIT | 5 | 4 | 1 | 0 |

The single failure is `MailReviewQueryIT.size100 uses constant batched queries and readers never query review summaries`, expected true but false, `MailReviewQueryIT.kt:239` (artifact://2187:796-799,2861-2871). This is the supplied established baseline, not a patch-introduced failure; it was not repaired or reproduced at another revision.

Fresh database evidence: isolated MySQL 8.0.36 Testcontainers JDBC URLs were `localhost:33472/review_query`, `:33473/talent_introduction`, `:33474/review_draft`, `:33475/talent_introduction`, and `:33476/delayed_reply` (artifact://2187:540,806,1117,2196,2589). Lifecycle cases use actual Spring service transactions, not mocked repositories or a wrapping test transaction. Due-time fixtures update database deadline columns rather than sleeping through 60 seconds.

Commands 2 and 4 each also ran Maven-bound frontend stages: 2038 tests / 354 suites / 2038 passed / 0 failed / 0 cancelled / 0 skipped / 0 todo, then successful node-check-app and node-check-task-modal-runtime (artifact://2185:3359-3375; artifact://2190:3288-3304). Thus 4076 JS test executions and four syntax-stage executions occurred as required-command side effects. Command 3 stopped at Surefire before those stages; command 1 does not execute tests. No optional full suite, base rerun, standalone frontend run, package, formatter, lint, extra smoke launcher, browser acceptance, or SMTP delivery verification was performed.

#### Direct invariant evidence and finding lineage

All file:line references below are current-head lines. Passing counts establish their named exercised cases; they do not replace inspection of admission and downstream dispatch.

- **F-9 — CLOSED (T1/I-2/I-3):** `DelayedManualReplyService.kt:536-559` holds the real LEGACY inbound/contact lock (repository `:103-113`) through admission. Existing-job requeue now applies the same unresolved-source exclusion at `service:206-208`; FAILED retry applies it at `:315-317`, before CAS. Retry uses `observed.actorUsername`, so admin cannot evade the original actor's target exclusion. The matcher `:619-633` excludes the current attempt, matches DRAFT/REVIEW origin and LEGACY contact + actor + real inbound id, and blocks WAITING/SENDING/UNKNOWN. New MySQL cases `LifecycleIT:481-502,505-529` exercise terminal A -> B WAITING -> rejected A requeue/owner retry/admin retry, assert persisted rows unchanged and one unresolved task, then prove B remains claimable while A is not. Original competing-first-enqueue case `:878-884` also executes.
- **F-12 — CLOSED (T1/I-2/I-6):** First-insert guard `DelayedManualReplyService.kt:167-170` compares original restore.draftVersion with the locked validated ref before job insertion/binding. The surrounding REQUIRES_NEW READ_COMMITTED transaction (`:115`) rolls back the preceding reservation on rejection. Accepted insert origin_version/restore and binding agree (`:173-189`), and claim checks row.version/sendVersion/attempt against the job (`:370-384`). New MySQL case `LifecycleIT:532-555` prepares v1, unchanged-autosaves v2, proves SOURCE_CHANGED with zero attempts/jobs and an unchanged unbound draft, then accepts and claims fresh v2. Explicit CANCELLED requeue and FAILED retry retain authorized current-version refresh (`service:209-229,319-348`; exercised at `LifecycleIT:388-409,920-936`).
- **F-3 — remains CLOSED, fixed actor/source identity:** The locked existing-job path checks identity before idempotent return or requeue (`service:127-139,637-650`): actor, origin kind/id and owner (or frozen review version). Fresh `LifecycleIT:887-899` rejects cross-actor/cross-kind collisions and cancelled DRAFT -> LEGACY replacement. Round 2 does not weaken this boundary.
- **F-1/F-4 — remain CLOSED, draft write/binding safety:** Save/discard retain READ_COMMITTED and source-row locks (`MailReplyDraftService.kt:50-56,82-88`) plus unresolved-job SQL guards (`MailReplyDraftRepository.kt:64-73`). Exact terminal failed/cancelled binding release is `MailReplyDraftService.kt:218-239`. Permission-read -> queue commit -> save continuation, same-body higher-version retry, changed-body new task, and cancellation body/context/attachment preservation execute at `LifecycleIT:853-875,920-936,350-384,1067-1105`.
- **F-2/F-6 — remain CLOSED, admission versus result transitions:** Claim revalidates source/current persisted actor (`DelayedManualReplyService.kt:370-384,536-577`), and review original-author grant precedes its source lock (`MailReviewDraftService.kt:306-320`). State/result transitions lock without re-admitting obsolete permissions/content (`DelayedManualReplyService.kt:579-594`, review result `:295-300`). Current downgrade/revocation/new-inbound and resolvable cancellation/preflight/recovery cases execute at `LifecycleIT:939-994`; actual result persistence after grant/context changes at `:998-1022`.
- **F-5/F-7 — remain CLOSED, state-first routing and idempotent convergence:** Existing job routing precedes standalone attempt rejection (`DelayedManualReplyService.kt:127-159`). SENDING repeat and failed retry -> cancel -> requeue execute at `LifecycleIT:902-916`. Same-token SENT returns the durable record before writes (`ManualReplySendAttemptService.kt:650-657`); UNKNOWN definite success is allowed, old-token/late failure are not (`:1039-1106`). Fresh convergence/duplicate/later-claimed-round cases are `LifecycleIT:658-718,998-1022,1158-1180`.
- **F-8 — remains CLOSED:** The inherited dispatch-reference type is shared end-to-end at the child-03 interface; explicit later-child forwarding decision is retained, as detailed below.
- **F-10/F-11 — remain CLOSED:** Projection/retry revalidate source/permission/context and current account/attachment integrity (`DelayedManualReplyService.kt:461-505`); transaction-held projections remain conservative. Current review confirmation, account repair, actual attachment-integrity repair, and real-service transaction smoke execute at `LifecycleIT:957-981,1026-1036,1067-1105`. Trusted completion refs are in `:165-193`; required MySQL races at `:560-650,1039-1054`; QA/RAG/calendar/final-job and review-result SQL-trigger rollback/dedup at `:1115-1180`. Full runtime rehydration remains child 04. No SMTP proof is claimed.
- **P-1/A2–A6 — remain CLOSED:** Nullable trailing field/default is `MailReviewDraftModels.kt:74-79`; prepareSend locks original grant -> review -> attempt -> job and enforces CANCELLED revision (`MailReviewDraftService.kt:182-194`, repository `:145-151`). Missing/stale/zero/current revisions and first-send compatibility execute at `LifecycleIT:818-840,957-960`. Authorized RepositoryIT compatibility uses the existing real transaction-proxy pattern (`MailReplyDraftRepositoryIT.kt:87-92,179-186`); its eight cases pass without weakening business assertions or restoring a production lock bypass.

Other explicit plan evidence: database time +60000 and unchanged WAITING/SENDING repeat deadline/revision are `DelayedManualReplyService.kt:127-139,172-186`, exercised at `LifecycleIT:325-345`. Exhaustive job/attempt mapping is `service:653-666`; UNKNOWN is not claimable. Finalizers lock source -> attempt -> job, verify fingerprint/source/token, and commit record/QA/RAG/calendar/attempt/draft-or-review/job together (`ManualReplySendAttemptService.kt:628-775,803-893,1039-1106`). Lifecycle transaction code does not invoke SMTP. Ten public error messages match 04T4, and unknown historical codes map to SNAPSHOT_INVALID (`DelayedManualReplyService.kt:26-44`).

#### Downstream consuming-side checks

- DelayedReservationState is explicitly routed in enqueue (`DelayedManualReplyService.kt:143-159`), with existing job states routed first (`:127-140`). DelayedClaimRow uses inherited internal DelayedDispatchRef (`:61`); delayed finalizers accept it (`ManualReplySendAttemptService.kt:621-626,794-801`), matching `PendingMailOperationService.deliverPrepared:943-946`.
- Queue sync guarding maps WAITING/SENDING -> IN_PROGRESS, UNKNOWN -> UNKNOWN, FAILED/CANCELLED -> PERMANENT_FAILED, SENT -> DEDUP_SENT (`ManualReplySendAttemptService.kt:1132-1136`). The receiving `when` (`PendingMailOperationService.kt:961-1174`) explicitly handles these ClaimResult variants: blocked variants do not fall through into SMTP, and DEDUP_SENT returns completion.
- SEND_QUEUED/SEND_CANCELLED/SEND_REQUEUED have jobId/revision dedup (`MailReviewDraftRepository.kt:160-193`) and explicit acceptance by event mapping/schema dispatch (`:235-285`); they are not silently discarded.
- MailReviewSendRequest.delayExpectedRevision is consumed now in prepareSend. Later public-send-chain propagation is approved child-07 work (A3/A4); Pending forwarding delayedRef into result finalizers is the retained child-04 decision. Neither is reopened as child-03 authority/scope. No SMTP integration completion is claimed.
- Public projection fields (`DelayedManualReplyModels.kt:87-102`, `DelayedManualReplyService.kt:461-483`) and ten literal errors match 04T3/T4 exactly. Snapshot/evidence and raw SMTP diagnostics are not projected.

Focused search receipt: grep of `unresolvedJobFor|requireSameIdentity|source is SlottedSource.Draft|snapshot.restore as DelayedDraftRestore|originVersion|bindDraft|lockEnqueueSource` in DelayedManualReplyService.kt returned exclusion calls at 164/206/315 and definition 619, identity check 128 and definition 637, first-version guard 167-169, insert origin 181, binding 188, and claim version/binding checks 372-373. Grep of `DelayedDispatchRef|DelayedEnqueueOutcome|DelayedReservationState|SEND_QUEUED|SEND_CANCELLED|SEND_REQUEUED|delayExpectedRevision` across src/main located the producers and receiving sites cited above. Counts/callsite assertions do not rest on reading impressions.

### AUTO_FIX
- N/A. F-9 and F-12 are closed; no new proven four-gate violation was found.

### RECORD_ONLY
- O-1: Required command 3 retains supplied pre-existing MailReviewQueryIT:239: 99 run / 1 failure / 0 errors / 0 skipped, lifecycle 40/40 and RepositoryIT 8/8. Outside child repair scope; not repaired and no separate baseline reproduction.
- O-2: Previously recorded optional full-suite America/Coyhaique failures remain excluded. Optional suite was not run; no fresh result or repair is claimed.

### Required Action
- COMPLETE_CHILD
