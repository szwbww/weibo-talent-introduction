# fast-p child 03 brief — 人工回复接入与历史日历下载

- Master: docs/plans/2026-09-09/00-meeting-confirmation-master.md (commit 6d05499f65cdf1f55c5f92c9b0dad4bbd2907dc3)
- Child plan (THE complete approved contract — read fully first): docs/plans/2026-09-09/03-meeting-confirmation-send-download.md (commit 6d05499f65cdf1f55c5f92c9b0dad4bbd2907dc3)
- Shared audit (part of the contract): docs/plans/2026-09-09/meeting-confirmation-audit.md; code evidence: docs/plans/2026-09-09/meeting-confirmation-evidence/
- Dependencies: 01 (LIGHT_PASS), 02 (LIGHT_PASS). Downstream consumers: child 04 frontend (downloadUrl + calendarAttachment metadata + send contract), child 05.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-confirmation (branch fast/meeting-confirmation)
- Child base SHA: 6a1b54697778bdea64002516d5c63fbabedd0f13 (child 02 terminal code head)
- Execution report: docs/plans/fast/meeting-confirmation/children/03/execution.md
- Fix log: docs/plans/fast/meeting-confirmation/children/03/fix-log.md
- Implementer protocol: use execute-p skill. No inherited conversation. This brief adds run-specific environment; the child plan file is the authority on requirements.

## Authorized files (exactly 10; modify ONLY these)

1. src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt
2. src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt
3. src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxConversationService.kt
4. src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationController.kt
5. src/main/kotlin/com/weibo/talentintroduction/mail/controller/CalendarAttachmentController.kt (NEW)
6. src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt
7. src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt
8. src/test/kotlin/com/weibo/talentintroduction/mail/RagSendBridgeTest.kt
9. src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxConversationControllerTest.kt
10. src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt

## Work to implement (from child plan T1..T4 + Invariants I-1..I-4)

- T1 (I-1/I-2): PendingManualRichReplyRequest and sendManualRichReply gain trailing `meeting: MeetingInput? = null, previewAttachmentSha256: String? = null`; UnmatchedInboundMailController.manualRichReply passes them through. meeting + digest must both be present or both null → else 400 “会议附件配置不完整，请重新预览”. Pending service gains MeetingConfirmationService dependency; update the 3 existing Pending construction test sites with a real/mock dependency — no default-null that silently drops the production dep.
- After finalSubject/finalText/finalHtml render: call child-01 validateAndBuild with the server-resolved account/recipient/contact + processing; sha mismatch → 400 “会议配置已变化，请重新预览”; template-disabled rejection follows 01. Normalize expected text / finalText / MailContentService.htmlToPlainText(finalHtml) uniformly (CRLF→LF, NBSP→space, all consecutive whitespace→single space, trim); BOTH derived bodies must contain the FULL expected meeting text (not date/link keyword search); else 400 “会议正文与附件不一致，请编辑会议后重新生成，或移除日历附件”.
- Pass the same 01 snapshot instance into SendPayload and ComposedMail. With meeting: inReplyTo AND references both = cleaned real record.messageId (existing max255 check retained); without meeting: original construction unchanged. Safety checks never auto-confirm because Zoom came from the form; original warning path requires the original confirmation flow; payload extension fields preserved. DEDUP/SENT/UNKNOWN/safe-failure strictly follow original branches.
- T2 (I-3/I-4): MailboxConversationService gains MailRecordRepository dependency; on the current page.rows MAIL_RECORD OUTBOUND SENT id set do findAllById exactly once; attach metadata only when contact/account ownership matches and the snapshot parses. INBOUND rows with equal numeric ids never read the outbound snapshot. Don't change union/keyset/count/current expert-material parsing. DTO trailing nullable default keeps existing construction sites.
- New CalendarAttachmentController.kt injecting MailRecordRepository + MailSenderAccountRepository; GET /api/mail/conversations/{contactId}/messages/{mailRecordId}/calendar-attachment. Snapshot parsing via child-01 CalendarAttachmentCodec.parseOrNull at both read sites; ownership/status checks explicit at both. Not found / mismatch / not SENT / empty snapshot / corrupt → uniform 404 “日历附件不可用”. Active account scope uses the SAME expression as MailboxConversationService.activeAccountCodes (findAllByAccountCodeNot(SIMULATOR_ACCOUNT_CODE)) — no extra enabled condition, no new user-level permission. Response: 200, Content-Type text/calendar; charset=UTF-8, Content-Disposition attachment (Spring ContentDisposition safe filename), Content-Length real bytes, Cache-Control private,no-store, X-Content-Type-Options nosniff. Auth via existing interceptor; download never logs URL/query, no external network, no state change.
- T3 (I-1..I-4): extend PendingMailOperationServiceTest with calendar scenarios (real generator + controller params + actual ComposedMail; controller param passing; safety confirmation retains meeting; sha/content/ownership rejection; no-meeting old path). Inside MailboxConversationControllerTest.kt add class CalendarAttachmentIntegrationTest covering timeline batch read / ownership / corrupt / old-row null and real HTTP bytes. The 3 Pending tests get constructor injection added; MailboxConversationControllerTest gains @MockBean MailRecordRepository with default findAllById returning empty set, individual stubs for calendar scenarios, so the mysqlIt context lacks no bean; existing assertions stay.
- T4 (I-2): UnmatchedInboundMailController param additions shift operatorStatus-mapping lines; sync OperatorStatusWriteSeamGuardTest NoiseSite actual line numbers for that file (path/context unchanged; whitelist not widened). Measure with `rg -n 'operatorStatus = ' UnmatchedInboundMailController.kt` — never guess offsets; a changed fragment is out of plan scope.
- Keep all 4 invariants; especially I-2: meeting validation happens AFTER final variable render, BEFORE Safety/claim/SMTP; suppression/RAG-QA exclusivity/safetyWarningConfirmed/strongConfirmationText and audit convergence retained; thread headers from the one real processing.messageId.

