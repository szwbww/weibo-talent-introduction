# Fast-P Child Brief — 11 资源注册、启用与整体验收门禁

- Master: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb5)
- Plan: docs/plans/2026-09-07/11-release-and-cache-gate.md — the complete approved contract. Read it first.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials (branch fast/mailbox-materials)
- Base SHA: 51e08279cf4a66de598752275acd2a886c7b3c07 (= child 10 Code head)
- Execution report: docs/plans/fast/mailbox-materials/children/11/execution.md

## Role
Use execute-p (read skill://execute-p first). Implement ONLY child 11. You are the sole writer (final child).

## Authorized files (exactly the plan's 10-file list)
1. src/main/resources/static/index.html (modify: register ONLY the new resource references; NO preview-page copying)
2. src/main/kotlin/com/weibo/talentintroduction/config/MailAttachmentStorageProperties.kt (modify: metadataOnly default true; explicit false must remain possible as an emergency fallback)
3. src/test/js/batchSendTaskConsoleVisualFix.test.js (modify)
4. src/test/js/checkRepliesRelocation.test.js (modify)
5. src/test/js/manualReplySubjectPrefill.test.js (modify)
6. src/test/js/overlayAndDialogContrast.test.js (modify)
7. src/test/js/ragKnowledgeBasePage.test.js (modify)
8. src/test/js/ragWorkbenchRender.test.js (modify)
9. src/test/js/trustReplyWorkbenchSharedMount.test.js (modify)
10. docs/runbooks/mailbox-material-chat-rollout.md (new)

No other files. Proof requiring an unlisted file → return PLAN_CONFLICT.

## Constraints
- Invariants I-1..I-3 + S-1..S-5 of the plan; the S resource-registration block is authoritative.
- I-1: ALL of index's 7 resource keys (styles.css, trust-reply-workbench.js, app.js, expert-materials.js, expert-materials.css, mailbox-chat.js, mailbox-chat.css) carry the SAME key `?v=20260907-material-chat`; new JS loads BEFORE app.js (script order: existing workbench → expert-materials → mailbox-chat → app); CSS order: styles → expert-materials → mailbox-chat; keep the original workbench loading position; all 7 pinned-key test files synced to the new key + required resource order — do NOT relax the checks; old key must have 0 hits in production index.
- I-2: metadataOnly defaults TRUE only now (children 01-10 verified); config must still allow explicit false (emergency fallback). Migration remains forward-compatible; rollback priority = disable new components + metadataOnly first, never drop attachment indexes/stored files.
- I-3: runbook documents release + rollback steps (rollout doc): confirm actual HEAD/worktree + max Flyway version → test-DB backup/migration validation → start compatible backend → historical dryRun reconcile if needed → test-mailbox 19+20 and 1000-attachment pressure → enable metadataOnly → load versioned frontend → two-host/check/analyze/send chain acceptance. Rollback: stop new submissions + wait for idle worker / explicitly close active connections; metadataOnly=false returns to legacy receiving only; remove new resource registrations to restore legacy hosts; backend nullable/readiness compat code stays; never roll back to a pre-01 binary that doesn't know null; no DROP of new tables; no deletion of new files; queued tasks continue/pause per operator decision — never lost by page switches. NO production changes in this plan.
- Do NOT copy preview/mock pages into index.html; do not register any DOM/CSS not defined by 08/10.
- NOTE the pre-existing repo caveat: cache-key pin tests exist with TWO spellings — use the actual key value to locate them; the 7 files listed are the plan's authorized set (bump key + assert new resource order where the tests pin index content). Existing FlywayMigrationIntegrationTest / IT gates unaffected by this child (no migration).
- Environment: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. MySQL test DB provisioned at 127.0.0.1:3306 (root/root, talent_introduction). Flyway IT bare invocation env-blocked; known-good: DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock + -Dapi.version=1.40 (this child adds no migration).
- Fast-p evidence excluded from your commit.

## Required commands (run all; exact output + exit codes in execution.md)
1. node --test src/test/js/*.test.js (full JS suite — cache-key tests must be green with the new key)
2. JAVA_HOME=... mvn test (full suite: Java + exec-plugin Node; metadataOnly=true default must not break docker-free tests — if a test assumed default false, adjust ONLY within the authorized 8 test files listed above or report PLAN_CONFLICT)
3. Regression sanity for default-flip: JAVA_HOME=... mvn test -Dtest=ImapMailReceiveServiceTest,MailboxAttachmentServiceTest,ImapMetadataFetchIT -Pmysql-it test (mysqlIt-gated part) — run only as needed to prove the flip is compatible (if the plan requires it); otherwise record the reasoning.
4. node --check src/main/resources/static/app.js && node --check src/main/resources/static/expert-materials.js && node --check src/main/resources/static/mailbox-chat.js (post-registration integrity)

## Commit
Commit the implementation locally as: `feat(fast-p): implement 11` — 10 authorized files only. No push/merge/amend/rebase.

## Acceptance for return
Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Write full result to execution.md (files changed, per-command evidence, I-1..I-3 checks incl. 7-key equality + old-key-zero-hits evidence, deviations).
