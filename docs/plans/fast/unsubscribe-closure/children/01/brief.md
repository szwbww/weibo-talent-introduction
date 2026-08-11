# Fast-P Child Brief — 01 (body-link)

- Master: docs/plans/2026-08-11/unsubscribe-closure-master.md
- Plan: docs/plans/2026-08-11/unsubscribe-01-body-link.md — **the complete approved contract. Read it first.**
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/unsubscribe-closure
- Branch: fast/unsubscribe-closure
- Child base (product boundary): `8e8ddfcd6c02c754de3e50b3c02004a2900e5be5`
- Execution report: docs/plans/fast/unsubscribe-closure/children/01/execution.md

## Authority

You are authorized to create exactly one local implementation commit for this child on branch `fast/unsubscribe-closure` in the worktree above. No push, merge, rebase, amend, or history rewrite. Do NOT commit anything under `docs/plans/fast/` — the controller commits fast-p evidence separately.

## Process

1. Read `skill://execute-p` and follow it against the child plan file above.
2. Implement exactly the plan's tasks T-1..T-4 and the 变更文件清单 (4 files). Modify **only** those 4 authorized files:
   - `src/main/resources/application.yml`
   - `src/main/resources/db/migration/V87__append_unsubscribe_line_to_cold_outreach_templates.sql` (new)
   - `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeBodyLinkMigrationTest.kt` (new)
   - `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt`
3. Preserve every invariant I-1..I-4 and interaction point IP-1..IP-4 exactly as specified (CONCAT + NOT LIKE guard + placeholder-replacement: false in the same commit; no `mail_template` writes; literal unsubscribe line as given; no changes to the 5 IP-4 frozen files).
4. Run every required command from the plan's 验证命令 section, in the worktree, with `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`:
   - Full regression: `mvn test` (must pass)
   - `mvn test -Dtest=UnsubscribeBodyLinkMigrationTest`
   - `mvn test -Dtest=MailVariableServiceTest`
   - `mvn clean package` (must be BUILD SUCCESS)
   - `git diff --check`
   The `-DmigrationIt=true` Docker test is optional; skip it (no Docker assumption).
5. Commit locally as `feat(fast-p): implement 01` containing only the 4 authorized files.
6. Write the full result to `docs/plans/fast/unsubscribe-closure/children/01/execution.md` (append-only; include commands, exit codes, test counts, deviations if any, and your agent identity). Do not include this file or any fast-p file in the commit.
7. Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, commit SHA, command summary, report path.

## Global constraints

- No formatters/linters/project-wide unrelated test runs beyond the named commands.
- Do not fix unrelated bugs; do not touch files outside the authorized list even if tests fail for them — report in execution.md instead.
- If the plan conflicts with reality (missing symbol, wrong line number), do NOT improvise: return `PLAN_CONFLICT` with evidence.
- Do not review or modify later children (02, 02b).
