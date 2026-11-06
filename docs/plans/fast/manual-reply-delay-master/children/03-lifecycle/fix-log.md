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


## Epoch 3 — Round 1/3

- Before: ed041935a500aa4517ca9f880644a2e7110063d5
- Fix commit: 63bf74170d126428dc8a9cab279506cca157bc90
- Findings: A5/A6 existing-test compatibility only. The raw-service attachment/ownership test now uses the same Spring `ProxyFactory` / `TransactionInterceptor` pattern as the existing HTTP test, backed by the existing isolated datasource transaction manager. Production `save` annotations supply real REQUIRES_NEW READ_COMMITTED transactions. All existing business assertions and mocks remain unchanged; no F-1–F-11/P-1 production work was replayed.
- Authorized files changed: `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt` only (5 insertions, 1 deletion). This append-only report is excluded from the commit; controller-owned ledger changes were preserved.
- Commands: All four ran freshly after the final edit in the retained target worktree with `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.

  | Command after environment prefix | Exit | Executed / failures / errors / skipped | Evidence |
  |---|---:|---|---|
  | `mvn -q -DskipTests test-compile` | 0 | compilation only; 0 tests | bg_98; 197.41s |
  | `mvn test -Dtest='ManualReplySendAttemptServiceTest,MailReplyDraftServiceTest'` | 0 | JVM 76 / 0 / 0 / 0 | artifact://2133; 183.35s |
  | `mvn test -Dtest='DelayedManualReplyLifecycleIT,MailReplyDraftRepositoryIT,MailReplyDraftSendIntegrationTest,MailReviewDraftIT,MailReviewQueryIT' -DmysqlIt=true` | 1 | JVM 96 / 1 / 0 / 0; 95 passed | artifact://2135; 352.40s |
  | `mvn test -Dtest='PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest'` | 0 | JVM 103 / 0 / 0 / 0 | artifact://2138; 187.61s |
  | `git diff --check` | 0 | no whitespace errors | final-state precommit check |

- Result: FIXED
- Notes:
  - Execution result: READY_FOR_VERIFICATION, not independent PASS. Fresh independent verification remains required.
  - MySQL results: repository 8/8 (the former no-transaction error is gone), lifecycle 37/37, draft send 27/27, review draft 19/19, review query 4/5. The sole failure is the documented `MailReviewQueryIT:239` (`expected true but was false`), retained as baseline-only. No baseline rerun or optional full suite was performed.
  - MySQL 8.0.36 Testcontainers used isolated localhost ports 33454 (`review_query`), 33455 (`talent_introduction`), 33456 (`review_draft`), 33457 (`talent_introduction`), 33458 (`delayed_reply`). No production datasource was used. No production SMTP/browser proof is claimed.
  - Commands 2 and 4 each also passed Maven-bound frontend tests: 2038 tests / 354 suites / 2038 pass / 0 fail / 0 skipped, and configured app/task-modal-runtime syntax checks. Command 3 stopped at Surefire before frontend stages.
  - Epoch: NEW; executor `Fix03Epoch3Round1`; approval: current amended A5/A6 invocation. Plan canonical path `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md`; SHA-256 `d3c5cdddd4daf0c3a58fef12036b0d6dd27b6546c70048b1e4528c6a00ac7b82`, rechecked unchanged before commit. Its Git blob matches approved identity commit `0bfe5b07c85e76f85c43a31d30fde8b37f30b9b9`.
  - Precommit authority/evidence HEAD was `1b9690d555d8a0d12931a5c98945f7a56555c4b3`; product Before remains the canonical SHA above. Post-execution code SHA and Evidence HEAD are the Fix commit; no evidence commit was made.
  - Worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`, branch `fast/manual-reply-delay-master`, Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`, common Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`. Optional identity helper failed on the unrelated stale registered publish worktree; authorized native identity checks were used before staging/commit. Fix commit equals target HEAD and is reachable from the recorded branch (`git merge-base --is-ancestor` exit 0). Only pre-existing controller ledger changes remained after commit, before this report append.
  - No production edits, new worktree, scaffold, migration, mocking-contract change, unrelated test change, or review-fast-p invocation. Historical results were used only to classify the allowed baseline failure.


## Epoch 3 — Round 2/3

- Before: 63bf74170d126428dc8a9cab279506cca157bc90
- Fix commit: 9bbb3b050f533a0010d48d545b4756abc8aa7e82
- Findings: F-9, F-12.
- Authorized files changed:
  - `src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyLifecycleIT.kt`
