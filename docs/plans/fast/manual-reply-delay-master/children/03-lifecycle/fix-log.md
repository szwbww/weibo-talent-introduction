# Fast-P Fix Log — 03-lifecycle

## Epoch 1 — No repairs
- Fix round: 0
- Fix commits: —
- Result: PAUSED_FOR_HUMAN before automatic repair.
- Reason: Verify03Lifecycle returned PAUSE for P-1 authority conflict. F-1 through F-11 remain open; no repair dispatch or repair commit occurred.
- Resume: after explicit authority resolution, preserve this epoch and start a new epoch at repair counter zero. Do not replay completed children.

## Epoch 2 — Round 1/3

- Result: **PAUSE** — F-1–F-11 and P-1 repair implemented in the epoch-2 authorized scope; required MySQL command exposes one necessary existing-test transaction compatibility change outside that scope. No production lock bypass restored. Independent verification remains pending.
- Executor: `Fix03Epoch2Round1`.
- Findings: F-1, F-2, F-3, F-4, F-5, F-6, F-7, F-8, F-9, F-10, F-11, P-1.
- Before SHA (product): `798206d5b2c8dafd892f5c5f275e952a99c18fa8`.
- Before HEAD (including authority/evidence): `bba47650a7aab1c3b145220250ae869c6f990490`.
- Product base: `72906c0c5e36393429320817ea0de4f7d6f3ff05`.
- Actual Fix commit SHA: `ed041935a500aa4517ca9f880644a2e7110063d5`.
- Commit subject: `fix(fast-p): repair 03-lifecycle round 1`.
- Target root: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`.
- Branch: `fast/manual-reply-delay-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`.
- Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Epoch-2 plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md`.
- Execution identity: the above canonical path + SHA-256 `db5277f157a23fbc4e37ffa45b51d3ea99822dc00c4ff526b9caf020e5fef692` (approved A2/A3, plan commit `ea61d579425e894ee37c600f599beef1fdb46395`).

### Files in the repair commit

1. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/DelayedManualReplyRepository.kt` — real LEGACY source lock and current account availability query.
2. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepository.kt` — mandatory source-lock transaction, SQL save/discard guards against unresolved delayed bindings.
3. `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailReviewDraftRepository.kt` — ordered attempt/job locks for review preparation revision validation.
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyService.kt` — fixed actor/origin identity; job/attempt routing; separate admission versus result-transition locks; claim revalidation; retry capability/admission validation; inherited dispatch-reference type.
5. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReplyDraftService.kt` — READ_COMMITTED writes, exact completed binding release, and completed-source idempotent reference handling.
6. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftModels.kt` — nullable `delayExpectedRevision` appended with default.
7. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReviewDraftService.kt` — CANCELLED preparation revision, original-author authorization locking, result persistence independent of current grants/context.
8. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualReplySendAttemptService.kt` — `DelayedDispatchRef`, current locked attempt identity, exact result source/token checks, same-token SENT idempotent record return.
9. `src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyLifecycleIT.kt` — trusted-ref completion fixtures and 15 additional MySQL tests, for 37 lifecycle tests total.

The authorized `MailReplyDraftSendIntegrationTest.kt` and `MailReviewDraftIT.kt` were intentionally unchanged; their existing assertions pass. No plan/ledger/fast-p artifact is in the product commit. No migrations, application configuration, unrelated tests, or other child implementation files were changed.

### Commands at the final product state

Every Maven invocation used `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` in the target worktree.

| Command after environment prefix | Exit | Executed / failures / errors / skipped | Evidence |
|---|---:|---|---|
| `mvn -q -DskipTests test-compile` | 0 | compilation only; 0 tests | bg_94, 186.79s |
| `mvn test -Dtest='ManualReplySendAttemptServiceTest,MailReplyDraftServiceTest'` | 0 | JVM 76 / 0 / 0 / 0 | artifact://2116; 192.30s |
| `mvn test -Dtest='DelayedManualReplyLifecycleIT,MailReplyDraftRepositoryIT,MailReplyDraftSendIntegrationTest,MailReviewDraftIT,MailReviewQueryIT' -DmysqlIt=true` | 1 | JVM 96 / 1 / 1 / 0; 94 passed | artifact://2118; 390.00s |
| `mvn test -Dtest='PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest'` | 0 | JVM 103 / 0 / 0 / 0 | artifact://2122; 204.37s |
| `git diff --check` | 0 | no whitespace errors | native target-worktree check before commit |

- MySQL class results: lifecycle 37/37; draft send integration 27/27; review draft 19/19; review query 4/5 with the known line-239 failure; draft repository 7/8 with one error at line 97.
- MySQL 8.0.36 ran in isolated Testcontainers: localhost ports 33448 (`review_query`), 33449 (`talent_introduction`), 33450 (`review_draft`), 33451 (`talent_introduction`), 33452 (`delayed_reply`). No production database or SMTP was contacted.
- Commands 2 and 4 each also ran Maven-bound frontend tests: 2038 tests / 354 suites / 2038 pass / 0 fail / 0 skipped, plus successful configured app and task-modal-runtime syntax stages. Command 3 stopped before those stages because Surefire failed.
- A preliminary compile (bg_93 / artifact://2113) exited 1 because three new matrix calls incorrectly used `.name` on String attempt statuses. Corrected only those type errors before the final successful compile and required test commands. It is not counted as final-state success.
- No optional full suite, baseline reproduction, extra worktree, browser acceptance, production SMTP, or independent review/verification skill was run.

### Runtime and regression evidence

- F-1: latch-controlled permission-read → enqueue commit → save continuation proves stale permission snapshot cannot overwrite the now-WAITING draft.
- F-2/F-6: current downgrade/revocation/new-inbound rejects admission or claim; admin cancellation, preflight failure, missing-source stale recovery, and actual-result persistence remain possible as appropriate.
- F-3/F-5/F-9: cross-origin/cross-actor fingerprint collisions reject; SENDING repeat retains projection; failed retry → cancellation → current-revision requeue succeeds; real competing LEGACY fingerprints produce one job/attempt.
- F-4: identical-body save after failure clears the old exact binding and retries the newer version; changed-body save produces a new job while the failed job cannot advertise retry.
- F-7/F-8: claim and finalization share existing internal `DelayedDispatchRef`; duplicate definite success returns the same record without repeated writes; an old token cannot finish a newly claimed later round.
- F-10: account and attachment failures remain non-retryable until current state/bytes pass validation; source/grant/context checks also gate capability and retry admission. Transition responses do not re-enter admission locks after attempt/job locks; later standalone projection performs current validation.
- F-11: real-MySQL retry/finalize barrier, original cancel/claim/save/discard races, illegal matrix case, current permissions, trusted draft/review refs, and same-token UNKNOWN convergence all execute. SQL triggers inject QA, RAG, calendar, final-job, and review-result write failures; record, attempt, job and affected draft/review writes roll back together. Removing each trigger permits exactly one durable success and duplicate success is read-only. Triggers are removed in `finally`.
- Direct real-service transaction smoke is supplied by the executed lifecycle test `real service transaction smoke preserves attached draft and commits one result`: real attachment upload/storage and service transactions exercise enqueue/repeat, blocked save/discard, cancellation with exact body/context/attachment preservation, attachment-integrity failure and repair, retry/claim, and durable finalization/idempotent result. SMTP delivery is deliberately not exercised or claimed. No throwaway scaffold was created; fixture attachment cleanup uses the existing support shutdown hook.
- P-1: first preparation accepts absent revision; CANCELLED preparation rejects absent/stale revision (409), nonpositive revision (400), and accepts the current revision while retaining APPROVED content/version.

### Scope boundary and next epoch

- The known `MailReviewQueryIT:239` failure matches the recorded baseline and remains RECORD_ONLY.
- New required-command error: `MailReplyDraftRepositoryIT.real attachment metadata ownership empty save missing restoration and business state isolation`, line 97, throws `Draft send operations require an existing transaction`. Lines 87/97 directly construct `MailReplyDraftService` and call save without the Spring transaction proxy. The production lock guard was not weakened to accommodate this caller.
- Existing service constructor collaborators contain no transaction manager/template; the repository exposes CRUD/locks, not a whole-save transaction executor. The minimal compatibility amendment is a real READ_COMMITTED transaction around that existing test's raw-service writes (or its existing Spring proxy), with all behavioral assertions retained.
- Human approval for necessary test compatibility arrived during closing; controller A5/A6 changed the plan bytes to SHA-256 `d3c5cdddd4daf0c3a58fef12036b0d6dd27b6546c70048b1e4528c6a00ac7b82`. Controller explicitly authorized this epoch-2 closing commit against the original completed scope, with the new test-file authority held for epoch 3. This report does not transfer completion to the new plan identity. `MailReplyDraftRepositoryIT.kt` was not edited.
- Optional worktree helper failed because it resolves an unrelated stale publish worktree path with `strict=True`. Controller authorized native read-only identity fallback; exact root/branch/git-dir/HEAD were checked before staging, and `git merge-base --is-ancestor HEAD fast/manual-reply-delay-master` succeeded after the product commit. No stale worktree was pruned or modified.
- Next action: controller checkpoints epoch-2 evidence and approved A5/A6, then executes the bounded existing-test compatibility change under epoch 3 and dispatches fresh independent verification. Do not advance to child 04 or claim overall FIXED at this scope boundary.

### Controller canonical closing fields — epoch 2 round 1
- Before: 798206d5b2c8dafd892f5c5f275e952a99c18fa8
- Fix commit: ed041935a500aa4517ca9f880644a2e7110063d5
- Result: PAUSE
- Notes: Canonical field aliases for the actual commit already reported above; no new execution or verifier verdict. A5/A6 authority is now recorded; next work is epoch3 compatibility repair, not redoing epoch2.

