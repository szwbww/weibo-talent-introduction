# Fast-P Execution — 06-attachment-flow

## Epoch 1 — IMPLEMENTATION

- Executor: isolated implementer `Implementer06`.
- Product base: `c75693a2e9dc6cc2f5b1a90b57eb84b072e43908` (child 05 terminal code head); child 05 evidence commit precedes this implementation in Git ancestry.
- Plan: `docs/plans/fast/meeting-mail-master/children/06-attachment-flow/brief.md` (byte-identical copy of the approved `docs/plans/2026-09-16/meeting-mail-06-attachment-flow.md`).
- Upstream interfaces inherited: child 04 `OutboundAttachmentService` (`resolveForSend`, `loadSnapshots`, `resolveForMessageDownload`) plus `OutboundAttachmentException` and the `outbound-*` HTTP endpoints; child 05 `mail_record.outbound_attachments_json` snapshot column and `OutboundMailFile` MIME payloads; child 02's shared manual-send finalize gate.
- Required commands: `mvn test -Dtest=PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,RagSendBridgeTest,OutboundAttachmentFlowTest`; `mvn test -Dtest=MailboxConversationControllerTest -DmysqlIt=true` for the real-query path.
- Dispatch: fresh implementer acquired through the Required Agent Gate.


### Result: BLOCKED (one required command could not run — MySQL unavailable)

- Status: implementation complete and committed; unit-level required command PASS; the `-DmysqlIt=true`
  required command could not execute because no MySQL server is listening (see 未验证).
- Commit: `82a46dcc32d50cbc352165656842417bc0a569f2` — `feat(fast-p): implement 06-attachment-flow`
  (branch `fast/meeting-mail-master`, HEAD; exactly the 10 Authorized Files; no docs/plans or evidence
  file included).