- Commands: Final-state commands ran in the retained worktree with `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`.

  | Command after environment prefix | Exit | Executed / failures / errors / skipped | Evidence |
  |---|---:|---|---|
  | `mvn -q -DskipTests test-compile` | 0 | compilation only; 0 tests | bg_108; 176.12s |
  | `mvn test -Dtest='ManualReplySendAttemptServiceTest,MailReplyDraftServiceTest'` | 0 | JVM 76 / 0 / 0 / 0 | artifact://2167; 182.60s |
  | `mvn test -Dtest='DelayedManualReplyLifecycleIT,MailReplyDraftRepositoryIT,MailReplyDraftSendIntegrationTest,MailReviewDraftIT,MailReviewQueryIT' -DmysqlIt=true` | 1 | JVM 99 / 1 / 0 / 0; 98 passed | artifact://2169; 367.22s |
  | `mvn test -Dtest='PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest'` | 0 | JVM 103 / 0 / 0 / 0 | artifact://2172; 188.10s |
  | `git diff --check` | 0 | no whitespace errors | precommit check |

- Result: FIXED
- Notes:
  - READY_FOR_VERIFICATION; this is execution evidence, not independent PASS. Fresh distinct re-verification follows.
  - F-9: existing CANCELLED requeue and FAILED retry now apply the existing other-unresolved-job exclusion under the source lock before CAS. Retry uses the original job actor for source matching, including when admin retries. Existing attempt exclusion, true inbound target, revision checks and dispatch token behavior remain unchanged.
  - F-12: first DRAFT insertion rejects an original snapshot version differing from the locked validated draft version before insert/bind. Explicit CANCELLED requeue and FAILED retry retain same-content current-version refresh.
  - Added three MySQL regressions: terminal A / waiting B rejects A requeue; terminal A / waiting B rejects owner and admin retry; unchanged autosave before first enqueue rejects stale preparation without persisting an attempt/job/binding. Assertions inspect persisted rows, revisions/snapshots, attempt states and actual claimability. Fresh snapshot admission remains claimable.
  - MySQL results: lifecycle 40/40, repository 8/8, draft send 27/27, review draft 19/19, review query 4/5. Sole failure remains the recorded `MailReviewQueryIT:239` (`expected true but was false`); no repair or separate baseline reproduction. Earlier closed findings and RECORD_ONLY notes were untouched.
  - Isolated MySQL 8.0.36 Testcontainers ports: 33466 review_query, 33467 talent_introduction, 33468 review_draft, 33469 talent_introduction, 33470 delayed_reply. No production datasource, browser or SMTP proof claimed.
  - Commands 2 and 4 also each passed Maven-bound frontend tests: 2038 tests / 354 suites / 2038 passed / 0 failed / 0 skipped, plus configured app/task-modal-runtime syntax checks. Command 3 stopped at Surefire.
  - Initial test-compile failed at new assertion line 551 on nullable cross-module `saved.id` smart casting (artifact://2164, exit 1, 157.11s; no tests). Bounded mechanical correction added `requireNotNull`; all four required commands above ran after that final edit.
  - Plan canonical path `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-03-lifecycle.md`; SHA-256 `d3c5cdddd4daf0c3a58fef12036b0d6dd27b6546c70048b1e4528c6a00ac7b82`, unchanged on closing identity check; approved identity `0bfe5b07c85e76f85c43a31d30fde8b37f30b9b9`.
  - Retained worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`, branch `fast/manual-reply-delay-master`, Git directory `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`. Optional worktree helper again failed on the unrelated stale publish worktree; used the already-authorized native read-only identity fallback. Commit equals target HEAD and `git merge-base --is-ancestor HEAD fast/manual-reply-delay-master` exited 0.
  - Product commit contains only the two authorized files (87 insertions). This append-only log is uncommitted; controller-owned verify-log/ledger changes preserved. No plan change, migration, new class/interface/state, extra worktree, optional suite, scaffold or review-fast-p invocation.

### Terminal closing record — epoch 3

Reverify03Round2 independently returned LIGHT_PASS_WITH_NOTES / COMPLETE_CHILD at 9bbb3b050f533a0010d48d545b4756abc8aa7e82; F-9/F-12 closed and earlier corrections retained. Current epoch has two repair rounds; prior epoch2 has one, all SHAs preserved above. No new tests were run for this closing record.