## Environment

- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home (JDK 11 mandatory).
- Dedicated fresh empty MySQL test DB for the plan-mandated `-Pmysql-it` gate: database `talent_introduction_mc03` in container `mailbox-refinement-mysql` (mysql:8.0.36, root/root) at 127.0.0.1:3306 — provisioned empty for this run; Flyway applies migrations on context start. Run gate as: DB_URL='jdbc:mysql://localhost:3306/talent_introduction_mc03?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai' DB_USERNAME=root DB_PASSWORD=root mvn -Pmysql-it -Dtest=MailboxConversationControllerTest test — never point it at talent_introduction (the prior run's DB) or any real business DB. Record the listener check in execution.md. Do not stop/start/modify the container.
- Migration IT (not a child-03 required command, but if run): DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock + -Dapi.version=1.40 + -DmigrationIt=true.
- Child 01 codec/service and child 02 column/fingerprint/MIME are committed dependencies at the base (6a1b546); reuse exactly. No public send API duplication: app.js mcHostSendRichReply needs no change (03 adds no second send API).

## Required commands (run all freshly; exact command, exit code, counts in execution.md; unverifiable → NOT_RUN, never pass)

1. Targeted unit classes: JAVA_HOME=... mvn test -Dtest=PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,RagSendBridgeTest
2. MySQL IT gate against the isolated empty DB (command above with DB_URL/DB_USERNAME/DB_PASSWORD env) — plan mandates `mvn -Pmysql-it -Dtest=MailboxConversationControllerTest test`; record container listener verification first.
3. Full suite: JAVA_HOME=... mvn test (fresh; baselines: seed 3234/0/9 + node 766/0; child-01 head 3283/0/9; child-02 head 3300/0/9)

## Downstream interfaces child 04 frontend will consume (must match plan exactly)

- conversation timeline message items: trailing `calendarAttachment: { filename, byteLength, downloadUrl } | null` — only MAIL_RECORD+OUTBOUND+SENT rows; child 04 renders the download link/card from downloadUrl and file bytes only through GET calendar-attachment.
- Send request: optional meeting + previewAttachmentSha256 passed through the EXISTING manual-rich-reply endpoint (no new send API); 400 messages verbatim from the plan.
- GET calendar-attachment downloadUrl returns exactly the archived original bytes (sha == preview == SMTP) — child 04 never re-renders.

## Constraints

- Only the 10-file whitelist. A compile/test proof requiring another file → STOP and report PLAN_CONFLICT (do not extend scope).
- No SMTP to real mailboxes, no new DB columns/tables (V123 already landed in 02), no external storage, no cache framework, no meeting state sync.
- The OperatorStatusWriteSeamGuardTest sync is limited to actual measured line numbers for UnmatchedInboundMailController entries; do not touch other noise sites.
- Do not modify files outside the whitelist; docs/plans/fast/** stays out of your commit.
- Commit implementation locally as: feat(fast-p): implement 03
- Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Do not review later children, repair unrelated behavior, push, merge, or rewrite history.
