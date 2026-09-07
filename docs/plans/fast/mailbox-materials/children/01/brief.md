# Fast-P Child Brief — 01 附件元数据存储兼容

- Master: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb5)
- Plan: docs/plans/2026-09-07/01-attachment-storage-compat.md — the complete approved contract. Read it first.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials (branch fast/mailbox-materials)
- Base SHA: 8a0c5360e25e875e52800d17797a7b1ea4bd452c
- Execution report: docs/plans/fast/mailbox-materials/children/01/execution.md

## Role
Use execute-p (read skill://execute-p first). Implement ONLY child 01. Serialized run: no later child may be touched. One writer at a time; you are the sole writer now.

## Authorized files (exactly the plan's 10-file list)
1. src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachment.kt
2. src/main/resources/db/migration/V118__allow_attachment_metadata_only.sql (new)
3. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentService.kt
4. src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt
5. src/main/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractor.kt
6. src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxAttachmentServiceTest.kt
8. src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseServiceTest.kt
9. src/test/kotlin/com/weibo/talentintroduction/document/service/DocumentTextExtractorTest.kt
10. src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt

No other files. If compile proof requires an unlisted file, do NOT extend scope — return PLAN_CONFLICT.

## Constraints
- Invariants I-1..I-3 of the plan; persistence contract V118 in the master plan (nullable columns only, no new state columns; keep V36 CHECK/FK/all data).
- Migration numbering: V118 confirmed free at base (max V117). Verify again during your work; never overwrite an applied migration.
- Environment: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. Do not use other JDKs.
- Preserve existing patterns; no refactors of adjacent code; no new abstractions.
- Verification of test changes: tests must fail on the plausible bug they guard, not re-pin implementation.
- Fast-p evidence (docs/plans/fast/**) is excluded from your implementation commit; the controller commits evidence separately. Keep brief/execution/verify/fix logs OUT of the commit.

## Required commands (run all; record exact output summary + exit codes in execution.md)
1. Targeted tests: JAVA_HOME=... mvn test -Dtest=MailboxAttachmentServiceTest,ExpertDocumentBrowseServiceTest,DocumentTextExtractorTest
2. Flyway IT (docker available at baseline): JAVA_HOME=... mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true test
3. Full suite: JAVA_HOME=... mvn test (compile + all tests incl. Node via exec plugin)
4. Baseline env facts (from ledger Baseline): JS node --test green at base; Flyway IT result is being recorded by the controller — if the baseline Flyway IT was env-blocked, reproduce and report the same; do not skip the gate silently.

## Commit
Commit the implementation locally as: `feat(fast-p): implement 01`
Only the 10 authorized files in the commit. No push/merge/amend/rebase/history rewrite.

## Acceptance for return
Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Write the full result (files changed, per-command evidence, invariants check, deviations) to execution.md.