- Plan identity: `docs/plans/fast/meeting-mail-master/children/06-attachment-flow/brief.md`
  sha256 `04e4500920152dc7ee3ddc2afd865048506155be42933dfb659453e79151c4ff` (byte-identical to
  `docs/plans/2026-09-16/meeting-mail-06-attachment-flow.md`), unchanged during execution.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master`
  @ `fast/meeting-mail-master` @ `.git/worktrees/weibo-talent-introduction-fast-meeting-mail-master`.
- Product base: `c75693a2e9dc6cc2f5b1a90b57eb84b072e43908`; post-execution code SHA = the commit above.

#### Files changed (all inside the authorized list)

| # | File | Change |
|---|---|---|
| 1 | `mail/service/PendingMailOperationService.kt` | `attachmentIds`/`authenticatedUsername` on both entries + shared gate; 04 file-set resolved before `prepareAndClaim`; one file set feeds `SendPayload.outboundAttachments` and `ComposedMail.outboundAttachments`; SENT-requestId attachment semantics comparison; injected `OutboundAttachmentService` |
| 2 | `mail/controller/UnmatchedInboundMailController.kt` | passes session username + `attachmentIds` to the send gate |
| 3 | `mail/controller/MailboxConversationController.kt` | request DTO gains `attachmentIds`; session identity forwarded; new `ConversationOutboundAttachment` + `outboundAttachments` on the message DTO |
| 4 | `mail/service/MailboxConversationService.kt` | projects the SENT `MANUAL_RICH_REPLY` snapshot from the existing `findAllById` window; corrupt snapshot → empty list + body/path-free diagnostic |
| 5 | `mail/controller/OutboundAttachmentController.kt` | `GET /{contactId}/messages/{mailRecordId}/outbound-attachments/{attachmentId}/download` with message-relationship authorization |
| 6 | `test/.../PendingMailOperationServiceTest.kt` | constructor dep + dual-entry attachment flow, fail-closed matrix, SENT resubmit semantics |
| 7 | `test/.../PendingMailOperationServiceTrustWorkbenchTest.kt` | constructor dep + two-stage confirmation keeps the same file set |
| 8 | `test/.../RagSendBridgeTest.kt` | constructor dep + RAG evidence ordering unaffected by attachments |
| 9 | `test/.../MailboxConversationControllerTest.kt` | session-identity/attachmentIds forwarding; timeline projection (SENT only, one batch read, corrupt/FAILED/INBOUND rows empty) |
| 10 | `test/.../OutboundAttachmentFlowTest.kt` | (new) upload → both send entries → timeline → sent download end-to-end |

#### Commands

| Command | Result | Evidence |
|---|---|---|
| `mvn test -Dtest=PendingMailOperationServiceTest,PendingMailOperationServiceTrustWorkbenchTest,RagSendBridgeTest,OutboundAttachmentFlowTest` (JDK11) | PASS | exit 0, BUILD SUCCESS; `Tests run: 115, Failures: 0, Errors: 0` (Pending 35, TrustWorkbench 60, RagSendBridge 11, OutboundAttachmentFlow 9); node JS suite also ran: 923 pass / 0 fail |
| `mvn test -Dtest=MailboxConversationControllerTest -DmysqlIt=true` (JDK11) | BLOCKED (environment) | exit 1; all 27 tests error with `IllegalStateException: Failed to load ApplicationContext` → `FlywaySqlException: Unable to obtain connection from database: Communications link failure` / `java.net.ConnectException: Connection refused`. No MySQL is listening on 127.0.0.1:3306 (`nc -z` closed) and the OrbStack docker daemon is not running (`docker ps` → `dial unix /Users/lukai/.orbstack/run/docker.sock: no such file or directory`), so the former `docker run mysql:8.0.36` data source cannot be started. Code-independent: no test body executed. |

#### Invariant coverage

- **I-1**: both entries pass the same `attachmentIds` + session identity into `executeManualRichSend`; `resolveForSend` (04 ownership + capacity + real original bytes) runs before `prepareAndClaim`; one file set feeds `SendPayload.outboundAttachments` and `ComposedMail.outboundAttachments`. Proven in `PendingMailOperationServiceTest` (identity taken from `authenticatedUsername`, never the body `operatorName`; `InOrder` resolve-before-claim; cross-user/cross-expert/unknown/duplicate/over-limit → 0 claim/0 SMTP/no delivery classification) and end-to-end in `OutboundAttachmentFlowTest` (403/404/409 for foreign uploader, other expert, missing original, tampered original; `Mockito.verify(..., never()).prepareAndClaim`).
- **I-2**: completed `requestId` reads the original record first (no anchor re-query, no SMTP) and compares ordered filename/contentType/byteLength/sha256 via 04 `loadSnapshots`; identical → original SENT; different (or original-has/now-none) → `OutboundAttachmentException.conflict("该请求已发送，附件与原请求不同，请发起新回复")`; no-attachment short-circuit untouched; UNKNOWN/IN_PROGRESS still 409 "发送状态未知" without resend or requestId switch.
- **I-3**: `outboundAttachments` defaults `[]`; only `MAIL_RECORD + OUTBOUND + MANUAL_RICH_REPLY + SENT` rows project the child-05 snapshot, reusing the same `findAllById` window (verified `times(1)`); FAILED/corrupt/INTRODUCTION/INBOUND rows stay empty and the page stays 200 (`Corrupt outbound attachment snapshot on mail record 500` diagnostic carries no body/path); `attachmentCount`/`firstAttachmentNames`/`materialCount` and `calendarAttachment` semantics unchanged.
- **I-4**: new sent-download endpoint checks session (anonymous 401), `record.contactId == path`, OUTBOUND + MANUAL_RICH_REPLY + SENT, real non-`SIMULATOR_NOOP` sender account readable through the existing `findAllByAccountCodeNot` rule (disabled account still readable), snapshot membership of the attachment id, then 04 verified original read with metadata↔snapshot name/type/size/hash equality; missing/wrong-contact/wrong-message/unreferenced/mismatch/simulator all 404; any logged-in operator may download sent files while draft download stays uploader-only; download SHA equals the SMTP payload SHA.
- **I-5**: `source_inbound_id`, thread anchors, QA/RAG selection and body rendering, unsubscribe pre-check, meeting-body SHA check and two-stage safety confirmation are untouched; the confirmation resubmit re-resolves the same ids to the same file set; missing/tampered originals raise 04's 400/404/409/413 before the claim (never `finalizeFailure`/SMTP UNKNOWN).

#### End-to-end path actually exercised

`POST /api/mail/conversations/{contactId}/outbound-attachments` (real multipart + real temp-dir storage, real SHA) → `sendManualRichReply` / `sendConversationManualRichReply` (real `OutboundAttachmentService`, mocked SMTP, finalize stub writing `mail_record.outbound_attachments_json` with the real codec) → `MailboxConversationService.listMessages` DTO with `outboundAttachments[].downloadUrl` → `GET /api/mail/conversations/{contactId}/messages/{mailRecordId}/outbound-attachments/{attachmentId}/download` (real controller + AuthInterceptor + GlobalExceptionHandler) returning the uploaded bytes with `attachment`/`nosniff`/`private,no-store` headers and the same SHA as the SMTP attachment.

#### Deviation (recorded, inside the authorized scope)

- The three new collaborators are declared with a nullable default (`outboundAttachmentService` in `PendingMailOperationService`; `mailRecordRepository`/`mailSenderAccountRepository` in `OutboundAttachmentController`) and validated with `requireNotNull` at the attachment/endpoint seams, instead of non-null required constructor parameters. Reason: `MeetingCalendarSendIntegrationTest` (positional construction, child 02) and `OutboundAttachmentServiceTest` (standalone `OutboundAttachmentController(service)`, child 04) are **not** in the Authorized Files, so a required parameter would break their compilation and the required commands. This mirrors the pre-existing `mailRecordRagFactRepository`/`ragKnowledgeBase` precedent in the same constructor; Spring injects the real beans at runtime, and every attachment path fails closed.

#### 未验证 (exact error)

- `MailboxConversationControllerTest` (including the new real-SQL timeline projection/batch-read test) was NOT executed:
  `org.flywaydb.core.internal.exception.FlywaySqlException: Unable to obtain connection from database:
  Communications link failure` → `com.mysql.cj.jdbc.exceptions.CommunicationsException` →
  `java.net.ConnectException: Connection refused` (`target/surefire-reports/com.weibo.talentintroduction.mail.controller.MailboxConversationControllerTest.txt`).
  Blocker: no MySQL listening (127.0.0.1:3306 closed; OrbStack docker daemon down). The class itself is
  unaffected: the whole test module compiles (`mvn -q test-compile` exit 0) and only the runtime datasource
  is missing. Re-run once MySQL is up:
  `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailboxConversationControllerTest -DmysqlIt=true`.
- Human acceptance checklists A-1..A-4 (real SMTP sandbox, manual UI flows) were not performed by this implementer.
