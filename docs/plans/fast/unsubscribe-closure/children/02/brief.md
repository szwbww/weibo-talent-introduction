# Fast-P Child Brief — 02 (suppression-gate)

- Master: docs/plans/2026-08-11/unsubscribe-closure-master.md
- Plan: docs/plans/2026-08-11/unsubscribe-02-suppression-gate.md — **the complete approved contract. Read it first.**
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/unsubscribe-closure
- Branch: fast/unsubscribe-closure
- Child base (product boundary): `6a822c6a6ee0f3a94dd31c2660cfac922333e535` (child 01 code head)
- Execution report: docs/plans/fast/unsubscribe-closure/children/02/execution.md

## Authority

You are authorized to create exactly one local implementation commit for this child on branch `fast/unsubscribe-closure` in the worktree above. No push, merge, rebase, amend, or history rewrite. Do NOT commit anything under `docs/plans/fast/` — the controller commits fast-p evidence separately.

## Process

1. Read `skill://execute-p` and follow it against the child plan file above.
2. Implement exactly the plan's tasks T-1..T-6 and the 变更文件清单 (9 files). Modify **only** those 9 authorized files:
   - `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt`
   - `src/main/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionService.kt`
   - `src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt`
   - `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`
   - `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt`
   - `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt`
   - `src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt`
   - `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`
   - `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt`
3. Preserve every invariant I-1..I-5 and interaction point IP-1..IP-4 exactly as specified (fail-closed check before `getSender`; exception-only interception; `RecipientSuppressedException : IllegalStateException`; `allowSuppressedRecipient` default false; claim-before check at `PendingMailOperationService` before `prepareAndClaim`).
4. Run every required command from the plan's 验证命令 section, in the worktree, with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`:
   - Full regression: `mvn test` (must pass)
   - `mvn test -Dtest=SmtpMailDeliveryServiceTest`
   - `mvn test -Dtest=PendingMailOperationServiceTrustWorkbenchTest`
   - `mvn test -Dtest=InitialOutreachServiceTest`
   - `mvn test -Dtest='ManualExpertMailService*Test,ManualInitialOutreachServiceTest,MeetingScheduleServiceTest,AutoMailReplyServiceTest,BatchSendTaskRuntimeIntegrationTest'`
   - `mvn clean package` (must be BUILD SUCCESS)
   - `git diff --check`
5. Commit locally as `feat(fast-p): implement 02` containing only the 9 authorized files.
6. Write the full result to `docs/plans/fast/unsubscribe-closure/children/02/execution.md` (append-only; include commands, exit codes, test counts, deviations if any, and your agent identity). Do not include this file or any fast-p file in the commit.
7. Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

## Prior-child context (child 01 already landed)

- `src/main/resources/application.yml` now has `spring.flyway.placeholder-replacement: false` (child 01). Your plan does not touch it — do not revert or re-edit it.
- `V87__append_unsubscribe_line_to_cold_outreach_templates.sql` exists (child 01). Do not touch.
- Full suite baseline at master base 8e8ddfc: exit 0, BUILD SUCCESS, no baseline failures.

## Global constraints

- No formatters/linters/project-wide unrelated test runs beyond the named commands.
- Do not fix unrelated bugs; do not touch files outside the authorized list even if tests fail for them — report in execution.md instead.
- If the plan conflicts with reality (missing symbol, wrong line number), do NOT improvise: return `PLAN_CONFLICT` with evidence.
- Do not review or modify later children (02b).
