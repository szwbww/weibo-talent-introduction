# Fast-P Child Brief — 02 有界、持久化附件传输服务

- Master: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb5)
- Plan: docs/plans/2026-09-07/02-attachment-transfer-core.md — the complete approved contract. Read it first.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials (branch fast/mailbox-materials)
- Base SHA: 8779d71f00567625ebebe206801994aefcc5725b (= child 01 Code head)
- Execution report: docs/plans/fast/mailbox-materials/children/02/execution.md

## Role
Use execute-p (read skill://execute-p first). Implement ONLY child 02. Serialized run: children 03+ untouched. You are the sole writer.

## Authorized files (exactly the plan's 10-file list)
1. src/main/resources/db/migration/V119__create_mail_attachment_transfer.sql (new)
2. src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachmentTransfer.kt (new)
3. src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailAttachmentTransferRepository.kt (new)
4. src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferService.kt (new)
5. src/main/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferWorker.kt (new)
6. src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapAttachmentContentFetcher.kt (new)
7. src/main/kotlin/com/weibo/talentintroduction/config/MailAttachmentStorageProperties.kt (modify)
8. src/test/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferServiceTest.kt (new)
9. src/test/kotlin/com/weibo/talentintroduction/mail/service/AttachmentTransferWorkerIT.kt (new)
10. src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt (modify: target 118→119)

No other files. Compile proof requiring an unlisted file → return PLAN_CONFLICT, never extend scope.

## Constraints
- Invariants I-1..I-4 of the plan; the complete mail_attachment_transfer field/state/queue contract is in master plan 持久化契约 (02 table + queue rules) — authoritative types/defaults/CHECKs/uniqueness/indexes and the claim-lease semantics. Do NOT invent extra states/columns.
- Migration numbering: V119 free (child 01 added V118; max is now V118). Verify before writing; never overwrite an applied migration.
- Environment: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home.
- Preserve existing patterns; no adjacent refactors. Fast-p evidence (docs/plans/fast/**) excluded from your commit.
- MySQL test DB for the mysqlIt gate is provisioned by the controller at 127.0.0.1:3306 (root/root, database talent_introduction) — matches src/test/resources/application.yml defaults; DO NOT start or stop it.
- Flyway IT bare invocation is environment-blocked in this machine (docker-java client API 1.32 vs OrbStack daemon min API 1.40 → "Docker is required"); known-good invocation: DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock and -Dapi.version=1.40 (child 01 precedent, 13/13 pass). Use the workaround; record both the bare env-block (baseline-reproduced) and the workaround pass.

## Required commands (run all; exact output summary + exit codes in execution.md)
1. Targeted unit tests: JAVA_HOME=... mvn test -Dtest=AttachmentTransferServiceTest
2. Worker IT via the master-mandated mysqlIt gate: JAVA_HOME=... mvn -Pmysql-it -Dtest=AttachmentTransferWorkerIT test (real MySQL on 127.0.0.1:3306; Flyway migrates the scratch test DB — never touch production DBs)
3. Flyway IT: JAVA_HOME=... DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test
4. Full suite: JAVA_HOME=... mvn test
Local IMAP fixtures live inside the IT files (JDK socket; no new middleware deps).

## Commit
Commit the implementation locally as: `feat(fast-p): implement 02` — 10 authorized files only. No push/merge/amend/rebase.

## Acceptance for return
Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Write full result to execution.md (files changed, per-command evidence, invariants I-1..I-4 checks, deviations).
