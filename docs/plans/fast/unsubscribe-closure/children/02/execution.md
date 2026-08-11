# Child 02 Execution Report — suppression-gate (fail-closed)

- Plan: `docs/plans/2026-08-11/unsubscribe-02-suppression-gate.md` (canonical path in this worktree)
- Plan SHA-256: `7ee9a4eaf9c3a5dec82542ab3bd60c4dc695d98e14cbd3fbf8b511c995fff892`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/unsubscribe-closure/docs/plans/2026-08-11/unsubscribe-02-suppression-gate.md@7ee9a4eaf9c3a5dec82542ab3bd60c4dc695d98e14cbd3fbf8b511c995fff892`
- Execution epoch: NEW
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/unsubscribe-closure@fast/unsubscribe-closure@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/unsubscribe-closure`
- Child base (product boundary): `6a822c6a6ee0f3a94dd31c2660cfac922333e535` (child 01 code head)
- Pre-execution HEAD: `bf0cb023f3d71a56810f99596dfba1f1d122bcaf` (controller evidence commit on top of child 01)
- Implementation commit: `f09f8c314951279aaabd025d31d4e045d2928aa6` — `feat(fast-p): implement 02` (9 files, +189 −22)
- Executor: `Impl02` (task subagent, execute-p)
- JDK: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn invocation

## Tasks implemented

| Task | Status | Files |
|---|---|---|
| T-1 ComposedMail.allowSuppressedRecipient = false | IMPLEMENTED | `IntroductionMailComposer.kt` |
| T-2 RecipientSuppressedException : IllegalStateException | IMPLEMENTED | `EmailSuppressionService.kt` (appended) |
| T-3 SmtpMailDeliveryService 4th dep + fail-closed first-line check | IMPLEMENTED | `SmtpMailDeliveryService.kt` |
| T-4 PendingMailOperationService pre-claim 400 check + new dep | IMPLEMENTED | `PendingMailOperationService.kt` |
| T-5 ManualMailSendCommand.allowSuppressed + compose() passthrough; ManualMailSendRequest + toCommand() | IMPLEMENTED | `ManualExpertMailService.kt`, `ExpertContactManagementController.kt` |
| T-6 tests/fixtures (21+3 / +2 / +1) | IMPLEMENTED | 3 test files |

## Commands (all run freshly in this invocation, in the worktree)

| Command | Exit | Result |
|---|---|---|
| `mvn test -Dtest=SmtpMailDeliveryServiceTest` | 0 | Tests run: 24, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS |
| `mvn test -Dtest=PendingMailOperationServiceTrustWorkbenchTest` | 0 | Tests run: 28, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS |
| `mvn test -Dtest=InitialOutreachServiceTest` | 0 | Tests run: 10, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS |
| `mvn test -Dtest='ManualExpertMailService*Test,ManualInitialOutreachServiceTest,MeetingScheduleServiceTest,AutoMailReplyServiceTest,BatchSendTaskRuntimeIntegrationTest'` | 0 | Tests run: 140, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS (Gate 5 + AutoMailReply 40 + ManualExpert 24 + ManualInitialOutreach 43 + MeetingSchedule 7 + BatchSendTask 21) |
| `mvn test` (full regression) | 0 | Tests run: 2290, Failures: 0, Errors: 0, Skipped: 4 — BUILD SUCCESS |
| `mvn clean package` | 0 | Tests run: 2290, Failures: 0, Errors: 0, Skipped: 4 — BUILD SUCCESS |
| `git diff --check` | 0 | clean |

Note: `mvn clean package` output also contains the WAR package step (BUILD SUCCESS); the artifact build was exercised by the same command.

## Acceptance-criteria evidence

- I-1: `git diff` shows the suppression check as the first statement of `send()`, before `smtpSenderFactory.getSender(account)`; new test asserts `verify(factory, never()).getSender(anyValue(testAccount()))` — passed.
- I-2: `grep -n "SUPPRESSED" SmtpMailDeliveryService.kt` → zero hits; `SmtpErrorCategory.kt` and `MailDeliveryService.kt` unchanged (not in the 9-file diff).
- I-3: `RecipientSuppressedException(email: String) : IllegalStateException("收件人已退订，禁止外发：$email")`; type test passed.
- I-4: both new fields default `false`; only `ManualExpertMailService.composeComposeTemplate()` sets `allowSuppressedRecipient`; `BatchMailSendRequest.toCommand()` untouched (no field).
- I-5/IP-3: check at `PendingMailOperationService.kt:255`, `prepareAndClaim` at `:262` — check precedes claim; both new workbench tests pass (`prepareAndClaim` never, `send` never, `finalizeFailure` never, 400 BAD_REQUEST).
- IP-2: new `InitialOutreachServiceTest` case passes (all-suppressed batch: skipped=2, sent=0, failed=0, no `status = "FAILED"` records, `send` never).
- IP-4: `ManualInitialOutreachService.kt` untouched; `ManualMailSendCommand(` construction there inherits default false.

## Deviations / observations

1. One implementation hiccup fixed during iteration: `ResponseStatusException.statusCode` is Spring 6 API; this project is Spring 5 (JDK 11), so the new test asserts `ex.status` (HttpStatus) instead. No product code change.
2. `Mockito.any()` returns null and Kotlin null-checks non-null parameters, causing NPE in the first draft of the new `never()` verifies; switched to the file-local `anyValue(default)` helper pattern already established in these test files. No product code change.
3. Plan I-4 acceptance text says `grep allowSuppressedRecipient` should hit "恰好 2 处" (definition + ManualExpertMailService construction), but the plan's own T-3 snippet requires reading the field inside `SmtpMailDeliveryService.send()` — so the actual count is 3 in `src/main/kotlin` (definition at `IntroductionMailComposer.kt:79`, explicit set at `ManualExpertMailService.kt:255`, read at `SmtpMailDeliveryService.kt:20`). The field is defined once and only ever explicitly set by the manual single-send path, so the behavioral contract of I-4 is fully met. Recorded as an observation, not a conflict.
4. Plan line numbers referenced the pre-change file (e.g. `ManualExpertMailService.kt:244` for the `ComposedMail(` construction); after T-5 edits the construction sits at `:255`. Same code point.

## Freshness

- Plan identity rechecked after implementation: YES (SHA unchanged `7ee9a4ea…`)
- Worktree identity rechecked before commit and after commit: YES (root/branch/git-dir unchanged)
- Commit reachable from target branch: YES — `f09f8c3` is HEAD of `fast/unsubscribe-closure`
- Required commands run this invocation: YES (all 7 listed above, freshly, after final implementation state)
- Historical evidence used only as baseline: YES

## Remaining blocker

- None.
