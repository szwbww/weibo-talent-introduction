# Fast-P Child Brief — 04 全部收信分支的元数据登记与幂等

- Master: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb5)
- Plan: docs/plans/2026-09-07/04-inbound-metadata-persistence.md — the complete approved contract. Read it first.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials (branch fast/mailbox-materials)
- Base SHA: 95d8661ed3d9e0557fe69cce6f6dde24dbea5e79 (= child 03 Code head)
- Execution report: docs/plans/fast/mailbox-materials/children/04/execution.md

## Role
Use execute-p (read skill://execute-p first). Implement ONLY child 04. Serialized run: children 05+ untouched. You are the sole writer.

## Authorized files (exactly the plan's 10-file list)
1. src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt (modify)
2. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt (modify)
3. src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt (modify)
4. src/main/kotlin/com/weibo/talentintroduction/mail/domain/InboundMailProcessing.kt (modify)
5. src/main/kotlin/com/weibo/talentintroduction/mail/repository/InboundMailProcessingRepository.kt (modify)
6. src/main/resources/db/migration/V120__scope_inbound_uid_by_validity.sql (new)
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt (modify)
8. src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt (modify)
9. src/test/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailServiceTest.kt (modify)
10. src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt (modify: target 119→120)

No other files. Compile/test proof requiring an unlisted file (including any line-number-pin test) → return PLAN_CONFLICT.

## Constraints
- Invariants I-1..I-4 of the plan; migration contract from master 持久化契约 04 section: uid_validity BIGINT NOT NULL DEFAULT 0 (0 = historical unknown only), unique key replaced by (account/uid_validity/uid), history NOT backfilled, LEGACY_UID_UNVERIFIABLE via existing MANUAL_REVIEW+reason (no second business state field), BODY_TRUNCATED same. No transfer-table changes (V119 shape from child 02 is fixed).
- Environment: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home.
- Preserve state machine (ConversationStateService.transition only), classification, send logic order; no new auto SMTP retry; the SMTP-success/DB-failure out-of-transaction risk is recorded, NOT claimed fixed.
- Migration numbering: V120 free (max V119). Verify before writing; never overwrite an applied migration.
- MySQL test DB provisioned at 127.0.0.1:3306 (root/root, talent_introduction). Flyway IT bare invocation env-blocked; known-good: DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock + -Dapi.version=1.40.
- Fast-p evidence excluded from your commit.

## Required commands (run all; exact output + exit codes in execution.md)
1. Targeted tests: JAVA_HOME=... mvn test -Dtest=AutoMailReplyServiceTest,MailAttachmentServiceTest,UnmatchedInboundMailServiceTest
2. Flyway IT (workaround): JAVA_HOME=... DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test
3. Full suite: JAVA_HOME=... mvn test

## Commit
Commit the implementation locally as: `feat(fast-p): implement 04` — 10 authorized files only. No push/merge/amend/rebase.

## Acceptance for return
Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Write full result to execution.md (files changed, per-command evidence, I-1..I-4 checks, deviations).
