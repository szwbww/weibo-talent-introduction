# Fast-P Child Brief — 05 检查回复进度与机器邮件隔离

- Master: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb5)
- Plan: docs/plans/2026-09-07/05-reply-check-progress-and-machine-mail.md — the complete approved contract. Read it first.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials (branch fast/mailbox-materials)
- Base SHA: c9f80906c872b21d0f4887c7c3f4c95e961b1cef (= child 04 Code head)
- Execution report: docs/plans/fast/mailbox-materials/children/05/execution.md

## Role
Use execute-p (read skill://execute-p first). Implement ONLY child 05. Serialized run: children 06+ untouched. You are the sole writer.

## Authorized files (exactly the plan's 10-file list)
1. src/main/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyService.kt (modify)
2. src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt (modify)
3. src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt (modify)
4. src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt (modify)
5. src/main/kotlin/com/weibo/talentintroduction/mail/service/DmarcAttachmentTransferConsumer.kt (new)
6. src/main/kotlin/com/weibo/talentintroduction/config/MailAttachmentStorageProperties.kt (modify)
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchAutoMailReplyServiceTest.kt (modify)
8. src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt (modify)
9. src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMetadataFetchIT.kt (MODIFY-EXTEND: file was created by child 03 at this same path; plan wording 新增 predates 03. Extend it with the slow-transfer/disconnect/next-account protocol tests — same path stays authorized, no new file)
10. src/test/kotlin/com/weibo/talentintroduction/mail/service/DmarcAttachmentTransferConsumerTest.kt (new)

No other files. Compile/test proof requiring an unlisted file → return PLAN_CONFLICT.

## Constraints
- Invariants I-1..I-4 of the plan; task-progress contract from master 持久化契约 05: only a new `accountProgress` object key inside the existing details JSON (accountCode/phase/startedAt/updatedAt); existing accountsPolled/fetched keys unchanged; NO new TaskProgressLog DB columns; no new task terminal states (SUCCESS/FAILED per account; COMPLETED/PARTIAL_SUCCESS/FAILED/CANCELLED aggregate); file-transfer progress never counted as checked mail; partial failure keeps completed account counts; execution-token validation intact.
- accountProgress.phase limited to CONNECTING/READING_METADATA/PROCESSING_MAIL/COMPLETED/FAILED.
- I-2 budgets: per-account IMAP receive window 120s total (configurable via MailAttachmentStorageProperties-style config per plan), per-message 60s; deadline really closes the connection (watchdog forceClose + finally cleanup); cancel only at safe boundaries — never claim SMTP cancellation; message shows 正在结束当前处理 when mid-business-processing.
- I-3 DMARC: metadata-mode DMARC branch enqueues a SYSTEM request via the child-02 queue (purpose=DMARC, no attachmentId, no expert document), does NOT wait on the checking thread; DmarcAttachmentTransferConsumer registers purpose=DMARC consumer; bounded unzip (total XML cap 20MiB, max 10 archive members), reject path traversal and over-limit compression; wrap extracted XML as original ReceivedMailAttachment, confirm original DmarcReportParser returns non-null BEFORE calling original IngestService (never let parse-null silent-skip read as success); never hand compressed content to the legacy unbounded readBytes; cleanup machine temp files after success (only aggregate report data persists); parse failure -> task FAILED with errorCode=DMARC_PARSE_FAILED; queue retry restarts fetch from scratch is v1-allowed; existing machine-message detector uses filename only, rules unchanged.
- I-4: partial failure reports accurate completed-processed counts with account results.
- Environment: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home. MySQL test DB provisioned at 127.0.0.1:3306 (root/root, talent_introduction). Flyway IT bare invocation env-blocked; known-good: DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock + -Dapi.version=1.40 (this child adds no migration; run only if the plan requires).
- Fast-p evidence excluded from your commit.

## Required commands (run all; exact output + exit codes in execution.md)
1. Targeted tests: JAVA_HOME=... mvn test -Dtest=BatchAutoMailReplyServiceTest,MailAutomationControllerTest,DmarcAttachmentTransferConsumerTest
2. Protocol IT via mysqlIt gate: JAVA_HOME=... mvn -Pmysql-it -Dtest=ImapMetadataFetchIT test
3. Full suite: JAVA_HOME=... mvn test

## Commit
Commit the implementation locally as: `feat(fast-p): implement 05` — 10 authorized files only. No push/merge/amend/rebase.

## Acceptance for return
Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Write full result to execution.md (files changed, per-command evidence, I-1..I-4 checks, deviations).
