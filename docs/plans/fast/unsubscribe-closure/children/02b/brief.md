# Fast-P Child Brief — 02b (mailto-channel)

- Master: docs/plans/2026-08-11/unsubscribe-closure-master.md
- Plan: docs/plans/2026-08-11/unsubscribe-02b-mailto-channel.md — **the complete approved contract. Read it first.**
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/unsubscribe-closure
- Branch: fast/unsubscribe-closure
- Child base (product boundary): `f09f8c314951279aaabd025d31d4e045d2928aa6` (child 02 code head)
- Execution report: docs/plans/fast/unsubscribe-closure/children/02b/execution.md

## Authority

You are authorized to create exactly one local implementation commit for this child on branch `fast/unsubscribe-closure` in the worktree above. No push, merge, rebase, amend, or history rewrite. Do NOT commit anything under `docs/plans/fast/` — the controller commits fast-p evidence separately.

## Process

1. Read `skill://execute-p` and follow it against the child plan file above.
2. Implement exactly the plan's tasks T-1..T-2 and the 变更文件清单 (4 files). Modify **only** those 4 authorized files:
   - `src/main/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionService.kt`
   - `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
   - `src/test/kotlin/com/weibo/talentintroduction/mail/service/EmailSuppressionServiceTest.kt`
   - `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`
3. Preserve every invariant I-1..I-3 exactly as specified (subject exact-equality only, set `unsubscribe|退订|取消订阅`; subject-priority source distinction MAILTO vs INBOUND_REPLY; all 3 capture call sites pass `received.subject`). `UNSUBSCRIBE_PHRASES` and `looksLikeUnsubscribe` public behavior must not change.
4. Run every required command from the plan's 验证命令 section, in the worktree, with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`:
   - Full regression: `mvn test` (must pass)
   - `mvn test -Dtest=EmailSuppressionServiceTest`
   - `mvn test -Dtest=AutoMailReplyServiceTest`
   - `mvn clean package` (must be BUILD SUCCESS)
   - `git diff --check`
5. Commit locally as `feat(fast-p): implement 02b` containing only the 4 authorized files.
6. Write the full result to `docs/plans/fast/unsubscribe-closure/children/02b/execution.md` (append-only; include commands, exit codes, test counts, deviations if any, and your agent identity). Do not include this file or any fast-p file in the commit.
7. Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

## Prior-child context (children 01 and 02 already landed)

- `EmailSuppressionService.kt` has a top-level `RecipientSuppressedException : IllegalStateException` appended at file end (child 02). Your plan does not touch it — do not move, remove, or edit the exception class; it lives in the same file your plan modifies. Only add the subject detection + `detectUnsubscribeSource` inside the service class body per the plan.
- `application.yml` has `placeholder-replacement: false`; V87 migration exists. Do not touch.
- `ComposedMail` has `allowSuppressedRecipient` field; `SmtpMailDeliveryService` has the fail-closed check. Do not touch.
- Full suite baseline at master base 8e8ddfc: exit 0, BUILD SUCCESS, no baseline failures. Current suite: 2290 tests green.

## Global constraints

- No formatters/linters/project-wide unrelated test runs beyond the named commands.
- Do not fix unrelated bugs; do not touch files outside the authorized list even if tests fail for them — report in execution.md instead.
- If the plan conflicts with reality (missing symbol, wrong line number), do NOT improvise: return `PLAN_CONFLICT` with evidence.
- No later children in this run.
