# Fast-P Child Brief — 03 MIME 元数据读取与正文白名单

- Master: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb5)
- Plan: docs/plans/2026-09-07/03-mime-metadata-reader.md — the complete approved contract. Read it first.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials (branch fast/mailbox-materials)
- Base SHA: 7c2420e80f98775c1fcf4970598617faca378c0b (= child 02 Code head)
- Execution report: docs/plans/fast/mailbox-materials/children/03/execution.md

## Role
Use execute-p (read skill://execute-p first). Implement ONLY child 03. Serialized run: children 04+ untouched. You are the sole writer.

## Authorized files (exactly the plan's 8-file list)
1. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailReceiveService.kt (modify)
2. src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt (modify)
3. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt (modify)
4. src/main/kotlin/com/weibo/talentintroduction/mail/service/DmarcReportParser.kt (modify)
5. src/main/kotlin/com/weibo/talentintroduction/config/MailAttachmentStorageProperties.kt (modify)
6. src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt (modify)
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMetadataFetchIT.kt (new)
8. src/test/kotlin/com/weibo/talentintroduction/mail/service/DmarcReportParserTest.kt (modify)

No other files. Compile proof requiring an unlisted file → return PLAN_CONFLICT.

## Constraints
- Invariants I-1..I-3 of the plan; metadataOnly default stays false (config-property added, tests opt in). No migration in this child (V119 stays max).
- Environment: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home.
- Preserve existing patterns (constructor param order in MailReceiveService etc.); old ByteArray consumers keep compiling. No adjacent refactors. Fast-p evidence excluded from your commit.
- MySQL test DB provisioned at 127.0.0.1:3306 (root/root, talent_introduction) — for the ImapMetadataFetchIT mysqlIt gate per master command list. Do NOT start/stop containers.
- Flyway IT bare invocation env-blocked (docker-java client API 1.32 vs OrbStack 1.40); known-good: DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock + -Dapi.version=1.40. This child touches no migrations; run the IT only if the plan requires it.

## Required commands (run all; exact output + exit codes in execution.md)
1. Targeted tests: JAVA_HOME=... mvn test -Dtest=ImapMailReceiveServiceTest,DmarcReportParserTest
2. Metadata fetch IT via mysqlIt gate: JAVA_HOME=... mvn -Pmysql-it -Dtest=ImapMetadataFetchIT test
3. Full suite: JAVA_HOME=... mvn test
Local IMAP fixtures live inside the IT (JDK socket; no new middleware deps). Protocol evidence: capture the IMAP command log showing no attachment content FETCH.

## Commit
Commit the implementation locally as: `feat(fast-p): implement 03` — 8 authorized files only. No push/merge/amend/rebase.

## Acceptance for return
Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Write full result to execution.md (files changed, per-command evidence, I-1..I-3 checks incl. real protocol FETCH evidence and 1000-attachment completeness, deviations).
