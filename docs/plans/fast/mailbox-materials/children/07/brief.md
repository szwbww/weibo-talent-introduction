# Fast-P Child Brief — 07 专家会话查询与关注持久化

- Master: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb5)
- Plan: docs/plans/2026-09-07/07-expert-conversations-follow.md — the complete approved contract. Read it first.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials (branch fast/mailbox-materials)
- Base SHA: fb913c58f375f51eb6284d8c3b449134258811f4 (= child 06 Code head)
- Execution report: docs/plans/fast/mailbox-materials/children/07/execution.md

## Role
Use execute-p (read skill://execute-p first). Implement ONLY child 07. Serialized run: children 08+ untouched. You are the sole writer.

## Authorized files (exactly the plan's 10-file list)
1. src/main/resources/db/migration/V121__create_expert_follow.sql (new)
2. src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertFollowService.kt (new)
3. src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt (new)
4. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt (new)
5. src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt (new)
6. src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt (new)
7. src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt (new)
8. src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt (modify: target 120→121)
9. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt (modify: resolveAttachments delegates to 06's resolveMessageAttachments; disable unqualified findFirstByMessageId fallback)
10. src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt (modify)

No other files. Compile/test proof requiring an unlisted file → return PLAN_CONFLICT.

## Constraints
- Invariants I-1..I-4 of the plan; API contract in master plan API契约: GET /api/mail/mailbox/conversations (page=0, size 20 1..100, q?, followed?, pendingOnly?, waitingReply?, accountCode?, original mail filter params; group-by-expert BEFORE pagination; sort latestEvent DESC, contactId DESC); GET .../conversations/{contactId}/messages (limit default 50 1..100, before?, accountCode?; only THIS expert; newest-window ascending); PUT/DELETE .../conversations/{contactId}/follow (idempotent true/false, empty body, no toggle); summary items incl. contactId/name/email/orcid/institution/accountCodes/followed/receivedCount/sentCount/failedCount/pendingCount/waitingReply/latestMessage{...}/latestInbound{processingId,...}|null/materialCount.
- SQL: one normalized UNION SQL basis shared by summary/count/timeline; inbound authority = linked inbound_mail_processing; outbound = OUTBOUND mail_record; exclude unmatched + standalone machine mail; text columns unified utf8mb4_unicode_ci to avoid UNION collation clash; receivedCount = processing rows; sentCount = OUTBOUND AND send_status='SENT'; failedCount = OUTBOUND AND send_status='FAILED'; pendingCount = processing.process_status='MANUAL_REVIEW' (reuse existing repository predicate); waitingReply = account-range history receivedCount=0 AND sentCount>0 (FAILED not counted as sent); direction/date/subject/labels via EXISTS for membership only (never recompute replied-ness); label joins on the corresponding source label without duplicate rows; aggregation happens across the account-range full history (no implicit 7-day window on the default chat); followed & pending/waiting filters compose; timeline cursor (time,source,id) encoded+validated, bound to contact/account scope; same timestamp no loss/dup.
- I-1: DB-group experts before pagination (SQL-level); event time receivedAt or COALESCE(sentAt,createdAt); stable id tiebreak.
- I-4: summary WITHOUT full body; only current expert's timeline loads; latestInbound carries the REAL processing.id (never derived from mail_record); messages DTO has body/cleanedBody rendered via existing clean-display logic, attachmentCount + firstAttachmentNames(≤3), messageId/inReplyTo, sendStatus/processStatus, technical info; taskExecutionId drill-down keeps the legacy table and its date semantics; unmatched inbound keeps its dedicated entry (never a fake 未知专家 in the list); GET never calls IMAP.
- I-3: expert_follow PK (username, expert_contact_id); username ONLY from Session AUTH_USERNAME; body never accepts username; missing/invalid login rejected (401); PUT sets true / DELETE sets false, both idempotent; current admin-only usage, no user table.
- Source resolution: MailboxService.resolveAttachments now delegates to ExpertMaterialService.resolveMessageAttachments; the unqualified findFirstByMessageId fallback (without account/expert/direction bounds) is disabled; MailboxServiceTest extended for exact bridge, unique old relation, cross-account ambiguity.
- Migration numbering: V121 free (max V120). Verify before writing; never overwrite an applied migration. expert_follow = final schema migration of the master plan (V118→V121 chain).
- Tests: MailboxConversationRepositoryIT is the master-mandated REAL-MySQL gate (G-1: no mocks proving GROUP BY) — mysqlIt-gated, run with -Pmysql-it against the provisioned 127.0.0.1:3306 (root/root, talent_introduction), covering multi-label, mixed records, same-timestamp stable pagination + EXPLAIN; MailboxConversationControllerTest calls the REAL ExpertFollowService against a test DB/controlled JDBC (not pure mocks); FlywayMigrationIntegrationTest target 121 with V121 contract.
- Environment: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. Flyway IT bare invocation env-blocked; known-good: DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock + -Dapi.version=1.40.
- Fast-p evidence excluded from your commit.

## Required commands (run all; exact output + exit codes in execution.md)
1. Repository IT (real MySQL): JAVA_HOME=... mvn -Pmysql-it -Dtest=MailboxConversationRepositoryIT test
2. Targeted: JAVA_HOME=... mvn test -Dtest=MailboxConversationControllerTest,MailboxServiceTest
3. Flyway IT (workaround): JAVA_HOME=... DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40 test
4. Full suite: JAVA_HOME=... mvn test

## Commit
Commit the implementation locally as: `feat(fast-p): implement 07` — 10 authorized files only. No push/merge/amend/rebase.

## Acceptance for return
Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Write full result to execution.md (files changed, per-command evidence, I-1..I-4 checks incl. real-MySQL grouping/pagination evidence, deviations).
